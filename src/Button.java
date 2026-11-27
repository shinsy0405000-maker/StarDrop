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
        // glow(0~1): 마우스를 올리면 부드럽게 1 이 된다 → 색이 바뀌고, 1.5px 떠오르고, 둘레에 빛이 번진다
        double t = glow;
        Color accent = pressed ? Ui.MINT : Ui.AMBER;
        Color bg = primary ? Ui.mix(Ui.AMBER, Ui.AMBER_LIGHT, t) : Ui.mix(background, new Color(28, 26, 52, background.getAlpha()), t);
        Color border = primary ? Ui.AMBER : pressed ? Ui.MINT : Ui.mix(Ui.LINE, Ui.AMBER, t);
        Color ink = primary ? Ui.DARK_INK : pressed ? Ui.MINT : Ui.mix(Ui.INK, Ui.AMBER, t);
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(0, -1.5 * t);
        if (t > 0.01 || primary) {                         // 둘레의 빛 (노란 버튼은 늘 은은하게)
            double strength = primary ? 0.35 + 0.65 * t : t;
            for (int i = 1; i <= 4; i++) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (strength * 46 / i)));
                g.setStroke(new BasicStroke(i * 2f));
                g.draw(new Rectangle2D.Double(rect.getX() - i * 0.5, rect.getY() - i * 0.5, rect.getWidth() + i, rect.getHeight() + i));
            }
        }
        g.setColor(bg);
        g.fill(rect);
        if (primary) {                                     // 노란 버튼: 몇 초마다 빛이 비스듬히 훑고 지나간다
            double cycle = 3.4, run = 0.9, p = (anim + rect.getX() * 0.003) % cycle;
            if (p < run) {
                double bandW = rect.getHeight() * 1.2, bx = rect.getX() - bandW + (rect.getWidth() + bandW * 2) * (p / run);
                java.awt.Shape clip = g.getClip();
                g.clip(rect);
                g.setPaint(new java.awt.GradientPaint((float) (bx - bandW / 2), 0, new Color(255, 255, 255, 0), (float) bx, 0, new Color(255, 255, 255, 110), true));
                g.fill(new Rectangle2D.Double(bx - bandW / 2, rect.getY(), bandW, rect.getHeight()));
                g.setClip(clip);
            }
        }
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(rect.getX() + 0.5, rect.getY() + 0.5, rect.getWidth() - 1, rect.getHeight() - 1));
        Font f = Ui.font(size, weight, false, 0), kf = Ui.font(11, Font.PLAIN, true, 0);
        double labelW = Ui.width(g, label, f), keyW = key == null ? 0 : Ui.width(g, key, kf) + 5;
        double x = rect.getCenterX() - (labelW + keyW) / 2, y = Ui.middle(g, f, rect);
        Ui.text(g, label, x, y, f, ink);
        if (key != null) Ui.text(g, key, x + labelW + 5, y, kf, Ui.DIM);
        g.setTransform(old);
    }
}
