/**
 * 캠페인 구성: 태양계 9단계 × 구역 16개 = 144구역.
 *
 * 첫 단계(지구)의 16구역은 웹 버전과 똑같다 (같은 설정, 같은 씨앗 → 같은 행성 배치와 정답).
 * 다음 단계로 갈수록 그 행성의 특징을 딴 천체가 더해지고, 더 많이 휘어야 하며, 조준 점선이 짧아진다.
 * 구역 번호(index)는 0~143 이고, 단계 = index / 16, 단계 안의 번호 = index % 16 이다.
 */
final class Campaign {
    private Campaign() { }

    static final String[] CHAPTERS = {"지구", "화성", "금성", "수성", "목성", "토성", "천왕성", "해왕성", "태양"};
    static final int STAGES = 16;
    static final int COUNT = CHAPTERS.length * STAGES;

    /** 지구 단계 = 웹 버전의 16구역 (이름, 행성, 달, 블랙홀, 밀어내는 별, 웜홀, 최소 휨, 점선 길이) */
    private static final LevelConfig[] EARTH = {
        new LevelConfig("첫 배달",       1, 0, 0, 0, 0, 0.5, 300),
        new LevelConfig("돌아가는 길",   2, 0, 0, 0, 0, 0.7, 280),
        new LevelConfig("달그림자",      2, 1, 0, 0, 0, 0.8, 260),
        new LevelConfig("세 개의 우물",  3, 0, 0, 0, 0, 0.9, 250),
        new LevelConfig("공전 시간표",   3, 1, 0, 0, 0, 1.0, 240),
        new LevelConfig("검은 입",       2, 0, 1, 0, 0, 1.0, 240),
        new LevelConfig("밀어내는 별",   3, 0, 0, 1, 0, 1.0, 230),
        new LevelConfig("지름길",        2, 0, 0, 0, 1, 0.9, 230),
        new LevelConfig("사건의 지평선", 3, 1, 1, 0, 0, 1.1, 220),
        new LevelConfig("역풍",          3, 1, 0, 1, 0, 1.2, 210),
        new LevelConfig("환승역",        4, 0, 0, 0, 1, 1.2, 200),
        new LevelConfig("당기고 밀고",   3, 0, 1, 1, 0, 1.3, 190),
        new LevelConfig("쌍둥이 달",     4, 2, 0, 0, 0, 1.4, 180),
        new LevelConfig("위험한 지름길", 3, 0, 1, 0, 1, 1.4, 170),
        new LevelConfig("혼잡 구간",     4, 1, 0, 1, 1, 1.5, 160),
        new LevelConfig("마지막 소포",   4, 2, 1, 1, 1, 1.6, 150),
    };

    /**
     * 단계마다 더해지는 것 {행성, 달, 블랙홀, 밀어내는 별, 웜홀}
     *   화성: 달이 둘 (포보스, 데이모스)       금성: 두꺼운 대기 → 밀어내는 별
     *   수성: 태양 가까이 → 블랙홀              목성: 큰 행성과 많은 달
     *   토성: 달과 웜홀                         천왕성: 옆으로 누운 행성 → 밀어내는 별과 웜홀
     *   해왕성: 거센 바람 → 블랙홀과 밀어내는 별  태양: 모든 것
     */
    private static final int[][] EXTRA = {
        {0, 0, 0, 0, 0},   // 지구
        {0, 1, 0, 0, 0},   // 화성
        {0, 0, 0, 1, 0},   // 금성
        {0, 0, 1, 0, 0},   // 수성
        {1, 1, 0, 0, 0},   // 목성
        {0, 1, 0, 0, 1},   // 토성
        {1, 0, 0, 1, 1},   // 천왕성
        {1, 0, 1, 1, 0},   // 해왕성
        {1, 1, 1, 1, 1},   // 태양
    };

    /** 단계를 시작할 때 보여 주는 안내 */
    static final String[] CHAPTER_TIPS = {
        null,
        "화성 단계: 모든 구역에 달이 하나씩 더 있습니다. 달은 발사하는 순간부터 돌기 시작합니다.",
        "금성 단계: 두꺼운 대기처럼 소포를 밀어내는 별이 하나씩 더 있습니다.",
        "수성 단계: 태양 가까운 곳입니다. 모든 구역에 블랙홀이 하나씩 더 있습니다.",
        "목성 단계: 행성과 달이 하나씩 더 많습니다. 점선이 짧아졌으니 궤도를 상상해 보세요.",
        "토성 단계: 달과 웜홀이 더해집니다.",
        "천왕성 단계: 행성, 밀어내는 별, 웜홀이 하나씩 더 있습니다.",
        "해왕성 단계: 행성, 블랙홀, 밀어내는 별이 하나씩 더 있습니다.",
        "태양 단계: 마지막입니다. 모든 천체가 하나씩 더 있고, 궤도는 더 크게 휘어야 합니다.",
    };

