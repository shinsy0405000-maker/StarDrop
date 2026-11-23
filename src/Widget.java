import java.awt.geom.Rectangle2D;

/** 클릭할 수 있는 화면 요소의 공통 부모 */
abstract class Widget implements Drawable {
    protected final Rectangle2D rect;
    protected final Runnable action;
    protected boolean hover, enabled = true;

    protected Widget(Rectangle2D rect, Runnable action) {
        this.rect = rect;
        this.action = action;
    }

    boolean contains(double x, double y) { return enabled && rect.contains(x, y); }

    void click() { if (enabled) action.run(); }
}
