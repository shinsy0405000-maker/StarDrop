import java.util.prefs.Preferences;

/**
 * 스킨 저장: 지금 쓰는 스킨, 넣은 코드, 이 컴퓨터의 기기 번호.
 * 진행 상황(Progress)과 같은 곳(Preferences 의 "stardrop")에 저장한다.
 */
final class SkinStore {
    private SkinStore() { }

    private static final String SELECTED = "skin", CODES = "skinCodes", DEVICE = "deviceId";

    private static Preferences prefs() {
        return Preferences.userRoot().node("stardrop");
    }

    /** 저장해 둔 스킨. 없거나, 그 사이 잠겨 버렸으면 기본 스킨 */
    static Skin selected(int totalStars) {
        try {
            Skin s = Skin.byId(prefs().get(SELECTED, Skin.CLASSIC.id));
            if (s != null && unlocked(s, totalStars)) return s;
        } catch (Exception e) {
            // 저장소를 못 읽으면 기본 스킨
        }
        return Skin.CLASSIC;
    }

    static void select(Skin skin) {
        try {
            prefs().put(SELECTED, skin.id);
        } catch (Exception e) {
            // 저장 못 해도 이번 판에는 바뀐다
        }
    }

    /** 이 컴퓨터의 기기 번호. 처음 부를 때 만들어 저장한다 */
    static String device() {
        try {
            String d = prefs().get(DEVICE, "");
            if (SkinCode.normalizeDevice(d).length() == 10) return SkinCode.format(d);
            d = SkinCode.newDevice();
            prefs().put(DEVICE, d);
            return d;
        } catch (Exception e) {
            return "";
        }
    }

    /** 이 스킨을 쓸 수 있는가 */
    static boolean unlocked(Skin skin, int totalStars) {
        return switch (skin.unlock) {
            case FREE -> true;
            case STARS -> totalStars >= skin.need;
            case PAID -> SkinCode.verify(skin.id, device(), savedCode(skin));
            case ACHIEVEMENT -> false;     // 업적 시스템이 생기면 여기서 확인한다
        };
    }

    /**
     * 코드를 넣는다. 어느 유료 스킨의 코드인지 몰라도 되게, 유료 스킨마다 맞춰 본다.
     * @return 열린 스킨, 맞는 스킨이 없으면 null
     */
    static Skin redeem(String code) {
        String device = device();
        for (Skin s : Skin.values()) {
            if (s.unlock == Skin.Unlock.PAID && SkinCode.verify(s.id, device, code)) {
                saveCode(s, code.replaceAll("[^A-Za-z0-9_-]", ""));
                return s;
            }
        }
        return null;
    }

    // 저장 모양: "galaxy=코드;gold=코드"
    private static String savedCode(Skin skin) {
        try {
            for (String part : prefs().get(CODES, "").split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).equals(skin.id)) return part.substring(eq + 1);
            }
        } catch (Exception e) {
            // 없음
        }
        return "";
    }

    private static void saveCode(Skin skin, String code) {
        try {
            StringBuilder sb = new StringBuilder(skin.id + "=" + code);
            for (String part : prefs().get(CODES, "").split(";")) {
                if (!part.isBlank() && !part.startsWith(skin.id + "=")) sb.append(";").append(part);
            }
            prefs().put(CODES, sb.toString());
        } catch (Exception e) {
            // 저장 못 하면 다음에 다시 넣어야 한다
        }
    }
}
