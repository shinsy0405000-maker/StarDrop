import java.util.ArrayList;
import java.util.List;

/**
 * 구역을 자동으로 만든다: 천체를 겹치지 않게 놓고, 풀 수 있는 구역만 골라 별을 배치한다.
 *
 * 웹 버전의 build / tryAttempt 를 한 줄씩 그대로 옮겼다.
 * 난수(Mulberry32)와 수학 함수(JsMath)도 웹과 같은 것을 쓰므로,
 * 같은 씨앗이면 행성 위치, 정답, 별 위치까지 웹과 소수점 끝자리까지 똑같다.
 */
class LevelGenerator {
    private LevelGenerator() { }

    /** 무한 배달: 씨앗 숫자에 따라 구성이 달라진다 (웹의 endlessCfg) */
    static LevelConfig endlessConfig(long seed) {
        Mulberry32 r = new Mulberry32((int) seed ^ 0x5bd1e995);
        int planets = 2 + (int) (r.next() * 3);
        double minTurn = 1.0 + r.next() * 0.6;
        int preview = 170 + (int) (r.next() * 60);
        int moons = 0, holes = 0, reps = 0, worms = 0;
        if (r.next() < 0.55) moons = 1 + (r.next() < 0.3 ? 1 : 0);
        if (r.next() < 0.45) holes = 1;
        if (r.next() < 0.45) reps = 1;
        if (r.next() < 0.45) worms = 1;
        return new LevelConfig("무한 배달", planets, moons, holes, reps, worms, minTurn, preview);
    }

    /** 풀 수 있는 구역이 나올 때까지 시도 번호를 하나씩 올려 가며 만든다. 끝내 못 만들면 null */
    static Level create(long seed, LevelConfig cfg, int firstTry, int maxTries) {
        for (int attempt = firstTry; attempt < firstTry + maxTries; attempt++) {
            Level level = tryOnce(seed, cfg, attempt);
            if (level != null) return level;
        }
        return null;
    }

    /** 한 번 시도. 배치에 실패했거나 풀 수 없는 구역이면 null */
    static Level tryOnce(long seed, LevelConfig cfg, int attempt) {
        Mulberry32 rng = new Mulberry32((int) (seed * 9973 + attempt * 7919L));
        Level level = build(rng, seed, cfg);
        if (level == null) return null;
        double[] sol = Solver.solve(level, cfg.minTurn);
        if (sol == null) return null;
        level.setSolution(sol[0], sol[1]);
        return placeStars(level) ? level : null;
    }

    // ------------------------------------------------------------ 천체 배치
    /** 배치하는 동안 쓰는 임시 기록 */
    private static final class Draft {
        double sx, sy, tx, ty;                                   // 출발점, 배달지
        final List<double[]> used = new ArrayList<>();           // 이미 차지한 자리 {x, y, 차지 반지름}
        final List<double[]> planets = new ArrayList<>();        // {x, y, 반지름, 달 궤도, 달 반지름, 달 속도, 달 위상} (달이 없으면 궤도 0)
        final List<double[]> holes = new ArrayList<>(), reps = new ArrayList<>();
        int moonsLeft;

        /** (x, y)에 반지름 need 만큼의 자리가 비어 있는가 */
        boolean fits(double x, double y, double need) {
            if (x < need + 20 || x > Rules.W - need - 20 || y < need + 20 || y > Rules.H - need - 20) return false;
            if (JsMath.hypot(x - sx, y - sy) < need + 150) return false;
            if (JsMath.hypot(x - tx, y - ty) < need + 110) return false;
            for (double[] u : used) if (JsMath.hypot(x - u[0], y - u[1]) < need + u[2] + 80) return false;
            return true;
        }

        /** 행성 하나를 만든다. 아직 놓을 달이 남았고 행성이 크면 달을 붙인다 */
        double[] planet(Mulberry32 rng, double x, double y, double R) {
            double[] p = {x, y, R, 0, 0, 0, 0};
            if (moonsLeft > 0 && R >= 40) {
                p[3] = R + 50 + rng.next() * 28;                                    // 달 궤도 반지름
                p[4] = 9 + rng.next() * 4;                                          // 달 반지름
                p[5] = (0.5 + rng.next() * 0.45) * (rng.next() < 0.5 ? -1 : 1);     // 도는 속도와 방향
                p[6] = rng.next() * Math.PI * 2;                                    // 처음 위치
                moonsLeft--;
            }
            return p;
        }

        /** 행성이 차지하는 반지름 (달이 있으면 달 궤도까지) */
        static double space(double[] p) {
            return p[3] > 0 ? p[3] + p[4] : p[2];
        }

        /** 빈자리에 행성을 놓아 본다. 못 놓으면 붙였던 달을 돌려놓는다 */
        boolean tryPlanet(double[] p) {
            if (fits(p[0], p[1], space(p))) {
                planets.add(p);
                used.add(new double[]{p[0], p[1], space(p)});
                return true;
            }
            if (p[3] > 0) moonsLeft++;
            return false;
        }

