import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.List;

/**
 * 처음 화면을 그리는 층 (웹의 #title): 도장, 큰 제목과 빛 번짐, 설명, 메뉴 버튼, 오른쪽 위 버튼, 조작 안내.
 * 움직이지 않는 화면이라 ScreenPainter 가 한 장으로 그려 두고(titleLayer) 다시 쓴다.
 */
abstract class TitlePainter extends HudPainter {
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------ 처음 화면 (웹의 #title)
    protected void drawTitle(Graphics2D g, int w, int h, List<Widget> out) {
        // 왼쪽을 어둡게 덮어 글자가 잘 보이게 (CSS: linear-gradient(100deg, ...))
        double ang = Math.toRadians(100), dx = Math.sin(ang), dy = -Math.cos(ang);
        double half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2;       // CSS 그라데이션 선의 절반 길이
        g.setPaint(new java.awt.LinearGradientPaint((float) (w / 2.0 - dx * half), (float) (h / 2.0 - dy * half),
                (float) (w / 2.0 + dx * half), (float) (h / 2.0 + dy * half), new float[]{0f, 0.34f, 0.68f, 1f},
                new Color[]{new Color(7, 8, 24, 240), new Color(7, 8, 24, 184), new Color(7, 8, 24, 0), new Color(7, 8, 24, 0)}));
        g.fillRect(0, 0, w, h);

        double padX = Ui.clamp(w * 0.05, 16, 72), padY = Ui.clamp(h * 0.06, 20, 64);
        double h1 = Ui.clamp(w * 0.13, 64, 168), ledeSize = Ui.clamp(w * 0.015, 15, 18);
        Font ledeFont = Ui.font((float) ledeSize, Font.PLAIN, false, 0);
        double ledeMax = Math.min(40 * Ui.width(g, "0", ledeFont), w - padX * 2);
        List<String> lede = TextWrap.lines(g, Lang.t("엔진은 1.2초치뿐입니다. 행성의 중력을 빌려 소포를 휘돌려 보내세요. "
                + "지구에서 태양까지 9단계, 144개 구역은 전부 자동 풀이기가 검증한 항로가 있고, 그 뒤로는 무한히 새 구역이 만들어집니다."), ledeFont, ledeMax);

        // 아래에서부터 쌓아 올린다 (CSS 의 justify-content: flex-end)
        Font keyFont = Ui.font(12, Font.PLAIN, false, 0), kbdFont = Ui.font(12, Font.PLAIN, true, 0);
        double keysH = 12 * 1.8 + 1, keysTop = h - padY - keysH;
        double menuH = 12 + Ui.lineHeight(g, Ui.font(16, Ui.MEDIUM, false, 0)) + 12 + 2, menuTop = keysTop - 22 - menuH;
        double ledeLine = ledeSize * 1.65, ledeTop = menuTop - 26 - lede.size() * ledeLine;
        Font stampFont = Ui.font(12, Font.PLAIN, true, 0.22f);
        double stampH = 5 + 17 + 5 + 2;
        // 설명이 길어서(영어 등) 위쪽이 화면 밖으로 나가면 제목 글자를 그만큼 줄인다
        h1 = Math.max(40, Math.min(h1, (ledeTop - 20 - 18 - stampH - padY) / (0.92 * 2)));
        double h1Line = h1 * 0.92, h1Top = ledeTop - 20 - h1Line * 2;
        double stampTop = h1Top - 18 - stampH;

        // 도장 (2도 기울임, 점선 테두리)
        String stamp = Lang.t("취급주의 · 블랙홀 근처 배송");
        double stampW = Ui.width(g, stamp, stampFont) + 20 + 2;
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-2), padX + stampW / 2, stampTop + stampH / 2);
        g.setColor(Ui.AMBER);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(padX + 0.5, stampTop + 0.5, stampW - 1, stampH - 1));
        Ui.text(g, stamp, padX + 11, Ui.baseline(g, stampFont, stampTop + 6, 17), stampFont, Ui.AMBER);
        g.setTransform(old);

        // 제목: 뒤에 주황빛 번짐(text-shadow)을 깐다
        Font titleFont = Ui.display((float) h1, -0.01f);
        double line1 = Ui.baseline(g, titleFont, h1Top, h1Line), line2 = line1 + h1Line;
        drawTitleGlow(g, padX, line1, line2, titleFont, h1);
        Ui.text(g, Lang.t("별똥"), padX, line1, titleFont, Ui.AMBER);
        Ui.text(g, Lang.t("택배"), padX, line2, titleFont, Ui.INK);
        for (int i = 0; i < lede.size(); i++) Ui.text(g, lede.get(i), padX, Ui.baseline(g, ledeFont, ledeTop + i * ledeLine, ledeLine), ledeFont, Ui.SOFT_INK);

        // 메뉴 버튼
        String[] labels = {Lang.t("배달 시작"), Lang.t("구역 목록"), Lang.t("무한 배달"), Lang.t("스킨")};
        Runnable[] actions = {() -> startCampaign(firstUndone()), this::showLevels, this::startEndless, this::openSkins};
        double bx = padX;
        for (int i = 0; i < labels.length; i++) {
            double bw = Button.widthFor(g, labels[i], null, 16, i == 0 ? Font.BOLD : Ui.MEDIUM, 22);
            out.add(new Button(new Rectangle2D.Double(bx, menuTop, bw, menuH), labels[i], null, 16, Ui.MEDIUM, i == 0, false, new Color(14, 15, 38, 217), actions[i]));
            bx += bw + 10;
        }

        // 오른쪽 위: 언어, 후원하기, 버그 제보 (웹에는 없는 버튼. 도구 버튼과 같은 모양)
        // 언어 버튼에는 지금 언어를 못 읽는 사람도 찾을 수 있게 늘 "Language" 를 붙인다
        double toolH = 7 + Ui.lineHeight(g, Ui.font(13, Font.PLAIN, false, 0)) + 7 + 2;
        String langLabel = Lang.current() == Lang.Code.EN ? "Language" : Lang.t("언어") + " · Language";
        String donate = Lang.t("후원하기"), bug = Lang.t("버그 제보");
        double rw = Button.widthFor(g, bug, "F1", 13, Font.PLAIN, 12), dw = Button.widthFor(g, donate, null, 13, Font.PLAIN, 12);
        double lw = Button.widthFor(g, langLabel, null, 13, Font.PLAIN, 12);
        out.add(new Button(new Rectangle2D.Double(w - 16 - rw - 6 - dw - 6 - lw, 14, lw, toolH), langLabel, null, 13, false, false, this::openLanguage));
        out.add(new Button(new Rectangle2D.Double(w - 16 - rw - 6 - dw, 14, dw, toolH), donate, null, 13, false, false, this::openDonate));
        out.add(new Button(new Rectangle2D.Double(w - 16 - rw, 14, rw, toolH), bug, "F1", 13, false, false, this::openBugReport));

        // 조작 안내 (키 모양 상자)
        String[][] keys = {{Lang.t("마우스"), Lang.t("조준")}, {Lang.t("클릭"), Lang.t("발사")}, {"Space", Lang.t("추진")}, {"←→↑↓", Lang.t("미세 조정 (Shift로 더 세밀하게)")}};
        double kx = padX, base = Ui.baseline(g, keyFont, keysTop, keysH), space = Ui.width(g, " ", keyFont);
        g.setStroke(new BasicStroke(1f));
        for (String[] k : keys) {
            double kw = Ui.width(g, k[0], kbdFont) + 12 + 2;
            g.setColor(Ui.LINE);
            g.draw(new Rectangle2D.Double(kx + 0.5, keysTop + 1.5, kw - 1, 18));
            Ui.text(g, k[0], kx + 7, base, kbdFont, Ui.INK);
            kx += kw + 3;
            Ui.text(g, k[1], kx, base, keyFont, Ui.DIM);
            kx += Ui.width(g, k[1], keyFont) + space;
        }
    }

    /**
     * 제목 글자의 빛 번짐 (CSS: text-shadow 0 0 60px rgba(255,170,60,.35)).
     * 글자를 작게 그려서 흐리게(가우스 블러) 만든 뒤 크게 늘려 붙인다. 처음 화면 그림을 만들 때 한 번만 불린다.
     */
    protected void drawTitleGlow(Graphics2D g, double x, double line1, double line2, Font f, double size) {
        int down = 6, pad = 120;                       // 1/6 크기로 그리고, 번짐이 퍼질 여백
        double textW = Math.max(Ui.width(g, Lang.t("별똥"), f), Ui.width(g, Lang.t("택배"), f));
        double top = line1 - size - pad, height = line2 - line1 + size * 1.3 + pad * 2;
        int iw = (int) ((textW + pad * 2) / down) + 2, ih = (int) (height / down) + 2;
        BufferedImage small = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB_PRE);   // 흐리게 할 때 색이 검게 섞이지 않도록 "투명도를 곱한" 형식
        Graphics2D tg = small.createGraphics();
        tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        tg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        tg.scale(1.0 / down, 1.0 / down);
        tg.translate(pad - x, -top);
        Ui.text(tg, Lang.t("별똥"), x, line1, f, new Color(255, 170, 60, 89));
        Ui.text(tg, Lang.t("택배"), x, line2, f, new Color(255, 170, 60, 89));
        tg.dispose();
        BufferedImage blurred = gaussian(gaussian(small, 30.0 / down, true), 30.0 / down, false);
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(blurred, (int) Math.round(x - pad), (int) Math.round(top), iw * down, ih * down, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    /** 가로 또는 세로 방향 가우스 블러 (sigma = 퍼지는 정도) */
    private static BufferedImage gaussian(BufferedImage src, double sigma, boolean horizontal) {
        int r = (int) Math.ceil(sigma * 3);
        float[] k = new float[r * 2 + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) sum += k[i + r] = (float) Math.exp(-i * i / (2 * sigma * sigma));
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        Kernel kernel = horizontal ? new Kernel(k.length, 1, k) : new Kernel(1, k.length, k);
        return new ConvolveOp(kernel, ConvolveOp.EDGE_ZERO_FILL, null).filter(src, null);
    }
}
