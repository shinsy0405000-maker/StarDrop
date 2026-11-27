import java.awt.geom.Rectangle2D;

/** 클릭할 수 있는 화면 요소의 공통 부모 */
abstract class Widget implements Drawable {
    protected final Rectangle2D rect;
    protected final Runnable action;
    protected boolean hover, enabled = true;
    protected double glow;          // 마우스를 올린 정도 0~1 (부드럽게 바뀐다. 화면이 매 프레임 채워 준다)

    protected Widget(Rectangle2D rect, Runnable action) {
        this.rect = rect;
        this.action = action;
    }

    boolean contains(double x, double y) { return enabled && rect.contains(x, y); }

    void click() { if (enabled) action.run(); }

    /** 프레임이 바뀌어도 같은 버튼임을 알아보는 이름 (마우스 효과를 이어 가려고) */
    String key() { return getClass().getSimpleName() + "@" + Math.round(rect.getX()) + "," + Math.round(rect.getY()); }
}
