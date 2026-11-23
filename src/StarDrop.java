import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/*
 * 별똥 택배 — 중력을 이용한 우주 배달 퍼즐 게임 (순수 자바, Swing)
 *
 * 실행:  javac -encoding UTF-8 StarDrop.java
 *        java StarDrop
 *
 * 조작:  마우스 이동 = 조준,  클릭 = 발사,  비행 중 누르고 있기(또는 Space) = 추진
 *        ← → ↑ ↓ = 미세 조정,  R = 다시 조준,  H = 정답 방향,  M = 소리,  Esc = 처음 화면
 *
 * 파일 구성 (위에서 아래 순서)
 *   Rules          게임 규칙 숫자 모음
 *   Drawable       화면에 그릴 수 있는 것 (인터페이스)
 *   CelestialBody  중력을 가진 천체 (인터페이스)
 *   GravityBody    제자리에 있는 천체의 공통 부모 (추상 클래스)
 *   Planet, BlackHole, Repulsor   GravityBody 를 상속한 천체들
 *   Moon           행성 주위를 도는 천체
 *   Wormhole       들어가면 반대편으로 나오는 문
 *   Ship           날아가는 소포
 *   Level          구역 하나
 *   LevelConfig, LevelGenerator   구역을 자동으로 만드는 부분
 *   Solver         모든 각도와 세기를 쏴 보는 자동 풀이기
 *   Paints, Particle, Sound       그리기 도우미, 불꽃 입자, 효과음
 *   StarDrop       창, 입력, 게임 진행, 화면 그리기 (main 이 여기 있음)
 */

// ============================================================================
// 게임 규칙 숫자
// ============================================================================
final class Rules {
    private Rules() { }

    static final int W = 1600, H = 900;          // 게임 세계의 크기 (창 크기와 상관없이 고정)
    static final double DT = 0.01;               // 물리 계산 한 번에 흐르는 시간(초)
    static final int MAX_STEPS = 1600;           // 한 번 발사의 최대 계산 횟수 (16초)
    static final double MIN_SPEED = 170, MAX_SPEED = 520;
    static final double TARGET_R = 30;           // 배달지 반지름
    static final double STAR_R = 26;             // 별을 얻는 거리
    static final double THRUST = 170;            // 추진 가속도
    static final double FUEL = 1.2;              // 추진 연료(초)

    // Ship.step() 의 결과
    static final int FLYING = 0, ARRIVED = 1, CRASHED = 2, LOST = 3;
}

// ============================================================================
// 인터페이스
// ============================================================================
/** 화면에 그릴 수 있는 모든 것. anim = 계속 흐르는 시간, flightTime = 발사 후 흐른 시간 */
interface Drawable {
    void draw(Graphics2D g, double anim, double flightTime);
}

/** 중력을 가진 천체. 물리 계산은 천체의 종류를 몰라도 이 메서드만 부르면 된다. */
interface CelestialBody extends Drawable {
    /**
     * (x, y)에 있는 소포가 받는 가속도를 acc[0], acc[1] 에 더한다.
     * @return 소포가 이 천체에 부딪혔으면 true
     */
    boolean pull(double x, double y, double flightTime, double[] acc);
}

// ============================================================================
// 제자리에 있는 천체의 공통 부모
// ============================================================================
abstract class GravityBody implements CelestialBody {
    private final double x, y;
    private final double mass;       // 양수면 끌어당기고 음수면 밀어낸다
    private final double hitRadius;  // 이 안으로 들어오면 충돌 (0 이면 충돌 없음)
    private final double soften;     // 중심에서 힘이 무한대가 되지 않게 하는 값

    protected GravityBody(double x, double y, double mass, double hitRadius, double soften) {
        if (hitRadius < 0) throw new IllegalArgumentException("반지름은 음수일 수 없습니다: " + hitRadius);
        this.x = x;
        this.y = y;
        this.mass = mass;
        this.hitRadius = hitRadius;
        this.soften = soften;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getMass() { return mass; }
    public double getHitRadius() { return hitRadius; }

    /** 만유인력: 가속도 = 질량 / 거리^2, 방향은 천체 쪽 */
    @Override
    public boolean pull(double px, double py, double flightTime, double[] acc) {
        double dx = x - px, dy = y - py;
        double dist2 = dx * dx + dy * dy;
        if (dist2 < hitRadius * hitRadius) return true;
        double q = dist2 + soften;
        double k = mass / (q * Math.sqrt(q));
        acc[0] += dx * k;
        acc[1] += dy * k;
        return false;
    }
}

// ============================================================================
// 행성: 겉모습을 난수로 직접 그려서 이미지로 저장해 둔다
// ============================================================================
class Planet extends GravityBody {
    private static final String[] KINDS = {"gas", "rock", "terra", "lava", "ice", "gas"};
    private static final double GRAVITY = 2200;   // 질량 = GRAVITY * 반지름^2

    private final double radius;
    private final long lookSeed;     // 겉모습을 정하는 난수 씨앗
    private BufferedImage sprite;    // 처음 그릴 때 한 번만 만드는 원본 그림
    private BufferedImage scaled;    // 지금 창 크기에 맞게 줄여 둔 그림 (매 프레임 확대/축소하면 느리다)
    private double scaledFor;
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
        if (scaled == null || Math.abs(s - scaledFor) > 1e-6) {
            int px = Math.max(2, (int) Math.round(spriteHalf * 2 * s));
            scaled = Paints.newImage(g, px, px, true);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            sg.drawImage(sprite, 0, 0, px, px, null);
            sg.dispose();
            scaledFor = s;
        }
        Point2D corner = world.transform(new Point2D.Double(getX() - spriteHalf, getY() - spriteHalf), null);
        g.setTransform(new AffineTransform());      // 화면 픽셀 그대로 그린다
        g.drawImage(scaled, (int) Math.round(corner.getX()), (int) Math.round(corner.getY()), null);
        g.setTransform(world);
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

// ============================================================================
// 블랙홀: 작지만 아주 무겁다
// ============================================================================
class BlackHole extends GravityBody {
    BlackHole(double x, double y) {
        super(x, y, 8_000_000, 15, 0);
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        double x = getX(), y = getY();
        Paints.glow(g, x, y, 95, new Color(255, 140, 50, 92));
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(-0.35);
        g.scale(1, 0.42);
        for (int i = 0; i < 4; i++) {           // 돌아가는 원반
            g.setColor(Paints.hsl(24 + i * 9, 1, 0.68 - i * 0.06, 215 - i * 43));
            g.setStroke(new BasicStroke(5 - i * 0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double rr = 25 + i * 9, start = -Math.toDegrees(anim * (2.2 - i * 0.4) + i * 1.7);
            g.draw(new Arc2D.Double(-rr, -rr, rr * 2, rr * 2, start, 246, Arc2D.OPEN));
        }
        g.setTransform(old);
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(x - 15, y - 15, 30, 30));
        g.setColor(new Color(255, 225, 180, 230));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Ellipse2D.Double(x - 16, y - 16, 32, 32));
    }
}

// ============================================================================
// 밀어내는 별: 질량이 음수라서 소포를 밀어낸다. 부딪히지는 않는다
// ============================================================================
class Repulsor extends GravityBody {
    Repulsor(double x, double y) {
        super(x, y, -4_500_000, 0, 1600);
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        double x = getX(), y = getY();
        for (int i = 0; i < 3; i++) {           // 바깥으로 퍼지는 고리
            double phase = (anim * 0.55 + i / 3.0) % 1, rr = 14 + phase * 78;
            g.setColor(Paints.hsl(188, 0.95, 0.7, (int) ((1 - phase) * 128)));
            g.setStroke(new BasicStroke((float) (2 * (1 - phase) + 0.5)));
            g.draw(new Ellipse2D.Double(x - rr, y - rr, rr * 2, rr * 2));
        }
        Paints.glow(g, x, y, 34, new Color(160, 245, 255, 205));
        g.setColor(new Color(242, 254, 255));
        g.fill(new Ellipse2D.Double(x - 7, y - 7, 14, 14));
    }
}

// ============================================================================
// 달: 행성 주위를 돈다. 위치가 시간에 따라 바뀌므로 GravityBody 를 쓰지 않고 직접 구현
// ============================================================================
class Moon implements CelestialBody {
    private final double centerX, centerY;   // 도는 중심(행성 위치)
    private final double orbit, radius, speed, phase, mass;

    Moon(double centerX, double centerY, double orbit, double radius, double speed, double phase) {
        if (radius <= 0 || orbit <= 0) throw new IllegalArgumentException("달의 반지름과 궤도는 0보다 커야 합니다");
        this.centerX = centerX;
        this.centerY = centerY;
        this.orbit = orbit;
        this.radius = radius;
        this.speed = speed;
        this.phase = phase;
        this.mass = 3000 * radius * radius;
    }

    private double xAt(double t) { return centerX + Math.cos(phase + speed * t) * orbit; }
    private double yAt(double t) { return centerY + Math.sin(phase + speed * t) * orbit; }

    @Override
    public boolean pull(double px, double py, double flightTime, double[] acc) {
        double dx = xAt(flightTime) - px, dy = yAt(flightTime) - py;
        double dist2 = dx * dx + dy * dy;
        if (dist2 < radius * radius) return true;
        double k = mass / (dist2 * Math.sqrt(dist2));
        acc[0] += dx * k;
        acc[1] += dy * k;
        return false;
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        g.setColor(new Color(220, 225, 255, 34));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 7f}, 0f));
        g.draw(new Ellipse2D.Double(centerX - orbit, centerY - orbit, orbit * 2, orbit * 2));
        double x = xAt(flightTime), y = yAt(flightTime);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) radius, new Point2D.Double(x - radius * 0.4, y - radius * 0.4),
                new float[]{0f, 0.6f, 1f}, new Color[]{new Color(241, 238, 230), new Color(154, 151, 143), new Color(58, 57, 64)}, CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
    }
}

// ============================================================================
// 웜홀: 한쪽 입구로 들어가면 속도를 그대로 지닌 채 반대편에서 나온다
// ============================================================================
class Wormhole implements Drawable {
    private final double ax, ay, bx, by, radius;

    Wormhole(double ax, double ay, double bx, double by) {
        this.ax = ax;
        this.ay = ay;
        this.bx = bx;
        this.by = by;
        this.radius = 20;
    }

    public double getAx() { return ax; }
    public double getAy() { return ay; }
    public double getBx() { return bx; }
    public double getBy() { return by; }
    public double getRadius() { return radius; }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        g.setColor(new Color(180, 140, 255, 26));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 9f}, 0f));
        g.draw(new Line2D.Double(ax, ay, bx, by));
        drawMouth(g, ax, ay, 1, anim);
        drawMouth(g, bx, by, -1, anim);
    }

    private void drawMouth(Graphics2D g, double x, double y, int dir, double anim) {
        Paints.glow(g, x, y, 52, new Color(170, 120, 255, 102));
        g.setColor(new Color(5, 3, 12));
        g.fill(new Ellipse2D.Double(x - radius * 0.8, y - radius * 0.8, radius * 1.6, radius * 1.6));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int arm = 0; arm < 3; arm++) {     // 소용돌이 팔 3개
            Path2D p = new Path2D.Double();
            for (int i = 0; i <= 12; i++) {
                double s = i / 12.0, rr = 4 + (radius * 1.45 - 4) * s, a = dir * (anim * 2.4 + s * 3.6) + arm * Math.PI * 2 / 3;
                if (i == 0) p.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
                else p.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
            }
            g.setColor(Paints.hsl(268 + arm * 14, 0.95, 0.74, 217));
            g.draw(p);
        }
    }
}

// ============================================================================
// 소포: 위치와 속도는 step() 을 통해서만 바뀐다
// ============================================================================
class Ship {
    private double x, y, vx, vy;
    private int steps;                 // 지금까지 계산한 횟수
    private double turned;             // 지금까지 방향이 꺾인 양(라디안)
    private boolean inWormhole;        // 웜홀 안에 있는 동안 다시 이동하지 않게
    private boolean justWarped;        // 이번 step 에서 웜홀을 통과했는가
    private CelestialBody crashedInto; // 부딪힌 천체
    private final double[] acc = new double[2];

