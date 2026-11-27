import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 유료 스킨 코드 확인.
 *
 * 코드 = 개발자가 "STARDROP|스킨 id|기기 번호" 라는 글에 서명(Ed25519 전자 서명)한 것.
 *   - 서명은 개발자만 가진 비밀 열쇠(개인키)로만 만들 수 있고,
 *   - 게임에는 확인용 열쇠(공개키)만 들어 있어서, 코드가 진짜인지 확인만 할 수 있다.
 *   → 이 소스 코드가 GitHub에 공개돼 있어도 코드를 지어낼 수 없다.
 * 코드에 기기 번호가 들어가므로, 받은 코드는 그 컴퓨터에서만 열린다 (다른 사람에게 줘도 소용없음).
 *
 * 코드는 개발자용 "코드 발급기"(게임 저장소에는 없음)로 만든다.
 */
final class SkinCode {
    private SkinCode() { }

    /** 확인용 열쇠 (공개키). 비밀 열쇠는 개발자만 가지고 있다 */
    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEA6GNVpEGN5ootMrHx6eva5F6jdlE4nvsbWyYNYOu+JX8=";

    /** 기기 번호에 쓰는 글자: 헷갈리는 0, O, 1, I, L 은 뺐다 */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** 서명할 글 */
    static String message(String skinId, String device) {
        return "STARDROP|" + skinId + "|" + normalizeDevice(device);
    }

    /** 이 코드가 이 기기에서 이 스킨을 여는 진짜 코드인가 */
    static boolean verify(String skinId, String device, String code) {
        try {
            byte[] sig = Base64.getUrlDecoder().decode(code.replaceAll("[^A-Za-z0-9_-]", ""));
            if (sig.length != 64) return false;
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(message(skinId, device).getBytes(StandardCharsets.UTF_8));
            return s.verify(sig);
        } catch (Exception e) {
            return false;       // 잘못된 글자, 길이 등
        }
    }

    /** 새 기기 번호: 글자 10개를 "ABCDE-FGHJK" 처럼 */
    static String newDevice() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        return format(sb.toString());
    }

    /** 대문자로 바꾸고 글자·숫자만 남긴다 ("abcde fghjk" → "ABCDEFGHJK") */
    static String normalizeDevice(String device) {
        return device.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /** "ABCDEFGHJK" → "ABCDE-FGHJK" */
    static String format(String device) {
        String d = normalizeDevice(device);
        return d.length() == 10 ? d.substring(0, 5) + "-" + d.substring(5) : d;
    }
}
