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
import java.util.Random;

/** 행성: 겉모습(가스, 암석, 지구형, 용암, 얼음)을 난수로 직접 그려서 이미지로 저장해 둔다 */
class Planet extends GravityBody {
    private static final String[] KINDS = {"gas", "rock", "terra", "lava", "ice", "gas"};
    private static final double GRAVITY = 2200;   // 질량 = GRAVITY * 반지름^2

    private final double radius;
    private final long lookSeed;     // 겉모습을 정하는 난수 씨앗
    private BufferedImage sprite;    // 처음 그릴 때 한 번만 만드는 원본 그림
    private final CachedImage scaled = new CachedImage();   // 지금 창 크기에 맞게 줄여 둔 그림 (매 프레임 확대/축소하면 느리다)
    private double spriteHalf;

    Planet(double x, double y, double radius, long lookSeed) {
        super(x, y, GRAVITY * radius * radius, radius, 0);
        if (radius <= 0) throw new IllegalArgumentException("행성 반지름은 0보다 커야 합니다: " + radius);
        this.radius = radius;
        this.lookSeed = lookSeed;
    }

    public double getRadius() { return radius; }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        if (sprite == null) sprite = makeSprite();
        AffineTransform world = g.getTransform();
        double s = world.getScaleX();
        int px = Math.max(2, (int) Math.round(spriteHalf * 2 * s));
        BufferedImage img = scaled.get(() -> shrink(g, px), px);     // 화면 크기가 바뀔 때만 다시 줄인다
        Point2D corner = world.transform(new Point2D.Double(getX() - spriteHalf, getY() - spriteHalf), null);
        g.setTransform(new AffineTransform());      // 화면 픽셀 그대로 그린다
        g.drawImage(img, (int) Math.round(corner.getX()), (int) Math.round(corner.getY()), null);
        g.setTransform(world);
    }

    /** 원본 그림을 화면 크기(px × px)로 부드럽게 줄인 그림 */
    private BufferedImage shrink(Graphics2D screen, int px) {
        BufferedImage img = ImageCache.newImage(screen, px, px, true);
        Graphics2D sg = img.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        sg.drawImage(sprite, 0, 0, px, px, null);
        sg.dispose();
        return img;
    }

    private BufferedImage makeSprite() {
        Random r = new Random(lookSeed);
        String kind = KINDS[r.nextInt(KINDS.length)];
        double hue = r.nextDouble() * 360, tilt = (r.nextDouble() - 0.5) * 0.9, R = radius;
        boolean ring = kind.equals("gas") && R > 42 && r.nextDouble() < 0.6;
        double glowHue = kind.equals("gas") ? hue : kind.equals("rock") ? 20 + hue % 30
                : kind.equals("terra") ? 205 : kind.equals("lava") ? 16 : 195;

        spriteHalf = (ring ? 2.5 : 1.75) * R;
        int size = (int) Math.ceil(spriteHalf * 2 * 2.5);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / (spriteHalf * 2), size / (spriteHalf * 2));
        g.translate(spriteHalf, spriteHalf);

        // 대기 글로우
        g.setPaint(new RadialGradientPaint(0, 0, (float) (R * 1.7), new float[]{0.5f, 0.65f, 1f},
                new Color[]{Paints.hsl(glowHue, 0.85, 0.65, 105), Paints.hsl(glowHue, 0.85, 0.6, 34), Paints.hsl(glowHue, 0.85, 0.6, 0)}));
        g.fill(new Ellipse2D.Double(-R * 1.7, -R * 1.7, R * 3.4, R * 3.4));

        if (ring) drawRing(g, hue, tilt, false);
        Shape oldClip = g.getClip();
        AffineTransform base = g.getTransform();
        Rectangle2D all = new Rectangle2D.Double(-R * 1.4, -R * 1.4, R * 2.8, R * 2.8);
        g.clip(new Ellipse2D.Double(-R, -R, 2 * R, 2 * R));

        if (kind.equals("gas")) {                       // 가스 행성: 줄무늬
            g.setColor(Paints.hsl(hue, 0.55, 0.55, 255));
            g.fill(all);
            g.rotate(tilt);
            double y = -R * 1.2;
            while (y < R * 1.2) {
                double bandH = R * (0.07 + r.nextDouble() * 0.2);
                g.setColor(Paints.hsl(hue + (r.nextDouble() - 0.5) * 50, 0.45 + r.nextDouble() * 0.3, 0.38 + r.nextDouble() * 0.34, 190));
                Path2D band = new Path2D.Double();
                band.moveTo(-R * 1.3, y);
                band.curveTo(-R * 0.4, y + (r.nextDouble() - 0.5) * R * 0.14, R * 0.4, y + (r.nextDouble() - 0.5) * R * 0.14, R * 1.3, y);
                band.lineTo(R * 1.3, y + bandH + 1);
                band.lineTo(-R * 1.3, y + bandH + 1);
                band.closePath();
                g.fill(band);
                y += bandH;
            }
        } else if (kind.equals("rock")) {               // 암석 행성: 얼룩과 크레이터
            g.setColor(Paints.hsl(glowHue, 0.18, 0.46, 255));
            g.fill(all);
            for (int i = 0; i < 22; i++) blot(g, r, R, Paints.hsl(glowHue, 0.1 + r.nextDouble() * 0.15, 0.28 + r.nextDouble() * 0.36, 100), 0.08, 0.4);
            for (int i = 0; i < 9; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * R * 0.9, cr = R * (0.05 + r.nextDouble() * 0.13);
                double cx = Math.cos(a) * d, cy = Math.sin(a) * d;
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(new Ellipse2D.Double(cx - cr, cy - cr, cr * 2, cr * 2));
                g.setColor(new Color(255, 255, 255, 56));
                g.setStroke(new BasicStroke((float) Math.max(0.6, cr * 0.18)));
                g.draw(new Arc2D.Double(cx - cr, cy - cr, cr * 2, cr * 2, 200, 130, Arc2D.OPEN));
            }
        } else if (kind.equals("terra")) {              // 지구형: 바다, 땅, 구름
            g.setColor(Paints.hsl(208 + r.nextDouble() * 14, 0.62, 0.42, 255));
            g.fill(all);
            for (int k = 0; k < 5; k++) {
                double cx = (r.nextDouble() - 0.5) * R * 1.6, cy = (r.nextDouble() - 0.5) * R * 1.6, landHue = 90 + r.nextDouble() * 50;
                for (int i = 0; i < 9; i++) {
                    g.setColor(Paints.hsl(landHue + (r.nextDouble() - 0.5) * 40, 0.35 + r.nextDouble() * 0.2, 0.3 + r.nextDouble() * 0.18, 215));
                    double w = R * (0.08 + r.nextDouble() * 0.2), h = R * (0.06 + r.nextDouble() * 0.14);
                    g.fill(new Ellipse2D.Double(cx + (r.nextDouble() - 0.5) * R * 0.5 - w, cy + (r.nextDouble() - 0.5) * R * 0.5 - h, w * 2, h * 2));
                }
            }
            g.rotate(tilt * 0.5);
            for (int i = 0; i < 12; i++) {
                g.setColor(new Color(255, 255, 255, 64 + r.nextInt(90)));
                double w = R * (0.15 + r.nextDouble() * 0.3), h = R * (0.03 + r.nextDouble() * 0.05);
                g.fill(new Ellipse2D.Double((r.nextDouble() - 0.5) * R * 1.8 - w, (r.nextDouble() - 0.5) * R * 1.7 - h, w * 2, h * 2));
            }
        } else if (kind.equals("lava")) {               // 용암 행성: 어두운 땅에 빛나는 균열
            g.setColor(new Color(43, 16, 12));
            g.fill(all);
            for (int i = 0; i < 14; i++) blot(g, r, R, Paints.hsl(10 + r.nextDouble() * 14, 0.45, 0.1 + r.nextDouble() * 0.12, 150), 0.1, 0.35);
            for (int i = 0; i < 13; i++) {
                double x = (r.nextDouble() - 0.5) * R * 1.8, y = (r.nextDouble() - 0.5) * R * 1.8, a = r.nextDouble() * Math.PI * 2;
                Path2D crack = new Path2D.Double();
                crack.moveTo(x, y);
                for (int k = 0; k < 6; k++) {
                    a += (r.nextDouble() - 0.5) * 1.5;
                    x += Math.cos(a) * R * 0.16;
                    y += Math.sin(a) * R * 0.16;
                    crack.lineTo(x, y);
                }
                float w = (float) (R * (0.015 + r.nextDouble() * 0.035));
                double crackHue = 22 + r.nextDouble() * 26;
                g.setStroke(new BasicStroke(w * 3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(Paints.hsl(crackHue, 1, 0.55, 60));
                g.draw(crack);
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(Paints.hsl(crackHue, 1, 0.6 + r.nextDouble() * 0.15, 235));
                g.draw(crack);
            }
        } else {                                        // 얼음 행성: 밝은 바탕에 줄금
            g.setColor(Paints.hsl(190 + r.nextDouble() * 20, 0.45, 0.78, 255));
            g.fill(all);
            for (int i = 0; i < 12; i++) blot(g, r, R, Paints.hsl(195 + r.nextDouble() * 25, 0.4 + r.nextDouble() * 0.3, 0.6 + r.nextDouble() * 0.3, 128), 0.1, 0.4);
            g.rotate(tilt);
            for (int i = 0; i < 16; i++) {
                g.setColor(Paints.hsl(200 + r.nextDouble() * 20, 0.6, 0.4 + r.nextDouble() * 0.5, 100));
                g.setStroke(new BasicStroke((float) (R * (0.01 + r.nextDouble() * 0.025))));
                double y = (r.nextDouble() - 0.5) * R * 2;
                Path2D line = new Path2D.Double();
                line.moveTo(-R, y);
                line.quadTo(0, y + (r.nextDouble() - 0.5) * R * 0.7, R, y + (r.nextDouble() - 0.5) * R * 0.5);
                g.draw(line);
            }
        }

        // 명암: 빛은 왼쪽 위에서 온다
        g.setTransform(base);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-R * 0.25, -R * 0.25), (float) (R * 1.55), new Point2D.Double(-R * 0.5, -R * 0.5),
                new float[]{0f, 0.3f, 0.72f, 1f},
                new Color[]{new Color(255, 255, 255, 56), new Color(0, 0, 0, 0), new Color(2, 2, 14, 158), new Color(2, 2, 14, 237)},
                CycleMethod.NO_CYCLE));
        g.fill(all);
        g.setClip(oldClip);
        if (ring) drawRing(g, hue, tilt, true);
        g.dispose();
        return img;
    }

    private void blot(Graphics2D g, Random r, double R, Color color, double min, double max) {
        double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * R;
        double w = R * (min + r.nextDouble() * (max - min)), h = R * (min + r.nextDouble() * (max - min)) * (0.5 + r.nextDouble() * 0.5);
        g.setColor(color);
        g.fill(new Ellipse2D.Double(Math.cos(a) * d - w, Math.sin(a) * d - h, w * 2, h * 2));
    }

    /** 고리: 뒤쪽 반은 행성보다 먼저, 앞쪽 반은 나중에 그려서 행성을 감싸 보이게 한다 */
    private void drawRing(Graphics2D g, double hue, double tilt, boolean front) {
        AffineTransform old = g.getTransform();
        g.rotate(tilt * 0.6 + 0.25);
        for (int i = 0; i < 5; i++) {
            double rx = radius * (1.45 + i * 0.19), ry = rx * 0.27;
            g.setColor(Paints.hsl(hue + 20 + i * 8, 0.55, 0.72 - i * 0.05, 140 - i * 18));
            g.setStroke(new BasicStroke((float) (radius * (i % 2 == 1 ? 0.05 : 0.12))));
            g.draw(new Arc2D.Double(-rx, -ry, rx * 2, ry * 2, front ? 180 : 0, 180, Arc2D.OPEN));
        }
        g.setTransform(old);
    }
}
