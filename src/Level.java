import java.util.List;

/** 구역 하나: 출발점, 배달지, 천체들, 웜홀, 별 3개, 자동 풀이기가 찾은 정답 */
class Level {
    private final String name;
    private final long seed;
    private final double startX, startY, targetX, targetY;
    private final CelestialBody[] bodies;
    private final Wormhole wormhole;         // 없으면 null
    private final int previewSteps;          // 조준할 때 보여 주는 예측 길이
    private final double[][] stars = new double[3][2];
    private double solutionAngle, solutionPower;

    Level(String name, long seed, double startX, double startY, double targetX, double targetY,
          List<CelestialBody> bodies, Wormhole wormhole, int previewSteps) {
        this.name = name;
        this.seed = seed;
        this.startX = startX;
        this.startY = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.bodies = bodies.toArray(new CelestialBody[0]);
        this.wormhole = wormhole;
        this.previewSteps = previewSteps;
    }

    public String getName() { return name; }
    public long getSeed() { return seed; }
    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getTargetX() { return targetX; }
    public double getTargetY() { return targetY; }
    public int getBodyCount() { return bodies.length; }
    public CelestialBody getBody(int i) { return bodies[i]; }
    public Wormhole getWormhole() { return wormhole; }
    public int getPreviewSteps() { return previewSteps; }
    public double getStarX(int i) { return stars[i][0]; }
    public double getStarY(int i) { return stars[i][1]; }
    public double getSolutionAngle() { return solutionAngle; }
    public double getSolutionPower() { return solutionPower; }

    void setSolution(double angle, double power) {
        solutionAngle = angle;
        solutionPower = power;
    }

    void setStar(int i, double x, double y) {
        stars[i][0] = x;
        stars[i][1] = y;
    }
}
