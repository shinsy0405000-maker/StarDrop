import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 색과 글꼴, 글자 그리기 도우미 (웹 버전의 CSS 값과 같은 숫자) */
final class Ui {
    private Ui() { }

    static final Color VOID = new Color(7, 8, 24), PANEL = new Color(14, 15, 38), PANEL_SEE = new Color(14, 15, 38, 209);
    static final Color INK = new Color(236, 238, 255), DIM = new Color(143, 148, 189), LINE = new Color(52, 56, 106);
    static final Color AMBER = new Color(255, 198, 92), AMBER_LIGHT = new Color(255, 217, 140), MINT = new Color(111, 242, 196);
    static final Color DANGER = new Color(255, 125, 107), DARK_INK = new Color(27, 19, 4), SOFT_INK = new Color(201, 205, 240);
    static final BasicStroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f);

    private static final Map<String, Font> FONTS = new HashMap<>();

    /** 크기, 굵기, 고정폭 여부, 자간(글자 크기 대비 비율)으로 글꼴을 만든다 */
    static Font font(float size, int style, boolean mono, float tracking) {
        String key = size + "/" + style + "/" + mono + "/" + tracking;
        Font f = FONTS.get(key);
        if (f == null) {
            f = new Font(mono ? Font.MONOSPACED : Font.SANS_SERIF, style, 1).deriveFont(size);
            if (tracking != 0) {
                Map<java.awt.font.TextAttribute, Object> attr = new HashMap<>();
                attr.put(java.awt.font.TextAttribute.TRACKING, tracking);
                f = f.deriveFont(attr);
            }
            FONTS.put(key, f);
        }
        return f;
    }

    static void text(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) y);
    }

    static double width(Graphics2D g, String s, Font f) {
        return f.getStringBounds(s, g.getFontRenderContext()).getWidth();
    }

    /** 사각형 세로 가운데에 글자를 놓을 때의 기준선 위치 */
    static double middle(Graphics2D g, Font f, Rectangle2D r) {
        java.awt.FontMetrics fm = g.getFontMetrics(f);
        return r.getCenterY() + (fm.getAscent() - fm.getDescent()) / 2.0;
    }

    /** 폭 maxW 안에 들어가도록 글을 여러 줄로 나눈다 */
    static List<String> wrap(Graphics2D g, String s, Font f, double maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (width(g, test, f) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
