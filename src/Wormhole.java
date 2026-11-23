import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** 웜홀: 한쪽 입구로 들어가면 속도를 그대로 지닌 채 반대편에서 나온다 */
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
