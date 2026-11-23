import java.awt.Graphics2D;

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
