import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * 배경 한 장 그리기: 색 그라데이션 → 성운과 먼 별(웹과 같은 배치) → 은하수 띠·먼지·큰 별 → 휘어진 시공간 격자 → 비네트.
 *
 * 구역이나 창 크기가 바뀔 때만 한 번 그려서(WorldPainter.makeBackground) 매 프레임에는 붙이기만 한다.
 * 웹처럼 게임 세계 바깥 여백까지 채우므로 창 비율이 달라도 검은 띠가 생기지 않는다.
 */
final class BackgroundPainter {
    private BackgroundPainter() { }

    static void paint(Graphics2D g, int w, int h, Level level, double viewX, double viewY, double viewScale) {
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, w * 0.3f, h, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(6, 7, 22), new Color(10, 9, 32), new Color(19, 10, 36)}));
        g.fillRect(0, 0, w, h);
        if (level == null) return;
        java.awt.geom.AffineTransform screenTransform = g.getTransform();
        g.translate(viewX, viewY);
        g.scale(viewScale, viewScale);
        double vx0 = -viewX / viewScale, vy0 = -viewY / viewScale, vw = w / viewScale, vh = h / viewScale;

        // 성운과 별: 웹과 같은 난수 순서로 그리고, 빛을 더하는 방식(lighter)으로 겹친다
        Mulberry32 r = new Mulberry32((int) (level.getSeed() * 17 + 3));
        double baseHue = level.getLookHue();
        java.awt.Composite normal = g.getComposite();
        g.setComposite(AdditiveComposite.INSTANCE);
        for (int i = 0; i < 9; i++) {           // 성운
            double x = vx0 + r.next() * vw, y = vy0 + r.next() * vh, rad = 180 + r.next() * 420, hue = baseHue + (r.next() - 0.5) * 90;
            g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) rad, new float[]{0f, 0.5f, 1f},
                    new Color[]{hsla(hue, 75, 45, 0.07 + r.next() * 0.09), hsla(hue + 25, 70, 35, 0.04), new Color(0, 0, 0, 0)}));
            g.fill(new Rectangle2D.Double(x - rad, y - rad, rad * 2, rad * 2));
        }
        for (int i = 0; i < 520; i++) {         // 별
            double x = vx0 + r.next() * vw, y = vy0 + r.next() * vh, size = 0.3 + r.next() * r.next() * 1.5;
            double hue = r.next() < 0.2 ? 30 + r.next() * 30 : 210 + r.next() * 40;
            g.setColor(hsla(hue, r.next() * 60, 75 + r.next() * 25, 0.15 + r.next() * 0.75));
            Paints.circle(g, x, y, size);
        }
        paintDeepSpace(g, level, vx0, vy0, vw, vh, baseHue);
        g.setComposite(normal);
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
        // 비네트: 화면 가장자리로 갈수록 어둡게 (가운데 넓은 부분은 그대로). 배경에 미리 넣어 매 프레임 비용이 없다
        g.setTransform(screenTransform);
        g.translate(w / 2.0, h / 2.0);
        g.scale(w / 2.0, h / 2.0);
        g.setPaint(new RadialGradientPaint(0f, 0f, 1.5f, new float[]{0f, 0.45f, 0.75f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(2, 2, 10, 90), new Color(2, 2, 10, 170)}));
        g.fill(new Rectangle2D.Double(-1, -1, 2, 2));
        g.setTransform(screenTransform);
    }

    /**
     * 웹 버전에는 없는, 더 깊은 우주 느낌을 내는 층 (배경 그림에 한 번만 그려지므로 매 프레임 비용은 없다).
     *   은하수 띠: 화면을 비스듬히 가로지르는 옅은 빛의 띠와 그 안의 작은 별 무리
     *   성운 덩어리: 길쭉하게 늘인 밝은 구름 몇 개
     *   먼지 띠: 빛을 가리는 어두운 구름 (밝은 곳과 대비가 생겨 깊어 보인다)
     *   큰 별: 십자 모양 빛살이 있는 밝은 별 몇 개
     * 웹과 같은 배치를 지키려고 원래 난수와 따로 굴리는 난수(Random)를 쓴다.
     */
    private static void paintDeepSpace(Graphics2D g, Level level, double vx0, double vy0, double vw, double vh, double baseHue) {
        java.util.Random r = new java.util.Random(level.getSeed() * 31 + 5);
        double cx = vx0 + vw * (0.3 + r.nextDouble() * 0.4), cy = vy0 + vh * (0.3 + r.nextDouble() * 0.4);
        double ang = Math.toRadians(-35 + r.nextDouble() * 70), ca = Math.cos(ang), sa = Math.sin(ang);
        double span = Math.hypot(vw, vh);

        // 은하수 띠: 띠를 따라 늘어선 빛 덩어리 여러 개
        for (int i = 0; i < 26; i++) {
            double t = (i / 25.0 - 0.5) * span * 1.1, off = (r.nextGaussian()) * vh * 0.06;
            double x = cx + ca * t - sa * off, y = cy + sa * t + ca * off;
            double rad = vh * (0.16 + r.nextDouble() * 0.16), hue = baseHue + 200 + (r.nextDouble() - 0.5) * 60;
            ellipseGlow(g, x, y, rad * 1.8, rad * 0.75, ang, hsla(hue, 40, 55, 0.035 + r.nextDouble() * 0.035));
        }
        for (int i = 0; i < 420; i++) {         // 띠 안의 작은 별 무리
            double t = (r.nextDouble() - 0.5) * span * 1.1, off = r.nextGaussian() * vh * 0.07;
            double x = cx + ca * t - sa * off, y = cy + sa * t + ca * off;
            g.setColor(hsla(215 + r.nextDouble() * 30, 30, 85, 0.12 + r.nextDouble() * 0.45));
            Paints.circle(g, x, y, 0.25 + r.nextDouble() * 0.55);
        }
        // 성운 덩어리: 길쭉한 밝은 구름 (안쪽은 밝고 바깥은 다른 색으로 번진다)
        for (int i = 0; i < 5; i++) {
            double x = vx0 + r.nextDouble() * vw, y = vy0 + r.nextDouble() * vh, rad = 120 + r.nextDouble() * 220;
            double hue = baseHue + (r.nextDouble() - 0.5) * 140, a = r.nextDouble() * Math.PI;
            ellipseGlow(g, x, y, rad * 1.6, rad * 0.7, a, hsla(hue, 80, 50, 0.07 + r.nextDouble() * 0.05));
            ellipseGlow(g, x, y, rad * 0.7, rad * 0.35, a, hsla(hue + 30, 85, 62, 0.08));
        }
        // 먼지 띠: 빛을 빼는 어두운 구름 (보통 섞기)
        java.awt.Composite add = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        for (int i = 0; i < 7; i++) {
            double t = (r.nextDouble() - 0.5) * span * 0.9, off = r.nextGaussian() * vh * 0.04;
            double x = cx + ca * t - sa * off, y = cy + sa * t + ca * off, rad = vh * (0.08 + r.nextDouble() * 0.1);
            ellipseGlow(g, x, y, rad * 2.2, rad * 0.5, ang + (r.nextDouble() - 0.5) * 0.4, new Color(4, 3, 14, 70 + r.nextInt(60)));
        }
        g.setComposite(add);
        // 큰 별: 빛 번짐 + 십자 빛살
        for (int i = 0; i < 7; i++) {
            double x = vx0 + r.nextDouble() * vw, y = vy0 + r.nextDouble() * vh, size = 1.2 + r.nextDouble() * 1.6;
            Color c = r.nextDouble() < 0.3 ? new Color(255, 214, 170) : new Color(190, 212, 255);
            Paints.glow(g, x, y, size * 9, new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
            double len = size * (14 + r.nextDouble() * 14);
            for (int k = 0; k < 2; k++) {
                double ax = k == 0 ? 1 : 0, ay = k == 0 ? 0 : 1;
                g.setPaint(new java.awt.LinearGradientPaint((float) (x - ax * len), (float) (y - ay * len), (float) (x + ax * len), (float) (y + ay * len),
                        new float[]{0f, 0.5f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(c.getRed(), c.getGreen(), c.getBlue(), 150), new Color(0, 0, 0, 0)}));
                g.fill(new Rectangle2D.Double(Math.min(x - ax * len, x - 0.6), Math.min(y - ay * len, y - 0.6), ax * len * 2 + 1.2 * ay, ay * len * 2 + 1.2 * ax));
            }
            g.setColor(Color.WHITE);
            Paints.circle(g, x, y, size * 0.8);
        }
    }

    /** 기울어진 타원 모양의 빛 (가운데 밝고 가장자리 투명) */
    private static void ellipseGlow(Graphics2D g, double x, double y, double rx, double ry, double angle, Color c) {
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(angle);
        g.scale(rx / ry, 1);
        g.setPaint(new RadialGradientPaint(0f, 0f, (float) ry, new float[]{0f, 1f}, new Color[]{c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Rectangle2D.Double(-ry, -ry, ry * 2, ry * 2));
        g.setTransform(old);
    }

    /** CSS 의 hsla(색상, 채도%, 밝기%, 투명도 0~1) */
    static Color hsla(double h, double s, double l, double a) {
        return Paints.hsl(h, s / 100, l / 100, (int) Math.round(a * 255));
    }

    static void warp(Level lv, double x, double y, double[] out) {
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
}
