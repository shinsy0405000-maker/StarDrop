import java.util.Locale;
import java.util.Map;

/**
 * 언어 설정: 한국어, English, 日本語, 中文 네 가지.
 *
 * 게임 안의 글은 한국어로 적어 두고, 화면에 그릴 때 Lang.t("배달 시작") 처럼 감싸면
 * 지금 고른 언어의 글로 바꿔 준다. 표에 없는 글은 한국어 그대로 나온다.
 * 숫자가 들어가는 글은 Lang.f("%d회", 3) 처럼 쓴다 (String.format 과 같다).
 *
 * 고른 언어는 진행 기록과 같은 곳(Preferences)에 저장되고, 처음 켤 때는 컴퓨터 언어를 따른다.
 * 번역 글 자체는 길어서 LangTable.java 에 따로 모아 두었다.
 */
final class Lang {
    private Lang() { }

    enum Code {
        KO("한국어", "ko"), EN("English", "en"), JA("日本語", "ja"), ZH("中文", "zh");

        final String nativeName;   // 그 언어로 쓴 언어 이름 (고르는 창에 보인다)
        final String tag;          // 저장할 때 쓰는 짧은 이름

        Code(String nativeName, String tag) {
            this.nativeName = nativeName;
            this.tag = tag;
        }
    }

    private static Code current = load();

    static Code current() {
        return current;
    }

    /** 일본어나 중국어처럼 한자·가나 글꼴이 필요한 언어인가 */
    static boolean cjk() {
        return current == Code.JA || current == Code.ZH;
    }

    static void set(Code code) {
        current = code;
        try {
            java.util.prefs.Preferences.userRoot().node("stardrop").put("lang", code.tag);
        } catch (Exception e) {
            // 저장하지 못해도 이번 실행에서는 바뀐다
        }
    }

    private static Code load() {
        String saved = null;
        try {
            saved = java.util.prefs.Preferences.userRoot().node("stardrop").get("lang", null);
        } catch (Exception e) {
            // 읽지 못하면 컴퓨터 언어를 따른다
        }
        String tag = saved != null ? saved : Locale.getDefault().getLanguage();
        for (Code c : Code.values()) if (c.tag.equals(tag)) return c;
        return saved == null && !tag.isEmpty() ? Code.EN : Code.KO;     // 네 언어가 아닌 컴퓨터는 영어로
    }

    /** 한국어 글 → 지금 언어의 글 */
    static String t(String ko) {
        if (ko == null || current == Code.KO) return ko;
        String[] row = TABLE.get(ko);
        return row == null ? ko : row[current.ordinal() - 1];
    }

    /** 숫자 등이 들어가는 글 (String.format 과 같다) */
    static String f(String ko, Object... args) {
        return String.format(t(ko), args);
    }

    /** 표에 이 글이 있는가 (확인용) */
    static boolean has(String ko) {
        return TABLE.containsKey(ko);
    }

    // ------------------------------------------------------------ 번역 표 (내용은 LangTable.java)
    private static final Map<String, String[]> TABLE = LangTable.build();
}