    Ship(Level level, double angle, double power) {
        double speed = Rules.MIN_SPEED + (Rules.MAX_SPEED - Rules.MIN_SPEED) * power;
        x = level.getStartX();
        y = level.getStartY();
        vx = Math.cos(angle) * speed;
        vy = Math.sin(angle) * speed;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public int getSteps() { return steps; }
    public double getTime() { return steps * Rules.DT; }
    public double getTurned() { return turned; }
    public boolean hasJustWarped() { return justWarped; }
    public CelestialBody getCrashedInto() { return crashedInto; }

    /** DT 초만큼 전진. 결과는 Rules.FLYING / ARRIVED / CRASHED / LOST */
    int step(Level level, boolean thrust) {
        acc[0] = 0;
        acc[1] = 0;
        double time = steps * Rules.DT;

        // 1) 모든 천체의 가속도를 더한다 (다형성: 종류를 몰라도 pull 만 부르면 된다)
        for (int i = 0; i < level.getBodyCount(); i++) {
            CelestialBody body = level.getBody(i);
            if (body.pull(x, y, time, acc)) {
                crashedInto = body;
                return Rules.CRASHED;
            }
        }

        // 2) 추진: 지금 날아가는 방향으로 가속
        double speed2 = vx * vx + vy * vy;
        if (thrust) {
            double speed = Math.sqrt(speed2);
            acc[0] += vx / speed * Rules.THRUST;
            acc[1] += vy / speed * Rules.THRUST;
        }

        // 3) 가속도 -> 속도 -> 위치
        double newVx = vx + acc[0] * Rules.DT, newVy = vy + acc[1] * Rules.DT;
        turned += Math.abs(vx * newVy - vy * newVx) / speed2;
        vx = newVx;
        vy = newVy;
        x += vx * Rules.DT;
        y += vy * Rules.DT;
        steps++;

        // 4) 웜홀
        justWarped = false;
        Wormhole w = level.getWormhole();
        if (w != null) {
            double r2 = w.getRadius() * w.getRadius();
            double da = (x - w.getAx()) * (x - w.getAx()) + (y - w.getAy()) * (y - w.getAy());
            double db = (x - w.getBx()) * (x - w.getBx()) + (y - w.getBy()) * (y - w.getBy());
            if (inWormhole) {
                if (da > r2 && db > r2) inWormhole = false;
            } else if (da < r2) {
                x += w.getBx() - w.getAx();
                y += w.getBy() - w.getAy();
                inWormhole = true;
                justWarped = true;
            } else if (db < r2) {
                x += w.getAx() - w.getBx();
                y += w.getAy() - w.getBy();
                inWormhole = true;
                justWarped = true;
            }
        }

        // 5) 도착 / 이탈 판정
        double tx = x - level.getTargetX(), ty = y - level.getTargetY();
        if (tx * tx + ty * ty < Rules.TARGET_R * Rules.TARGET_R) return Rules.ARRIVED;
        if (x < -350 || x > Rules.W + 350 || y < -350 || y > Rules.H + 350 || steps >= Rules.MAX_STEPS) return Rules.LOST;
        return Rules.FLYING;
    }
}

// ============================================================================
// 구역 하나
// ============================================================================
class Level {
    private final String name;
    private final long seed;
    private final double startX, startY, targetX, targetY;
    private final CelestialBody[] bodies;
    private final Wormhole wormhole;         // 없으면 null
    private final int previewSteps;          // 조준할 때 보여 주는 예측 길이
    private final double[][] stars = new double[3][2];
    private double solutionAngle, solutionPower;

    Level(String name, long seed, double startX, double startY, double targetX, double targetY,
          List<CelestialBody> bodies, Wormhole wormhole, int previewSteps) {
        this.name = name;
        this.seed = seed;
        this.startX = startX;
        this.startY = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.bodies = bodies.toArray(new CelestialBody[0]);
        this.wormhole = wormhole;
        this.previewSteps = previewSteps;
    }

    public String getName() { return name; }
    public long getSeed() { return seed; }
    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getTargetX() { return targetX; }
    public double getTargetY() { return targetY; }
    public int getBodyCount() { return bodies.length; }
    public CelestialBody getBody(int i) { return bodies[i]; }
    public Wormhole getWormhole() { return wormhole; }
    public int getPreviewSteps() { return previewSteps; }
    public double getStarX(int i) { return stars[i][0]; }
    public double getStarY(int i) { return stars[i][1]; }
    public double getSolutionAngle() { return solutionAngle; }
    public double getSolutionPower() { return solutionPower; }

    void setSolution(double angle, double power) {
        solutionAngle = angle;
        solutionPower = power;
    }

    void setStar(int i, double x, double y) {
        stars[i][0] = x;
        stars[i][1] = y;
    }
}

// ============================================================================
// 구역 자동 생성
// ============================================================================
/** 구역에 무엇을 몇 개 넣을지 */
class LevelConfig {
    final String name;
    final int planets, moons, blackHoles, repulsors, wormholes, previewSteps;
    final double minTurn;   // 정답 궤도가 최소 이만큼은 휘어야 한다 (직선으로 풀리는 구역 방지)

    LevelConfig(String name, int planets, int moons, int blackHoles, int repulsors, int wormholes, double minTurn, int previewSteps) {
        this.name = name;
        this.planets = planets;
        this.moons = moons;
        this.blackHoles = blackHoles;
        this.repulsors = repulsors;
        this.wormholes = wormholes;
        this.minTurn = minTurn;
        this.previewSteps = previewSteps;
    }
}

class LevelGenerator {
    private LevelGenerator() { }

    /** 계획서의 5개 구역. 구역마다 새 천체가 하나씩 등장한다 */
    static final LevelConfig[] CAMPAIGN = {
        new LevelConfig("첫 배달",     1, 0, 0, 0, 0, 0.5, 300),
        new LevelConfig("달그림자",    2, 1, 0, 0, 0, 0.8, 260),
        new LevelConfig("검은 입",     2, 0, 1, 0, 0, 1.0, 240),
        new LevelConfig("밀어내는 별", 3, 1, 0, 1, 0, 1.1, 220),
        new LevelConfig("마지막 소포", 3, 1, 1, 1, 1, 1.3, 190),
    };
    static final long[] CAMPAIGN_SEED = {7001, 7132, 7263, 7394, 7525};
    /** 미리 돌려 보고 찾아 둔 "처음 성공하는 시도 번호". 여기서 시작하면 구역이 바로 만들어진다 */
    static final int[] CAMPAIGN_FIRST_TRY = {0, 2, 5, 4, 0};

    /** 무한 배달: 씨앗 숫자에 따라 구성이 달라진다 */
    static LevelConfig endlessConfig(long seed) {
        Random r = new Random(seed * 31 + 5);
        int planets = 2 + r.nextInt(3);
        int moons = r.nextDouble() < 0.55 ? 1 : 0;
        int holes = r.nextDouble() < 0.45 ? 1 : 0;
        int reps = r.nextDouble() < 0.45 ? 1 : 0;
        int worms = r.nextDouble() < 0.45 ? 1 : 0;
        return new LevelConfig("무한 배달", planets, moons, holes, reps, worms, 1.0 + r.nextDouble() * 0.6, 170 + r.nextInt(60));
    }

    /** 풀 수 있는 구역이 나올 때까지 만든다 */
    static Level create(long seed, LevelConfig cfg, int firstTry) {
        for (int attempt = firstTry; attempt < firstTry + 3000; attempt++) {
            Level level = tryOnce(seed, cfg, attempt);
            if (level != null) return level;
        }
        throw new IllegalStateException("구역을 만들지 못했습니다: seed=" + seed);
    }

    /** 한 번 시도. 배치에 실패했거나 풀 수 없는 구역이면 null */
    static Level tryOnce(long seed, LevelConfig cfg, int attempt) {
        Level level = build(new Random(seed * 9973 + attempt * 7919L), seed, cfg);
        if (level == null) return null;
        double[] sol = Solver.solve(level, cfg.minTurn);
        if (sol == null) return null;
        level.setSolution(sol[0], sol[1]);
        return placeStars(level) ? level : null;
    }

    /** 출발점, 배달지, 천체를 서로 겹치지 않게 놓는다 */
    private static Level build(Random rng, long seed, LevelConfig cfg) {
        double sx = 130 + rng.nextDouble() * 100, sy = 220 + rng.nextDouble() * 460;
        double tx = 1370 + rng.nextDouble() * 100, ty = 220 + rng.nextDouble() * 460;
        List<double[]> used = new ArrayList<>();      // 이미 차지한 자리 {x, y, 필요한 반지름}
        List<double[]> planets = new ArrayList<>();   // {x, y, 반지름, 달 궤도, 달 반지름, 달 속도, 달 위상}
        List<double[]> holes = new ArrayList<>(), reps = new ArrayList<>();
        int moonsLeft = cfg.moons;

        // 1) 출발점과 배달지 사이를 가로막는 행성 하나
        boolean placed = false;
        for (int k = 0; k < 40 && !placed; k++) {
            double t = 0.42 + rng.nextDouble() * 0.16, R = 48 + rng.nextDouble() * 20;
            double dx = tx - sx, dy = ty - sy, d = Math.hypot(dx, dy), off = (rng.nextDouble() * 2 - 1) * R * 0.3;
            double[] p = planetSpec(rng, sx + dx * t - dy / d * off, sy + dy * t + dx / d * off, R, moonsLeft > 0);
            if (fits(used, p[0], p[1], space(p), sx, sy, tx, ty)) {
                planets.add(p);
                used.add(new double[]{p[0], p[1], space(p)});
                if (p[3] > 0) moonsLeft--;
                placed = true;
            }
        }
        if (!placed) return null;

        // 2) 나머지 행성, 블랙홀, 밀어내는 별
        for (int i = 1; i < cfg.planets; i++) {
            placed = false;
            for (int k = 0; k < 120 && !placed; k++) {
                double[] p = planetSpec(rng, 330 + rng.nextDouble() * 940, 110 + rng.nextDouble() * 680, 30 + rng.nextDouble() * 32, moonsLeft > 0);
                if (fits(used, p[0], p[1], space(p), sx, sy, tx, ty)) {
                    planets.add(p);
                    used.add(new double[]{p[0], p[1], space(p)});
                    if (p[3] > 0) moonsLeft--;
                    placed = true;
                }
            }
            if (!placed) return null;
        }
        if (moonsLeft > 0) return null;
        if (!placeSimple(rng, cfg.blackHoles, 70, holes, used, sx, sy, tx, ty)) return null;
        if (!placeSimple(rng, cfg.repulsors, 60, reps, used, sx, sy, tx, ty)) return null;

        // 3) 웜홀 (두 입구는 멀리 떨어뜨린다)
        double[] worm = null;
        if (cfg.wormholes > 0) {
            for (int k = 0; k < 200 && worm == null; k++) {
                double ax = 300 + rng.nextDouble() * 1000, ay = 90 + rng.nextDouble() * 720;
                double bx = 300 + rng.nextDouble() * 1000, by = 90 + rng.nextDouble() * 720;
                if (Math.hypot(ax - bx, ay - by) < 480) continue;
                if (!fits(used, ax, ay, 45, sx, sy, tx, ty) || !fits(used, bx, by, 45, sx, sy, tx, ty)) continue;
                worm = new double[]{ax, ay, bx, by};
            }
            if (worm == null) return null;
        }

        // 4) 절반의 확률로 좌우를 뒤집어 다양하게 만든 뒤, 실제 객체를 생성
        boolean flip = rng.nextDouble() < 0.5;
        List<CelestialBody> bodies = new ArrayList<>();
        for (int i = 0; i < planets.size(); i++) {
            double[] p = planets.get(i);
            double px = flip ? Rules.W - p[0] : p[0];
            bodies.add(new Planet(px, p[1], p[2], seed * 131 + i * 17 + 3));
            if (p[3] > 0) bodies.add(new Moon(px, p[1], p[3], p[4], flip ? -p[5] : p[5], flip ? Math.PI - p[6] : p[6]));
        }
        for (double[] h : holes) bodies.add(new BlackHole(flip ? Rules.W - h[0] : h[0], h[1]));
        for (double[] q : reps) bodies.add(new Repulsor(flip ? Rules.W - q[0] : q[0], q[1]));
        Wormhole wormhole = worm == null ? null
                : new Wormhole(flip ? Rules.W - worm[0] : worm[0], worm[1], flip ? Rules.W - worm[2] : worm[2], worm[3]);
        return new Level(cfg.name, seed, flip ? Rules.W - sx : sx, sy, flip ? Rules.W - tx : tx, ty, bodies, wormhole, cfg.previewSteps);
    }

    private static double[] planetSpec(Random rng, double x, double y, double R, boolean wantMoon) {
        double[] p = {x, y, R, 0, 0, 0, 0};
        if (wantMoon && R >= 40) {
            p[3] = R + 50 + rng.nextDouble() * 28;                                    // 달 궤도 반지름
            p[4] = 9 + rng.nextDouble() * 4;                                          // 달 반지름
            p[5] = (0.5 + rng.nextDouble() * 0.45) * (rng.nextDouble() < 0.5 ? -1 : 1); // 도는 속도와 방향
            p[6] = rng.nextDouble() * Math.PI * 2;                                    // 처음 위치
        }
        return p;
    }

    /** 행성이 차지하는 반지름 (달이 있으면 달 궤도까지) */
    private static double space(double[] p) {
        return p[3] > 0 ? p[3] + p[4] : p[2];
    }

    private static boolean placeSimple(Random rng, int count, double need, List<double[]> out, List<double[]> used,
                                       double sx, double sy, double tx, double ty) {
        for (int i = 0; i < count; i++) {
            boolean placed = false;
            for (int k = 0; k < 120 && !placed; k++) {
                double x = 330 + rng.nextDouble() * 940, y = 110 + rng.nextDouble() * 680;
                if (fits(used, x, y, need, sx, sy, tx, ty)) {
                    out.add(new double[]{x, y});
                    used.add(new double[]{x, y, need});
                    placed = true;
                }
            }
            if (!placed) return false;
        }
        return true;
    }

