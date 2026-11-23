import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

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
