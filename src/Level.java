import java.util.List;

/** 구역 하나: 출발점, 배달지, 천체들, 웜홀, 별 3개, 자동 풀이기가 찾은 정답 */
class Level {
    private final String name;
    private final long seed;
    private final double startX, startY, targetX, targetY;
    private final CelestialBody[] bodies;    // 행성, 블랙홀, 밀어내는 별, 달 순서 (웹과 같은 순서)
    private final Wormhole wormhole;         // 없으면 null
    private final int previewSteps;          // 조준할 때 보여 주는 예측 길이
    private final double[][] stars = new double[3][2];
    private double solutionAngle, solutionPower;

    // 겉모습 (웹의 makeLook): 배경 성운 색, 반짝이는 별 110개 {x, y, 크기, 위상, 속도}
    private final double lookHue;
    private final double[][] twinkles = new double[110][5];

    Level(String name, long seed, double startX, double startY, double targetX, double targetY,
          List<CelestialBody> bodies, List<Planet> planets, Wormhole wormhole, int previewSteps) {
        this.name = name;
        this.seed = seed;
        this.startX = startX;
        this.startY = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.bodies = bodies.toArray(new CelestialBody[0]);
        this.wormhole = wormhole;
        this.previewSteps = previewSteps;

        // 행성의 종류와 색, 배경 색, 반짝이는 별 위치를 씨앗으로 정한다 (웹과 같은 순서로 난수를 꺼낸다)
        Mulberry32 r = new Mulberry32((int) (seed * 31 + 7));
        lookHue = r.next() * 360;
        String[] kinds = {"gas", "rock", "terra", "lava", "ice", "gas"};
        for (Planet p : planets) {
            String kind = kinds[(int) (r.next() * kinds.length)];
            double hue = r.next() * 360, tilt = (r.next() - 0.5) * 0.9;
            int lookSeed = (int) (r.next() * 1e9);
            boolean ring = kind.equals("gas") && p.getRadius() > 42 && !p.hasMoon() && r.next() < 0.6;
            p.setLook(kind, hue, tilt, lookSeed, ring);
        }
        for (double[] t : twinkles) {
            t[0] = -500 + r.next() * 2600;
            t[1] = -500 + r.next() * 1900;
            t[2] = 0.6 + r.next() * 1.3;
            t[3] = r.next() * (Math.PI * 2);
            t[4] = 0.6 + r.next() * 2.2;
        }
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
    public double getLookHue() { return lookHue; }
    public double[][] getTwinkles() { return twinkles; }

    void setSolution(double angle, double power) {
        solutionAngle = angle;
        solutionPower = power;
    }

    void setStar(int i, double x, double y) {
        stars[i][0] = x;
        stars[i][1] = y;
    }
}