    /** (x, y)에 반지름 need 만큼의 자리가 비어 있는가 */
    private static boolean fits(List<double[]> used, double x, double y, double need, double sx, double sy, double tx, double ty) {
        if (x < need + 20 || x > Rules.W - need - 20 || y < need + 20 || y > Rules.H - need - 20) return false;
        if (Math.hypot(x - sx, y - sy) < need + 150) return false;
        if (Math.hypot(x - tx, y - ty) < need + 110) return false;
        for (double[] u : used) if (Math.hypot(x - u[0], y - u[1]) < need + u[2] + 80) return false;
        return true;
    }

    /** 정답 궤도의 30%, 54%, 78% 지점에 별을 놓는다 → 별 3개를 모두 얻는 길이 반드시 있다 */
    private static boolean placeStars(Level level) {
        List<double[]> path = new ArrayList<>();
        Ship ship = new Ship(level, level.getSolutionAngle(), level.getSolutionPower());
        int result;
        do {
            path.add(new double[]{ship.getX(), ship.getY()});
            result = ship.step(level, false);
        } while (result == Rules.FLYING);

        double[] where = {0.30, 0.54, 0.78};
        int n = path.size();
        for (int s = 0; s < 3; s++) {
            int base = (int) Math.round(n * where[s]), index = base;
            for (int tries = 0; badStarSpot(level, path.get(index), s) && tries < 400; tries++) {
                int shift = (tries / 2 + 1) * 3 * (tries % 2 == 0 ? 1 : -1);   // 앞뒤로 조금씩 옮겨 본다
                index = Math.max(1, Math.min(n - 2, base + shift));
            }
            if (badStarSpot(level, path.get(index), s)) return false;
            level.setStar(s, path.get(index)[0], path.get(index)[1]);
        }
        return true;
    }

    private static boolean badStarSpot(Level level, double[] p, int placedCount) {
        if (p[0] < 40 || p[0] > Rules.W - 40 || p[1] < 100 || p[1] > Rules.H - 70) return true;
        if (Math.hypot(p[0] - level.getStartX(), p[1] - level.getStartY()) < 70) return true;
        if (Math.hypot(p[0] - level.getTargetX(), p[1] - level.getTargetY()) < 70) return true;
        for (int i = 0; i < placedCount; i++) if (Math.hypot(p[0] - level.getStarX(i), p[1] - level.getStarY(i)) < 70) return true;
        return false;
    }
}

// ============================================================================
// 자동 풀이기
// ============================================================================
class Solver {
    private Solver() { }

    /** 한 번 끝까지 날려 본다. 결과 = {도착 결과, 화면 밖으로 나갔으면 1} 과 함께 ship 을 돌려준다 */
    private static int fly(Level level, double angle, double power, Ship[] shipOut, boolean[] wentOut) {
        Ship ship = new Ship(level, angle, power);
        int result;
        boolean out = false;
        while ((result = ship.step(level, false)) == Rules.FLYING) {
            if (ship.getX() < 20 || ship.getX() > Rules.W - 20 || ship.getY() < 20 || ship.getY() > Rules.H - 20) out = true;
        }
        if (shipOut != null) shipOut[0] = ship;
        if (wentOut != null) wentOut[0] = out;
        return result;
    }

    /**
     * 각도 720개(0.5도 간격) × 세기 12단계를 전부 쏴 본다 (완전 탐색).
     * 도착한 것 중에서 많이 휘고 너무 길지 않은 궤도를 고르되,
     * 각도와 세기가 조금 어긋나도 성공하는 "튼튼한" 정답만 채택한다.
     * @return {각도, 세기} 또는 정답이 없으면 null
     */
    static double[] solve(Level level, double minTurn) {
        List<double[]> hits = new ArrayList<>();   // {각도, 세기, 점수}
        Ship[] ship = new Ship[1];
        boolean[] out = new boolean[1];
        for (int a = 0; a < 720; a++) {
            double angle = a * Math.PI / 360;
            for (int p = 0; p < 12; p++) {
                double power = p / 11.0;
                if (fly(level, angle, power, ship, out) != Rules.ARRIVED || out[0]) continue;
                double turn = ship[0].getTurned(), time = ship[0].getTime();
                if (turn < minTurn || time < 2.5 || time > 11) continue;
                hits.add(new double[]{angle, power, Math.min(turn, 5) - 0.1 * time});
            }
        }
        hits.sort((u, v) -> Double.compare(v[2], u[2]));   // 점수 높은 순

        double[] angleShift = {-0.006, -0.003, 0.003, 0.006}, powerShift = {-0.012, 0, 0.012};
        for (int i = 0; i < Math.min(hits.size(), 40); i++) {
            double[] h = hits.get(i);
            boolean sturdy = true;
            for (double da : angleShift) {
                for (double dp : powerShift) {
                    double power = Math.max(0, Math.min(1, h[1] + dp));
                    if (fly(level, h[0] + da, power, null, null) != Rules.ARRIVED) sturdy = false;
                }
            }
            if (sturdy) return new double[]{h[0], h[1]};
        }
        return null;
    }
}

// ============================================================================
// 그리기 도우미
// ============================================================================
final class Paints {
    private Paints() { }

    /** HSL 색(색상 0~360, 채도 0~1, 밝기 0~1)과 투명도(0~255)로 Color 만들기 */
    static Color hsl(double h, double s, double l, int alpha) {
        h = ((h % 360) + 360) % 360 / 360.0;
        s = Math.max(0, Math.min(1, s));
        l = Math.max(0, Math.min(1, l));
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new Color(channel(p, q, h + 1.0 / 3), channel(p, q, h), channel(p, q, h - 1.0 / 3), Math.max(0, Math.min(255, alpha)));
    }

