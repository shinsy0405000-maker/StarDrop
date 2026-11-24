import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

/**
 * 행성: 겉모습(가스, 암석, 지구형, 용암, 얼음)을 난수로 직접 그려서 이미지로 저장해 둔다.
 * 그리는 순서와 난수 순서는 웹 버전의 drawPlanetSprite 와 같아서, 같은 구역이면 웹과 같은 모양이 나온다.
 */
class Planet extends GravityBody {
    private static final double GRAVITY = 2200;   // 질량 = GRAVITY * 반지름^2
    private static final double TAU = Math.PI * 2;

    private final double radius;
    private final boolean hasMoon;
    // 겉모습 (Level 이 정해 준다)
    private String kind = "rock";
    private double hue, tilt;
    private int lookSeed;
    private boolean ring;
    private final CachedImage sprite = new CachedImage();   // 지금 창 크기로 그려 둔 그림

    Planet(double x, double y, double radius, boolean hasMoon) {
        super(x, y, GRAVITY * radius * radius, radius, 0);
        if (radius <= 0) throw new IllegalArgumentException("행성 반지름은 0보다 커야 합니다: " + radius);
        this.radius = radius;
        this.hasMoon = hasMoon;
    }

    public double getRadius() { return radius; }
    public boolean hasMoon() { return hasMoon; }

    void setLook(String kind, double hue, double tilt, int lookSeed, boolean ring) {
        this.kind = kind;
        this.hue = hue;
        this.tilt = tilt;
        this.lookSeed = lookSeed;
        this.ring = ring;
        sprite.clear();
    }

    /** 그림의 절반 크기 (고리가 있으면 더 크다) */
    private double half() {
        return (ring ? 2.5 : 1.75) * radius;
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        AffineTransform world = g.getTransform();
        double half = half();
        int px = Math.max(8, (int) Math.ceil(half * 2 * world.getScaleX()));
        BufferedImage img = sprite.get(() -> makeSprite(g, px), px);     // 화면 크기가 바뀔 때만 다시 그린다
        Point2D corner = world.transform(new Point2D.Double(getX() - half, getY() - half), null);
        g.setTransform(new AffineTransform());      // 화면 픽셀 그대로 그린다
        g.drawImage(img, (int) Math.round(corner.getX()), (int) Math.round(corner.getY()), null);
        g.setTransform(world);
    }

