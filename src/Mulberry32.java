/**
 * 웹 버전과 똑같은 난수 생성기 (mulberry32).
 *
 * 자바의 java.util.Random 은 웹(자바스크립트)과 다른 숫자를 만들기 때문에,
 * 같은 씨앗이어도 행성 배치가 웹과 달라진다. 그래서 웹에서 쓴 계산을 그대로 옮겼다.
 * 같은 씨앗을 넣으면 웹과 소수점 끝자리까지 같은 숫자가 같은 순서로 나온다.
 */
final class Mulberry32 {
    private int state;

    Mulberry32(int seed) {
        state = seed;
    }

    /** 0 이상 1 미만의 난수 (웹의 r() 와 같다) */
    double next() {
        state += 0x6D2B79F5;
        int t = (state ^ (state >>> 15)) * (1 | state);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }
}