    private static int channel(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        double v = t < 1.0 / 6 ? p + (q - p) * 6 * t : t < 0.5 ? q : t < 2.0 / 3 ? p + (q - p) * (2.0 / 3 - t) * 6 : p;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** 가운데가 밝고 가장자리로 갈수록 투명해지는 빛 */
    static void glow(Graphics2D g, double x, double y, double radius, Color color) {
        // 그라데이션을 매 프레임 새로 계산하면 느리므로, 색마다 한 번만 그려 두고 크기만 바꿔 쓴다
        BufferedImage img = GLOWS.get(color.getRGB());
        if (img == null) {
            img = newImage(g, GLOW_SIZE, GLOW_SIZE, true);
            Graphics2D ig = img.createGraphics();
            Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
            ig.setPaint(new RadialGradientPaint(GLOW_SIZE / 2f, GLOW_SIZE / 2f, GLOW_SIZE / 2f, new float[]{0f, 1f}, new Color[]{color, clear}));
            ig.fillRect(0, 0, GLOW_SIZE, GLOW_SIZE);
            ig.dispose();
            GLOWS.put(color.getRGB(), img);
        }
        AffineTransform at = AffineTransform.getTranslateInstance(x - radius, y - radius);
        at.scale(radius * 2 / GLOW_SIZE, radius * 2 / GLOW_SIZE);
        g.drawImage(img, at, null);
    }

    private static final int GLOW_SIZE = 128;
    private static final Map<Integer, BufferedImage> GLOWS = new HashMap<>();

    /** 그래픽 카드가 빠르게 그릴 수 있는 형식의 빈 그림 만들기 */
    static BufferedImage newImage(Graphics2D g, int w, int h, boolean transparent) {
        GraphicsConfiguration gc = g.getDeviceConfiguration();
        try {
            if (gc != null) return gc.createCompatibleImage(w, h, transparent ? Transparency.TRANSLUCENT : Transparency.OPAQUE);
        } catch (RuntimeException e) {
            // 아래의 기본 형식으로 만든다
        }
        return new BufferedImage(w, h, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    static void circle(Graphics2D g, double x, double y, double r) {
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    static void ring(Graphics2D g, double x, double y, double r) {
        g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }
}

/** 불꽃 입자 하나 */
class Particle implements Drawable {
    private double x, y, vx, vy, life;
    private final double maxLife, size, hue;

    Particle(double x, double y, double vx, double vy, double life, double size, double hue) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.hue = hue;
    }

    /** @return 수명이 끝났으면 false */
    boolean update(double dt) {
        life -= dt;
        x += vx * dt;
        y += vy * dt;
        vx *= 0.985;
        vy *= 0.985;
        return life > 0;
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        double t = life / maxLife;
        g.setColor(Paints.hsl(hue, 0.95, 0.55 + t * 0.3, (int) (t * 255)));
        Paints.circle(g, x, y, size * (0.4 + t));
    }
}

/**
 * 소리: 음원 파일 없이 전부 계산해서 만든다 (웹 버전의 Web Audio 구성을 그대로 옮김).
 * 오디오 스레드 하나가 배경음, 추진음, 효과음을 섞고 메아리(딜레이)를 입혀 스피커로 보낸다.
 */
final class Sound {
    private Sound() { }

    private static final float RATE = 44100f;
    private static final int CHUNK = 512;                       // 한 번에 섞는 샘플 수 (약 12ms)
    private static final List<float[]> QUEUE = new ArrayList<>(); // 새로 재생할 효과음
    private static volatile boolean muted, thrustOn, started, broken;

    static boolean isMuted() { return muted; }
    static void setMuted(boolean m) { muted = m; }
    static void thrust(boolean on) { thrustOn = on; }

    /** 첫 클릭이나 키 입력 때 오디오 스레드를 켠다 (웹처럼 사용자가 조작한 뒤에 소리를 시작) */
    static synchronized void start() {
        if (started || broken) return;
        started = true;
        Thread t = new Thread(Sound::run, "sound");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------ 효과음 만들기
    /** 음 하나: f0 에서 f1 으로 미끄러지며, 빠르게 커졌다가 서서히 사라진다 */
    private static void tone(float[] buf, double f0, double f1, double dur, String type, double vol, double when) {
        int start = (int) (when * RATE), n = (int) (dur * RATE);
        double phase = 0;
        for (int i = 0; i < n && start + i < buf.length; i++) {
            double t = i / RATE;
            double f = f1 > 0 ? f0 * Math.pow(f1 / f0, t / dur) : f0;
            phase += f / RATE;
            double p = phase - Math.floor(phase), wave;
            if (type.equals("triangle")) wave = 4 * Math.abs(p - 0.5) - 1;
            else wave = Math.sin(2 * Math.PI * p);
            double env = t < 0.012 ? vol * t / 0.012 : vol * Math.pow(0.0008 / vol, (t - 0.012) / (dur - 0.012));
            buf[start + i] += (float) (wave * env);
        }
    }

    /** 잡음을 대역 필터로 걸러 "쉭" 소리를 만든다. 필터 중심 주파수가 f0 에서 f1 으로 바뀐다 */
    private static void noise(float[] buf, double dur, double f0, double f1, double vol) {
        int n = (int) (dur * RATE);
        Random r = new Random();
        Biquad bp = new Biquad();
        for (int i = 0; i < n && i < buf.length; i++) {
            double t = i / RATE;
            bp.bandpass(f0 * Math.pow(f1 / f0, t / dur), 1.1);
            double env = vol * Math.pow(0.0008 / vol, t / dur);
            buf[i] += (float) (bp.process(r.nextDouble() * 2 - 1) * env);
        }
    }

    private static float[] newBuffer(double seconds) { return new float[(int) (seconds * RATE)]; }

    private static void play(float[] buf) {
        if (muted || broken) return;
        start();
        synchronized (QUEUE) { QUEUE.add(buf); }
    }

    static void launch(double p) {
        float[] b = newBuffer(0.45);
        noise(b, 0.4, 300, 1500 + p * 1500, 0.35);
        tone(b, 140 + p * 80, 55, 0.35, "sine", 0.3, 0);
        play(b);
    }

    static void star(int n) {
        double f = new double[]{659.25, 830.61, 987.77}[Math.max(0, Math.min(2, n))];
        float[] b = newBuffer(0.65);
        tone(b, f, 0, 0.6, "sine", 0.22, 0);
        tone(b, f * 2, 0, 0.4, "triangle", 0.08, 0.04);
        play(b);
    }

    static void crash() {
        float[] b = newBuffer(0.75);
        noise(b, 0.7, 500, 50, 0.6);
        tone(b, 95, 32, 0.55, "sine", 0.5, 0);
        play(b);
    }

    static void win() {
        double[] notes = {523.25, 659.25, 783.99, 1046.5, 1318.5};
        float[] b = newBuffer(1.3);
        for (int i = 0; i < notes.length; i++) {
            tone(b, notes[i], 0, 0.8, "triangle", 0.17, i * 0.09);
            tone(b, notes[i] / 2, 0, 0.8, "sine", 0.1, i * 0.09);
        }
        play(b);
    }

    static void warp() {
        float[] b = newBuffer(0.4);
        tone(b, 260, 1300, 0.3, "sine", 0.2, 0);
        tone(b, 1300, 400, 0.3, "sine", 0.1, 0.05);
        play(b);
    }

    static void lost() {
        float[] b = newBuffer(0.75);
        tone(b, 420, 150, 0.7, "sine", 0.13, 0);
        play(b);
    }

    static void click() {
        float[] b = newBuffer(0.1);
        tone(b, 740, 0, 0.07, "triangle", 0.08, 0);
        play(b);
    }

    // ------------------------------------------------------------ 오디오 스레드
    private static void run() {
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            javax.sound.sampled.SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, CHUNK * 2 * 4);
            line.start();
            byte[] out = new byte[CHUNK * 2];
            List<float[]> voices = new ArrayList<>();
            List<int[]> positions = new ArrayList<>();
            Random rnd = new Random();

            // 배경음: 웹과 같은 7개 음, 짝수는 톱니파 홀수는 삼각파, 저역 필터를 천천히 흔든다
            double[] padFreq = {55, 110, 164.81, 220, 261.63, 329.63, 493.88};
            double[] padPhase = new double[7];
            Biquad padFilter = new Biquad(), thrustFilter = new Biquad();
            thrustFilter.bandpass(700, 0.6);
            float[] delay = new float[(int) (0.27 * RATE)];      // 메아리: 0.27초 뒤에 34%씩 되돌아온다
            int delayPos = 0;
            double time = 0, master = 0, thrustGain = 0;

            while (true) {
                synchronized (QUEUE) {
                    for (float[] q : QUEUE) { voices.add(q); positions.add(new int[1]); }
                    QUEUE.clear();
                }
                padFilter.lowpass(480 + 190 * Math.sin(2 * Math.PI * 0.06 * time), 0.8);
                for (int i = 0; i < CHUNK; i++) {
                    double t = time + i / RATE;
                    // 배경음
                    double pad = 0;
                    for (int k = 0; k < 7; k++) {
                        double f = padFreq[k] * Math.pow(2, ((k * 37 % 17) - 8) / 1200.0);
                        padPhase[k] += f / RATE;
                        double p = padPhase[k] - Math.floor(padPhase[k]);
                        double wave = k % 2 == 1 ? 4 * Math.abs(p - 0.5) - 1 : 2 * p - 1;
                        double g = k > 4 ? 0.06 : 0.16;
                        pad += wave * (g + g * 0.6 * Math.sin(2 * Math.PI * (0.05 + k * 0.013) * t));
                    }
                    pad = padFilter.process(pad) * 0.13 * Math.min(1, t / 5);
                    // 효과음
                    double fx = 0;
                    for (int v = 0; v < voices.size(); v++) {
                        int[] pos = positions.get(v);
                        if (pos[0] < voices.get(v).length) fx += voices.get(v)[pos[0]++];
                    }
                    // 추진음
                    thrustGain += ((thrustOn ? 0.16 : 0) - thrustGain) * (1 / (0.04 * RATE));
                    double th = thrustFilter.process(rnd.nextDouble() * 2 - 1) * thrustGain;
                    // 메아리
                    double echoed = delay[delayPos];
                    delay[delayPos] = (float) (fx + pad + echoed * 0.34);
                    delayPos = (delayPos + 1) % delay.length;
                    master += ((muted ? 0 : 0.7) - master) * (1 / (0.05 * RATE));
                    double s = Math.tanh((fx + pad + th + echoed * 0.3) * master * 1.6);
                    short v = (short) (s * 30000);
                    out[i * 2] = (byte) v;
                    out[i * 2 + 1] = (byte) (v >> 8);
                }
                time += CHUNK / RATE;
                for (int v = voices.size() - 1; v >= 0; v--) {
                    if (positions.get(v)[0] >= voices.get(v).length) { voices.remove(v); positions.remove(v); }
                }
                line.write(out, 0, out.length);
            }
        } catch (Exception | Error e) {
            broken = true;          // 소리 장치가 없어도 게임은 계속된다
        }
    }

    /** 2차 필터 (웹 오디오의 BiquadFilterNode 와 같은 공식) */
    private static final class Biquad {
        private double b0, b1, b2, a1, a2, x1, x2, y1, y2;

        void lowpass(double f, double q) {
            double w = 2 * Math.PI * f / RATE, alpha = Math.sin(w) / (2 * q), c = Math.cos(w), a0 = 1 + alpha;
            b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0;
        }

        void bandpass(double f, double q) {
            double w = 2 * Math.PI * Math.min(f, RATE * 0.45) / RATE, alpha = Math.sin(w) / (2 * q), c = Math.cos(w), a0 = 1 + alpha;
            b0 = alpha / a0; b1 = 0; b2 = -alpha / a0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0;
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }
}

// ============================================================================
// 화면 위의 버튼과 칸 (웹 버전의 HTML 버튼을 자바로 그린 것)
// ============================================================================
/** 클릭할 수 있는 화면 요소의 공통 부모 */
abstract class Widget implements Drawable {
    protected final Rectangle2D rect;
    protected final Runnable action;
    protected boolean hover, enabled = true;

    protected Widget(Rectangle2D rect, Runnable action) {
        this.rect = rect;
        this.action = action;
    }

    boolean contains(double x, double y) { return enabled && rect.contains(x, y); }

    void click() { if (enabled) action.run(); }
}

/** 글자 버튼. primary 는 노란 버튼, pressed 는 켜진 상태(민트색) */
class Button extends Widget {
    private final String label, key;
    private final boolean primary, pressed;
    private final float size;

    Button(Rectangle2D rect, String label, String key, float size, boolean primary, boolean pressed, Runnable action) {
        super(rect, action);
        this.label = label;
        this.key = key;
        this.size = size;
        this.primary = primary;
        this.pressed = pressed;
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        Color bg = primary ? (hover ? Ui.AMBER_LIGHT : Ui.AMBER) : Ui.PANEL_SEE;
        Color border = primary ? Ui.AMBER : pressed ? Ui.MINT : hover ? Ui.AMBER : Ui.LINE;
        Color ink = primary ? Ui.DARK_INK : pressed ? Ui.MINT : hover ? Ui.AMBER : Ui.INK;
        g.setColor(bg);
        g.fill(rect);
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(rect.getX() + 0.5, rect.getY() + 0.5, rect.getWidth() - 1, rect.getHeight() - 1));
        Font f = Ui.font(size, primary ? Font.BOLD : Font.PLAIN, false, 0);
        double labelW = Ui.width(g, label, f), keyW = key == null ? 0 : Ui.width(g, key, Ui.font(11, Font.PLAIN, true, 0)) + 5;
        double x = rect.getCenterX() - (labelW + keyW) / 2, y = Ui.middle(g, f, rect);
        Ui.text(g, label, x, y, f, ink);
        if (key != null) Ui.text(g, key, x + labelW + 5, y, Ui.font(11, Font.PLAIN, true, 0), Ui.DIM);
    }
}

/** 구역 목록의 칸 하나 */
class LevelTile extends Widget {
    private final String number, name, status;
    private final boolean done;

    LevelTile(Rectangle2D rect, String number, String name, String status, boolean done, boolean locked, Runnable action) {
        super(rect, action);
        this.number = number;
        this.name = name;
        this.status = status;
        this.done = done;
        this.enabled = !locked;
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        java.awt.Composite old = g.getComposite();
        if (!enabled) g.setComposite(AlphaComposite.SrcOver.derive(0.38f));
        g.setColor(Ui.PANEL);
        g.fill(rect);
        boolean solid = hover && enabled;
        g.setColor(solid ? Ui.AMBER : Ui.LINE);
        g.setStroke(solid ? new BasicStroke(1f) : Ui.DASHED);
        g.draw(new Rectangle2D.Double(rect.getX() + 0.5, rect.getY() + 0.5, rect.getWidth() - 1, rect.getHeight() - 1));
        double x = rect.getX() + 14;
        Ui.text(g, number, x, rect.getY() + 14 + 12, Ui.font(12, Font.PLAIN, true, 0), Ui.DIM);
        Ui.text(g, name, x, rect.getY() + 14 + 12 + 6 + 17, Ui.font(17, Font.BOLD, false, 0), Ui.INK);
        Ui.text(g, status, x, rect.getMaxY() - 14, Ui.font(14, Font.PLAIN, false, 0.08f), done ? Ui.AMBER : Ui.DIM);
        g.setComposite(old);
    }
}

/** 색과 글꼴, 글자 그리기 도우미 (웹 버전의 CSS 값과 같은 숫자) */
final class Ui {
    private Ui() { }

    static final Color VOID = new Color(7, 8, 24), PANEL = new Color(14, 15, 38), PANEL_SEE = new Color(14, 15, 38, 209);
    static final Color INK = new Color(236, 238, 255), DIM = new Color(143, 148, 189), LINE = new Color(52, 56, 106);
    static final Color AMBER = new Color(255, 198, 92), AMBER_LIGHT = new Color(255, 217, 140), MINT = new Color(111, 242, 196);
    static final Color DANGER = new Color(255, 125, 107), DARK_INK = new Color(27, 19, 4), SOFT_INK = new Color(201, 205, 240);
    static final BasicStroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f);

    private static final Map<String, Font> FONTS = new HashMap<>();

    /** 크기, 굵기, 고정폭 여부, 자간(글자 크기 대비 비율)으로 글꼴을 만든다 */
    static Font font(float size, int style, boolean mono, float tracking) {
        String key = size + "/" + style + "/" + mono + "/" + tracking;
        Font f = FONTS.get(key);
        if (f == null) {
            f = new Font(mono ? Font.MONOSPACED : Font.SANS_SERIF, style, 1).deriveFont(size);
            if (tracking != 0) {
                Map<java.awt.font.TextAttribute, Object> attr = new HashMap<>();
                attr.put(java.awt.font.TextAttribute.TRACKING, tracking);
                f = f.deriveFont(attr);
            }
            FONTS.put(key, f);
        }
        return f;
    }

    static void text(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) y);
    }

    static double width(Graphics2D g, String s, Font f) {
        return f.getStringBounds(s, g.getFontRenderContext()).getWidth();
    }

    /** 사각형 세로 가운데에 글자를 놓을 때의 기준선 위치 */
    static double middle(Graphics2D g, Font f, Rectangle2D r) {
        java.awt.FontMetrics fm = g.getFontMetrics(f);
        return r.getCenterY() + (fm.getAscent() - fm.getDescent()) / 2.0;
    }

