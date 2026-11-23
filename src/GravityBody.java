/** 제자리에 있는 천체(행성, 블랙홀, 밀어내는 별)의 공통 부모. 만유인력 계산이 여기 있다 */
abstract class GravityBody implements CelestialBody {
    private final double x, y;
    private final double mass;       // 양수면 끌어당기고 음수면 밀어낸다
    private final double hitRadius;  // 이 안으로 들어오면 충돌 (0 이면 충돌 없음)
    private final double soften;     // 중심에서 힘이 무한대가 되지 않게 하는 값

    protected GravityBody(double x, double y, double mass, double hitRadius, double soften) {
        if (hitRadius < 0) throw new IllegalArgumentException("반지름은 음수일 수 없습니다: " + hitRadius);
        this.x = x;
        this.y = y;
        this.mass = mass;
        this.hitRadius = hitRadius;
        this.soften = soften;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getMass() { return mass; }
    public double getHitRadius() { return hitRadius; }

    /** 만유인력: 가속도 = 질량 / 거리^2, 방향은 천체 쪽 */
    @Override
    public boolean pull(double px, double py, double flightTime, double[] acc) {
        double dx = x - px, dy = y - py;
        double dist2 = dx * dx + dy * dy;
        if (dist2 < hitRadius * hitRadius) return true;
        double q = dist2 + soften;
        double k = mass / (q * Math.sqrt(q));
        acc[0] += dx * k;
        acc[1] += dy * k;
        return false;
    }
}
