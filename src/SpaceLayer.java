import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 움직이는 배경: 천천히 흘러가는 별 두 겹(시차)과 가끔 지나가는 유성.
 *
 * 미리 그려 둔 배경(성운, 먼 별, 격자) 위, 게임 세계 아래에 그린다.
 *   - 가운데 겹: 작은 별이 느리게 흐른다
 *   - 앞 겹:     크고 빛나는 별이 조금 빠르게 흐른다
 *   두 겹은 마우스 위치에 따라 서로 다르게 살짝 밀려서(시차) 깊이가 느껴진다.
 * 위치는 창 크기 대비 비율(0~1)로 저장해서 창 크기가 바뀌어도 그대로 쓴다.
 */
final class SpaceLayer {
    /** 별 한 개: {x, y (0~1), 크기, 밝기, 반짝임 위상, 반짝임 속도, 색상} */
    private final List<double[]> mid = new ArrayList<>(), near = new ArrayList<>();
    /** 유성: {x, y, 방향x, 방향y, 속도, 길이, 남은 시간, 전체 시간} (창 좌표) */
    private final List<double[]> meteors = new ArrayList<>();
    private final Random rnd = new Random();
    private long seed = Long.MIN_VALUE;
    private double drift, nextMeteor = 1.5, parX, parY;

    /** 구역이 바뀌면 별 배치를 새로 만든다 (같은 구역이면 같은 배치) */
    void setSeed(long s) {
        if (s == seed) return;
        seed = s;
        Random r = new Random(s * 7919 + 13);
        mid.clear();
        near.clear();
        for (int i = 0; i < 150; i++) mid.add(star(r, 0.5 + r.nextDouble() * 0.9, 0.35 + r.nextDouble() * 0.5));
        for (int i = 0; i < 42; i++) near.add(star(r, 1.1 + r.nextDouble() * 1.3, 0.55 + r.nextDouble() * 0.45));
    }

    private static double[] star(Random r, double size, double bright) {
        double hue = r.nextDouble() < 0.25 ? 30 + r.nextDouble() * 25 : 205 + r.nextDouble() * 45;
        return new double[]{r.nextDouble(), r.nextDouble(), size, bright, r.nextDouble() * Math.PI * 2, 0.6 + r.nextDouble() * 2.2, hue};
    }

    /** 시간을 dt 초만큼 흘린다. mx, my = 마우스 위치를 창 가운데 기준 -1~1 로 */
    void update(double dt, double mx, double my, int w, int h) {
        drift += dt;
        parX += (mx - parX) * Math.min(1, dt * 2.5);
        parY += (my - parY) * Math.min(1, dt * 2.5);
        for (int i = meteors.size() - 1; i >= 0; i--) {
            double[] m = meteors.get(i);
            m[0] += m[2] * m[4] * dt;
            m[1] += m[3] * m[4] * dt;
            m[6] -= dt;
            if (m[6] <= 0) meteors.remove(i);
        }
        nextMeteor -= dt;
        if (nextMeteor <= 0 && w > 0) {
            nextMeteor = 2.5 + rnd.nextDouble() * 5;
            double a = Math.toRadians(150 + rnd.nextDouble() * 50);           // 오른쪽 위에서 왼쪽 아래로
            double x = w * (0.35 + rnd.nextDouble() * 0.75), y = -20 + rnd.nextDouble() * h * 0.35;
            double life = 0.7 + rnd.nextDouble() * 0.6;
            meteors.add(new double[]{x, y, Math.cos(a), Math.sin(a), 700 + rnd.nextDouble() * 700, 120 + rnd.nextDouble() * 180, life, life});
        }
    }

    /** 창 좌표(w × h)로 그린다 */
    void draw(Graphics2D g, int w, int h) {
        drawLayer(g, mid, w, h, 5, 12, false);
        drawLayer(g, near, w, h, 13, 26, true);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] m : meteors) {
            double fade = Math.min(1, m[6] / m[7] * 3) * Math.min(1, (m[7] - m[6]) * 8);   // 나타날 때와 사라질 때 부드럽게
            double hx = m[0], hy = m[1], tx = hx - m[2] * m[5], ty = hy - m[3] * m[5];
            g.setPaint(new GradientPaint((float) hx, (float) hy, new Color(235, 242, 255, (int) (230 * fade)),
                    (float) tx, (float) ty, new Color(140, 170, 255, 0)));
            g.draw(new Line2D.Double(hx, hy, tx, ty));
            glow(g, hx, hy, 9, COOL, 0.8 * fade);
        }
    }

    /**
     * 별 한 겹. speed = 1초에 흐르는 픽셀, shift = 마우스 시차로 밀리는 최대 픽셀.
     * 화면 밖으로 나간 별은 반대쪽에서 다시 들어온다.
     */
    private void drawLayer(Graphics2D g, List<double[]> stars, int w, int h, double speed, double shift, boolean glow) {
        double ox = -drift * speed - parX * shift, oy = drift * speed * 0.35 - parY * shift;
        for (double[] s : stars) {
            double x = wrap(s[0] * w + ox, w), y = wrap(s[1] * h + oy, h);
            double tw = 0.65 + 0.35 * Math.sin(drift * s[5] + s[4]);
            int alpha = (int) (255 * s[3] * tw);
            Color c = Paints.hsl(s[6], 0.45, 0.86, alpha);
            if (glow) glow(g, x, y, s[2] * 4.5, s[6] < 100 ? WARM : COOL, s[3] * tw * 0.4);
            g.setColor(c);
            g.fill(new Rectangle2D.Double(x - s[2] / 2, y - s[2] / 2, s[2], s[2]));
        }
    }

    // 빛 번짐 그림은 색마다 한 장씩 만들어 두므로(ImageCache), 색은 두 가지로 고정하고 진하기는 투명도로 조절한다
    private static final Color COOL = new Color(170, 200, 255), WARM = new Color(255, 210, 160);

    private static void glow(Graphics2D g, double x, double y, double r, Color c, double alpha) {
        if (alpha <= 0.01) return;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, alpha)));
        Paints.glow(g, x, y, r, c);
        g.setComposite(old);
    }

    private static double wrap(double v, double size) {
        double m = (v + 20) % (size + 40);
        return (m < 0 ? m + size + 40 : m) - 20;           // 가장자리 20px 밖에서 사라지고 들어오게
    }
}
