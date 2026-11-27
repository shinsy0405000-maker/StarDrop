/**
 * 진행 기록 저장: 구역마다 얻은 별 수를 "0,4,2,..." 처럼 한 줄로 이어서 컴퓨터 설정 저장소(Preferences)에 둔다.
 * 값: 0 = 아직, 1~4 = 별 0~3개로 배달함
 */
final class Progress {
    private Progress() { }

    /** 저장해 둔 기록을 progress 에 읽어 온다 */
    static void load(int[] progress) {
        try {
            // 웹과 같은 구역으로 바뀌면서 기록 이름을 progress2 로 새로 쓴다 (예전 5구역 기록과 섞이지 않게)
            String[] parts = java.util.prefs.Preferences.userRoot().node("stardrop").get("progress2", "").split(",");
            for (int i = 0; i < parts.length && i < progress.length; i++) progress[i] = Integer.parseInt(parts[i].trim());
        } catch (Exception e) {
            // 기록이 없거나 읽을 수 없으면 처음부터
        }
    }

    /** progress 를 저장한다 */
    static void save(int[] progress) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < progress.length; i++) sb.append(i == 0 ? "" : ",").append(progress[i]);
        try {
            java.util.prefs.Preferences.userRoot().node("stardrop").put("progress2", sb.toString());
        } catch (Exception e) {
            // 저장하지 못해도 게임은 계속된다
        }
    }
}
