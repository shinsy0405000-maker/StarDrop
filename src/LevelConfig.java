/** 구역에 무엇을 몇 개 넣을지 */
class LevelConfig {
    final String name;
    final int planets, moons, blackHoles, repulsors, wormholes, previewSteps;
    final double minTurn;   // 정답 궤도가 최소 이만큼은 휘어야 한다 (직선으로 풀리는 구역 방지)

    LevelConfig(String name, int planets, int moons, int blackHoles, int repulsors, int wormholes, double minTurn, int previewSteps) {
        this.name = name;
        this.planets = planets;
        this.moons = moons;
        this.blackHoles = blackHoles;
        this.repulsors = repulsors;
        this.wormholes = wormholes;
        this.minTurn = minTurn;
        this.previewSteps = previewSteps;
    }
}
