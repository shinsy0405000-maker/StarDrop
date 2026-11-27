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
        double t = enabled ? glow : 0;                    // 마우스를 올린 정도 → 떠오르고 노랗게 빛난다
        java.awt.geom.AffineTransform before = g.getTransform();
        g.translate(0, -2 * t);
        for (int i = 1; i <= 4 && t > 0.01; i++) {
            g.setColor(new java.awt.Color(255, 198, 92, (int) (t * 40 / i)));
            g.setStroke(new BasicStroke(i * 2.5f));
            g.draw(new Rectangle2D.Double(rect.getX() - i * 0.5, rect.getY() - i * 0.5, rect.getWidth() + i, rect.getHeight() + i));
        }
        g.setColor(Ui.mix(Ui.PANEL, new java.awt.Color(26, 24, 50), t));
        g.fill(rect);
        boolean solid = t > 0.5;
        g.setColor(Ui.mix(Ui.LINE, Ui.AMBER, t));
        g.setStroke(solid ? new BasicStroke(1f) : Ui.DASHED);
        g.draw(new Rectangle2D.Double(rect.getX() + 0.5, rect.getY() + 0.5, rect.getWidth() - 1, rect.getHeight() - 1));
        // CSS: 안쪽 여백 14, 번호(12px) → 6px 간격 → 이름(17px 굵게), 상태(14px)는 아래에 붙인다
        Font nf = Ui.font(12, Font.PLAIN, true, 0), tf = Ui.font(17, Font.BOLD, false, 0), sf = Ui.font(14, Font.PLAIN, false, 0.08f);
        double x = rect.getX() + 15, top = rect.getY() + 15, nh = Ui.lineHeight(g, nf), th = Ui.lineHeight(g, tf), sh = Ui.lineHeight(g, sf);
        Ui.text(g, number, x, Ui.baseline(g, nf, top, nh), nf, Ui.DIM);
        // 이름이 칸보다 길면(영어 등) 글자를 조금 줄여서 칸 안에 넣는다 (최소 13px)
        Font fit = tf;
        double room = rect.getWidth() - 30;
        for (float size = 16.5f; Ui.width(g, name, fit) > room && size >= 13; size -= 0.5f) fit = Ui.font(size, Font.BOLD, false, 0);
        Ui.text(g, name, x, Ui.baseline(g, tf, top + nh + 6, th), fit, Ui.INK);
        Ui.text(g, status, x, Ui.baseline(g, sf, rect.getMaxY() - 15 - sh, sh), sf, done ? Ui.AMBER : Ui.DIM);
        g.setTransform(before);
        g.setComposite(old);
    }
}
