/** 게임 규칙 숫자 모음 (화면 크기, 물리 계산 간격, 속도, 연료 등) */
final class Rules {
    private Rules() { }

    static final String VERSION = "1.0.0";       // 배포 버전 (버그 제보에 함께 적힌다)

    static final int W = 1600, H = 900;          // 게임 세계의 크기 (창 크기와 상관없이 고정)
    static final double DT = 0.01;               // 물리 계산 한 번에 흐르는 시간(초)
    static final int MAX_STEPS = 1600;           // 한 번 발사의 최대 계산 횟수 (16초)
    static final double MIN_SPEED = 170, MAX_SPEED = 520;
    static final double TARGET_R = 30;           // 배달지 반지름
    static final double STAR_R = 26;             // 별을 얻는 거리
    static final double THRUST = 170;            // 추진 가속도
    static final double FUEL = 1.2;              // 추진 연료(초)

    // Ship.step() 의 결과
    static final int FLYING = 0, ARRIVED = 1, CRASHED = 2, LOST = 3;
}
