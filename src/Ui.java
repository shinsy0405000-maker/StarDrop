import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 색과 글꼴, 글자 그리기 도우미 (웹 버전의 CSS 값과 같은 숫자).
 *
 * 글꼴은 웹과 같은 것을 src/fonts 폴더에 넣어 두고 읽는다 (셋 다 무료 글꼴, SIL Open Font License).
 *   제목:     Black Han Sans
 *   본문:     IBM Plex Sans KR (보통 400 / 중간 500 / 굵게 700)
 *   숫자·코드: IBM Plex Mono
 * 글꼴 파일을 못 찾으면 자바 기본 글꼴로 그린다.
 *
 * 일본어·중국어는 위 글꼴에 가나와 한자가 없어서, 컴퓨터에 깔린 글꼴을 골라 쓴다
 * (윈도우: Yu Gothic, Microsoft YaHei 등). 언어를 바꾸면 languageChanged() 로 글꼴을 다시 고른다.
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

    // ------------------------------------------------------------ 일본어·중국어 글꼴 (컴퓨터에 깔린 것 중 앞에서부터)
    private static final String[] JA_FAMILIES = {"Yu Gothic UI", "Yu Gothic", "Meiryo UI", "Meiryo", "MS Gothic",
        "Hiragino Sans", "Hiragino Kaku Gothic ProN", "Noto Sans CJK JP", "Noto Sans JP"};
    private static final String[] ZH_FAMILIES = {"Microsoft YaHei UI", "Microsoft YaHei", "DengXian", "SimHei",
        "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC"};
    private static Set<String> installed;

    /** 이 언어를 그릴 글꼴 이름. 한국어·영어는 null (들고 다니는 IBM Plex Sans KR 을 쓴다) */
    static String familyFor(Lang.Code code) {
        if (code != Lang.Code.JA && code != Lang.Code.ZH) return null;
        if (installed == null) {
            installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ENGLISH));
        }
        for (String name : code == Lang.Code.JA ? JA_FAMILIES : ZH_FAMILIES) if (installed.contains(name)) return name;
        return Font.SANS_SERIF;                 // 자바 기본 글꼴 (윈도우에서는 여러 나라 글꼴을 이어 붙여 둔 글꼴)
    }

    /** 이 언어의 본문 글꼴 (언어 고르는 창에서 각 언어 이름을 그 언어 글꼴로 쓸 때) */
    static Font fontFor(Lang.Code code, float size, int style) {
        String family = familyFor(code);
        if (family == null) return font(size, style, false, 0);
        return new Font(family, style == Font.BOLD ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
    }

    /** 언어를 바꿨을 때: 만들어 둔 글꼴을 버리고 새 언어에 맞게 다시 만든다 */
    static void languageChanged() {
        FONTS.clear();
        FALLBACK.clear();
    }

    /** 크기, 굵기(PLAIN / MEDIUM / BOLD), 고정폭 여부, 자간(글자 크기 대비 비율, CSS 의 letter-spacing em)으로 글꼴을 만든다 */
    static Font font(float size, int style, boolean mono, float tracking) {
        String cjk = mono ? null : familyFor(Lang.current());
        Font base = mono ? MONO : style == Font.BOLD ? BOLD : style == MEDIUM ? MEDIUM_FONT : REGULAR;
        if (cjk != null) base = new Font(cjk, style == Font.BOLD ? Font.BOLD : Font.PLAIN, 1);
        if (base == null) base = new Font(mono ? Font.MONOSPACED : Font.SANS_SERIF, style == Font.BOLD ? Font.BOLD : Font.PLAIN, 1);
        return make("body/" + mono + "/" + style, base, size, tracking);
    }

    /** 제목용 굵은 글꼴 (Black Han Sans, 일본어·중국어는 그 언어 글꼴의 굵은 모양) */
    static Font display(float size, float tracking) {
        String cjk = familyFor(Lang.current());
        Font base = cjk != null ? new Font(cjk, Font.BOLD, 1) : DISPLAY != null ? DISPLAY : new Font(Font.SANS_SERIF, Font.BOLD, 1);
        return make("display", base, size, tracking);
    }

    private static Font make(String kind, Font base, float size, float tracking) {
        String key = Lang.current() + "/" + kind + "/" + size + "/" + tracking;
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
            String cjk = familyFor(Lang.current());
            Font base = cjk != null ? new Font(cjk, Font.PLAIN, 1)
                    : REGULAR != null && k != REGULAR ? REGULAR : new Font(Font.SANS_SERIF, Font.PLAIN, 1);
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

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** 두 색을 t(0~1) 만큼 섞는다 (투명도 포함) */
    static Color mix(Color a, Color b, double t) {
        t = clamp(t, 0, 1);
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
}
