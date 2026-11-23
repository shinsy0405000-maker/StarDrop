import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** 자동 풀이기: 모든 각도와 세기를 쏴 보고, 조금 어긋나도 성공하는 정답을 찾는다 */
class Solver {
    private Solver() { }

    /** 한 각도에서 세기 12단계를 쏴 보고, 조건에 맞게 도착한 것만 돌려준다 */
    private static List<double[]> hitsAt(Level level, double angle, double minTurn) {
        List<double[]> hits = new ArrayList<>();
        Ship[] ship = new Ship[1];
        boolean[] out = new boolean[1];
        for (int p = 0; p < 12; p++) {
            double power = p / 11.0;
            if (fly(level, angle, power, ship, out) != Rules.ARRIVED || out[0]) continue;
            double turn = ship[0].getTurned(), time = ship[0].getTime();
            if (turn < minTurn || time < 2.5 || time > 11) continue;
            hits.add(new double[]{angle, power, Math.min(turn, 5) - 0.1 * time});
        }
        return hits;
    }

    /** 한 번 끝까지 날려 본다. 결과 = {도착 결과, 화면 밖으로 나갔으면 1} 과 함께 ship 을 돌려준다 */
    private static int fly(Level level, double angle, double power, Ship[] shipOut, boolean[] wentOut) {
        Ship ship = new Ship(level, angle, power);
        int result;
        boolean out = false;
        while ((result = ship.step(level, false)) == Rules.FLYING) {
            if (ship.getX() < 20 || ship.getX() > Rules.W - 20 || ship.getY() < 20 || ship.getY() > Rules.H - 20) out = true;
        }
        if (shipOut != null) shipOut[0] = ship;
        if (wentOut != null) wentOut[0] = out;
        return result;
    }

    /**
     * 각도 720개(0.5도 간격) × 세기 12단계를 전부 쏴 본다 (완전 탐색).
     * 도착한 것 중에서 많이 휘고 너무 길지 않은 궤도를 고르되,
     * 각도와 세기가 조금 어긋나도 성공하는 "튼튼한" 정답만 채택한다.
     *
     * 각도마다 따로 계산해도 되므로 CPU 코어 여러 개에 나눠서 동시에 돌린다 (parallel).
     * 결과는 각도 순서대로 다시 모으기 때문에 한 코어로 돌릴 때와 정답이 똑같다.
     * @return {각도, 세기} 또는 정답이 없으면 null
     */
    static double[] solve(Level level, double minTurn) {
        List<double[]> hits = new ArrayList<>();   // {각도, 세기, 점수}
        IntStream.range(0, 720).parallel()
                .mapToObj(a -> hitsAt(level, a * Math.PI / 360, minTurn))
                .toList()                          // 각도 순서 그대로
                .forEach(hits::addAll);
        hits.sort((u, v) -> Double.compare(v[2], u[2]));   // 점수 높은 순

        for (int i = 0; i < Math.min(hits.size(), 40); i++) {
            double[] h = hits.get(i);
            if (sturdy(level, h[0], h[1])) return new double[]{h[0], h[1]};
        }
        return null;
    }

    /** 각도와 세기를 조금씩 비틀어 쏴도 전부 도착하는가 (하나라도 실패하면 바로 false) */
    private static boolean sturdy(Level level, double angle, double power) {
        double[] angleShift = {-0.006, -0.003, 0.003, 0.006}, powerShift = {-0.012, 0, 0.012};
        for (double da : angleShift) {
            for (double dp : powerShift) {
                double p = Math.max(0, Math.min(1, power + dp));
                if (fly(level, angle + da, p, null, null) != Rules.ARRIVED) return false;
            }
        }
        return true;
    }
}
