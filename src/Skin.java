import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * 소포 스킨: 날아가는 소포(머리)와 꼬리의 모양·색.
 *
 * 스킨을 얻는 방법(Unlock)
 *   FREE         처음부터
 *   STARS        구역에서 모은 별이 need 개 이상이면
 *   PAID         need 원을 후원하고 받은 코드를 넣으면 (SkinCode 가 진짜 코드인지 확인)
 *   ACHIEVEMENT  업적 보상 — 업적 시스템이 생기면 쓸 자리 (아직 이 방법을 쓰는 스킨은 없다)
 *
 * 새 스킨을 넣으려면 아래 목록에 한 줄을 더하고, LangTable 에 이름 번역을 넣으면 된다.
 * id 는 저장과 코드에 쓰이므로 한 번 정하면 바꾸지 않는다.
 */
enum Skin {
    //      id         이름            얻는 방법        필요  머리 빛                        넓은 꼬리              가는 꼬리              모양           무지개  반짝이
    CLASSIC("classic", "기본 소포",    Unlock.FREE,      0,   new Color(255, 190, 90),  new int[]{255, 179, 71},  new int[]{255, 243, 208}, Shape.CIRCLE,  false, false),
    MINT   ("mint",    "민트 혜성",    Unlock.STARS,    30,   new Color(90, 235, 190),  new int[]{70, 225, 175},  new int[]{215, 255, 240}, Shape.CIRCLE,  false, false),
    BLOSSOM("blossom", "벚꽃 유성",    Unlock.STARS,    90,   new Color(255, 130, 180), new int[]{255, 115, 165}, new int[]{255, 225, 238}, Shape.STAR,    false, true),
    ICE    ("ice",     "얼음 혜성",    Unlock.STARS,   180,   new Color(120, 200, 255), new int[]{90, 170, 255},  new int[]{225, 246, 255}, Shape.DIAMOND, false, false),
    SOLAR  ("solar",   "태양 불꽃",    Unlock.STARS,   300,   new Color(255, 100, 45),  new int[]{255, 85, 35},   new int[]{255, 214, 100}, Shape.STAR,    false, false),
    GALAXY ("galaxy",  "은하수 소포",  Unlock.PAID,   5000,   new Color(170, 125, 255), new int[]{170, 125, 255}, new int[]{240, 230, 255}, Shape.STAR,    true,  true),
    GOLD   ("gold",    "황금 택배",    Unlock.PAID,   5000,   new Color(255, 212, 80),  new int[]{255, 196, 55},  new int[]{255, 250, 215}, Shape.BOX,     false, true);

    enum Unlock { FREE, STARS, PAID, ACHIEVEMENT }

    /** 머리 모양: 동그라미(기본), 마름모, 별, 상자 */
    enum Shape { CIRCLE, DIAMOND, STAR, BOX }

    final String id, name;
    final Unlock unlock;
    final int need;                // STARS: 별 개수, PAID: 원
    final Color glow;
    final int[] wide, thin;
    final Shape shape;
    final boolean rainbow, sparkle;

    Skin(String id, String name, Unlock unlock, int need, Color glow, int[] wide, int[] thin, Shape shape, boolean rainbow, boolean sparkle) {
        this.id = id;
        this.name = name;
        this.unlock = unlock;
        this.need = need;
        this.glow = glow;
        this.wide = wide;
        this.thin = thin;
        this.shape = shape;
        this.rainbow = rainbow;
        this.sparkle = sparkle;
    }