    private BufferedImage makeSprite(Graphics2D screen, int px) {
        BufferedImage img = ImageCache.newImage(screen, px, px, true);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double R = radius, half = half(), h = hue;
        g.scale(px / (2 * half), px / (2 * half));
        g.translate(half, half);
        Mulberry32 r = new Mulberry32(lookSeed);
        double kindHue = switch (kind) {
            case "gas" -> h;
            case "rock" -> 20 + (h % 30);
            case "terra" -> 205;
            case "lava" -> 16;
            default -> 195;
        };

        // 대기 글로우 (반지름 0.85R 부터 1.7R 까지 옅어진다)
        g.setPaint(new RadialGradientPaint(0, 0, (float) (R * 1.7), new float[]{0.5f, 0.675f, 1f},
                new Color[]{hsla(kindHue, 85, 65, 0.42), hsla(kindHue, 85, 60, 0.14), hsla(kindHue, 85, 60, 0)}));
        g.fill(circle(0, 0, R * 1.7));

        if (ring) drawRing(g, false);
        AffineTransform base = g.getTransform();
        Shape body = circle(0, 0, R);
        Rectangle2D square = new Rectangle2D.Double(-R, -R, 2 * R, 2 * R);
        g.clip(body);

        if (kind.equals("gas")) {                       // 가스 행성: 줄무늬
            g.setColor(hsla(h, 55, 55, 1));
            g.fill(square);
            g.rotate(tilt);
            double y = -R * 1.2;
            while (y < R * 1.2) {
                double band = R * (0.07 + r.next() * 0.2);
                g.setColor(hsla(h + (r.next() - 0.5) * 50, 45 + r.next() * 30, 38 + r.next() * 34, 0.75));
                Path2D p = new Path2D.Double();
                p.moveTo(-R * 1.3, y);
                p.curveTo(-R * 0.4, y + (r.next() - 0.5) * R * 0.14, R * 0.4, y + (r.next() - 0.5) * R * 0.14, R * 1.3, y);
                p.lineTo(R * 1.3, y + band + 1);
                p.lineTo(-R * 1.3, y + band + 1);
                g.fill(p);
                y += band;
            }
            for (int i = 0; i < 3; i++) {               // 소용돌이 점
                g.setColor(hsla(h + 30, 70, 70 - i * 10, 0.5));
                g.fill(ellipse((r.next() - 0.5) * R * 1.2, (r.next() - 0.5) * R * 1.2, R * (0.1 + r.next() * 0.12), R * 0.06, 0));
            }
        } else if (kind.equals("rock")) {               // 암석 행성: 얼룩과 크레이터
            g.setColor(hsla(kindHue, 18, 46, 1));
            g.fill(square);
            blot(g, r, 22, () -> hsla(kindHue, 10 + r.next() * 15, 28 + r.next() * 36, 0.4), 0.08, 0.4);
            for (int i = 0; i < 9; i++) {
                double a = r.next() * TAU, d = Math.sqrt(r.next()) * R * 0.9, cr = R * (0.05 + r.next() * 0.13);
                double x = JsMath.cos(a) * d, y = JsMath.sin(a) * d;
                g.setColor(new Color(0, 0, 0, 71));
                g.fill(circle(x, y, cr));
                g.setColor(new Color(255, 255, 255, 56));
                g.setStroke(new BasicStroke((float) Math.max(0.6, cr * 0.18)));
                g.draw(arc(x, y, cr, 0.4, 2.6));
            }
        } else if (kind.equals("terra")) {              // 지구형: 바다, 땅, 극지방 얼음, 구름
            g.setColor(hsla(208 + r.next() * 14, 62, 42, 1));
            g.fill(square);
            for (int k = 0; k < 5; k++) {
                double cx = (r.next() - 0.5) * R * 1.6, cy = (r.next() - 0.5) * R * 1.6, land = 90 + r.next() * 50;
                for (int i = 0; i < 9; i++) {
                    g.setColor(hsla(land + (r.next() - 0.5) * 40, 35 + r.next() * 20, 30 + r.next() * 18, 0.85));
                    g.fill(ellipse(cx + (r.next() - 0.5) * R * 0.5, cy + (r.next() - 0.5) * R * 0.5,
                            R * (0.08 + r.next() * 0.2), R * (0.06 + r.next() * 0.14), r.next() * 3));
                }
            }
            g.setColor(new Color(255, 255, 255, 217));
            g.fill(ellipse(0, -R * 0.96, R * 0.5, R * 0.14, 0));
            g.fill(ellipse(0, R * 0.97, R * 0.4, R * 0.12, 0));
            g.rotate(tilt * 0.5);
            for (int i = 0; i < 12; i++) {
                g.setColor(new Color(1f, 1f, 1f, (float) (0.25 + r.next() * 0.35)));
                g.fill(ellipse((r.next() - 0.5) * R * 1.8, (r.next() - 0.5) * R * 1.7, R * (0.15 + r.next() * 0.3), R * (0.03 + r.next() * 0.05), 0));
            }
        } else if (kind.equals("lava")) {               // 용암 행성: 어두운 땅에 빛나는 균열
            g.setColor(new Color(0x2b, 0x10, 0x0c));
            g.fill(square);
            blot(g, r, 14, () -> hsla(10 + r.next() * 14, 45, 10 + r.next() * 12, 0.6), 0.1, 0.35);
            for (int i = 0; i < 13; i++) {
                double x = (r.next() - 0.5) * R * 1.8, y = (r.next() - 0.5) * R * 1.8, a = r.next() * TAU;
                Path2D crack = new Path2D.Double();
                crack.moveTo(x, y);
                for (int k = 0; k < 6; k++) {
                    a += (r.next() - 0.5) * 1.5;
                    x += JsMath.cos(a) * R * 0.16;
                    y += JsMath.sin(a) * R * 0.16;
                    crack.lineTo(x, y);
                }
                Color c = hsla(22 + r.next() * 26, 100, 55 + r.next() * 20, 0.9);
                float w = (float) (R * (0.015 + r.next() * 0.035));
                // 웹의 shadowBlur(주황 번짐) 대신: 넓고 옅은 주황 선을 먼저 깐다
                g.setColor(new Color(255, 122, 42, 50));
                g.setStroke(new BasicStroke((float) (w + R * 0.16), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(crack);
                g.setColor(c);
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(crack);
            }
        } else {                                        // 얼음 행성: 밝은 바탕에 줄금
            g.setColor(hsla(190 + r.next() * 20, 45, 78, 1));
            g.fill(square);
            blot(g, r, 12, () -> hsla(195 + r.next() * 25, 40 + r.next() * 30, 60 + r.next() * 30, 0.5), 0.1, 0.4);
            g.rotate(tilt);
            for (int i = 0; i < 16; i++) {
                g.setColor(hsla(200 + r.next() * 20, 60, 40 + r.next() * 50, 0.4));
                g.setStroke(new BasicStroke((float) (R * (0.01 + r.next() * 0.025))));
                double y = (r.next() - 0.5) * R * 2;
                Path2D line = new Path2D.Double();
                line.moveTo(-R, y);
                line.quadTo(0, y + (r.next() - 0.5) * R * 0.7, R, y + (r.next() - 0.5) * R * 0.5);
                g.draw(line);
            }
        }
        g.setTransform(base);

        // 명암: 빛은 왼쪽 위에서 온다
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-R * 0.25, -R * 0.25), (float) (R * 1.55), new Point2D.Double(-R * 0.5, -R * 0.5),
                new float[]{0f, 0.032f, 0.323f, 0.729f, 1f},
                new Color[]{new Color(255, 255, 255, 56), new Color(255, 255, 255, 56), new Color(0, 0, 0, 0), new Color(2, 2, 14, 158), new Color(2, 2, 14, 237)},
                CycleMethod.NO_CYCLE));
        g.fill(square);
        g.setClip(null);
        g.setColor(hsla(kindHue, 90, 82, 0.3));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(arc(0, 0, R - 0.6, 2.6, 5.2));
        if (ring) drawRing(g, true);
        g.dispose();
        return img;
    }

    /** 얼룩 n개: 웹의 blot 과 같은 순서로 난수를 쓴다 (위치 → 색 → 크기 → 기울기) */
    private void blot(Graphics2D g, Mulberry32 r, int n, Supplier<Color> color, double min, double max) {
        double R = radius;
        for (int i = 0; i < n; i++) {
            double a = r.next() * TAU, d = Math.sqrt(r.next()) * R;
            g.setColor(color.get());
            double rx = R * (min + r.next() * (max - min)), ry = R * (min + r.next() * (max - min)) * (0.5 + r.next() * 0.5);
            g.fill(ellipse(JsMath.cos(a) * d, JsMath.sin(a) * d, rx, ry, r.next() * 3));
        }
    }

    /** 고리: 뒤쪽 반은 행성보다 먼저, 앞쪽 반은 나중에 그려서 행성을 감싸 보이게 한다 */
    private void drawRing(Graphics2D g, boolean front) {
        AffineTransform old = g.getTransform();
        g.rotate(tilt * 0.6 + 0.25);
        for (int i = 0; i < 5; i++) {
            double rx = radius * (1.45 + i * 0.19), ry = rx * 0.27;
            g.setColor(hsla(hue + 20 + i * 8, 55, 72 - i * 5, 0.55 - i * 0.07));
            g.setStroke(new BasicStroke((float) (radius * (i % 2 == 1 ? 0.05 : 0.12))));
            g.draw(new Arc2D.Double(-rx, -ry, rx * 2, ry * 2, front ? 180 : 0, 180, Arc2D.OPEN));
        }
        g.setTransform(old);
    }

    // ------------------------------------------------------------ 웹(캔버스)과 같은 모양을 만드는 도우미
    /** CSS 의 hsla(색상, 채도%, 밝기%, 투명도 0~1) */
    private static Color hsla(double h, double s, double l, double a) {
        return Paints.hsl(h, s / 100, l / 100, (int) Math.round(a * 255));
    }

    private static Shape circle(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, r * 2, r * 2);
    }

    /** 가운데 (x, y), 반지름 rx, ry, rot 라디안만큼 기운 타원 */
    private static Shape ellipse(double x, double y, double rx, double ry, double rot) {
        Shape e = new Ellipse2D.Double(-rx, -ry, rx * 2, ry * 2);
        AffineTransform at = AffineTransform.getTranslateInstance(x, y);
        at.rotate(rot);
        return at.createTransformedShape(e);
    }

    /** 캔버스의 arc(x, y, r, 시작, 끝): 시계 방향 라디안 */
    private static Shape arc(double x, double y, double r, double from, double to) {
        return new Arc2D.Double(x - r, y - r, r * 2, r * 2, -Math.toDegrees(from), -Math.toDegrees(to - from), Arc2D.OPEN);
    }
}
