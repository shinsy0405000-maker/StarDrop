import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 유료 스킨 서버 (Supabase). 계정 없이 게임 안에서 코드를 요청하고 받는다.
 *
 *   요청:  skin_request(기기 번호, 스킨, 입금자명)  → 개발자가 볼 수 있게 "확인 중" 으로 저장
 *   확인:  skin_status(기기 번호)                   → 이 기기의 요청들과, 발급됐으면 코드
 *
 * 둘 다 Supabase 의 데이터베이스 함수(RPC)다. 게임에 들어 있는 열쇠(KEY)는 공개용이라
 * 이 두 함수만 부를 수 있고, 표를 직접 읽거나 고치지는 못한다 (supabase/skin_requests.sql 참고).
 * 코드 자체는 개발자 서명(SkinCode)으로 확인하므로 서버가 거짓 코드를 줘도 열리지 않는다.
 */
final class SkinServer {
    private SkinServer() { }

    /** Supabase 프로젝트 주소와 공개용 열쇠 (publishable / anon key). 비어 있으면 "준비 중" */
    static final String URL = "https://uprvuccixlnloqnwyakw.supabase.co";
    static final String KEY = "sb_publishable_zfJR61YWduStzWrDbShJkA_bdfPFt8b";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    static boolean ready() {
        return !URL.isBlank() && !KEY.isBlank();
    }

    /**
     * 코드 요청을 보낸다.
     * @return "sent" (새로 보냄), "already" (이미 확인 중), "issued" (이미 발급됨)
     */
    static String request(String device, Skin skin, String payer) throws IOException {
        String body = "{\"p_device\":" + json(device) + ",\"p_skin\":" + json(skin.id) + ",\"p_payer\":" + json(payer.strip()) + "}";
        String answer = call("skin_request", body);
        return answer.replace("\"", "").strip();
    }

    /** 이 기기의 요청들: {스킨 id, 상태(pending/issued/rejected), 코드(없으면 "")} */
    static List<String[]> status(String device) throws IOException {
        String answer = call("skin_status", "{\"p_device\":" + json(device) + "}");
        List<String[]> rows = new ArrayList<>();
        Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(answer);
        while (m.find()) {
            String o = m.group();
            rows.add(new String[]{field(o, "skin"), field(o, "status"), field(o, "code")});
        }
        return rows;
    }

    private static String call(String function, String body) throws IOException {
        if (!ready()) throw new IOException("not ready");
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(URL.replaceAll("/+$", "") + "/rest/v1/rpc/" + function))
                .timeout(Duration.ofSeconds(10))
                .header("apikey", KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (KEY.startsWith("eyJ")) b.header("Authorization", "Bearer " + KEY);   // 예전 방식 열쇠(JWT)는 이것도 필요
        try {
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " " + r.body());
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /** {"skin":"gold","code":null} 같은 글에서 칸 하나를 읽는다 (null 이면 "") */
    private static String field(String o, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(null|\"((?:[^\"\\\\]|\\\\.)*)\")").matcher(o);
        return m.find() && m.group(2) != null ? m.group(2) : "";
    }

    /** 글자를 JSON 문자열로 ("" 와 \ 와 줄바꿈 처리) */
    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }
}
