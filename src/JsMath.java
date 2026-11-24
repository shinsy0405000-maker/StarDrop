/**
 * 웹(자바스크립트)과 소수점 끝자리까지 같은 결과를 내는 수학 함수.
 *
 * 궤도 계산은 아주 작은 오차도 몇 초 뒤에는 크게 벌어진다.
 * 자바의 Math.sin, Math.hypot 은 컴퓨터에 따라 마지막 자리가 조금 다를 수 있어서,
 *   - sin, cos, atan2 는 StrictMath (웹 브라우저와 같은 fdlibm 계산법)
 *   - hypot 은 크롬(V8)이 쓰는 계산법을 그대로 옮긴 것
 * 을 쓴다. 이렇게 하면 같은 구역에서 웹과 똑같은 정답이 나온다.
 */
final class JsMath {
    private JsMath() { }

    static double sin(double a) { return StrictMath.sin(a); }

    static double cos(double a) { return StrictMath.cos(a); }

    static double atan2(double y, double x) { return StrictMath.atan2(y, x); }

    /** √(x² + y²). 크롬과 같은 방법: 큰 값으로 나눈 뒤 오차를 보정하며 더한다 */
    static double hypot(double x, double y) {
        x = Math.abs(x);
        y = Math.abs(y);
        double max = Math.max(x, y);
        if (max == 0) return 0;
        double sum = 0, compensation = 0;
        for (double v : new double[]{x, y}) {
            double n = v / max, summand = n * n - compensation, next = sum + summand;
            compensation = (next - sum) - summand;
            sum = next;
        }
        return Math.sqrt(sum) * max;
    }
}
