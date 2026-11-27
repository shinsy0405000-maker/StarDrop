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

    /** 서버에서 받아 온 이 기기의 요청 상태 */
    static final class Sync {
        final java.util.List<Skin> unlocked = new java.util.ArrayList<>();       // 이번에 새로 열린 스킨
        final java.util.Set<Skin> pending = java.util.EnumSet.noneOf(Skin.class);  // 개발자가 확인 중
        final java.util.Set<Skin> rejected = java.util.EnumSet.noneOf(Skin.class); // 송금을 찾지 못해 거절됨
    }

    /**
     * 서버(Supabase)에 이 기기 앞으로 발급된 코드가 있는지 보고, 있으면 넣는다. 인터넷이 필요해서 화면 스레드에서 부르지 않는다.
     * @throws java.io.IOException 인터넷이 안 되거나 서버가 준비 안 됐을 때
     */
    static Sync sync() throws java.io.IOException {
        Sync result = new Sync();
        String device = device();
        for (String[] row : SkinServer.status(device)) {
            Skin s = Skin.byId(row[0]);
            if (s == null || s.unlock != Skin.Unlock.PAID) continue;
            boolean had = SkinCode.verify(s.id, device, savedCode(s));
            if ("issued".equals(row[1]) && !had && redeem(row[2]) == s) result.unlocked.add(s);
            else if ("pending".equals(row[1]) && !had) result.pending.add(s);
            else if ("rejected".equals(row[1]) && !had) result.rejected.add(s);
        }
        result.rejected.removeAll(result.pending);       // 거절된 뒤 다시 요청했으면 "확인 중"
        return result;
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
