import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** 우주 화면(게임 세계)을 그리는 층: 배경, 천체, 배달지, 별, 조준선, 소포와 꼬리 */
abstract class WorldPainter extends GameState {
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------ 배경
    /**
     * 배경(색 그라데이션, 성운, 별, 휘어진 시공간 격자)을 지금 창 크기 그대로 한 장 그려 둔다.
     * 웹처럼 게임 세계 바깥 여백까지 채우므로 창 비율이 달라도 검은 띠가 생기지 않는다.
     */
    protected BufferedImage makeBackground(Graphics2D screen, int w, int h) {
        return ImageCache.screenLayer(screen, w, h, false, g -> paintBackground(g, w, h));
    }

    private void paintBackground(Graphics2D g, int w, int h) {
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, w * 0.3f, h, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(6, 7, 22), new Color(10, 9, 32), new Color(19, 10, 36)}));
        g.fillRect(0, 0, w, h);
        if (level == null) return;
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
    }

    /** CSS 의 hsla(색상, 채도%, 밝기%, 투명도 0~1) */
    protected static Color hsla(double h, double s, double l, double a) {
        return Paints.hsl(h, s / 100, l / 100, (int) Math.round(a * 255));
    }

    protected static void warp(Level lv, double x, double y, double[] out) {
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

    protected void drawWorld(Graphics2D g) {
        for (double[] t : level.getTwinkles()) {       // 반짝이는 별 {x, y, 크기, 위상, 속도}
            g.setColor(new Color(223, 230, 255, (int) Math.round(255 * (0.25 + 0.75 * (0.5 + 0.5 * Math.sin(anim * t[4] + t[3]))))));
            g.fill(new Rectangle2D.Double(t[0], t[1], t[2], t[2]));
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

    protected void drawTarget(Graphics2D g) {
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

    protected void drawStartPad(Graphics2D g) {
        double x = level.getStartX(), y = level.getStartY();
        g.setColor(new Color(255, 198, 92, 140));
        g.setStroke(new BasicStroke(1.5f));
        Paints.ring(g, x, y, 20);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + anim * 0.2;
            g.draw(new Line2D.Double(x + Math.cos(a) * 24, y + Math.sin(a) * 24, x + Math.cos(a) * 28, y + Math.sin(a) * 28));
        }
    }

    protected void drawStars(Graphics2D g) {
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

    protected Shape starShape(double x, double y, double r, double rot, int points, double inner) {
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
    protected void drawGhost(Graphics2D g) {
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

    protected void drawAim(Graphics2D g) {
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

    protected void drawShip(Graphics2D g) {
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

    protected void drawTail(Graphics2D g, int length, double width, int[] rgb, double alpha) {
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
}
