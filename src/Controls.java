import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 입력을 처리하는 층: 마우스(조준, 발사, 추진, 버튼 클릭)와 키보드(방향키 미세 조정, 단축키).
 *
 * 단축키: Space/Enter 발사·추진, R 다시 조준, H 정답 방향, M 소리, F1 버그 제보, F3 FPS, F4 빛 번짐, Esc 처음 화면
 */
abstract class Controls extends Flight {
    private static final long serialVersionUID = 1L;

    /** 마우스와 키보드 입력을 연결한다 (생성자에서 한 번 부른다) */
    protected void installInput() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (state == AIM && !demo && !overlayOpen()) aimAt(e);
                boolean onButton = false;
                for (Widget w : widgets) if (w.contains(mouseX, mouseY)) onButton = true;
                setCursor(java.awt.Cursor.getPredefinedCursor(onButton ? java.awt.Cursor.HAND_CURSOR
                        : overlayOpen() || demo ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.CROSSHAIR_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (state == AIM && mouseDown && !demo && !overlayOpen()) aimAt(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Sound.start();
                for (int i = widgets.size() - 1; i >= 0; i--) {     // 버튼을 먼저 확인한다
                    Widget w = widgets.get(i);
                    if (w.contains(e.getX(), e.getY())) {
                        Sound.click();
                        w.click();
                        return;
                    }
                }
                if (overlayOpen() || demo || level == null) return;
                mouseDown = true;
                if (state == AIM) {
                    armed = true;
                    aimAt(e);
                } else if (state == FLY) {
                    thrusting = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (state == AIM && mouseDown && armed && !demo) launch(aimAngle, aimPower);
                mouseDown = false;
                armed = false;
                thrusting = false;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                leftHeld = rightHeld = upHeld = downHeld = shiftHeld = spaceDown = false;
                thrusting = false;
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Sound.start();
                int key = e.getKeyCode();
                if (key == KeyEvent.VK_SHIFT) shiftHeld = true;
                boolean firstArrow = setArrow(key, true);     // 키 반복으로 다시 들어온 것은 false
                if (key == KeyEvent.VK_F3) showFps = !showFps;
                if (key == KeyEvent.VK_F4) bloomOn = !bloomOn;
                if (key == KeyEvent.VK_F1) openBugReport();
                if (key == KeyEvent.VK_M) Sound.setMuted(!Sound.isMuted());
                if (key == KeyEvent.VK_ESCAPE && !titleOpen) showTitle();
                if (titleOpen || levelsOpen || demo || level == null) return;
                boolean firstPress = key != KeyEvent.VK_SPACE || !spaceDown;
                if (key == KeyEvent.VK_SPACE) spaceDown = true;
                if (state == WIN) {
                    if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) && firstPress) nextLevel();
                    if (key == KeyEvent.VK_R) retry();
                    return;
                }
                if (key == KeyEvent.VK_H) showHint = !showHint;
                if (key == KeyEvent.VK_R) retry();
                if (state == AIM) {
                    if (firstArrow) nudge(key);
                    if ((key == KeyEvent.VK_SPACE && firstPress) || key == KeyEvent.VK_ENTER) launch(aimAngle, aimPower);
                } else if (state == FLY && key == KeyEvent.VK_SPACE) {
                    thrusting = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) shiftHeld = false;
                setArrow(e.getKeyCode(), false);
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    spaceDown = false;
                    thrusting = false;
                }
            }
        });
    }

    /** 출발점에서 마우스까지의 방향이 각도, 거리가 세기 */
    private void aimAt(MouseEvent e) {
        if (level == null) return;
        double wx = (e.getX() - viewX) / viewScale, wy = (e.getY() - viewY) / viewScale;
        double dx = wx - level.getStartX(), dy = wy - level.getStartY(), dist = JsMath.hypot(dx, dy);
        if (dist < 10) return;
        aimAngle = JsMath.atan2(dy, dx);
        aimPower = Math.max(0, Math.min(1, (dist - 50) / 330));
        previewDirty = true;
    }

    /**
     * 누르고 있는 방향키마다 조준을 계속 움직인다.
     * 운영체제의 키 반복은 마지막에 누른 키 하나만 반복하므로, 키를 누른 상태를 직접 기억해서 처리한다.
     * 처음 0.25초는 가만히 있다가(한 번 톡 누르면 한 칸만 움직이게), 그 뒤로는 오래 누를수록 빨라진다.
     */
    @Override
    protected void aimWithKeys(double dt) {
        boolean any = leftHeld || rightHeld || upHeld || downHeld;
        if (!any || state != AIM || demo || overlayOpen()) {
            holdTime = 0;
            return;
        }
        holdTime += dt;
        if (holdTime < 0.25) return;
        double boost = Math.min(3, 1 + (holdTime - 0.25) * 2);           // 1배에서 시작해 1초 뒤 3배
        double turn = (shiftHeld ? 0.015 : 0.12) * boost * dt;            // 초당 각도 변화(라디안)
        double push = (shiftHeld ? 0.03 : 0.18) * boost * dt;             // 초당 세기 변화
        if (leftHeld) aimAngle -= turn;
        if (rightHeld) aimAngle += turn;
        if (upHeld) aimPower = Math.min(1, aimPower + push);
        if (downHeld) aimPower = Math.max(0, aimPower - push);
        previewDirty = true;
    }

    /** 방향키를 처음 눌렀을 때 한 칸만 움직인다 (톡톡 눌러서 미세 조정) */
    private void nudge(int key) {
        double fine = shiftHeld ? 0.0005 : 0.004, finePower = shiftHeld ? 0.001 : 0.006;
        if (key == KeyEvent.VK_LEFT) aimAngle -= fine;
        if (key == KeyEvent.VK_RIGHT) aimAngle += fine;
        if (key == KeyEvent.VK_UP) aimPower = Math.min(1, aimPower + finePower);
        if (key == KeyEvent.VK_DOWN) aimPower = Math.max(0, aimPower - finePower);
        previewDirty = true;
    }

    /** 방향키를 누르거나 뗐을 때 상태를 기록한다. 처음 누른 것이면 true */
    private boolean setArrow(int key, boolean down) {
        boolean before;
        switch (key) {
            case KeyEvent.VK_LEFT: before = leftHeld; leftHeld = down; break;
            case KeyEvent.VK_RIGHT: before = rightHeld; rightHeld = down; break;
            case KeyEvent.VK_UP: before = upHeld; upHeld = down; break;
            case KeyEvent.VK_DOWN: before = downHeld; downHeld = down; break;
            default: return false;
        }
        return down && !before;
    }
}