    /** 지구 단계 구역별 안내 문구 (웹 버전과 같은 내용, 구역 번호 → 문구) */
    private static final String[] TIPS = new String[STAGES];
    static {
        TIPS[0] = "마우스를 움직여 방향과 세기를 정하고, 클릭하면 발사합니다. 점선은 궤도의 앞부분만 보여 줍니다.";
        TIPS[1] = "날아가는 동안 화면을 누르고 있거나 Space를 누르면 진행 방향으로 추진합니다. 연료는 1.2초 분량입니다.";
        TIPS[2] = "달은 발사하는 순간부터 공전합니다. 점선 예측에는 달의 움직임이 이미 반영되어 있습니다.";
        TIPS[5] = "블랙홀은 작지만 끌어당기는 힘이 큽니다. 주황색 원반 안쪽으로 들어가면 돌아오지 못합니다.";
        TIPS[6] = "청록색 별은 소포를 밀어냅니다. 부딪히지는 않으니 방향을 꺾는 데 쓰세요.";
        TIPS[7] = "보라색 웜홀에 들어가면 속도를 그대로 지닌 채 반대편 웜홀에서 나옵니다.";
    }

    /** 이 구역 첫 화면에 보여 줄 안내 (지구 단계는 구역별, 다른 단계는 첫 구역에만. 없으면 null) */
    static String tip(int index) {
        if (chapterOf(index) == 0) return TIPS[index];
        return stageOf(index) == 0 ? CHAPTER_TIPS[chapterOf(index)] : null;
    }

    /**
     * 미리 돌려 보고 찾아 둔 "처음 성공하는 시도 번호" (구역 144개).
     * 여기서 시작하면 실패할 시도를 건너뛰어 구역이 바로 만들어진다. 지구 단계는 웹의 CAMPAIGN_ATT 와 같다.
     */
    static final int[] FIRST_TRY = {
        0, 0, 0, 11, 16, 6, 1, 2, 8, 1, 11, 7, 16, 1, 3, 13,   // 지구
        0, 6, 3, 6, 9, 5, 0, 30, 0, 2, 7, 3, 42, 2, 1, 15,   // 화성
        0, 1, 0, 0, 5, 0, 3, 3, 0, 1, 1, 3, 1, 6, 9, 3,   // 금성
        3, 1, 0, 5, 13, 1, 0, 12, 11, 9, 9, 6, 23, 10, 2, 39,   // 수성
        3, 3, 3, 6, 2, 3, 0, 32, 13, 1, 6, 28, 50, 1, 4, 115,   // 목성
        2, 2, 10, 7, 2, 4, 3, 5, 3, 0, 10, 1, 37, 11, 8, 20,   // 토성
        0, 1, 0, 3, 1, 1, 4, 1, 2, 0, 1, 7, 19, 2, 0, 17,   // 천왕성
        0, 0, 3, 0, 7, 5, 5, 4, 4, 0, 0, 20, 125, 4, 4, 19,   // 해왕성
        0, 2, 0, 0, 6, 4, 4, 1, 2, 2, 1, 17, 185, 15, 1, 594,   // 태양
    };

    static long seed(int index) { return 7001 + index * 131L; }

    static int chapterOf(int index) { return index / STAGES; }

    static int stageOf(int index) { return index % STAGES; }

    /** 단계 이름 (지금 고른 언어로) */
    static String chapterName(int index) { return Lang.t(CHAPTERS[chapterOf(index)]); }

    static int firstTry(int index) { return index < FIRST_TRY.length ? FIRST_TRY[index] : 0; }

    static LevelConfig config(int index) {
        int c = chapterOf(index);
        LevelConfig b = EARTH[stageOf(index)];
        if (c == 0) return b;
        int[] e = EXTRA[c];
        return new LevelConfig(b.name,
                b.planets + e[0],
                Math.min(3, b.moons + e[1]),
                b.blackHoles + e[2],
                b.repulsors + e[3],
                Math.min(1, b.wormholes + e[4]),
                b.minTurn + 0.06 * c,
                Math.max(110, b.previewSteps - 10 * c));
    }
}
