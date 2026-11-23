import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 구역을 자동으로 만든다: 천체를 겹치지 않게 놓고, 풀 수 있는 구역만 골라 별을 배치한다 */
class LevelGenerator {
    private LevelGenerator() { }

    /** 계획서의 5개 구역. 구역마다 새 천체가 하나씩 등장한다 */
    static final LevelConfig[] CAMPAIGN = {
        new LevelConfig("첫 배달",     1, 0, 0, 0, 0, 0.5, 300),
        new LevelConfig("달그림자",    2, 1, 0, 0, 0, 0.8, 260),
        new LevelConfig("검은 입",     2, 0, 1, 0, 0, 1.0, 240),
        new LevelConfig("밀어내는 별", 3, 1, 0, 1, 0, 1.1, 220),
        new LevelConfig("마지막 소포", 3, 1, 1, 1, 1, 1.3, 190),
    };
    static final long[] CAMPAIGN_SEED = {7001, 7132, 7263, 7394, 7525};
    /** 미리 돌려 보고 찾아 둔 "처음 성공하는 시도 번호". 여기서 시작하면 구역이 바로 만들어진다 */
    static final int[] CAMPAIGN_FIRST_TRY = {0, 2, 5, 4, 0};

    /** 무한 배달: 씨앗 숫자에 따라 구성이 달라진다 */
    static LevelConfig endlessConfig(long seed) {
        Random r = new Random(seed * 31 + 5);
        int planets = 2 + r.nextInt(3);
        int moons = r.nextDouble() < 0.55 ? 1 : 0;
        int holes = r.nextDouble() < 0.45 ? 1 : 0;
        int reps = r.nextDouble() < 0.45 ? 1 : 0;
        int worms = r.nextDouble() < 0.45 ? 1 : 0;
        return new LevelConfig("무한 배달", planets, moons, holes, reps, worms, 1.0 + r.nextDouble() * 0.6, 170 + r.nextInt(60));
    }

    /** 풀 수 있는 구역이 나올 때까지 만든다 */
    static Level create(long seed, LevelConfig cfg, int firstTry) {
        for (int attempt = firstTry; attempt < firstTry + 3000; attempt++) {
            Level level = tryOnce(seed, cfg, attempt);
            if (level != null) return level;
        }
        throw new IllegalStateException("구역을 만들지 못했습니다: seed=" + seed);
    }

    /** 한 번 시도. 배치에 실패했거나 풀 수 없는 구역이면 null */
    static Level tryOnce(long seed, LevelConfig cfg, int attempt) {
        Level level = build(new Random(seed * 9973 + attempt * 7919L), seed, cfg);
        if (level == null) return null;
        double[] sol = Solver.solve(level, cfg.minTurn);
        if (sol == null) return null;
        level.setSolution(sol[0], sol[1]);
        return placeStars(level) ? level : null;
    }

    /** 출발점, 배달지, 천체를 서로 겹치지 않게 놓는다 */
    private static Level build(Random rng, long seed, LevelConfig cfg) {
        double sx = 130 + rng.nextDouble() * 100, sy = 220 + rng.nextDouble() * 460;
        double tx = 1370 + rng.nextDouble() * 100, ty = 220 + rng.nextDouble() * 460;
        List<double[]> used = new ArrayList<>();      // 이미 차지한 자리 {x, y, 필요한 반지름}
        List<double[]> planets = new ArrayList<>();   // {x, y, 반지름, 달 궤도, 달 반지름, 달 속도, 달 위상}
        List<double[]> holes = new ArrayList<>(), reps = new ArrayList<>();
        int moonsLeft = cfg.moons;

        // 1) 출발점과 배달지 사이를 가로막는 행성 하나
        boolean placed = false;
        for (int k = 0; k < 40 && !placed; k++) {
            double t = 0.42 + rng.nextDouble() * 0.16, R = 48 + rng.nextDouble() * 20;
            double dx = tx - sx, dy = ty - sy, d = Math.hypot(dx, dy), off = (rng.nextDouble() * 2 - 1) * R * 0.3;
            double[] p = planetSpec(rng, sx + dx * t - dy / d * off, sy + dy * t + dx / d * off, R, moonsLeft > 0);
            if (fits(used, p[0], p[1], space(p), sx, sy, tx, ty)) {
                planets.add(p);
                used.add(new double[]{p[0], p[1], space(p)});
                if (p[3] > 0) moonsLeft--;
                placed = true;
            }
        }
        if (!placed) return null;

        // 2) 나머지 행성, 블랙홀, 밀어내는 별
        for (int i = 1; i < cfg.planets; i++) {
            placed = false;
            for (int k = 0; k < 120 && !placed; k++) {
                double[] p = planetSpec(rng, 330 + rng.nextDouble() * 940, 110 + rng.nextDouble() * 680, 30 + rng.nextDouble() * 32, moonsLeft > 0);
                if (fits(used, p[0], p[1], space(p), sx, sy, tx, ty)) {
                    planets.add(p);
                    used.add(new double[]{p[0], p[1], space(p)});
                    if (p[3] > 0) moonsLeft--;
                    placed = true;
                }
            }
            if (!placed) return null;
        }
        if (moonsLeft > 0) return null;
        if (!placeSimple(rng, cfg.blackHoles, 70, holes, used, sx, sy, tx, ty)) return null;
        if (!placeSimple(rng, cfg.repulsors, 60, reps, used, sx, sy, tx, ty)) return null;

        // 3) 웜홀 (두 입구는 멀리 떨어뜨린다)
        double[] worm = null;
        if (cfg.wormholes > 0) {
            for (int k = 0; k < 200 && worm == null; k++) {
                double ax = 300 + rng.nextDouble() * 1000, ay = 90 + rng.nextDouble() * 720;
                double bx = 300 + rng.nextDouble() * 1000, by = 90 + rng.nextDouble() * 720;
                if (Math.hypot(ax - bx, ay - by) < 480) continue;
                if (!fits(used, ax, ay, 45, sx, sy, tx, ty) || !fits(used, bx, by, 45, sx, sy, tx, ty)) continue;
                worm = new double[]{ax, ay, bx, by};
            }
            if (worm == null) return null;
        }

        // 4) 절반의 확률로 좌우를 뒤집어 다양하게 만든 뒤, 실제 객체를 생성
        boolean flip = rng.nextDouble() < 0.5;
        List<CelestialBody> bodies = new ArrayList<>();
        for (int i = 0; i < planets.size(); i++) {
            double[] p = planets.get(i);
            double px = flip ? Rules.W - p[0] : p[0];
            bodies.add(new Planet(px, p[1], p[2], seed * 131 + i * 17 + 3));
            if (p[3] > 0) bodies.add(new Moon(px, p[1], p[3], p[4], flip ? -p[5] : p[5], flip ? Math.PI - p[6] : p[6]));
        }
        for (double[] h : holes) bodies.add(new BlackHole(flip ? Rules.W - h[0] : h[0], h[1]));
        for (double[] q : reps) bodies.add(new Repulsor(flip ? Rules.W - q[0] : q[0], q[1]));
        Wormhole wormhole = worm == null ? null
                : new Wormhole(flip ? Rules.W - worm[0] : worm[0], worm[1], flip ? Rules.W - worm[2] : worm[2], worm[3]);
        return new Level(cfg.name, seed, flip ? Rules.W - sx : sx, sy, flip ? Rules.W - tx : tx, ty, bodies, wormhole, cfg.previewSteps);
    }