    static Skin byId(String id) {
        for (Skin s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    // ------------------------------------------------------------ 그리기 (게임 화면과 스킨 창 미리보기가 같이 쓴다)
    /**
     * 소포 머리. 기본 스킨은 예전과 똑같이 그린다.
     * @param glowR 바깥 빛 반지름, coreGlowR 안쪽 흰 빛 반지름, coreR 가운데 점 반지름, alpha 바깥 빛 진하기
     * @param angle 날아가는 방향 (상자·마름모가 그쪽을 본다), time 별 모양이 도는 데 쓰는 시간
     */
    void drawHead(Graphics2D g, double x, double y, double glowR, double coreGlowR, double coreR, int alpha, double angle, double time) {
        Paints.glow(g, x, y, glowR, new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), alpha));
        Paints.glow(g, x, y, coreGlowR, Color.WHITE);
        g.setColor(Color.WHITE);
        switch (shape) {
            case CIRCLE -> Paints.circle(g, x, y, coreR);
            case DIAMOND -> g.fill(polygon(x, y, angle, new double[]{coreR * 3.4, coreR * 1.6, coreR * 2.2, coreR * 1.6}, 4));
            case STAR -> g.fill(star(x, y, coreR * 3.2, coreR * 1.35, time * 2.5));
            case BOX -> {
                g.fill(polygon(x, y, angle + Math.PI / 4, new double[]{coreR * 2.6, coreR * 2.6, coreR * 2.6, coreR * 2.6}, 4));
                // 상자 끈: 가운데 열십자
                g.setColor(new Color(wide[0], wide[1], wide[2]));
                g.setStroke(new BasicStroke(1.3f));
                double c = Math.cos(angle), s = Math.sin(angle), r = coreR * 1.8;
                g.draw(new Line2D.Double(x - c * r, y - s * r, x + c * r, y + s * r));
                g.draw(new Line2D.Double(x + s * r, y - c * r, x - s * r, y + c * r));
            }
        }
    }

    /** 꼬리: 넓은 줄기 위에 가는 흰 줄기를 겹쳐서 혜성처럼. trail 의 null 은 웜홀로 끊긴 곳 */
    void drawTail(Graphics2D g, List<double[]> trail, double time) {
        drawStroke(g, trail, 80, 6.5, wide, 0.85, rainbow, time);
        drawStroke(g, trail, 30, 3, thin, 0.9, false, time);
        if (sparkle) drawSparkles(g, trail, time);
    }

    private static void drawStroke(Graphics2D g, List<double[]> trail, int length, double width, int[] rgb, double alpha, boolean rainbow, double time) {
        int n = trail.size(), from = Math.max(1, n - length);
        for (int i = from; i < n; i++) {
            double[] a = trail.get(i - 1), b = trail.get(i);
            if (a == null || b == null) continue;
            double t = (i - from) / (double) (n - from);
            int[] c = rgb;
            if (rainbow) {      // 자취를 따라 색이 돌고, 시간이 지나면 흘러간다
                int v = Color.HSBtoRGB((float) ((i * 0.012 - time * 0.25) % 1 + 1) % 1, 0.55f, 1f);
                c = new int[]{(v >> 16) & 255, (v >> 8) & 255, v & 255};
            }
            g.setColor(new Color(c[0], c[1], c[2], (int) (255 * alpha * t * t)));
            g.setStroke(new BasicStroke((float) (0.6 + width * t), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
        }
    }

    /** 반짝이: 자취의 7칸마다 작은 열십자 빛이 깜빡인다 (자취에 붙어 있어서 흘러가지 않는다) */
    private void drawSparkles(Graphics2D g, List<double[]> trail, double time) {
        int n = trail.size(), from = Math.max(0, n - 70);
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = from; i < n; i++) {
            if (i % 7 != 0 || trail.get(i) == null) continue;
            double[] p = trail.get(i);
            double fade = (i - from) / 70.0, blink = 0.5 + 0.5 * Math.sin(time * 9 + i * 1.7);
            int a = (int) (230 * fade * blink);
            if (a <= 4) continue;
            double r = 1.5 + 2.5 * blink;
            g.setColor(new Color(thin[0], thin[1], thin[2], a));
            g.draw(new Line2D.Double(p[0] - r, p[1], p[0] + r, p[1]));
            g.draw(new Line2D.Double(p[0], p[1] - r, p[0], p[1] + r));
        }
    }

    /** 가운데 (x, y) 에서 angle 방향부터 시계 방향으로 꼭짓점 거리 rs 를 잇는 다각형 */
    private static Path2D polygon(double x, double y, double angle, double[] rs, int corners) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < corners; i++) {
            double a = angle + i * Math.PI * 2 / corners;
            if (i == 0) p.moveTo(x + Math.cos(a) * rs[i], y + Math.sin(a) * rs[i]);
            else p.lineTo(x + Math.cos(a) * rs[i], y + Math.sin(a) * rs[i]);
        }
        p.closePath();
        return p;
    }

    /** 꼭짓점 5개짜리 별 */
    private static Path2D star(double x, double y, double outer, double inner, double angle) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = angle - Math.PI / 2 + i * Math.PI / 5, r = i % 2 == 0 ? outer : inner;
            if (i == 0) p.moveTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
            else p.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
        }
        p.closePath();
        return p;
    }
}
