import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.font.LineMetrics;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 색과 글꼴, 글자 그리기 도우미 (웹 버전의 CSS 값과 같은 숫자).
 *
 * 글꼴은 웹과 같은 것을 src/fonts 폴더에 넣어 두고 읽는다 (셋 다 무료 글꼴, SIL Open Font License).
 *   제목:     Black Han Sans
 *   본문:     IBM Plex Sans KR (보통 400 / 중간 500 / 굵게 700)
 *   숫자·코드: IBM Plex Mono
 * 글꼴 파일을 못 찾으면 자바 기본 글꼴로 그린다.
 */
final class Ui {
    private Ui() { }

    static final Color VOID = new Color(7, 8, 24), PANEL = new Color(14, 15, 38), PANEL_SEE = new Color(14, 15, 38, 209);
    static final Color INK = new Color(236, 238, 255), DIM = new Color(143, 148, 189), LINE = new Color(52, 56, 106);
    static final Color AMBER = new Color(255, 198, 92), AMBER_LIGHT = new Color(255, 217, 140), MINT = new Color(111, 242, 196);
    static final Color DANGER = new Color(255, 125, 107), DARK_INK = new Color(27, 19, 4), SOFT_INK = new Color(201, 205, 240);
    static final BasicStroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f);

    /** 글자 굵기: Font.PLAIN(400), Font.BOLD(700) 에 더해 중간 굵기(500) */
    static final int MEDIUM = 4;

    private static final Font DISPLAY = load("BlackHanSans-Regular.ttf");
    private static final Font REGULAR = load("IBMPlexSansKR-Regular.ttf");
    private static final Font MEDIUM_FONT = load("IBMPlexSansKR-Medium.ttf");
    private static final Font BOLD = load("IBMPlexSansKR-Bold.ttf");
    private static final Font MONO = load("IBMPlexMono-Medium.ttf");

    /** 실행 위치에 따라 글꼴 파일이 있는 곳이 달라서 몇 군데를 찾아본다 */
    private static Font load(String file) {
        try {
            InputStream in = Ui.class.getResourceAsStream("/fonts/" + file);       // IntelliJ: src 의 파일이 함께 복사된다
            if (in == null) {
                for (String dir : new String[]{"src/fonts/", "fonts/", "../src/fonts/"}) {
                    File f = new File(dir + file);
                    if (f.isFile()) {
                        in = new FileInputStream(f);
                        break;
                    }
                }
            }
            if (in == null) return null;
            try (InputStream stream = in) {
                return Font.createFont(Font.TRUETYPE_FONT, stream);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static final Map<String, Font> FONTS = new HashMap<>();
    private static final Map<Font, Font> FALLBACK = new HashMap<>();

    /** 크기, 굵기(PLAIN / MEDIUM / BOLD), 고정폭 여부, 자간(글자 크기 대비 비율, CSS 의 letter-spacing em)으로 글꼴을 만든다 */
    static Font font(float size, int style, boolean mono, float tracking) {
        Font base = mono ? MONO : style == Font.BOLD ? BOLD : style == MEDIUM ? MEDIUM_FONT : REGULAR;
        if (base == null) base = new Font(mono ? Font.MONOSPACED : Font.SANS_SERIF, style == Font.BOLD ? Font.BOLD : Font.PLAIN, 1);
        return make("body/" + mono + "/" + style, base, size, tracking);
    }

    /** 제목용 굵은 글꼴 (Black Han Sans) */
    static Font display(float size, float tracking) {
        return make("display", DISPLAY != null ? DISPLAY : new Font(Font.SANS_SERIF, Font.BOLD, 1), size, tracking);
    }

    private static Font make(String kind, Font base, float size, float tracking) {
        String key = kind + "/" + size + "/" + tracking;
        Font f = FONTS.get(key);
        if (f == null) {
            f = base.deriveFont(size);
            if (tracking != 0) {
                Map<TextAttribute, Object> attr = new HashMap<>();
                attr.put(TextAttribute.TRACKING, tracking);
                f = f.deriveFont(attr);
            }
            FONTS.put(key, f);
        }
        return f;
    }

    /** 이 글꼴에 없는 글자(고정폭 글꼴의 한글, 별 모양 등)를 대신 그릴 글꼴 — 브라우저처럼 */
    private static Font fallback(Font f) {
        return FALLBACK.computeIfAbsent(f, k -> {
            Font base = REGULAR != null && k != REGULAR ? REGULAR : new Font(Font.SANS_SERIF, Font.PLAIN, 1);
            Font fb = base.deriveFont(k.getSize2D());
            if (!fb.canDisplay('★')) fb = new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(k.getSize2D());
            Object tracking = k.getAttributes().get(TextAttribute.TRACKING);
            if (tracking != null) fb = fb.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
            return fb;
        });
    }

    /** 글을 "같은 글꼴로 그릴 수 있는 덩어리"로 나눈다 */
    private static List<Object[]> runs(String s, Font f) {
        List<Object[]> out = new ArrayList<>();
        if (f.canDisplayUpTo(s) == -1) {
            out.add(new Object[]{s, f});
            return out;
        }
        Font fb = fallback(f);
        int start = 0;
        boolean main = f.canDisplay(s.codePointAt(0));
        for (int i = 1; i <= s.length(); i++) {
            boolean here = i < s.length() && f.canDisplay(s.codePointAt(i));
            if (i == s.length() || here != main) {
                out.add(new Object[]{s.substring(start, i), main ? f : fb});
                start = i;
                main = here;
            }
        }
        return out;
    }

    static void text(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setColor(c);
        for (Object[] run : runs(s, f)) {
            String part = (String) run[0];
            Font rf = (Font) run[1];
            g.setFont(rf);
            g.drawString(part, (float) x, (float) y);
            x += rf.getStringBounds(part, g.getFontRenderContext()).getWidth();
        }
    }

    static double width(Graphics2D g, String s, Font f) {
        double w = 0;
        for (Object[] run : runs(s, f)) w += ((Font) run[1]).getStringBounds((String) run[0], g.getFontRenderContext()).getWidth();
        return w;
    }

    /** CSS 의 line-height: normal 높이 (글꼴마다 다르다) */
    static double lineHeight(Graphics2D g, Font f) {
        LineMetrics m = f.getLineMetrics("가", g.getFontRenderContext());
        return m.getAscent() + m.getDescent() + m.getLeading();
    }

    /** 높이 lineH 인 줄의 위쪽이 top 일 때 글자 기준선 위치 (CSS 처럼 남는 높이를 위아래로 나눈다) */
    static double baseline(Graphics2D g, Font f, double top, double lineH) {
        LineMetrics m = f.getLineMetrics("가", g.getFontRenderContext());
        return top + (lineH - m.getAscent() - m.getDescent()) / 2 + m.getAscent();
    }

    /** 사각형 세로 가운데에 글자를 놓을 때의 기준선 위치 */
    static double middle(Graphics2D g, Font f, Rectangle2D r) {
        return baseline(g, f, r.getY(), r.getHeight());
    }

    /**
     * 폭 maxW 안에 들어가도록 글을 여러 줄로 나눈다.
     * 브라우저처럼 띄어쓰기뿐 아니라 한글 글자 사이에서도 줄을 바꿀 수 있다 (마침표 앞은 제외).
     */
    static List<String> wrap(Graphics2D g, String s, Font f, double maxW) {
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
            if (width(g, piece, f) > maxW && lastBreak > lineStart) {
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
        return hangul(before) && hangul(here);
    }

    private static boolean hangul(char c) {
        return c >= 0xAC00 && c <= 0xD7A3;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
