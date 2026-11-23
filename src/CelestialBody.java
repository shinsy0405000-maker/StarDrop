/** 중력을 가진 천체. 물리 계산은 천체의 종류를 몰라도 이 메서드만 부르면 된다. */
interface CelestialBody extends Drawable {
    /**
     * (x, y)에 있는 소포가 받는 가속도를 acc[0], acc[1] 에 더한다.
     * @return 소포가 이 천체에 부딪혔으면 true
     */
    boolean pull(double x, double y, double flightTime, double[] acc);
}