    /** 폭 maxW 안에 들어가도록 글을 여러 줄로 나눈다 */
    static List<String> wrap(Graphics2D g, String s, Font f, double maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (width(g, test, f) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}

// ============================================================================
// 게임 본체: 창, 입력, 진행, 화면
// ============================================================================
public class StarDrop extends JPanel {
    private static final long serialVersionUID = 1L;

    // 진행 상태
    private static final int LOADING = 0, AIM = 1, FLY = 2, DEAD = 3, ARRIVE = 4, WIN = 5, DEMO_WAIT = 6;
    private int state = LOADING;
    private boolean demo;                 // 처음 화면 뒤에서 정답을 자동으로 쏘는 시범
    private boolean titleOpen, levelsOpen;
    private boolean endless;              // 무한 배달 모드인가
    private int levelIndex;               // 5개 구역 중 몇 번째인가
    private long endlessSeed;
    private transient Level level;
    private int loadTicket;               // 마지막으로 요청한 구역 번호
    private final int[] progress = new int[LevelGenerator.CAMPAIGN.length];   // 0 = 아직, 1~4 = 별 0~3개로 배달함

    // 조준과 비행
    private double aimAngle, aimPower = 0.5;
    private boolean previewDirty = true;
    private transient Ship ship;
    private double prevX, prevY;          // 한 스텝 전 위치 (부드럽게 그리기 위해)
    private double stepDebt;              // 아직 계산하지 않은 시간
    private double fuel = Rules.FUEL;
    private boolean thrusting, mouseDown, armed, showHint, spaceDown;
    // 키보드 조준: 지금 누르고 있는 방향키들 (여러 개를 동시에 누를 수 있다)
    private boolean leftHeld, rightHeld, upHeld, downHeld, shiftHeld;
    private double holdTime;              // 방향키를 누르고 있은 시간
    private final boolean[] gotStar = new boolean[3];
    private final double[] gotStarAt = new double[3];
    private int tries;
    private double waitTimer;             // 실패/성공 뒤 잠깐 기다리는 시간
    private String tip;                   // 구역 첫 화면의 안내 문구 (첫 발사 때 사라짐)

    // 그리기용 자료
    private final transient List<double[]> trail = new ArrayList<>();      // 날아간 자취 (null = 웜홀로 끊긴 곳)
    private transient List<double[]> ghost = new ArrayList<>();            // 직전 실패의 자취
    private final transient List<double[]> preview = new ArrayList<>();    // 조준 예측 점
    private int previewEnd;
    private final transient List<Particle> particles = new ArrayList<>();
    private final transient List<Object[]> floaters = new ArrayList<>();   // 떠오르는 글자 {글, x, y, 남은 시간, 색}
    private transient BufferedImage background;                            // 지금 창 크기로 그려 둔 배경
    private transient Level backgroundFor;
    private int backgroundW, backgroundH;
    private transient BufferedImage titleGlow;
    private double titleGlowSize;
    private final double[][] twinkles = new double[110][4];
    private double anim, shake;
    private long lastNanos = System.nanoTime();
    private final Random fx = new Random();
    private int fps, fpsFrames;
    private double fpsTime;
    private boolean showFps;

    // 화면 배치
    private double viewScale = 1, viewX, viewY;                            // 게임 세계 → 화면
    private double mouseX = -1, mouseY = -1;                               // 화면 좌표
    private transient List<Widget> widgets = new ArrayList<>();            // 지난번에 그린 버튼들

    /** 구역별 안내 문구 (웹 버전과 같은 내용) */
    private static final String[] TIPS = {
        "마우스를 움직여 방향과 세기를 정하고, 클릭하면 발사합니다. 점선은 궤도의 앞부분만 보여 줍니다.",
        "달은 발사하는 순간부터 공전합니다. 점선 예측에는 달의 움직임이 이미 반영되어 있습니다. 날아가는 동안 누르고 있거나 Space를 누르면 진행 방향으로 추진합니다(연료 1.2초).",
        "블랙홀은 작지만 끌어당기는 힘이 큽니다. 주황색 원반 안쪽으로 들어가면 돌아오지 못합니다.",
        "청록색 별은 소포를 밀어냅니다. 부딪히지는 않으니 방향을 꺾는 데 쓰세요.",
        "보라색 웜홀에 들어가면 속도를 그대로 지닌 채 반대편 웜홀에서 나옵니다.",
    };

    public StarDrop() {
        setPreferredSize(new Dimension(1280, 720));
        setBackground(Ui.VOID);
        setFocusable(true);
        Random r = new Random(11);
        for (double[] t : twinkles) {       // 웹처럼 화면 밖까지 넉넉히 흩어 둔다
            t[0] = -500 + r.nextDouble() * 2600;
            t[1] = -500 + r.nextDouble() * 1900;
            t[2] = r.nextDouble() * Math.PI * 2;
            t[3] = 0.6 + r.nextDouble() * 2.2;
        }
        loadProgress();
        installInput();
        showTitle();
        if (!GraphicsEnvironment.isHeadless()) startLoop();
    }

    // ------------------------------------------------------------ 게임 루프
    /**
     * 모니터 주사율(60Hz, 144Hz 등)에 맞춰 "계산 → 그리기"를 반복한다.
     * Swing 의 Timer 는 윈도우에서 간격이 들쭉날쭉해서 움직임이 끊겨 보이므로 직접 만든다.
     */
    private void startLoop() {
        Thread precise = new Thread(() -> {     // 윈도우 타이머를 1ms 단위로 정밀하게 만드는 요령
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) { /* 종료 */ }
        });
        precise.setDaemon(true);
        precise.start();

        int hz = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate();
        if (hz < 30 || hz > 240) hz = 60;
        final long frameNanos = 1_000_000_000L / hz;
        Thread loop = new Thread(() -> {
            long next = System.nanoTime();
            while (true) {
                try {
                    SwingUtilities.invokeAndWait(this::frame);
                } catch (Exception e) {
                    return;
                }
                next += frameNanos;
                long sleep = next - System.nanoTime();
                if (sleep > 0) LockSupport.parkNanos(sleep); else next = System.nanoTime();
            }
        }, "game-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private void frame() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        fpsFrames++;
        fpsTime += dt;
        if (fpsTime >= 0.5) {
            fps = (int) Math.round(fpsFrames / fpsTime);
            fpsFrames = 0;
            fpsTime = 0;
        }
        update(dt);
        if (isShowing()) paintImmediately(0, 0, getWidth(), getHeight());
        Toolkit.getDefaultToolkit().sync();
    }

    // ------------------------------------------------------------ 진행 기록 저장
    private void loadProgress() {
        try {
            String[] parts = java.util.prefs.Preferences.userRoot().node("stardrop").get("progress", "").split(",");
            for (int i = 0; i < parts.length && i < progress.length; i++) progress[i] = Integer.parseInt(parts[i].trim());
        } catch (Exception e) {
            // 기록이 없거나 읽을 수 없으면 처음부터
        }
    }

    private void saveProgress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < progress.length; i++) sb.append(i == 0 ? "" : ",").append(progress[i]);
        try {
            java.util.prefs.Preferences.userRoot().node("stardrop").put("progress", sb.toString());
        } catch (Exception e) {
            // 저장하지 못해도 게임은 계속된다
        }
    }

    // ------------------------------------------------------------ 화면 전환과 구역 불러오기
    private void showTitle() {
        titleOpen = true;
        levelsOpen = false;
        endless = false;
        loadLevel(LevelGenerator.CAMPAIGN_SEED[1], LevelGenerator.CAMPAIGN[1], LevelGenerator.CAMPAIGN_FIRST_TRY[1], true);
    }

    private void showLevels() {
        titleOpen = false;
        levelsOpen = true;
    }

    private void startCampaign(int index) {
        titleOpen = false;
        levelsOpen = false;
        endless = false;
        levelIndex = index;
        loadLevel(LevelGenerator.CAMPAIGN_SEED[index], LevelGenerator.CAMPAIGN[index], LevelGenerator.CAMPAIGN_FIRST_TRY[index], false);
    }

    private void startEndless() {
        titleOpen = false;
        levelsOpen = false;
        endless = true;
        endlessSeed = 1 + fx.nextInt(999_999);
        loadLevel(endlessSeed, LevelGenerator.endlessConfig(endlessSeed), 0, false);
    }

