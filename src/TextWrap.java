import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 글을 정해진 폭에 맞춰 여러 줄로 나누기 (브라우저의 줄 바꿈 규칙을 흉내).
 *   한국어: 띄어쓰기와 한글 글자 사이에서 바꿀 수 있다 (웹 버전과 같게)
 *   일본어·중국어: 글자마다 바꿀 수 있지만 。、 나 작은 가나가 줄 맨 앞에 오지 않게 한다
 *   영어: 띄어쓰기에서만 바꾼다
 */
final class TextWrap {
    private TextWrap() { }

    /**
     * 폭 maxW 안에 들어가도록 글을 여러 줄로 나눈다.
     * 브라우저처럼 띄어쓰기뿐 아니라 한글·가나·한자 글자 사이에서도 줄을 바꿀 수 있다 (마침표 앞은 제외).
     */
    static List<String> lines(Graphics2D g, String s, Font f, double maxW) {
        List<String> lines = wrapOnce(g, s, f, maxW);
        // 브라우저의 text-wrap: pretty 처럼, 마지막 줄에 한 덩어리(한 글자, 한 낱말)만 남으면 폭을 조금씩 줄여 다시 나눈다
        for (int i = 1; i <= 40 && lines.size() >= 2 && pieces(lines.get(lines.size() - 1)) == 1; i++) {
            List<String> retry = wrapOnce(g, s, f, maxW * (1 - 0.005 * i));
            if (retry.size() > lines.size()) break;
            lines = retry;
        }
        return lines;
    }

    private static List<String> wrapOnce(Graphics2D g, String s, Font f, double maxW) {
        List<String> lines = new ArrayList<>();
        int lineStart = 0, lastBreak = -1;
        for (int i = 1; i <= s.length(); i++) {
            if (i < s.length() && !canBreak(s, i)) continue;
            String piece = s.substring(lineStart, i).stripTrailing();
            if (Ui.width(g, piece, f) > maxW && lastBreak > lineStart) {
                lines.add(s.substring(lineStart, lastBreak).stripTrailing());
                lineStart = lastBreak;
                while (lineStart < s.length() && s.charAt(lineStart) == ' ') lineStart++;
            }
            lastBreak = i;
        }
        if (lineStart < s.length()) lines.add(s.substring(lineStart).stripTrailing());
        return lines;
    }

    /** 줄을 바꿀 수 있는 덩어리가 몇 개인가 */
    private static int pieces(String line) {
        int n = 1;
        for (int i = 1; i < line.length(); i++) if (canBreak(line, i)) n++;
        return n;
    }

    /** s 의 i 번째 글자 앞에서 줄을 바꿀 수 있는가 */
    private static boolean canBreak(String s, int i) {
        char before = s.charAt(i - 1), here = s.charAt(i);
        if (before == ' ') return here != ' ';
        if (here == ' ' || ".,!?)·%".indexOf(here) >= 0) return false;
        if (NO_BREAK_BEFORE.indexOf(here) >= 0 || NO_BREAK_AFTER.indexOf(before) >= 0) return false;
        return (hangul(before) && hangul(here)) || ideographic(before) || ideographic(here);
    }

    /** 일본어·중국어에서 줄 맨 앞에 오면 안 되는 글자 (마침표, 닫는 괄호, 작은 가나 등) */
    private static final String NO_BREAK_BEFORE = "。、，．！？：；）」』】〕・ー～々ゝゞぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ%";
    /** 줄 맨 끝에 오면 안 되는 글자 (여는 괄호) */
    private static final String NO_BREAK_AFTER = "（「『【〔";

    private static boolean hangul(char c) {
        return c >= 0xAC00 && c <= 0xD7A3;
    }

    /** 가나, 한자, 전각 문장부호: 앞뒤 어디서나 줄을 바꿀 수 있는 글자 (한글은 한글끼리만, 웹 버전과 같게) */
    private static boolean ideographic(char c) {
        return (c >= 0x3000 && c <= 0x30FF) || (c >= 0x3400 && c <= 0x9FFF) || (c >= 0xFF00 && c <= 0xFFEF);
    }
}
