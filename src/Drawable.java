import java.awt.Graphics2D;

/** 화면에 그릴 수 있는 모든 것. anim = 계속 흐르는 시간, flightTime = 발사 후 흐른 시간 */
interface Drawable {
    void draw(Graphics2D g, double anim, double flightTime);
}