    /** 구역 생성은 시간이 걸릴 수 있어서 다른 스레드에서 하고, 끝나면 화면 스레드로 넘긴다 */
    private void loadLevel(long seed, LevelConfig cfg, int firstTry, boolean asDemo) {
        state = LOADING;
        thrusting = false;
        Sound.thrust(false);
        final int ticket = ++loadTicket;      // 요청 번호. 늦게 끝난 옛 요청이 새 구역을 덮어쓰지 않게 한다
        Thread worker = new Thread(() -> {
            Level made = LevelGenerator.create(seed, cfg, firstTry);
            SwingUtilities.invokeLater(() -> {
                if (ticket == loadTicket) enterLevel(made, asDemo);
            });
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void enterLevel(Level made, boolean asDemo) {
        level = made;
        demo = asDemo;
        tries = 0;
        showHint = false;
        ghost = new ArrayList<>();
        particles.clear();
        floaters.clear();
        aimAngle = Math.atan2(level.getTargetY() - level.getStartY(), level.getTargetX() - level.getStartX());
        aimPower = 0.5;
        resetShot();
        tip = !demo && !endless ? TIPS[levelIndex] : null;
        if (demo) {
            state = DEMO_WAIT;
            waitTimer = 0.6;
        } else {
            state = AIM;
        }
    }

    private void resetShot() {
        ship = null;
        trail.clear();
        fuel = Rules.FUEL;
        thrusting = false;
        Sound.thrust(false);
        for (int i = 0; i < 3; i++) gotStar[i] = false;
        previewDirty = true;
    }

    private void retry() {
        if (demo || state == LOADING) return;
        if (state == FLY) ghost = new ArrayList<>(trail);
        state = AIM;
        waitTimer = 0;
        resetShot();
    }

    private void nextLevel() {
        if (!endless && levelIndex < LevelGenerator.CAMPAIGN.length - 1) startCampaign(levelIndex + 1);
        else startEndless();
    }

    // ------------------------------------------------------------ 진행
    /** 조준선: 궤도의 앞부분만 미리 계산해서 점으로 보여 준다 */
    private void updatePreview() {
        preview.clear();
        previewEnd = Rules.FLYING;
        previewDirty = false;
        if (level == null) return;
        Ship s = new Ship(level, aimAngle, aimPower);
        for (int i = 0; i < level.getPreviewSteps() && previewEnd == Rules.FLYING; i++) {
            previewEnd = s.step(level, false);
            if (i % 4 == 3) preview.add(new double[]{s.getX(), s.getY()});
        }
        if (previewEnd == Rules.CRASHED) preview.add(new double[]{s.getX(), s.getY()});
    }

    private void launch(double angle, double power) {
        resetShot();
        ship = new Ship(level, angle, power);
        prevX = ship.getX();
        prevY = ship.getY();
        stepDebt = 0;
        state = FLY;
        burst(ship.getX(), ship.getY(), 14, 40, 120, 0.5, 2.4);
        if (!demo) {
            tries++;
            tip = null;
            Sound.launch(power);
        }
    }

    private void update(double dt) {
        anim += dt;
        aimWithKeys(dt);
        if (state == AIM && previewDirty) updatePreview();
        if (state == FLY) {
            stepDebt += dt;
            while (stepDebt >= Rules.DT && state == FLY) {
                stepDebt -= Rules.DT;
                stepOnce();
            }
        } else if (waitTimer > 0) {
            waitTimer -= dt;
            if (waitTimer <= 0) {
                if (state == DEAD) {
                    state = AIM;
                    resetShot();
                } else if (state == DEMO_WAIT && level != null) {
                    launch(level.getSolutionAngle(), level.getSolutionPower());
                } else if (state == ARRIVE) {
                    state = WIN;
                    Sound.thrust(false);
                }
            }
        }
        particles.removeIf(p -> !p.update(dt));
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Object[] f = floaters.get(i);
            f[3] = (Double) f[3] - dt;
            f[2] = (Double) f[2] - 22 * dt;
            if ((Double) f[3] <= 0) floaters.remove(i);
        }
        if (shake > 0.3) shake *= 0.88; else shake = 0;
    }

    /** 물리 한 스텝 + 그에 따른 게임 처리 */
    /**
     * 누르고 있는 방향키마다 조준을 계속 움직인다.
     * 운영체제의 키 반복은 마지막에 누른 키 하나만 반복하므로, 키를 누른 상태를 직접 기억해서 처리한다.
     * 처음 0.25초는 가만히 있다가(한 번 톡 누르면 한 칸만 움직이게), 그 뒤로는 오래 누를수록 빨라진다.
     */
    private void aimWithKeys(double dt) {
        boolean any = leftHeld || rightHeld || upHeld || downHeld;
        if (!any || state != AIM || demo || overlayOpen()) {
            holdTime = 0;
            return;
        }
        holdTime += dt;
        if (holdTime < 0.25) return;
        double boost = Math.min(3, 1 + (holdTime - 0.25) * 2);           // 1배에서 시작해 1초 뒤 3배
        double turn = (shiftHeld ? 0.015 : 0.12) * boost * dt;            // 초당 각도 변화(라디안)
        double push = (shiftHeld ? 0.03 : 0.18) * boost * dt;             // 초당 세기 변화
        if (leftHeld) aimAngle -= turn;
        if (rightHeld) aimAngle += turn;
        if (upHeld) aimPower = Math.min(1, aimPower + push);
        if (downHeld) aimPower = Math.max(0, aimPower - push);
        previewDirty = true;
    }

    /** 방향키를 처음 눌렀을 때 한 칸만 움직인다 (톡톡 눌러서 미세 조정) */
    private void nudge(int key) {
        double fine = shiftHeld ? 0.0005 : 0.004, finePower = shiftHeld ? 0.001 : 0.006;
        if (key == KeyEvent.VK_LEFT) aimAngle -= fine;
        if (key == KeyEvent.VK_RIGHT) aimAngle += fine;
        if (key == KeyEvent.VK_UP) aimPower = Math.min(1, aimPower + finePower);
        if (key == KeyEvent.VK_DOWN) aimPower = Math.max(0, aimPower - finePower);
        previewDirty = true;
    }

    /** 방향키를 누르거나 뗐을 때 상태를 기록한다. 처음 누른 것이면 true */
    private boolean setArrow(int key, boolean down) {
        boolean before;
        switch (key) {
            case KeyEvent.VK_LEFT: before = leftHeld; leftHeld = down; break;
            case KeyEvent.VK_RIGHT: before = rightHeld; rightHeld = down; break;
            case KeyEvent.VK_UP: before = upHeld; upHeld = down; break;
            case KeyEvent.VK_DOWN: before = downHeld; downHeld = down; break;
            default: return false;
        }
        return down && !before;
    }

    private void stepOnce() {
        prevX = ship.getX();
        prevY = ship.getY();
        boolean burn = thrusting && fuel > 0 && !demo;
        if (burn) {
            fuel -= Rules.DT;
            if (ship.getSteps() % 2 == 0) {
                double speed = Math.hypot(ship.getVx(), ship.getVy());
                particles.add(new Particle(ship.getX(), ship.getY(), -ship.getVx() / speed * 150 + (fx.nextDouble() - 0.5) * 90,
                        -ship.getVy() / speed * 150 + (fx.nextDouble() - 0.5) * 90, 0.4, 2.6, 195));
            }
        }
        Sound.thrust(burn);
        int result = ship.step(level, burn);

        if (ship.hasJustWarped()) {
            trail.add(null);
            burst(prevX, prevY, 18, 275, 140, 0.6, 2.4);
            burst(ship.getX(), ship.getY(), 18, 275, 140, 0.6, 2.4);
            prevX = ship.getX();
            prevY = ship.getY();
            if (!demo) Sound.warp();
        }
        trail.add(new double[]{ship.getX(), ship.getY()});

        for (int i = 0; i < 3; i++) {
            double dx = ship.getX() - level.getStarX(i), dy = ship.getY() - level.getStarY(i);
            if (!gotStar[i] && dx * dx + dy * dy < Rules.STAR_R * Rules.STAR_R) {
                gotStar[i] = true;
                gotStarAt[i] = anim;
                burst(level.getStarX(i), level.getStarY(i), 22, 48, 170, 0.8, 2.6);
                if (!demo) Sound.star(starCount() - 1);
            }
        }
        if (result != Rules.FLYING) endFlight(result);
    }

    private void endFlight(int result) {
        thrusting = false;
        Sound.thrust(false);
        if (result == Rules.ARRIVED) {
            burst(level.getTargetX(), level.getTargetY(), 70, 155, 300, 1.3, 3.2);
            burst(level.getTargetX(), level.getTargetY(), 30, 45, 200, 1.1, 2.6);
            if (demo) {
                state = DEMO_WAIT;
                waitTimer = 1.6;
                return;
            }
            Sound.win();
            state = ARRIVE;                 // 불꽃을 잠깐 보여 준 뒤 영수증을 띄운다
            waitTimer = 0.9;
            if (!endless) {
                progress[levelIndex] = Math.max(progress[levelIndex], starCount() + 1);
                saveProgress();
            }
            return;
        }
        ghost = new ArrayList<>(trail);
        if (result == Rules.CRASHED) {
            burst(ship.getX(), ship.getY(), 60, 22, 260, 0.9, 3.4);
            burst(ship.getX(), ship.getY(), 20, 0, 90, 1.2, 4.5);
            if (!demo) {
                shake = 13;
                Sound.crash();
                boolean swallowed = ship.getCrashedInto() instanceof BlackHole;
                addFloater(swallowed ? "삼켜졌습니다" : "파손", ship.getX(), ship.getY() - 30, Ui.DANGER);
            }
        } else if (!demo) {
            Sound.lost();
            addFloater(ship.getSteps() >= Rules.MAX_STEPS ? "연락 두절" : "우주 미아", ship.getX(), ship.getY(), new Color(154, 160, 200));
        }
        state = demo ? DEMO_WAIT : DEAD;
        waitTimer = demo ? 1.0 : 0.65;
    }

    private int starCount() {
        int n = 0;
        for (boolean got : gotStar) if (got) n++;
        return n;
    }

    private void burst(double x, double y, int count, double hue, double speed, double life, double size) {
        for (int i = 0; i < count; i++) {
            double a = fx.nextDouble() * Math.PI * 2, v = speed * (0.2 + fx.nextDouble());
            particles.add(new Particle(x, y, Math.cos(a) * v, Math.sin(a) * v, life * (0.5 + fx.nextDouble() * 0.5),
                    size * (0.5 + fx.nextDouble()), hue + (fx.nextDouble() - 0.5) * 30));
        }
    }

    private void addFloater(String text, double x, double y, Color color) {
        floaters.add(new Object[]{text, Math.max(80, Math.min(Rules.W - 80, x)), Math.max(60, Math.min(Rules.H - 40, y)), 1.6, color});
    }

    // ------------------------------------------------------------ 입력
    /** 화면 위 버튼이 열려 있어 게임 조작을 막아야 하는가 */
    private boolean overlayOpen() {
        return titleOpen || levelsOpen || state == WIN;
    }

    private void installInput() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (state == AIM && !demo && !overlayOpen()) aimAt(e);
                boolean onButton = false;
                for (Widget w : widgets) if (w.contains(mouseX, mouseY)) onButton = true;
                setCursor(java.awt.Cursor.getPredefinedCursor(onButton ? java.awt.Cursor.HAND_CURSOR
                        : overlayOpen() || demo ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.CROSSHAIR_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (state == AIM && mouseDown && !demo && !overlayOpen()) aimAt(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Sound.start();
                for (int i = widgets.size() - 1; i >= 0; i--) {     // 버튼을 먼저 확인한다
                    Widget w = widgets.get(i);
                    if (w.contains(e.getX(), e.getY())) {
                        Sound.click();
                        w.click();
                        return;
                    }
                }
                if (overlayOpen() || demo || level == null) return;
                mouseDown = true;
                if (state == AIM) {
                    armed = true;
                    aimAt(e);
                } else if (state == FLY) {
                    thrusting = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (state == AIM && mouseDown && armed && !demo) launch(aimAngle, aimPower);
                mouseDown = false;
                armed = false;
                thrusting = false;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                leftHeld = rightHeld = upHeld = downHeld = shiftHeld = spaceDown = false;
                thrusting = false;
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Sound.start();
                int key = e.getKeyCode();
                if (key == KeyEvent.VK_SHIFT) shiftHeld = true;
                boolean firstArrow = setArrow(key, true);     // 키 반복으로 다시 들어온 것은 false
                if (key == KeyEvent.VK_F3) showFps = !showFps;
                if (key == KeyEvent.VK_M) Sound.setMuted(!Sound.isMuted());
                if (key == KeyEvent.VK_ESCAPE && !titleOpen) showTitle();
                if (titleOpen || levelsOpen || demo || level == null) return;
                boolean firstPress = key != KeyEvent.VK_SPACE || !spaceDown;
                if (key == KeyEvent.VK_SPACE) spaceDown = true;
                if (state == WIN) {
                    if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) && firstPress) nextLevel();
                    if (key == KeyEvent.VK_R) retry();
                    return;
                }
                if (key == KeyEvent.VK_H) showHint = !showHint;
                if (key == KeyEvent.VK_R) retry();
                if (state == AIM) {
                    if (firstArrow) nudge(key);
                    if ((key == KeyEvent.VK_SPACE && firstPress) || key == KeyEvent.VK_ENTER) launch(aimAngle, aimPower);
                } else if (state == FLY && key == KeyEvent.VK_SPACE) {
                    thrusting = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) shiftHeld = false;
                setArrow(e.getKeyCode(), false);
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    spaceDown = false;
                    thrusting = false;
                }
            }
        });
    }

    /** 출발점에서 마우스까지의 방향이 각도, 거리가 세기 */
    private void aimAt(MouseEvent e) {
        if (level == null) return;
        double wx = (e.getX() - viewX) / viewScale, wy = (e.getY() - viewY) / viewScale;
        double dx = wx - level.getStartX(), dy = wy - level.getStartY(), dist = Math.hypot(dx, dy);
        if (dist < 10) return;
        aimAngle = Math.atan2(dy, dx);
        aimPower = Math.max(0, Math.min(1, (dist - 50) / 330));
        previewDirty = true;
    }

    // ------------------------------------------------------------ 배경
    /**
     * 배경(색 그라데이션, 성운, 별, 휘어진 시공간 격자)을 지금 창 크기 그대로 한 장 그려 둔다.
     * 웹처럼 게임 세계 바깥 여백까지 채우므로 창 비율이 달라도 검은 띠가 생기지 않는다.
     */
    private BufferedImage makeBackground(Graphics2D screen, int w, int h) {
        double dev = screen.getTransform().getScaleX();          // 윈도우 배율(125%, 150% 등)
        int pw = Math.max(2, (int) Math.ceil(w * dev)), ph = Math.max(2, (int) Math.ceil(h * dev));
        BufferedImage img = Paints.newImage(screen, pw, ph, false);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, pw * 0.3f, ph, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(6, 7, 22), new Color(10, 9, 32), new Color(19, 10, 36)}));
        g.fillRect(0, 0, pw, ph);
        if (level == null) {
            g.dispose();
            return img;
        }
        g.scale(dev, dev);
        g.translate(viewX, viewY);
        g.scale(viewScale, viewScale);
        double vx0 = -viewX / viewScale, vy0 = -viewY / viewScale, vw = w / viewScale, vh = h / viewScale;

        Random r = new Random(level.getSeed() * 17 + 3);
        double baseHue = r.nextDouble() * 360;
        for (int i = 0; i < 9; i++) {           // 성운
            double x = vx0 + r.nextDouble() * vw, y = vy0 + r.nextDouble() * vh, rad = 180 + r.nextDouble() * 420, hue = baseHue + (r.nextDouble() - 0.5) * 90;
            g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) rad, new float[]{0f, 0.5f, 1f},
                    new Color[]{Paints.hsl(hue, 0.75, 0.45, 18 + r.nextInt(23)), Paints.hsl(hue + 25, 0.7, 0.35, 10), Paints.hsl(hue, 0.7, 0.35, 0)}));
            g.fill(new Rectangle2D.Double(x - rad, y - rad, rad * 2, rad * 2));
        }
        for (int i = 0; i < 520; i++) {         // 별
            double x = vx0 + r.nextDouble() * vw, y = vy0 + r.nextDouble() * vh, size = 0.3 + r.nextDouble() * r.nextDouble() * 1.5;
            g.setColor(Paints.hsl(r.nextDouble() < 0.2 ? 30 + r.nextDouble() * 30 : 210 + r.nextDouble() * 40, r.nextDouble() * 0.6, 0.75 + r.nextDouble() * 0.25, 38 + r.nextInt(190)));
            Paints.circle(g, x, y, size);
        }
        // 휘어진 시공간 격자: 격자점을 천체 쪽으로(밀어내는 별은 바깥으로) 옮겨서 그린다
        g.setColor(new Color(140, 165, 255, 22));
        g.setStroke(new BasicStroke((float) (1 / viewScale)));
        double[] p = new double[2];
        int grid = 50;
        double x0 = Math.floor(vx0 / grid) * grid, y0 = Math.floor(vy0 / grid) * grid;
        for (double x = x0; x <= vx0 + vw + grid; x += grid) {
            Path2D line = new Path2D.Double();
            for (double y = y0; y <= vy0 + vh + grid; y += 10) {
                warp(level, x, y, p);
                if (y == y0) line.moveTo(p[0], p[1]); else line.lineTo(p[0], p[1]);
            }
            g.draw(line);
        }
        for (double y = y0; y <= vy0 + vh + grid; y += grid) {
            Path2D line = new Path2D.Double();
            for (double x = x0; x <= vx0 + vw + grid; x += 10) {
                warp(level, x, y, p);
                if (x == x0) line.moveTo(p[0], p[1]); else line.lineTo(p[0], p[1]);
            }
            g.draw(line);
        }
        g.dispose();
        return img;
    }

    private static void warp(Level lv, double x, double y, double[] out) {
        double dx = 0, dy = 0;
        for (int i = 0; i < lv.getBodyCount(); i++) {
            if (!(lv.getBody(i) instanceof GravityBody)) continue;
            GravityBody b = (GravityBody) lv.getBody(i);
            double ex = b.getX() - x, ey = b.getY() - y, d2 = ex * ex + ey * ey + 400, d = Math.sqrt(d2);
            double pull = b.getMass() / d2 * 0.009, size = Math.min(Math.abs(pull), Math.min(d * 0.45, 26));
            dx += ex / d * (pull < 0 ? -size : size);
            dy += ey / d * (pull < 0 ? -size : size);
        }
        out[0] = x + dx;
        out[1] = y + dy;
    }

    // ------------------------------------------------------------ 화면 그리기
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        int w = getWidth(), h = getHeight();
        AffineTransform base = g.getTransform();      // 화면 좌표 (HUD 와 버튼은 여기에 그린다)

        // 게임 세계(1600x900)를 창 가운데에 꽉 차게
        viewScale = Math.min(w / (double) Rules.W, h / (double) Rules.H);
        viewX = (w - Rules.W * viewScale) / 2;
        viewY = (h - Rules.H * viewScale) / 2;

        if (background == null || backgroundFor != level || backgroundW != w || backgroundH != h) {
            background = makeBackground(g, w, h);
            backgroundFor = level;
            backgroundW = w;
            backgroundH = h;
        }
        g.setTransform(new AffineTransform());
        g.drawImage(background, (int) Math.round(base.getTranslateX()), (int) Math.round(base.getTranslateY()), null);
        g.setTransform(base);

        List<Widget> next = new ArrayList<>();
        if (level != null) {
            double sx = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0, sy = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0;
            g.translate(viewX + sx, viewY + sy);
            g.scale(viewScale, viewScale);
            drawWorld(g);
            g.setTransform(base);
            if (!demo && state != LOADING) drawHud(g, w, h, next);
        }
        if (titleOpen) drawTitle(g, w, h, next);
        if (levelsOpen) drawLevels(g, w, h, next);
        if (state == WIN && !demo) drawReceipt(g, w, h, next);
        if (state == LOADING) drawBusy(g, w, h);
        if (showFps) Ui.text(g, "FPS " + fps, w - 90, h - 40, Ui.font(13, Font.BOLD, true, 0), Ui.MINT);

        for (Widget wd : next) {                       // 버튼은 맨 위에 그린다
            wd.hover = wd.contains(mouseX, mouseY);
            wd.draw(g, anim, 0);
        }
        widgets = next;
    }

    private void drawWorld(Graphics2D g) {
        for (double[] t : twinkles) {           // 반짝이는 별
            g.setColor(new Color(223, 230, 255, (int) (60 + 190 * (0.5 + 0.5 * Math.sin(anim * t[3] + t[2])))));
            g.fill(new Rectangle2D.Double(t[0], t[1], t[2] % 1.3 + 0.6, t[2] % 1.3 + 0.6));
        }
        double flightTime = ship != null && state != AIM ? ship.getTime() : 0;

        // 화면에 그릴 것들을 Drawable 로 묶어 반복문 하나로 그린다 (다형성)
        List<Drawable> things = new ArrayList<>();
        if (level.getWormhole() != null) things.add(level.getWormhole());
        for (int i = 0; i < level.getBodyCount(); i++) things.add(level.getBody(i));
        for (Drawable d : things) d.draw(g, anim, flightTime);

        drawTarget(g);
        drawStartPad(g);
        drawStars(g);
        if (state != WIN && state != ARRIVE && !demo) drawGhost(g);
        if (state == AIM) drawAim(g);
        drawShip(g);
        for (Particle p : particles) p.draw(g, anim, flightTime);
        for (Object[] f : floaters) {
            Color c = (Color) f[4];
            int alpha = (int) (255 * Math.max(0, Math.min(1, (Double) f[3])));
            Font font = Ui.font(17, Font.BOLD, false, 0);
            String s = (String) f[0];
            Ui.text(g, s, (Double) f[1] - Ui.width(g, s, font) / 2, (Double) f[2], font, new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
        }
    }

    private void drawTarget(Graphics2D g) {
        double x = level.getTargetX(), y = level.getTargetY(), pulse = (anim * 0.7) % 1;
        Paints.glow(g, x, y, 70, new Color(111, 242, 196, 66));
        g.setColor(new Color(111, 242, 196, (int) ((1 - pulse) * 153)));
        g.setStroke(new BasicStroke(1.5f));
        Paints.ring(g, x, y, Rules.TARGET_R + pulse * 34);
        g.setColor(Ui.MINT);
        g.setStroke(new BasicStroke(4f));
        double r = Rules.TARGET_R - 5;
        for (int i = 0; i < 3; i++) {
            g.draw(new Arc2D.Double(x - r, y - r, r * 2, r * 2, -Math.toDegrees(anim * 0.9) - i * 120, -89, Arc2D.OPEN));
        }
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(111, 242, 196, 140));
        g.draw(new Arc2D.Double(x - 13, y - 13, 26, 26, Math.toDegrees(anim * 1.4), -252, Arc2D.OPEN));
        g.setColor(new Color(234, 255, 247));
        Paints.circle(g, x, y, 3.5);
    }

    private void drawStartPad(Graphics2D g) {
        double x = level.getStartX(), y = level.getStartY();
        g.setColor(new Color(255, 198, 92, 140));
        g.setStroke(new BasicStroke(1.5f));
        Paints.ring(g, x, y, 20);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + anim * 0.2;
            g.draw(new Line2D.Double(x + Math.cos(a) * 24, y + Math.sin(a) * 24, x + Math.cos(a) * 28, y + Math.sin(a) * 28));
        }
    }

    private void drawStars(Graphics2D g) {
        for (int i = 0; i < 3; i++) {
            double x = level.getStarX(i), y = level.getStarY(i);
            if (gotStar[i]) {
                double e = anim - gotStarAt[i];
                if (e < 0.5) {              // 얻은 직후 퍼지는 고리
                    g.setColor(new Color(255, 214, 120, (int) (255 * (1 - e * 2))));
                    g.setStroke(new BasicStroke(2f));
                    Paints.ring(g, x, y, 12 + e * 90);
                }
                continue;
            }
            y += Math.sin(anim * 2 + i * 2) * 2.5;
            Paints.glow(g, x, y, 30, new Color(255, 205, 100, 107));
            g.setColor(new Color(255, 214, 122));
            g.fill(starShape(x, y, 12.5, anim * 0.8 + i, 8, 0.38));
            g.setColor(new Color(255, 251, 233));
            g.fill(starShape(x, y, 6, anim * 0.8 + i, 8, 0.38));
        }
    }

    private Shape starShape(double x, double y, double r, double rot, int points, double inner) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < points; i++) {
            double a = rot + i * Math.PI * 2 / points, rr = i % 2 == 1 ? r * inner : r;
            if (i == 0) p.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
            else p.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
        }
        p.closePath();
        return p;
    }

    /** 직전 실패의 자취: 옅은 점선 */
    private void drawGhost(Graphics2D g) {
        if (ghost.isEmpty()) return;
        g.setColor(new Color(190, 200, 255, 77));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{2f, 6f}, 0f));
        Path2D path = new Path2D.Double();
        boolean pen = false;
        for (int i = 0; i < ghost.size(); i += 3) {
            double[] p = ghost.get(i);
            if (p == null) { pen = false; continue; }
            if (pen) path.lineTo(p[0], p[1]); else path.moveTo(p[0], p[1]);
            pen = true;
        }
        g.draw(path);
    }

    private void drawAim(Graphics2D g) {
        double sx = level.getStartX(), sy = level.getStartY();
        int n = preview.size();
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            g.setColor(new Color(255, 227, 166, (int) (242 - t * 204)));
            Paints.circle(g, preview.get(i)[0], preview.get(i)[1], 2.5 - t * 1.1);
        }
        if (previewEnd == Rules.CRASHED && n > 0) {     // 예측 중에 부딪히면 X 표시
            double[] p = preview.get(n - 1);
            g.setColor(new Color(255, 107, 107));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Line2D.Double(p[0] - 7, p[1] - 7, p[0] + 7, p[1] + 7));
            g.draw(new Line2D.Double(p[0] + 7, p[1] - 7, p[0] - 7, p[1] + 7));
        }
        // 화살표: 세기가 셀수록 길다
        double len = 34 + aimPower * 86, ca = Math.cos(aimAngle), sa = Math.sin(aimAngle), ex = sx + ca * len, ey = sy + sa * len;
        g.setColor(Ui.AMBER);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(sx + ca * 24, sy + sa * 24, ex, ey));
        Path2D head = new Path2D.Double();
        head.moveTo(ex + ca * 11, ey + sa * 11);
        head.lineTo(ex - sa * 6.5, ey + ca * 6.5);
        head.lineTo(ex + sa * 6.5, ey - ca * 6.5);
        head.closePath();
        g.fill(head);
        g.setColor(new Color(255, 198, 92, 230));
        g.setStroke(new BasicStroke(4f));
        g.draw(new Arc2D.Double(sx - 20, sy - 20, 40, 40, -Math.toDegrees(aimAngle) - aimPower * 180, aimPower * 360, Arc2D.OPEN));

        if (showHint) {                                 // 풀이기가 찾은 정답 방향
            double ha = level.getSolutionAngle(), hl = 34 + level.getSolutionPower() * 86;
            g.setColor(Ui.MINT);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 6f}, 0f));
            g.draw(new Line2D.Double(sx + Math.cos(ha) * 24, sy + Math.sin(ha) * 24, sx + Math.cos(ha) * hl, sy + Math.sin(ha) * hl));
            Paints.circle(g, sx + Math.cos(ha) * hl, sy + Math.sin(ha) * hl, 4.5);
        }
    }

    private void drawShip(Graphics2D g) {
        if (ship != null && state == FLY) {
            // 꼬리: 넓은 주황 줄기 위에 가는 흰 줄기를 겹쳐서 혜성처럼
            drawTail(g, 80, 6.5, new int[]{255, 179, 71}, 0.85);
            drawTail(g, 30, 3, new int[]{255, 243, 208}, 0.9);
            // 물리 스텝 사이를 이어서 부드럽게 보이게 한다
            double k = stepDebt / Rules.DT, x = prevX + (ship.getX() - prevX) * k, y = prevY + (ship.getY() - prevY) * k;
            if (Math.abs(ship.getX() - prevX) > 40 || Math.abs(ship.getY() - prevY) > 40) { x = ship.getX(); y = ship.getY(); }
            Paints.glow(g, x, y, 26, new Color(255, 190, 90, 191));
            Paints.glow(g, x, y, 7, Color.WHITE);
            g.setColor(Color.WHITE);
            Paints.circle(g, x, y, 3);
        } else if (state == AIM || (state == DEMO_WAIT && ship == null)) {
            double pulse = 1 + Math.sin(anim * 4) * 0.12, x = level.getStartX(), y = level.getStartY();
            Paints.glow(g, x, y, 22 * pulse, new Color(255, 190, 90, 179));
            Paints.glow(g, x, y, 6.5, Color.WHITE);
            g.setColor(Color.WHITE);
            Paints.circle(g, x, y, 2.8);
        }
    }

    private void drawTail(Graphics2D g, int length, double width, int[] rgb, double alpha) {
        int n = trail.size(), from = Math.max(1, n - length);
        for (int i = from; i < n; i++) {
            double[] a = trail.get(i - 1), b = trail.get(i);
            if (a == null || b == null) continue;
            double t = (i - from) / (double) (n - from);
            g.setColor(new Color(rgb[0], rgb[1], rgb[2], (int) (255 * alpha * t * t)));
            g.setStroke(new BasicStroke((float) (0.6 + width * t), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
        }
    }

    // ------------------------------------------------------------ 화면 위 UI (화면 좌표, 웹 버전 CSS 와 같은 크기)
    private void drawHud(Graphics2D g, int w, int h, List<Widget> out) {
        // 왼쪽 위: 운송장
        int total = LevelGenerator.CAMPAIGN.length;
        String no = endless ? "No. " + endlessSeed : String.format("No. %02d / %02d", levelIndex + 1, total);
        String[][] cells = {{"운송장", no, "mono"}, {"구역", level.getName(), ""}, {"별", stars(starCount()), "star"}, {"발사", tries + "회", "mono"}};
        Font label = Ui.font(10, Font.PLAIN, false, 0.14f);
        double x = 16, y = 14, cellH = 44, waybillW = 0;
        double[] widths = new double[cells.length];
        for (int i = 0; i < cells.length; i++) {
            Font vf = valueFont(cells[i][2]);
            widths[i] = Math.max(Ui.width(g, cells[i][0], label), Ui.width(g, cells[i][1], vf)) + 24;
            waybillW += widths[i];
        }
        g.setColor(Ui.PANEL_SEE);
        g.fill(new Rectangle2D.Double(x, y, waybillW, cellH));
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(x + 0.5, y + 0.5, waybillW - 1, cellH - 1));
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) g.draw(new Line2D.Double(x + 0.5, y, x + 0.5, y + cellH));
            Ui.text(g, cells[i][0], x + 12, y + 17, label, Ui.DIM);
            Ui.text(g, cells[i][1], x + 12, y + 36, valueFont(cells[i][2]), cells[i][2].equals("star") ? Ui.AMBER : Ui.INK);
            x += widths[i];
        }

        // 오른쪽 위: 버튼들 (자리가 모자라면 운송장 아래 줄로)
        String[][] tools = {{"다시 조준", "R"}, {"정답 방향 보기", "H"}, {Sound.isMuted() ? "소리 켜기" : "소리 끄기", null}, {"구역 목록", null}};
        Runnable[] actions = {this::retry, () -> showHint = !showHint, () -> Sound.setMuted(!Sound.isMuted()), this::showLevels};
        Font tf = Ui.font(13, Font.PLAIN, false, 0);
        double toolsW = -6;
        double[] tw = new double[tools.length];
        for (int i = 0; i < tools.length; i++) {
            tw[i] = Ui.width(g, tools[i][0], tf) + 24 + (tools[i][1] == null ? 0 : Ui.width(g, tools[i][1], Ui.font(11, Font.PLAIN, true, 0)) + 5);
            toolsW += tw[i] + 6;
        }
        double tx = w - 16 - toolsW, ty = 14;
        if (tx < 16 + waybillW + 8) {
            tx = Math.max(16, w - 16 - toolsW);
            ty = 14 + cellH + 8;
        }
        for (int i = 0; i < tools.length; i++) {
            out.add(new Button(new Rectangle2D.Double(tx, ty, tw[i], 31), tools[i][0], tools[i][1], 13, false, i == 1 && showHint, actions[i]));
            tx += tw[i] + 6;
        }

        // 왼쪽 아래: 조준 값
        double deg = ((-Math.toDegrees(aimAngle)) % 360 + 360) % 360;
        Ui.text(g, String.format("각도 %.1f°  ·  세기 %d%%", deg, Math.round(aimPower * 100)), 16, h - 18, Ui.font(12, Font.PLAIN, true, 0), Ui.DIM);

        // 오른쪽 아래: 추진 연료
        Font ff = Ui.font(11, Font.PLAIN, false, 0.12f);
        double barX = w - 16 - 120, barY = h - 14 - 10;
        Ui.text(g, "추진 연료", barX - 8 - Ui.width(g, "추진 연료", ff), barY + 7, ff, Ui.DIM);
        g.setColor(Ui.PANEL_SEE);
        g.fill(new Rectangle2D.Double(barX, barY, 120, 6));
        g.setColor(new Color(127, 220, 255));
        g.fill(new Rectangle2D.Double(barX, barY, 120 * Math.max(0, fuel / Rules.FUEL), 6));
        g.setColor(Ui.LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(barX - 0.5, barY - 0.5, 121, 7));

        // 아래 가운데: 안내 문구
        if (tip != null && state == AIM) {
            Font f = Ui.font(14, Font.PLAIN, false, 0);
            double boxW = Math.min(500, w - 32);
            List<String> lines = Ui.wrap(g, tip, f, boxW - 28 - 3);
            double boxH = 20 + lines.size() * 22.4, bx = (w - boxW) / 2, by = h - 44 - boxH;
            g.setColor(new Color(14, 15, 38, 230));
            g.fill(new Rectangle2D.Double(bx, by, boxW, boxH));
            g.setColor(Ui.AMBER);
            g.fill(new Rectangle2D.Double(bx, by, 3, boxH));
            for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), bx + 17, by + 10 + 16 + i * 22.4, f, Ui.INK);
        }
    }

    private Font valueFont(String kind) {
        if (kind.equals("mono")) return Ui.font(14, Font.PLAIN, true, 0);
        if (kind.equals("star")) return Ui.font(14, Font.BOLD, false, 0.1f);
        return Ui.font(14, Font.BOLD, false, 0);
    }

    private static String stars(int n) {
        return "★★★".substring(0, n) + "☆☆☆".substring(0, 3 - n);
    }

    private void drawTitle(Graphics2D g, int w, int h, List<Widget> out) {
        // 왼쪽을 어둡게 덮어 글자가 잘 보이게 (웹과 같은 그라데이션)
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, w, (float) (w * Math.tan(Math.toRadians(10))), new float[]{0f, 0.34f, 0.68f},
                new Color[]{new Color(7, 8, 24, 240), new Color(7, 8, 24, 184), new Color(7, 8, 24, 0)}));
        g.fillRect(0, 0, w, h);

        double padX = Ui.clamp(w * 0.05, 16, 72), padB = Ui.clamp(h * 0.06, 20, 64);
        double h1 = Ui.clamp(w * 0.13, 64, 168), ledeSize = Ui.clamp(w * 0.015, 15, 18);
        Font ledeFont = Ui.font((float) ledeSize, Font.PLAIN, false, 0);
        List<String> lede = Ui.wrap(g, "엔진은 1.2초치뿐입니다. 행성의 중력을 빌려 소포를 휘돌려 보내세요. "
                + "5개 구역은 전부 자동 풀이기가 검증한 항로가 있고, 그 뒤로는 무한히 새 구역이 만들어집니다.", ledeFont, ledeSize * 26);

        // 아래에서부터 쌓아 올린다
        double keysY = h - padB;
        double menuH = 46, menuY = keysY - 12 - 22 - menuH;
        double ledeLine = ledeSize * 1.65, ledeBottom = menuY - 26, ledeTop = ledeBottom - lede.size() * ledeLine;
        double h1Bottom = ledeTop - 20, line2 = h1Bottom - h1 * 0.2, line1 = line2 - h1 * 0.92;
        double stampY = line1 - h1 * 0.78 - 18 - 26;

        // 도장
        Font stampFont = Ui.font(12, Font.PLAIN, true, 0.22f);
        String stamp = "취급주의 · 블랙홀 근처 배송";
        double stampW = Ui.width(g, stamp, stampFont) + 20;
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-2), padX, stampY + 13);
        g.setColor(Ui.AMBER);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
        g.draw(new Rectangle2D.Double(padX, stampY, stampW, 26));
        Ui.text(g, stamp, padX + 10, stampY + 17, stampFont, Ui.AMBER);
        g.setTransform(old);

        // 제목: 뒤에 주황빛 번짐을 깐다
        Font titleFont = Ui.font((float) h1, Font.BOLD, false, -0.01f);
        drawTitleGlow(g, padX, line1, line2, titleFont, h1);
        Ui.text(g, "별똥", padX, line1, titleFont, Ui.AMBER);
        Ui.text(g, "택배", padX, line2, titleFont, Ui.INK);
        for (int i = 0; i < lede.size(); i++) Ui.text(g, lede.get(i), padX, ledeTop + ledeLine * (i + 0.72), ledeFont, Ui.SOFT_INK);

        // 메뉴 버튼
        String[] labels = {"배달 시작", "구역 목록", "무한 배달"};
        Runnable[] actions = {() -> {
            int first = 0;
            while (first < progress.length && progress[first] > 0) first++;
            startCampaign(first < progress.length ? first : 0);
        }, this::showLevels, this::startEndless};
        double bx = padX;
        for (int i = 0; i < 3; i++) {
            Font bf = Ui.font(16, i == 0 ? Font.BOLD : Font.PLAIN, false, 0);
            double bw = Ui.width(g, labels[i], bf) + 44;
            out.add(new Button(new Rectangle2D.Double(bx, menuY, bw, menuH), labels[i], null, 16, i == 0, false, actions[i]));
            bx += bw + 10;
        }

        // 조작 안내 (키 모양 상자)
        String[][] keys = {{"마우스", "조준"}, {"클릭", "발사"}, {"Space", "추진"}, {"←→↑↓", "미세 조정 (Shift로 더 세밀하게)"}};
        Font kf = Ui.font(12, Font.PLAIN, false, 0), km = Ui.font(12, Font.PLAIN, true, 0);
        double kx = padX;
        for (String[] k : keys) {
            double kw = Ui.width(g, k[0], km) + 12;
            g.setColor(Ui.LINE);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Rectangle2D.Double(kx, keysY - 13, kw, 18));
            Ui.text(g, k[0], kx + 6, keysY, km, Ui.INK);
            kx += kw + 3;
            Ui.text(g, k[1], kx, keysY, kf, Ui.DIM);
            kx += Ui.width(g, k[1], kf) + 6;
        }
    }

    /** 제목 글자의 빛 번짐: 작게 그렸다가 크게 늘리면 흐려지는 원리 */
    private void drawTitleGlow(Graphics2D g, double x, double line1, double line2, Font f, double size) {
        int pad = (int) (size * 0.6), down = 12;
        double textW = Math.max(Ui.width(g, "별똥", f), Ui.width(g, "택배", f));
        int iw = (int) ((textW + pad * 2) / down) + 2, ih = (int) ((line2 - line1 + size + pad * 2) / down) + 2;
        if (titleGlow == null || titleGlowSize != size) {
            titleGlow = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = titleGlow.createGraphics();
            tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            tg.scale(1.0 / down, 1.0 / down);
            tg.setFont(f);
            tg.setColor(new Color(255, 170, 60, 90));
            tg.drawString("별똥", pad, (float) (pad + size * 0.8));
            tg.drawString("택배", pad, (float) (pad + size * 0.8 + (line2 - line1)));
            tg.dispose();
            titleGlowSize = size;
        }
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(titleGlow, (int) (x - pad), (int) (line1 - size * 0.8 - pad), iw * down, ih * down, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    /** 구역 목록 화면 */
    private void drawLevels(Graphics2D g, int w, int h, List<Widget> out) {
        g.setColor(new Color(7, 8, 24, 237));
        g.fillRect(0, 0, w, h);
        double padX = Ui.clamp(w * 0.05, 16, 72), padY = Ui.clamp(h * 0.05, 20, 56);
        Font hf = Ui.font((float) Ui.clamp(w * 0.05, 32, 52), Font.BOLD, false, 0);
        double headY = padY + hf.getSize2D() * 0.85;
        Ui.text(g, "배달 구역", padX, headY, hf, Ui.INK);
        int collected = 0;
        for (int p : progress) collected += Math.max(0, p - 1);
        Font tf = Ui.font(14, Font.PLAIN, true, 0);
        String total = "모은 별 " + collected + " / " + progress.length * 3;
        Ui.text(g, total, w - padX - Ui.width(g, total, tf), headY, tf, Ui.AMBER);

        double gridY = headY + 22 + 10, avail = w - padX * 2;
        int cols = Math.max(1, (int) ((avail + 10) / (168 + 10)));
        double tileW = (avail - (cols - 1) * 10) / cols;
        for (int i = 0; i < progress.length; i++) {
            final int index = i;
            boolean locked = i > 0 && progress[i - 1] == 0 && progress[i] == 0;
            String status = locked ? "잠김" : progress[i] > 0 ? stars(progress[i] - 1) : "미배달";
            double tx = padX + (i % cols) * (tileW + 10), ty = gridY + (i / cols) * (108 + 10);
            out.add(new LevelTile(new Rectangle2D.Double(tx, ty, tileW, 108), String.format("%02d", i + 1),
                    LevelGenerator.CAMPAIGN[i].name, status, progress[i] > 0, locked, () -> startCampaign(index)));
        }
        int rows = (progress.length + cols - 1) / cols;
        double by = gridY + rows * 118 - 10 + 22, bx = padX;
        String[] labels = {"처음 화면", "무한 배달"};
        Runnable[] actions = {this::showTitle, this::startEndless};
        for (int i = 0; i < 2; i++) {
            double bw = Ui.width(g, labels[i], Ui.font(16, Font.PLAIN, false, 0)) + 44;
            out.add(new Button(new Rectangle2D.Double(bx, by, bw, 46), labels[i], null, 16, false, false, actions[i]));
            bx += bw + 8;
        }
    }

    /** 배달 완료 영수증 */
    private void drawReceipt(Graphics2D g, int w, int h, List<Widget> out) {
        g.setColor(new Color(7, 8, 24, 140));
        g.fillRect(0, 0, w, h);
        Font noteFont = Ui.font(14, Font.PLAIN, false, 0);
        double cw = Math.min(440, w - 32), innerW = cw - 52;
        int n = starCount();
        String note = n == 3 ? "별 세 개를 모두 지났습니다." : "별 " + (3 - n) + "개를 놓쳤습니다. 별은 세 개를 모두 지나는 궤도 위에 놓여 있습니다.";
        List<String> noteLines = Ui.wrap(g, note, noteFont, innerW);
        double ch = 28 + 16 + 18 + 54 + 6 + noteLines.size() * 22.4 + 18 + 14 + 14 * 2 + 8 + 14 + 22 + 40 + 24;
        double cx = (w - cw) / 2, cy = (h - ch) / 2;
        g.setColor(Ui.PANEL);
        g.fill(new Rectangle2D.Double(cx, cy, cw, ch));
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(cx + 0.5, cy + 0.5, cw - 1, ch - 1));

        // 도장
        Font stampFont = Ui.font(22, Font.BOLD, false, 0.06f);
        double sw = Ui.width(g, "배달 완료", stampFont) + 24, sx = cx + cw - 18 - sw, sy = cy + 16;
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(7), sx + sw / 2, sy + 18);
        g.setColor(Ui.MINT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Rectangle2D.Double(sx, sy, sw, 36));
        Ui.text(g, "배달 완료", sx + 12, sy + 26, stampFont, Ui.MINT);
        g.setTransform(old);

        double x = cx + 26, y = cy + 28 + 12;
        String title = endless ? "무한 배달 No. " + endlessSeed : String.format("운송장 No. %02d · %s", levelIndex + 1, level.getName());
        Ui.text(g, title, x, y, Ui.font(12, Font.PLAIN, true, 0.08f), Ui.DIM);
        y += 18 + 46;
        Ui.text(g, stars(n), x, y, Ui.font(54, Font.PLAIN, false, 0.08f), Ui.AMBER);
        y += 20;
        for (String line : noteLines) {
            y += 22.4;
            Ui.text(g, line, x, y - 6, noteFont, Ui.SOFT_INK);
        }
        y += 18;
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Line2D.Double(x, y, x + innerW, y));
        Font dt = Ui.font(14, Font.PLAIN, false, 0), dd = Ui.font(14, Font.PLAIN, true, 0);
        String time = String.format("%.2f초", ship.getTime()), count = tries + "회";
        y += 14 + 14;
        Ui.text(g, "비행 시간", x, y, dt, Ui.DIM);
        Ui.text(g, time, x + innerW - Ui.width(g, time, dd), y, dd, Ui.INK);
        y += 8 + 14 + 4;
        Ui.text(g, "발사 횟수", x, y, dt, Ui.DIM);
        Ui.text(g, count, x + innerW - Ui.width(g, count, dd), y, dd, Ui.INK);
        y += 14;
        g.draw(new Line2D.Double(x, y, x + innerW, y));
        y += 22;

        boolean last = !endless && levelIndex == LevelGenerator.CAMPAIGN.length - 1;
        String[] labels = {last ? "무한 배달 시작" : endless ? "다음 배달" : "다음 구역", "다시 배달", "구역 목록"};
        Runnable[] actions = {this::nextLevel, this::retry, this::showLevels};
        double bx = x;
        for (int i = 0; i < 3; i++) {
            double bw = Ui.width(g, labels[i], Ui.font(15, i == 0 ? Font.BOLD : Font.PLAIN, false, 0)) + 32;
            out.add(new Button(new Rectangle2D.Double(bx, y, bw, 40), labels[i], null, 15, i == 0, false, actions[i]));
            bx += bw + 8;
        }
    }

    /** "항로 계산 중" 상자 */
    private void drawBusy(Graphics2D g, int w, int h) {
        Font f = Ui.font(13, Font.PLAIN, true, 0.2f);
        String s = "항로 계산 중";
        double bw = Ui.width(g, s, f) + 40, bh = 42, bx = (w - bw) / 2, by = (h - bh) / 2;
        g.setColor(new Color(7, 8, 24, 230));
        g.fill(new Rectangle2D.Double(bx, by, bw, bh));
        g.setColor(Ui.AMBER);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(bx + 0.5, by + 0.5, bw - 1, bh - 1));
        Ui.text(g, s, bx + 20, by + 26, f, Ui.AMBER);
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("별똥 택배");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new StarDrop());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);   // 웹 버전처럼 화면 가득
            frame.setVisible(true);
        });
    }
}