        /** 블랙홀이나 밀어내는 별을 count 개 놓는다 */
        boolean placeSimple(Mulberry32 rng, int count, double need, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                boolean placed = false;
                for (int k = 0; k < 120 && !placed; k++) {
                    double x = 330 + rng.next() * 940, y = 110 + rng.next() * 680;
                    if (fits(x, y, need)) {
                        out.add(new double[]{x, y});
                        used.add(new double[]{x, y, need});
                        placed = true;
                    }
                }
                if (!placed) return false;
            }
            return true;
        }
    }

    /** 출발점, 배달지, 천체를 서로 겹치지 않게 놓는다 (웹의 build) */
    private static Level build(Mulberry32 rng, long seed, LevelConfig cfg) {
        Draft d = new Draft();
        d.sx = 130 + rng.next() * 100;
        d.sy = 220 + rng.next() * 460;
        d.tx = 1370 + rng.next() * 100;
        d.ty = 220 + rng.next() * 460;
        d.moonsLeft = cfg.moons;

        // 1) 출발점과 배달지 사이를 가로막는 행성 하나
        boolean placed = false;
        for (int k = 0; k < 40 && !placed; k++) {
            double t = 0.42 + rng.next() * 0.16, R = 48 + rng.next() * 20;
            double dx = d.tx - d.sx, dy = d.ty - d.sy, dist = JsMath.hypot(dx, dy);
            double off = (rng.next() * 2 - 1) * R * 0.3;
            double x = d.sx + dx * t - dy / dist * off, y = d.sy + dy * t + dx / dist * off;
            placed = d.tryPlanet(d.planet(rng, x, y, R));
        }
        if (!placed) return null;

        // 2) 나머지 행성, 블랙홀, 밀어내는 별
        for (int i = 1; i < cfg.planets; i++) {
            placed = false;
            for (int k = 0; k < 120 && !placed; k++) {
                double x = 330 + rng.next() * 940, y = 110 + rng.next() * 680;
                placed = d.tryPlanet(d.planet(rng, x, y, 30 + rng.next() * 32));
            }
            if (!placed) return null;
        }
        if (!d.placeSimple(rng, cfg.blackHoles, 70, d.holes)) return null;
        if (!d.placeSimple(rng, cfg.repulsors, 60, d.reps)) return null;

        // 3) 웜홀 (두 입구는 멀리 떨어뜨린다)
        double[] worm = null;
        if (cfg.wormholes > 0) {
            for (int k = 0; k < 200 && worm == null; k++) {
                double ax = 300 + rng.next() * 1000, ay = 90 + rng.next() * 720;
                double bx = 300 + rng.next() * 1000, by = 90 + rng.next() * 720;
                if (JsMath.hypot(ax - bx, ay - by) < 480) continue;
                if (!d.fits(ax, ay, 45) || !d.fits(bx, by, 45)) continue;
                worm = new double[]{ax, ay, bx, by};
                d.used.add(new double[]{ax, ay, 45});
                d.used.add(new double[]{bx, by, 45});
            }
            if (worm == null) return null;
        }
        if (d.moonsLeft > 0) return null;

        // 4) 절반의 확률로 좌우를 뒤집어 다양하게 만든 뒤, 실제 객체를 생성
        boolean flip = rng.next() < 0.5;
        List<CelestialBody> bodies = new ArrayList<>();
        List<Planet> planets = new ArrayList<>();
        for (double[] p : d.planets) {
            Planet planet = new Planet(flip ? Rules.W - p[0] : p[0], p[1], p[2], p[3] > 0);
            planets.add(planet);
            bodies.add(planet);
        }
        for (double[] h : d.holes) bodies.add(new BlackHole(flip ? Rules.W - h[0] : h[0], h[1]));
        for (double[] q : d.reps) bodies.add(new Repulsor(flip ? Rules.W - q[0] : q[0], q[1]));
        // 달은 맨 뒤에 (웹과 같은 순서로 더해야 계산 결과가 끝자리까지 같다)
        for (int i = 0; i < d.planets.size(); i++) {
            double[] p = d.planets.get(i);
            if (p[3] > 0) bodies.add(new Moon(planets.get(i).getX(), p[1], p[3], p[4], flip ? -p[5] : p[5], flip ? Math.PI - p[6] : p[6]));
        }
        Wormhole wormhole = worm == null ? null
                : new Wormhole(flip ? Rules.W - worm[0] : worm[0], worm[1], flip ? Rules.W - worm[2] : worm[2], worm[3]);
        return new Level(cfg.name, seed, flip ? Rules.W - d.sx : d.sx, d.sy, flip ? Rules.W - d.tx : d.tx, d.ty,
                bodies, planets, wormhole, cfg.previewSteps);
    }

    // ------------------------------------------------------------ 별 배치
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
            for (int tries = 1; tries <= 400 && badStarSpot(level, path.get(index), s); tries++) {
                int shift = (tries % 2 == 1 ? 1 : -1) * ((tries + 1) / 2) * 3;   // 앞뒤로 조금씩 옮겨 본다
                index = Math.max(1, Math.min(n - 2, base + shift));
            }
            if (badStarSpot(level, path.get(index), s)) return false;
            level.setStar(s, path.get(index)[0], path.get(index)[1]);
        }
        return true;
    }

    private static boolean badStarSpot(Level level, double[] p, int placedCount) {
        if (p[0] < 40 || p[0] > Rules.W - 40 || p[1] < 100 || p[1] > Rules.H - 70) return true;
        if (JsMath.hypot(p[0] - level.getStartX(), p[1] - level.getStartY()) < 70) return true;
        if (JsMath.hypot(p[0] - level.getTargetX(), p[1] - level.getTargetY()) < 70) return true;
        for (int i = 0; i < placedCount; i++) if (JsMath.hypot(p[0] - level.getStarX(i), p[1] - level.getStarY(i)) < 70) return true;
        return false;
    }
}
