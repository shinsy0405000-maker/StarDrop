import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/*
 * 별똥 택배 — 중력을 이용한 우주 배달 퍼즐 게임 (순수 자바, Swing)
 *
 * 실행:  javac -encoding UTF-8 -d out src/*.java
 *        java -cp out StarDrop
 *
 * 이 파일은 게임 진행(구역 불러오기, 물리 한 스텝, 성공/실패 처리)과 입력, main 을 맡는다.
 * 그리기는 ScreenPainter / WorldPainter, 상태 변수는 GameState 에 있다.
 */
public class StarDrop extends ScreenPainter {
    private static final long serialVersionUID = 1L;

    /** 구역별 안내 문구 (웹 버전과 같은 내용) */
    private static final String[] TIPS = {
        "마우스를 움직여 방향과 세기를 정하고, 클릭하면 발사합니다. 점선은 궤도의 앞부분만 보여 줍니다.",
        "달은 발사하는 순간부터 공전합니다. 점선 예측에는 달의 움직임이 이미 반영되어 있습니다. 날아가는 동안 누르고 있거나 Space를 누르면 진행 방향으로 추진합니다(연료 1.2초).",
        "블랙홀은 작지만 끌어당기는 힘이 큽니다. 주황색 원반 안쪽으로 들어가면 돌아오지 못합니다.",
        "청록색 별은 소포를 밀어냅니다. 부딪히지는 않으니 방향을 꺾는 데 쓰세요.",
        "보라색 웜홀에 들어가면 속도를 그대로 지닌 채 반대편 웜홀에서 나옵니다.",
    };

    public StarDrop() {
        setPreferredSize(new Dimension(1280, 720));
        setBackground(Ui.VOID);
        setFocusable(true);
        Random r = new Random(11);
        for (double[] t : twinkles) {       // 웹처럼 화면 밖까지 넉넉히 흩어 둔다
            t[0] = -500 + r.nextDouble() * 2600;
            t[1] = -500 + r.nextDouble() * 1900;
            t[2] = r.nextDouble() * Math.PI * 2;
            t[3] = 0.6 + r.nextDouble() * 2.2;
        }
        loadProgress();
        installInput();
        showTitle();
        if (!GraphicsEnvironment.isHeadless()) startLoop();
    }

    // ------------------------------------------------------------ 게임 루프
    /**
     * 모니터 주사율(60Hz, 144Hz 등)에 맞춰 "계산 → 그리기"를 반복한다.
     * Swing 의 Timer 는 윈도우에서 간격이 들쭉날쭉해서 움직임이 끊겨 보이므로 직접 만든다.
     */
    private void startLoop() {
        Thread precise = new Thread(() -> {     // 윈도우 타이머를 1ms 단위로 정밀하게 만드는 요령
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) { /* 종료 */ }
        });
        precise.setDaemon(true);
        precise.start();

        int hz = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate();
        if (hz < 30 || hz > 240) hz = 60;
        final long frameNanos = 1_000_000_000L / hz;
        Thread loop = new Thread(() -> {
            long next = System.nanoTime();
            while (true) {
                try {
                    SwingUtilities.invokeAndWait(this::frame);
                } catch (Exception e) {
                    return;
                }
                next += frameNanos;
                long sleep = next - System.nanoTime();
                if (sleep > 0) LockSupport.parkNanos(sleep); else next = System.nanoTime();
            }
        }, "game-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private void frame() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        fpsFrames++;
        fpsTime += dt;
        if (fpsTime >= 0.5) {
            fps = (int) Math.round(fpsFrames / fpsTime);
            fpsFrames = 0;
            fpsTime = 0;
        }
        update(dt);
        if (isShowing()) paintImmediately(0, 0, getWidth(), getHeight());
        Toolkit.getDefaultToolkit().sync();
    }

    // ------------------------------------------------------------ 진행 기록 저장
    private void loadProgress() {
        try {
            String[] parts = java.util.prefs.Preferences.userRoot().node("stardrop").get("progress", "").split(",");
            for (int i = 0; i < parts.length && i < progress.length; i++) progress[i] = Integer.parseInt(parts[i].trim());
        } catch (Exception e) {
            // 기록이 없거나 읽을 수 없으면 처음부터
        }
    }

