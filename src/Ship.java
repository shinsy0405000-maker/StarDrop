/** 날아가는 소포. 위치와 속도는 step() 을 통해서만 바뀐다 */
class Ship {
    private double x, y, vx, vy;
    private int steps;                 // 지금까지 계산한 횟수
    private double turned;             // 지금까지 방향이 꺾인 양(라디안)
    private boolean inWormhole;        // 웜홀 안에 있는 동안 다시 이동하지 않게
    private boolean justWarped;        // 이번 step 에서 웜홀을 통과했는가
    private CelestialBody crashedInto; // 부딪힌 천체
    private final double[] acc = new double[2];

    Ship(Level level, double angle, double power) {
        double speed = Rules.MIN_SPEED + (Rules.MAX_SPEED - Rules.MIN_SPEED) * power;
        x = level.getStartX();
        y = level.getStartY();
        vx = JsMath.cos(angle) * speed;
        vy = JsMath.sin(angle) * speed;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public int getSteps() { return steps; }
    public double getTime() { return steps * Rules.DT; }
    public double getTurned() { return turned; }
    public boolean hasJustWarped() { return justWarped; }
    public CelestialBody getCrashedInto() { return crashedInto; }

    /** DT 초만큼 전진. 결과는 Rules.FLYING / ARRIVED / CRASHED / LOST */
    int step(Level level, boolean thrust) {
        acc[0] = 0;
        acc[1] = 0;
        double time = steps * Rules.DT;

        // 1) 모든 천체의 가속도를 더한다 (다형성: 종류를 몰라도 pull 만 부르면 된다)
        for (int i = 0; i < level.getBodyCount(); i++) {
            CelestialBody body = level.getBody(i);
            if (body.pull(x, y, time, acc)) {
                crashedInto = body;
                return Rules.CRASHED;
            }
        }

        // 2) 추진: 지금 날아가는 방향으로 가속
        double speed2 = vx * vx + vy * vy;
        if (thrust) {
            double speed = Math.sqrt(speed2);
            acc[0] += vx / speed * Rules.THRUST;
            acc[1] += vy / speed * Rules.THRUST;
        }

        // 3) 가속도 -> 속도 -> 위치
        double newVx = vx + acc[0] * Rules.DT, newVy = vy + acc[1] * Rules.DT;
        turned += Math.abs(vx * newVy - vy * newVx) / speed2;
        vx = newVx;
        vy = newVy;
        x += vx * Rules.DT;
        y += vy * Rules.DT;
        steps++;

        // 4) 웜홀
        justWarped = false;
        Wormhole w = level.getWormhole();
        if (w != null) {
            double r2 = w.getRadius() * w.getRadius();
            double da = (x - w.getAx()) * (x - w.getAx()) + (y - w.getAy()) * (y - w.getAy());
            double db = (x - w.getBx()) * (x - w.getBx()) + (y - w.getBy()) * (y - w.getBy());
            if (inWormhole) {
                if (da > r2 && db > r2) inWormhole = false;
            } else if (da < r2) {
                x += w.getBx() - w.getAx();
                y += w.getBy() - w.getAy();
                inWormhole = true;
                justWarped = true;
            } else if (db < r2) {
                x += w.getAx() - w.getBx();
                y += w.getAy() - w.getBy();
                inWormhole = true;
                justWarped = true;
            }
        }

        // 5) 도착 / 이탈 판정
        double tx = x - level.getTargetX(), ty = y - level.getTargetY();
        if (tx * tx + ty * ty < Rules.TARGET_R * Rules.TARGET_R) return Rules.ARRIVED;
        if (x < -350 || x > Rules.W + 350 || y < -350 || y > Rules.H + 350 || steps >= Rules.MAX_STEPS) return Rules.LOST;
        return Rules.FLYING;
    }
}
