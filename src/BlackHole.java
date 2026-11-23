import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/** 블랙홀: 작지만 아주 무겁다. 주황색 원반 안쪽으로 들어가면 삼켜진다 */
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
