import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

/** 달: 행성 주위를 돈다. 위치가 시간에 따라 바뀌므로 GravityBody 를 쓰지 않고 직접 구현 */
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