    private static double[] planetSpec(Random rng, double x, double y, double R, boolean wantMoon) {
        double[] p = {x, y, R, 0, 0, 0, 0};
        if (wantMoon && R >= 40) {
            p[3] = R + 50 + rng.nextDouble() * 28;                                    // 달 궤도 반지름
            p[4] = 9 + rng.nextDouble() * 4;                                          // 달 반지름
            p[5] = (0.5 + rng.nextDouble() * 0.45) * (rng.nextDouble() < 0.5 ? -1 : 1); // 도는 속도와 방향
            p[6] = rng.nextDouble() * Math.PI * 2;                                    // 처음 위치
        }
        return p;
    }

    /** 행성이 차지하는 반지름 (달이 있으면 달 궤도까지) */
    private static double space(double[] p) {
        return p[3] > 0 ? p[3] + p[4] : p[2];
    }

    private static boolean placeSimple(Random rng, int count, double need, List<double[]> out, List<double[]> used,
                                       double sx, double sy, double tx, double ty) {
        for (int i = 0; i < count; i++) {
            boolean placed = false;
            for (int k = 0; k < 120 && !placed; k++) {
                double x = 330 + rng.nextDouble() * 940, y = 110 + rng.nextDouble() * 680;
                if (fits(used, x, y, need, sx, sy, tx, ty)) {
                    out.add(new double[]{x, y});
                    used.add(new double[]{x, y, need});
                    placed = true;
                }
            }
            if (!placed) return false;
        }
        return true;
    }

    /** (x, y)에 반지름 need 만큼의 자리가 비어 있는가 */
    private static boolean fits(List<double[]> used, double x, double y, double need, double sx, double sy, double tx, double ty) {
        if (x < need + 20 || x > Rules.W - need - 20 || y < need + 20 || y > Rules.H - need - 20) return false;
        if (Math.hypot(x - sx, y - sy) < need + 150) return false;
        if (Math.hypot(x - tx, y - ty) < need + 110) return false;
        for (double[] u : used) if (Math.hypot(x - u[0], y - u[1]) < need + u[2] + 80) return false;
        return true;
    }

    /** 정답 궤도의 30%, 54%, 78% 지점에 별을 놓는다 → 별 3개를 모두 얻는 길이 반드시 있다 */
    private static boolean placeStars(Level level) {
        List<double[]> path = new ArrayList<>();
        Ship ship = new Ship(level, level.getSolutionAngle(), level.getSolutionPower());
        int result;
        do {
            path.add(new double[]{ship.getX(), ship.getY()});
            result = ship.step(level, false);
        } while (result == Rules.FLYING);

        double[] where = {0.30, 0.54, 0.78};
        int n = path.size();
        for (int s = 0; s < 3; s++) {
            int base = (int) Math.round(n * where[s]), index = base;
            for (int tries = 0; badStarSpot(level, path.get(index), s) && tries < 400; tries++) {
                int shift = (tries / 2 + 1) * 3 * (tries % 2 == 0 ? 1 : -1);   // 앞뒤로 조금씩 옮겨 본다
                index = Math.max(1, Math.min(n - 2, base + shift));
            }
            if (badStarSpot(level, path.get(index), s)) return false;
            level.setStar(s, path.get(index)[0], path.get(index)[1]);
        }
        return true;
    }

    private static boolean badStarSpot(Level level, double[] p, int placedCount) {
        if (p[0] < 40 || p[0] > Rules.W - 40 || p[1] < 100 || p[1] > Rules.H - 70) return true;
        if (Math.hypot(p[0] - level.getStartX(), p[1] - level.getStartY()) < 70) return true;
        if (Math.hypot(p[0] - level.getTargetX(), p[1] - level.getTargetY()) < 70) return true;
        for (int i = 0; i < placedCount; i++) if (Math.hypot(p[0] - level.getStarX(i), p[1] - level.getStarY(i)) < 70) return true;
        return false;
    }
}
