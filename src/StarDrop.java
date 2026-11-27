import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.util.ArrayList;

/*
 * 별똥 택배 — 중력을 이용한 우주 배달 퍼즐 게임 (순수 자바, Swing)
 *
 * 실행:  javac -encoding UTF-8 -d out src/*.java
 *        java -cp out StarDrop
 *
 * 게임 본체는 기능별로 층을 나눠, 위 층이 아래 층을 상속한다 (아래 → 위):
 *   GameState      상태 변수
 *   WorldPainter   우주 화면 (천체, 별, 조준선, 소포)      ← 배경 한 장은 BackgroundPainter
 *   HudPainter     게임 화면 위 정보 (운송장, 버튼, 연료, 안내)
 *   TitlePainter   처음 화면
 *   PanelPainter   구역 목록, 배달 완료 영수증
 *   ScreenPainter  한 프레임을 그리는 순서 (paintComponent, 화면 효과)
 *   Flight         비행 진행 (발사, 물리 한 스텝, 도착·충돌)
 *   Controls       마우스·키보드 입력
 *   StarDrop       화면 전환, 구역 불러오기, 창 열기, main  ← 이 파일
 */
public class StarDrop extends Controls {
    private static final long serialVersionUID = 1L;

    public StarDrop() {
        setPreferredSize(new Dimension(1280, 720));
        setBackground(Ui.VOID);
        setFocusable(true);
        Progress.load(progress);
        installInput();
        showTitle();
        if (!GraphicsEnvironment.isHeadless()) GameLoop.start(this::frame);
    }

    /** 한 프레임: 시간 계산 → 게임 진행 → 화면 그리기 (GameLoop 가 부른다) */
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

    // ------------------------------------------------------------ 화면 전환과 구역 불러오기
    protected void showTitle() {
        titleOpen = true;
        levelsOpen = false;
        endless = false;
        loadLevel(Campaign.seed(2), Campaign.config(2), Campaign.firstTry(2), true);     // 뒤에서 "달그림자"를 시범으로 날린다
    }

    protected void showLevels() {
        titleOpen = false;
        levelsOpen = true;
        // 지금 하던 단계(처음이면 아직 못 끝낸 첫 단계)를 펼쳐 보여 준다
        listChapter = level != null && !endless && !demo ? Campaign.chapterOf(levelIndex) : Campaign.chapterOf(firstUndone());
    }


    protected void startCampaign(int index) {
        titleOpen = false;
        levelsOpen = false;
        endless = false;
        levelIndex = index;
        loadLevel(Campaign.seed(index), Campaign.config(index), Campaign.firstTry(index), false);
    }

    protected void startEndless() {
        titleOpen = false;
        levelsOpen = false;
        endless = true;
        endlessSeed = 1 + fx.nextInt(999_998);
        loadLevel(endlessSeed, LevelGenerator.endlessConfig(endlessSeed), 0, false);
    }

    /** 구역 생성은 시간이 걸릴 수 있어서 다른 스레드에서 하고, 끝나면 화면 스레드로 넘긴다 */
    private void loadLevel(long seed, LevelConfig cfg, int firstTry, boolean asDemo) {
        state = LOADING;
        thrusting = false;
        Sound.thrust(false);
        final int ticket = ++loadTicket;      // 요청 번호. 늦게 끝난 옛 요청이 새 구역을 덮어쓰지 않게 한다
        Thread worker = new Thread(() -> {
            Level made = LevelGenerator.create(seed, cfg, firstTry, 600);   // 웹처럼 600번까지 시도
            SwingUtilities.invokeLater(() -> {
                if (ticket != loadTicket) return;
                if (made != null) enterLevel(made, asDemo);
                else if (endless) startEndless();             // 이 씨앗으로는 못 만들면 다른 씨앗으로
            });
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void enterLevel(Level made, boolean asDemo) {
        level = made;
        fadeStart = anim;
        demo = asDemo;
        tries = 0;
        showHint = false;
        ghost = new ArrayList<>();
        particles.clear();
        floaters.clear();
        aimAngle = JsMath.atan2(level.getTargetY() - level.getStartY(), level.getTargetX() - level.getStartX());
        aimPower = 0.5;
        resetShot();
        tip = !demo && !endless ? Campaign.tip(levelIndex) : null;
        if (demo) {
            state = DEMO_WAIT;
            waitTimer = 0.6;
        } else {
            state = AIM;
        }
    }

    protected void retry() {
        if (demo || state == LOADING) return;
        if (state == FLY) ghost = new ArrayList<>(trail);
        state = AIM;
        waitTimer = 0;
        resetShot();
    }

    /** 버그 제보 창: 지금 상황(버전, 구역, 운영체제 등)을 자동으로 적어서 띄운다 */
    protected void openBugReport() {
        thrusting = false;
        mouseDown = false;
        armed = false;
        String info = BugReport.describe(this);
        SwingUtilities.invokeLater(() -> {      // 버튼을 누른 처리가 끝난 뒤에 창을 연다
            BugReport.open(this, info);
            requestFocusInWindow();
        });
    }

    /** 언어 고르는 창. 고르면 바로 바뀐다 */
    protected void openLanguage() {
        thrusting = false;
        mouseDown = false;
        armed = false;
        SwingUtilities.invokeLater(() -> {
            LanguagePicker.open(this, this::applyLanguage);
            requestFocusInWindow();
        });
    }

    /** 언어를 바꾸고, 글자가 들어간 미리 그린 그림(처음 화면)과 창 제목을 새로 만든다 */
    private void applyLanguage(Lang.Code code) {
        Lang.set(code);
        Ui.languageChanged();
        titleLayer.clear();
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame frame) frame.setTitle(Lang.t("별똥 택배"));
        repaint();
    }

    /** 후원 창 */
    protected void openDonate() {
        thrusting = false;
        mouseDown = false;
        armed = false;
        SwingUtilities.invokeLater(() -> {
            Donate.open(this);
            requestFocusInWindow();
        });
    }

    protected void nextLevel() {
        if (!endless && levelIndex < Campaign.COUNT - 1) startCampaign(levelIndex + 1);
        else startEndless();
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(Lang.t("별똥 택배"));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new StarDrop());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);   // 웹 버전처럼 화면 가득
            frame.setVisible(true);
        });
    }
}
