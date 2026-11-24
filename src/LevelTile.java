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
        // CSS: 안쪽 여백 14, 번호(12px) → 6px 간격 → 이름(17px 굵게), 상태(14px)는 아래에 붙인다
        Font nf = Ui.font(12, Font.PLAIN, true, 0), tf = Ui.font(17, Font.BOLD, false, 0), sf = Ui.font(14, Font.PLAIN, false, 0.08f);
        double x = rect.getX() + 15, top = rect.getY() + 15, nh = Ui.lineHeight(g, nf), th = Ui.lineHeight(g, tf), sh = Ui.lineHeight(g, sf);
        Ui.text(g, number, x, Ui.baseline(g, nf, top, nh), nf, Ui.DIM);
        Ui.text(g, name, x, Ui.baseline(g, tf, top + nh + 6, th), tf, Ui.INK);
        Ui.text(g, status, x, Ui.baseline(g, sf, rect.getMaxY() - 15 - sh, sh), sf, done ? Ui.AMBER : Ui.DIM);
        g.setComposite(old);
    }
}
