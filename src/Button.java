import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** 글자 버튼. primary 는 노란 버튼, pressed 는 켜진 상태(민트색) */
class Button extends Widget {
    private final String label, key;
    private final boolean primary, pressed;
    private final float size;

    Button(Rectangle2D rect, String label, String key, float size, boolean primary, boolean pressed, Runnable action) {
        super(rect, action);
        this.label = label;
        this.key = key;
        this.size = size;
        this.primary = primary;
        this.pressed = pressed;
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        Color bg = primary ? (hover ? Ui.AMBER_LIGHT : Ui.AMBER) : Ui.PANEL_SEE;
        Color border = primary ? Ui.AMBER : pressed ? Ui.MINT : hover ? Ui.AMBER : Ui.LINE;
        Color ink = primary ? Ui.DARK_INK : pressed ? Ui.MINT : hover ? Ui.AMBER : Ui.INK;
        g.setColor(bg);
        g.fill(rect);
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(rect.getX() + 0.5, rect.getY() + 0.5, rect.getWidth() - 1, rect.getHeight() - 1));
        Font f = Ui.font(size, primary ? Font.BOLD : Font.PLAIN, false, 0);
        double labelW = Ui.width(g, label, f), keyW = key == null ? 0 : Ui.width(g, key, Ui.font(11, Font.PLAIN, true, 0)) + 5;
        double x = rect.getCenterX() - (labelW + keyW) / 2, y = Ui.middle(g, f, rect);
        Ui.text(g, label, x, y, f, ink);
        if (key != null) Ui.text(g, key, x + labelW + 5, y, Ui.font(11, Font.PLAIN, true, 0), Ui.DIM);
    }
}
