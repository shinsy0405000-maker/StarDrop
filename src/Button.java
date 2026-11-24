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
    private final int weight;          // 글자 굵기: Font.PLAIN, Ui.MEDIUM, Font.BOLD
    private final Color background;

    /** 화면 위 도구 버튼 (보통 굵기, 반투명 배경) */
    Button(Rectangle2D rect, String label, String key, float size, boolean primary, boolean pressed, Runnable action) {
        this(rect, label, key, size, primary ? Font.BOLD : Font.PLAIN, primary, pressed, Ui.PANEL_SEE, action);
    }

    Button(Rectangle2D rect, String label, String key, float size, int weight, boolean primary, boolean pressed, Color background, Runnable action) {
        super(rect, action);
        this.label = label;
        this.key = key;
        this.size = size;
        this.weight = primary ? Font.BOLD : weight;
        this.primary = primary;
        this.pressed = pressed;
        this.background = background;
    }

    /** 이 글자로 버튼을 만들 때 필요한 폭 (좌우 여백과 테두리 포함) */
    static double widthFor(Graphics2D g, String label, String key, float size, int weight, double padX) {
        double w = Ui.width(g, label, Ui.font(size, weight, false, 0)) + padX * 2 + 2;
        if (key != null) w += 5 + Ui.width(g, key, Ui.font(11, Font.PLAIN, true, 0));
        return w;
    }

    @Override
    public void draw(Graphics2D g, double anim, double flightTime) {
        Color bg = primary ? (hover ? Ui.AMBER_LIGHT : Ui.AMBER) : background;
        Color border = primary ? Ui.AMBER : pressed ? Ui.MINT : hover ? Ui.AMBER : Ui.LINE;
        Color ink = primary ? Ui.DARK_INK : pressed ? Ui.MINT : hover ? Ui.AMBER : Ui.INK;
        g.setColor(bg);
        g.fill(rect);
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(rect.getX() + 0.5, rect.getY() + 0.5, rect.getWidth() - 1, rect.getHeight() - 1));
        Font f = Ui.font(size, weight, false, 0), kf = Ui.font(11, Font.PLAIN, true, 0);
        double labelW = Ui.width(g, label, f), keyW = key == null ? 0 : Ui.width(g, key, kf) + 5;
        double x = rect.getCenterX() - (labelW + keyW) / 2, y = Ui.middle(g, f, rect);
        Ui.text(g, label, x, y, f, ink);
        if (key != null) Ui.text(g, key, x + labelW + 5, y, kf, Ui.DIM);
    }
}