    private void saveProgress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < progress.length; i++) sb.append(i == 0 ? "" : ",").append(progress[i]);
        try {
            java.util.prefs.Preferences.userRoot().node("stardrop").put("progress", sb.toString());
        } catch (Exception e) {
            // 저장하지 못해도 게임은 계속된다
        }
    }

    // ------------------------------------------------------------ 화면 전환과 구역 불러오기
    protected void showTitle() {
        titleOpen = true;
        levelsOpen = false;
        endless = false;
        loadLevel(LevelGenerator.CAMPAIGN_SEED[1], LevelGenerator.CAMPAIGN[1], LevelGenerator.CAMPAIGN_FIRST_TRY[1], true);
    }

    protected void showLevels() {
        titleOpen = false;
        levelsOpen = true;
    }

    protected void startCampaign(int index) {
        titleOpen = false;
        levelsOpen = false;
        endless = false;
        levelIndex = index;
        loadLevel(LevelGenerator.CAMPAIGN_SEED[index], LevelGenerator.CAMPAIGN[index], LevelGenerator.CAMPAIGN_FIRST_TRY[index], false);
    }

    protected void startEndless() {
        titleOpen = false;
        levelsOpen = false;
        endless = true;
        endlessSeed = 1 + fx.nextInt(999_999);
        loadLevel(endlessSeed, LevelGenerator.endlessConfig(endlessSeed), 0, false);
    }

    /** 구역 생성은 시간이 걸릴 수 있어서 다른 스레드에서 하고, 끝나면 화면 스레드로 넘긴다 */
    private void loadLevel(long seed, LevelConfig cfg, int firstTry, boolean asDemo) {
        state = LOADING;
        thrusting = false;
        Sound.thrust(false);
        final int ticket = ++loadTicket;      // 요청 번호. 늦게 끝난 옛 요청이 새 구역을 덮어쓰지 않게 한다
        Thread worker = new Thread(() -> {
            Level made = LevelGenerator.create(seed, cfg, firstTry);
            SwingUtilities.invokeLater(() -> {
                if (ticket == loadTicket) enterLevel(made, asDemo);
            });
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void enterLevel(Level made, boolean asDemo) {
        level = made;
        demo = asDemo;
        tries = 0;
        showHint = false;
        ghost = new ArrayList<>();
        particles.clear();
        floaters.clear();
        aimAngle = Math.atan2(level.getTargetY() - level.getStartY(), level.getTargetX() - level.getStartX());
        aimPower = 0.5;
        resetShot();
        tip = !demo && !endless ? TIPS[levelIndex] : null;
        if (demo) {
            state = DEMO_WAIT;
            waitTimer = 0.6;
        } else {
            state = AIM;
        }
    }

    private void resetShot() {
        ship = null;
        trail.clear();
        fuel = Rules.FUEL;
        thrusting = false;
        Sound.thrust(false);
        for (int i = 0; i < 3; i++) gotStar[i] = false;
        previewDirty = true;
    }

    protected void retry() {
        if (demo || state == LOADING) return;
        if (state == FLY) ghost = new ArrayList<>(trail);
        state = AIM;
        waitTimer = 0;
        resetShot();
    }

    protected void nextLevel() {
        if (!endless && levelIndex < LevelGenerator.CAMPAIGN.length - 1) startCampaign(levelIndex + 1);
        else startEndless();
    }

    // ------------------------------------------------------------ 진행
    /** 조준선: 궤도의 앞부분만 미리 계산해서 점으로 보여 준다 */
    private void updatePreview() {
        preview.clear();
        previewEnd = Rules.FLYING;
        previewDirty = false;
        if (level == null) return;
        Ship s = new Ship(level, aimAngle, aimPower);
        for (int i = 0; i < level.getPreviewSteps() && previewEnd == Rules.FLYING; i++) {
            previewEnd = s.step(level, false);
            if (i % 4 == 3) preview.add(new double[]{s.getX(), s.getY()});
        }
        if (previewEnd == Rules.CRASHED) preview.add(new double[]{s.getX(), s.getY()});
    }

    private void launch(double angle, double power) {
        resetShot();
        ship = new Ship(level, angle, power);
        prevX = ship.getX();
        prevY = ship.getY();
        stepDebt = 0;
        state = FLY;
        burst(ship.getX(), ship.getY(), 14, 40, 120, 0.5, 2.4);
        if (!demo) {
            tries++;
            tip = null;
            Sound.launch(power);
        }
    }

    private void update(double dt) {
        anim += dt;
        aimWithKeys(dt);
        if (state == AIM && previewDirty) updatePreview();
        if (state == FLY) {
            stepDebt += dt;
            while (stepDebt >= Rules.DT && state == FLY) {
                stepDebt -= Rules.DT;
                stepOnce();
            }
        } else if (waitTimer > 0) {
            waitTimer -= dt;
            if (waitTimer <= 0) {
                if (state == DEAD) {
                    state = AIM;
                    resetShot();
                } else if (state == DEMO_WAIT && level != null) {
                    launch(level.getSolutionAngle(), level.getSolutionPower());
                } else if (state == ARRIVE) {
                    state = WIN;
                    Sound.thrust(false);
                }
            }
        }
        particles.removeIf(p -> !p.update(dt));
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Object[] f = floaters.get(i);
            f[3] = (Double) f[3] - dt;
            f[2] = (Double) f[2] - 22 * dt;
            if ((Double) f[3] <= 0) floaters.remove(i);
        }
        if (shake > 0.3) shake *= 0.88; else shake = 0;
    }

    /** 물리 한 스텝 + 그에 따른 게임 처리 */
    /**
     * 누르고 있는 방향키마다 조준을 계속 움직인다.
     * 운영체제의 키 반복은 마지막에 누른 키 하나만 반복하므로, 키를 누른 상태를 직접 기억해서 처리한다.
     * 처음 0.25초는 가만히 있다가(한 번 톡 누르면 한 칸만 움직이게), 그 뒤로는 오래 누를수록 빨라진다.
     */
    private void aimWithKeys(double dt) {
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

    private void stepOnce() {
        prevX = ship.getX();
        prevY = ship.getY();
        boolean burn = thrusting && fuel > 0 && !demo;
        if (burn) {
            fuel -= Rules.DT;
            if (ship.getSteps() % 2 == 0) {
                double speed = Math.hypot(ship.getVx(), ship.getVy());
                particles.add(new Particle(ship.getX(), ship.getY(), -ship.getVx() / speed * 150 + (fx.nextDouble() - 0.5) * 90,
                        -ship.getVy() / speed * 150 + (fx.nextDouble() - 0.5) * 90, 0.4, 2.6, 195));
            }
        }
        Sound.thrust(burn);
        int result = ship.step(level, burn);

        if (ship.hasJustWarped()) {
            trail.add(null);
            burst(prevX, prevY, 18, 275, 140, 0.6, 2.4);
            burst(ship.getX(), ship.getY(), 18, 275, 140, 0.6, 2.4);
            prevX = ship.getX();
            prevY = ship.getY();
            if (!demo) Sound.warp();
        }
        trail.add(new double[]{ship.getX(), ship.getY()});

        for (int i = 0; i < 3; i++) {
            double dx = ship.getX() - level.getStarX(i), dy = ship.getY() - level.getStarY(i);
            if (!gotStar[i] && dx * dx + dy * dy < Rules.STAR_R * Rules.STAR_R) {
                gotStar[i] = true;
                gotStarAt[i] = anim;
                burst(level.getStarX(i), level.getStarY(i), 22, 48, 170, 0.8, 2.6);
                if (!demo) Sound.star(starCount() - 1);
            }
        }
        if (result != Rules.FLYING) endFlight(result);
    }

    private void endFlight(int result) {
        thrusting = false;
        Sound.thrust(false);
        if (result == Rules.ARRIVED) {
            burst(level.getTargetX(), level.getTargetY(), 70, 155, 300, 1.3, 3.2);
            burst(level.getTargetX(), level.getTargetY(), 30, 45, 200, 1.1, 2.6);
            if (demo) {
                state = DEMO_WAIT;
                waitTimer = 1.6;
                return;
            }
            Sound.win();
            state = ARRIVE;                 // 불꽃을 잠깐 보여 준 뒤 영수증을 띄운다
            waitTimer = 0.9;
            if (!endless) {
                progress[levelIndex] = Math.max(progress[levelIndex], starCount() + 1);
                saveProgress();
            }
            return;
        }
        ghost = new ArrayList<>(trail);
        if (result == Rules.CRASHED) {
            burst(ship.getX(), ship.getY(), 60, 22, 260, 0.9, 3.4);
            burst(ship.getX(), ship.getY(), 20, 0, 90, 1.2, 4.5);
            if (!demo) {
                shake = 13;
                Sound.crash();
                boolean swallowed = ship.getCrashedInto() instanceof BlackHole;
                addFloater(swallowed ? "삼켜졌습니다" : "파손", ship.getX(), ship.getY() - 30, Ui.DANGER);
            }
        } else if (!demo) {
            Sound.lost();
            addFloater(ship.getSteps() >= Rules.MAX_STEPS ? "연락 두절" : "우주 미아", ship.getX(), ship.getY(), new Color(154, 160, 200));
        }
        state = demo ? DEMO_WAIT : DEAD;
        waitTimer = demo ? 1.0 : 0.65;
    }

    private void burst(double x, double y, int count, double hue, double speed, double life, double size) {
        for (int i = 0; i < count; i++) {
            double a = fx.nextDouble() * Math.PI * 2, v = speed * (0.2 + fx.nextDouble());
            particles.add(new Particle(x, y, Math.cos(a) * v, Math.sin(a) * v, life * (0.5 + fx.nextDouble() * 0.5),
                    size * (0.5 + fx.nextDouble()), hue + (fx.nextDouble() - 0.5) * 30));
        }
    }

    private void addFloater(String text, double x, double y, Color color) {
        floaters.add(new Object[]{text, Math.max(80, Math.min(Rules.W - 80, x)), Math.max(60, Math.min(Rules.H - 40, y)), 1.6, color});
    }

    private void installInput() {
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
        double dx = wx - level.getStartX(), dy = wy - level.getStartY(), dist = Math.hypot(dx, dy);
        if (dist < 10) return;
        aimAngle = Math.atan2(dy, dx);
        aimPower = Math.max(0, Math.min(1, (dist - 50) / 330));
        previewDirty = true;
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("별똥 택배");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new StarDrop());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);   // 웹 버전처럼 화면 가득
            frame.setVisible(true);
        });
    }
}
