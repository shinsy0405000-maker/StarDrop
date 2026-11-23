import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/** 밀어내는 별: 질량이 음수라서 소포를 밀어낸다. 부딪히지는 않는다 */
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
