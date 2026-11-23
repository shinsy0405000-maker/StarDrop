import javax.swing.JPanel;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 게임 화면이 가진 모든 상태(변수)를 모아 둔 맨 아래 층.
 *
 * 게임 본체는 기능별로 네 층으로 나뉘어 있고, 위 층은 아래 층을 상속해서 그대로 쓴다.
 *   GameState     상태 변수 (이 파일)
 *   WorldPainter  우주 화면 그리기: 배경, 천체, 별, 조준선, 소포
 *   ScreenPainter 화면 위 UI 그리기: HUD, 처음 화면, 구역 목록, 영수증
 *   StarDrop      게임 진행, 물리 한 스텝, 입력, main
 * 버튼을 누르면 실행할 동작(다시 조준, 구역 목록 등)은 맨 위 StarDrop 이 만들고,
 * 아래 층에서는 abstract 로 이름만 정해 둔다.
 */
abstract class GameState extends JPanel {
    private static final long serialVersionUID = 1L;

    // 진행 상태
    protected static final int LOADING = 0, AIM = 1, FLY = 2, DEAD = 3, ARRIVE = 4, WIN = 5, DEMO_WAIT = 6;
    protected int state = LOADING;
    protected boolean demo;                 // 처음 화면 뒤에서 정답을 자동으로 쏘는 시범
    protected boolean titleOpen, levelsOpen;
    protected boolean endless;              // 무한 배달 모드인가
    protected int levelIndex;               // 5개 구역 중 몇 번째인가
    protected long endlessSeed;
    protected transient Level level;
    protected int loadTicket;               // 마지막으로 요청한 구역 번호
    protected final int[] progress = new int[LevelGenerator.CAMPAIGN.length];   // 0 = 아직, 1~4 = 별 0~3개로 배달함

    // 조준과 비행
    protected double aimAngle, aimPower = 0.5;
    protected boolean previewDirty = true;
    protected transient Ship ship;
    protected double prevX, prevY;          // 한 스텝 전 위치 (부드럽게 그리기 위해)
    protected double stepDebt;              // 아직 계산하지 않은 시간
    protected double fuel = Rules.FUEL;
    protected boolean thrusting, mouseDown, armed, showHint, spaceDown;
    // 키보드 조준: 지금 누르고 있는 방향키들 (여러 개를 동시에 누를 수 있다)
    protected boolean leftHeld, rightHeld, upHeld, downHeld, shiftHeld;
    protected double holdTime;              // 방향키를 누르고 있은 시간
    protected final boolean[] gotStar = new boolean[3];
    protected final double[] gotStarAt = new double[3];
    protected int tries;
    protected double waitTimer;             // 실패/성공 뒤 잠깐 기다리는 시간
    protected String tip;                   // 구역 첫 화면의 안내 문구 (첫 발사 때 사라짐)

    // 그리기용 자료
    protected final transient List<double[]> trail = new ArrayList<>();      // 날아간 자취 (null = 웜홀로 끊긴 곳)
    protected transient List<double[]> ghost = new ArrayList<>();            // 직전 실패의 자취
    protected final transient List<double[]> preview = new ArrayList<>();    // 조준 예측 점
    protected int previewEnd;
    protected final transient List<Particle> particles = new ArrayList<>();
    protected final transient List<Object[]> floaters = new ArrayList<>();   // 떠오르는 글자 {글, x, y, 남은 시간, 색}
    protected transient BufferedImage background;                            // 지금 창 크기로 그려 둔 배경
    protected transient Level backgroundFor;
    protected int backgroundW, backgroundH;
    protected transient BufferedImage titleGlow;
    protected double titleGlowSize;
    protected final double[][] twinkles = new double[110][4];
    protected double anim, shake;
    protected long lastNanos = System.nanoTime();
    protected final Random fx = new Random();
    protected int fps, fpsFrames;
    protected double fpsTime;
    protected boolean showFps;

    // 화면 배치
    protected double viewScale = 1, viewX, viewY;                            // 게임 세계 → 화면
    protected double mouseX = -1, mouseY = -1;                               // 화면 좌표
    protected transient List<Widget> widgets = new ArrayList<>();            // 지난번에 그린 버튼들

    // ------------------------------------------------------------ 버튼이 부르는 동작 (StarDrop 이 구현)
    protected abstract void retry();
    protected abstract void showLevels();
    protected abstract void showTitle();
    protected abstract void startCampaign(int index);
    protected abstract void startEndless();
    protected abstract void nextLevel();

    protected int starCount() {
        int n = 0;
        for (boolean got : gotStar) if (got) n++;
        return n;
    }

    // ------------------------------------------------------------ 입력
    /** 화면 위 버튼이 열려 있어 게임 조작을 막아야 하는가 */
    protected boolean overlayOpen() {
        return titleOpen || levelsOpen || state == WIN;
    }
}
