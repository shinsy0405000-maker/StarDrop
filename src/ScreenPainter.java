import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.List;

/**
 * 화면 위 UI 를 그리는 층: HUD, 처음 화면, 구역 목록, 배달 완료 영수증. paintComponent 가 여기 있다.
 * 크기와 위치는 웹 버전의 CSS 숫자를 그대로 옮겼다 (padding, margin, 글자 크기, 줄 높이).
 */
abstract class ScreenPainter extends WorldPainter {
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------ 화면 그리기
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        int w = getWidth(), h = getHeight();
        AffineTransform base = g.getTransform();      // 화면 좌표 (HUD 와 버튼은 여기에 그린다)

        // 게임 세계(1600x900)를 창 가운데에 꽉 차게
        viewScale = Math.min(w / (double) Rules.W, h / (double) Rules.H);
        viewX = (w - Rules.W * viewScale) / 2;
        viewY = (h - Rules.H * viewScale) / 2;

        // 배경은 구역, 창 크기, 윈도우 배율이 바뀔 때만 새로 그린다
        double dev = ImageCache.deviceScale(g);
        ImageCache.drawScreenLayer(g, background.get(() -> makeBackground(g, w, h), level, w, h, dev));

        // 1층: 게임 세계와 HUD
        List<Widget> hud = new ArrayList<>(), overlay = new ArrayList<>();
        boolean covered = overlayOpen() && !(state == WIN && demo);
        if (level != null) {
            double sx = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0, sy = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0;
            g.translate(viewX + sx, viewY + sy);
            g.scale(viewScale, viewScale);
            drawWorld(g);
            g.setTransform(base);
            if (!demo && state != LOADING) drawHud(g, w, h, hud);
        }
        drawWidgets(g, hud, !covered);

        // 2층: 화면을 덮는 판 (웹처럼 HUD 버튼도 그 아래에 깔린다)
        if (titleOpen) {
            // 처음 화면은 움직이지 않으므로 통째로 한 장 그려 두고 붙이기만 한다 (버튼만 매번 그린다)
            BufferedImage layer = titleLayer.get(() -> {
                titleButtons = new ArrayList<>();
                return ImageCache.screenLayer(g, w, h, true, lg -> drawTitle(lg, w, h, titleButtons));
            }, w, h, dev);
            ImageCache.drawScreenLayer(g, layer);
            overlay.addAll(titleButtons);
        }
        if (levelsOpen) drawLevels(g, w, h, overlay);
        if (state == WIN && !demo) drawReceipt(g, w, h, overlay);
        drawWidgets(g, overlay, true);
        if (state == LOADING) drawBusy(g, w, h);
        if (showFps) Ui.text(g, "FPS " + fps, w - 90, h - 40, Ui.font(13, Font.BOLD, true, 0), Ui.MINT);
        widgets = covered ? overlay : hud;
    }

    /** 버튼들을 그린다. interactive 가 아니면(위에 다른 판이 덮였으면) 마우스를 올려도 반응하지 않는다 */
    private void drawWidgets(Graphics2D g, List<Widget> list, boolean interactive) {
        for (Widget wd : list) {
            wd.hover = interactive && wd.contains(mouseX, mouseY);
            wd.draw(g, anim, 0);
        }
    }

    // ------------------------------------------------------------ HUD (웹의 #hud, #readout, #fuel, #tip)
    protected void drawHud(Graphics2D g, int w, int h, List<Widget> out) {
        // 왼쪽 위: 운송장 (칸마다 작은 제목 + 값, 점선 칸막이)
        String[][] cells;
        if (endless) {
            cells = new String[][]{{Lang.t("운송장"), "No. " + endlessSeed, "mono"}, {Lang.t("구역"), Lang.t(level.getName()), ""},
                {Lang.t("별"), stars(starCount()), "star"}, {Lang.t("발사"), Lang.f("%d회", tries), "mono"}};
        } else {
            String no = String.format("No. %02d / %d", Campaign.stageOf(levelIndex) + 1, Campaign.STAGES);
            cells = new String[][]{{Lang.t("단계"), Campaign.chapterName(levelIndex), ""}, {Lang.t("운송장"), no, "mono"}, {Lang.t("구역"), Lang.t(level.getName()), ""},
                {Lang.t("별"), stars(starCount()), "star"}, {Lang.t("발사"), Lang.f("%d회", tries), "mono"}};
        }
        Font label = Ui.font(10, Font.PLAIN, false, 0.14f);
        double labelH = Ui.lineHeight(g, label), valueH = 0, x = 16, y = 14, waybillW = 2;
        double[] widths = new double[cells.length];
        for (int i = 0; i < cells.length; i++) {
            Font vf = valueFont(cells[i][2]);
            valueH = Math.max(valueH, Ui.lineHeight(g, vf));
            widths[i] = Math.max(Ui.width(g, cells[i][0], label), Ui.width(g, cells[i][1], vf)) + 24 + (i < cells.length - 1 ? 1 : 0);
            waybillW += widths[i];
        }
        double inner = 6 + labelH + 1 + valueH + 6, waybillH = inner + 2;
        g.setColor(Ui.PANEL_SEE);
        g.fill(new Rectangle2D.Double(x, y, waybillW, waybillH));
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(x + 0.5, y + 0.5, waybillW - 1, waybillH - 1));
        double cx = x + 1;
        for (int i = 0; i < cells.length; i++) {
            Font vf = valueFont(cells[i][2]);
            double vh = Ui.lineHeight(g, vf), top = y + 1 + (inner - (labelH + 1 + vh)) / 2;   // 칸 안에서 세로 가운데
            Ui.text(g, cells[i][0], cx + 12, Ui.baseline(g, label, top, labelH), label, Ui.DIM);
            Ui.text(g, cells[i][1], cx + 12, Ui.baseline(g, vf, top + labelH + 1, vh), vf, cells[i][2].equals("star") ? Ui.AMBER : Ui.INK);
            cx += widths[i];
            if (i < cells.length - 1) {                                 // 칸막이 점선
                g.setColor(Ui.LINE);
                g.setStroke(Ui.DASHED);
                g.draw(new Line2D.Double(cx - 0.5, y + 1, cx - 0.5, y + waybillH - 1));
            }
        }

        // 오른쪽 위: 버튼들 (운송장과 같은 높이. 자리가 모자라면 아래 줄 왼쪽으로)
        String[][] tools = {{Lang.t("다시 조준"), "R"}, {Lang.t("정답 방향 보기"), "H"}, {Lang.t(Sound.isMuted() ? "소리 켜기" : "소리 끄기"), null}, {Lang.t("구역 목록"), null}};
        Runnable[] actions = {this::retry, () -> showHint = !showHint, () -> Sound.setMuted(!Sound.isMuted()), this::showLevels};
        double toolsW = -6;
        double[] tw = new double[tools.length];
        for (int i = 0; i < tools.length; i++) {
            tw[i] = Button.widthFor(g, tools[i][0], tools[i][1], 13, Font.PLAIN, 12);
            toolsW += tw[i] + 6;
        }
        double tx = w - 16 - toolsW, ty = 14, th = waybillH;
        if (tx < 16 + waybillW + 8) {
            tx = 16;
            ty = 14 + waybillH + 8;
            th = 7 + Ui.lineHeight(g, Ui.font(13, Font.PLAIN, false, 0)) + 7 + 2;
        }
        for (int i = 0; i < tools.length; i++) {
            out.add(new Button(new Rectangle2D.Double(tx, ty, tw[i], th), tools[i][0], tools[i][1], 13, false, i == 1 && showHint, actions[i]));
            tx += tw[i] + 6;
        }

        // 왼쪽 아래: 조준 값
        Font mono12 = Ui.font(12, Font.PLAIN, true, 0);
        double deg = ((-Math.toDegrees(aimAngle)) % 360 + 360) % 360;
        Ui.text(g, Lang.f("각도 %.1f° · 세기 %d%%", deg, Math.round(aimPower * 100)), 16, Ui.baseline(g, mono12, h - 14 - 17, 17), mono12, Ui.DIM);

        // 오른쪽 아래: 추진 연료
        Font ff = Ui.font(11, Font.PLAIN, false, 0.12f);
        double boxTop = h - 14 - 17, barX = w - 16 - 120, barY = boxTop + (17 - 6) / 2.0;
        String fuelLabel = Lang.t("추진 연료");
        Ui.text(g, fuelLabel, barX - 8 - Ui.width(g, fuelLabel, ff), Ui.baseline(g, ff, boxTop, 17), ff, Ui.DIM);
        g.setColor(new Color(14, 15, 38, 204));
        g.fill(new Rectangle2D.Double(barX, barY, 120, 6));
        g.setColor(new Color(127, 220, 255));
        g.fill(new Rectangle2D.Double(barX + 1, barY + 1, 118 * Ui.clamp(fuel / Rules.FUEL, 0, 1), 4));
        g.setColor(Ui.LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(barX + 0.5, barY + 0.5, 119, 5));

        // 아래 가운데: 안내 문구 (폭은 글자 62개, 왼쪽에 노란 줄)
        if (tip != null && state == AIM) {
            Font f = Ui.font(14, Font.PLAIN, false, 0);
            double boxW = Math.min(62 * Ui.width(g, "0", f), w - 32);
            List<String> lines = Ui.wrap(g, Lang.t(tip), f, boxW - 3 - 28);
            double boxH = 20 + lines.size() * 22.4, bx = (w - boxW) / 2, by = h - 44 - boxH;
            g.setColor(new Color(14, 15, 38, 230));
            g.fill(new Rectangle2D.Double(bx, by, boxW, boxH));
            g.setColor(Ui.AMBER);
            g.fill(new Rectangle2D.Double(bx, by, 3, boxH));
            for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), bx + 17, Ui.baseline(g, f, by + 10 + i * 22.4, 22.4), f, Ui.INK);
        }
    }

    protected Font valueFont(String kind) {
        if (kind.equals("mono")) return Ui.font(14, Font.PLAIN, true, 0);
        if (kind.equals("star")) return Ui.font(14, Font.BOLD, false, 0.1f);
        return Ui.font(14, Font.BOLD, false, 0);
    }

    protected static String stars(int n) {
        return "★★★".substring(0, n) + "☆☆☆".substring(0, 3 - n);
    }

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
        List<String> lede = Ui.wrap(g, Lang.t("엔진은 1.2초치뿐입니다. 행성의 중력을 빌려 소포를 휘돌려 보내세요. "
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
        String[] labels = {Lang.t("배달 시작"), Lang.t("구역 목록"), Lang.t("무한 배달")};
        Runnable[] actions = {() -> startCampaign(firstUndone()), this::showLevels, this::startEndless};
        double bx = padX;
        for (int i = 0; i < 3; i++) {
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

    // ------------------------------------------------------------ 구역 목록 (웹의 #levels + 단계 탭)
    protected void drawLevels(Graphics2D g, int w, int h, List<Widget> out) {
        g.setColor(new Color(7, 8, 24, 237));
        g.fillRect(0, 0, w, h);
        double padX = Ui.clamp(w * 0.05, 16, 72), padY = Ui.clamp(h * 0.05, 20, 56);
        Font hf = Ui.display((float) Ui.clamp(w * 0.05, 32, 52), 0);
        double headH = Ui.lineHeight(g, hf), headBase = Ui.baseline(g, hf, padY, headH);
        Ui.text(g, Lang.t("배달 구역"), padX, headBase, hf, Ui.INK);
        int collected = 0;
        for (int p : progress) collected += Math.max(0, p - 1);
        Font tf = Ui.font(14, Font.PLAIN, true, 0);
        String total = Lang.f("모은 별 %d / %d", collected, progress.length * 3);
        Ui.text(g, total, w - padX - Ui.width(g, total, tf), headBase, tf, Ui.AMBER);

        // 단계 탭: 지구 → 화성 → ... → 태양. 앞 단계를 끝내야 열린다
        double tabY = padY + headH + 22, tabH = 8 + Ui.lineHeight(g, Ui.font(14, Font.BOLD, false, 0)) + 8 + 2, tx = padX;
        Font countFont = Ui.font(11, Font.PLAIN, true, 0);
        for (int c = 0; c < Campaign.CHAPTERS.length; c++) {
            final int chapter = c;
            int first = c * Campaign.STAGES, got = 0;
            for (int i = first; i < first + Campaign.STAGES; i++) got += Math.max(0, progress[i] - 1);
            boolean locked = first > 0 && progress[first - 1] == 0 && progress[first] == 0;
            String count = got + "/" + Campaign.STAGES * 3;
            String name = Lang.t(Campaign.CHAPTERS[c]);
            double bw = Button.widthFor(g, name, count, 14, Font.BOLD, 14);
            if (tx + bw > w - padX && tx > padX) {                 // 줄이 넘치면 다음 줄로
                tx = padX;
                tabY += tabH + 6;
            }
            Button tab = new Button(new Rectangle2D.Double(tx, tabY, bw, tabH), name, count, 14, Font.BOLD,
                    false, c == listChapter, Ui.PANEL, () -> listChapter = chapter);
            tab.enabled = !locked;
            out.add(tab);
            tx += bw + 6;
        }

        // 고른 단계의 구역 16개 (CSS: grid, 칸 최소 폭 168, 높이 108, 간격 10)
        double gridY = tabY + tabH + 16, avail = w - padX * 2;
        int cols = Math.max(1, (int) ((avail + 10) / (168 + 10)));
        double tileW = (avail - (cols - 1) * 10) / cols;
        for (int s = 0; s < Campaign.STAGES; s++) {
            final int index = listChapter * Campaign.STAGES + s;
            boolean locked = index > 0 && progress[index - 1] == 0 && progress[index] == 0;
            String status = locked ? Lang.t("잠김") : progress[index] > 0 ? stars(progress[index] - 1) : Lang.t("미배달");
            double x = padX + (s % cols) * (tileW + 10), y = gridY + (s / cols) * (108 + 10);
            out.add(new LevelTile(new Rectangle2D.Double(x, y, tileW, 108), String.format("%02d", s + 1),
                    Lang.t(Campaign.config(index).name), status, progress[index] > 0, locked, () -> startCampaign(index)));
        }
        int rows = (Campaign.STAGES + cols - 1) / cols;
        double by = gridY + rows * 118 - 10 + 22, bx = padX, bh = 10 + Ui.lineHeight(g, Ui.font(15, Ui.MEDIUM, false, 0)) + 10 + 2;
        String[] labels = {Lang.t("처음 화면"), Lang.t("무한 배달"), Lang.t("버그 제보"), Lang.t("후원하기")};
        Runnable[] actions = {this::showTitle, this::startEndless, this::openBugReport, this::openDonate};
        for (int i = 0; i < labels.length; i++) {
            double bw = Button.widthFor(g, labels[i], null, 15, Ui.MEDIUM, 16);
            out.add(new Button(new Rectangle2D.Double(bx, by, bw, bh), labels[i], null, 15, Ui.MEDIUM, false, false, new Color(14, 15, 38, 217), actions[i]));
            bx += bw + 8;
        }
    }

    // ------------------------------------------------------------ 배달 완료 영수증 (웹의 #win .receipt)
    protected void drawReceipt(Graphics2D g, int w, int h, List<Widget> out) {
        g.setColor(new Color(7, 8, 24, 140));
        g.fillRect(0, 0, w, h);
        Font noteFont = Ui.font(14, Font.PLAIN, false, 0), noFont = Ui.font(12, Font.PLAIN, true, 0.08f);
        Font dt = Ui.font(14, Font.PLAIN, false, 0), dd = Ui.font(14, Font.PLAIN, true, 0), starFont = Ui.font(54, Font.PLAIN, false, 0.08f);
        double cw = Math.min(440, w - 32), innerW = cw - 2 - 52;
        int n = starCount();
        String note = n == 3 ? Lang.t("별 세 개를 모두 지났습니다.") : Lang.f("별 %d개를 놓쳤습니다. 별은 세 개를 모두 지나는 궤도 위에 놓여 있습니다.", 3 - n);
        List<String> noteLines = Ui.wrap(g, note, noteFont, innerW);
        double rowH = Ui.lineHeight(g, dt), btnH = 10 + Ui.lineHeight(g, Ui.font(15, Ui.MEDIUM, false, 0)) + 10 + 2;
        double listH = 1 + 14 + rowH + 8 + rowH + 14 + 1;
        double ch = 1 + 28 + 17 + 18 + 54 + 6 + noteLines.size() * 22.4 + 18 + listH + 22 + btnH + 24 + 1;
        double cx = (w - cw) / 2, cy = (h - ch) / 2;
        g.setColor(Ui.PANEL);
        g.fill(new Rectangle2D.Double(cx, cy, cw, ch));
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(cx + 0.5, cy + 0.5, cw - 1, ch - 1));

        // 도장 (7도 기울임)
        Font stampFont = Ui.display(22, 0.06f);
        double sh = 2 + 4 + Ui.lineHeight(g, stampFont) + 4 + 2, sw = Ui.width(g, Lang.t("배달 완료"), stampFont) + 24 + 4;
        double sx = cx + cw - 1 - 18 - sw, sy = cy + 1 + 16;
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(7), sx + sw / 2, sy + sh / 2);
        g.setColor(Ui.MINT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Rectangle2D.Double(sx + 1, sy + 1, sw - 2, sh - 2));
        Ui.text(g, Lang.t("배달 완료"), sx + 14, Ui.baseline(g, stampFont, sy + 6, sh - 12), stampFont, Ui.MINT);
        g.setTransform(old);

        double x = cx + 1 + 26, y = cy + 1 + 28;
        String title = endless ? Lang.f("무한 배달 No. %d", endlessSeed)
                : Lang.f("운송장 %s No. %02d · %s", Campaign.chapterName(levelIndex), Campaign.stageOf(levelIndex) + 1, Lang.t(level.getName()));
        // 언어에 따라 제목이 길어서 도장과 겹치면: "운송장" 을 빼고, 그래도 길면 끝을 … 로 줄인다
        double room = sx - 10 - x;
        if (!endless && Ui.width(g, title, noFont) > room) {
            title = String.format("%s No. %02d · %s", Campaign.chapterName(levelIndex), Campaign.stageOf(levelIndex) + 1, Lang.t(level.getName()));
        }
        while (title.length() > 4 && Ui.width(g, title, noFont) > room) title = title.substring(0, title.length() - 2) + "…";
        Ui.text(g, title, x, Ui.baseline(g, noFont, y, 17), noFont, Ui.DIM);
        y += 17 + 18;
        Ui.text(g, stars(n), x, Ui.baseline(g, starFont, y, 54), starFont, Ui.AMBER);
        y += 54 + 6;
        for (String line : noteLines) {
            Ui.text(g, line, x, Ui.baseline(g, noteFont, y, 22.4), noteFont, Ui.SOFT_INK);
            y += 22.4;
        }
        y += 18;
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Line2D.Double(x, y + 0.5, x + innerW, y + 0.5));
        String time = Lang.f("%.2f초", ship.getTime()), count = Lang.f("%d회", tries);
        double row = y + 1 + 14;
        Ui.text(g, Lang.t("비행 시간"), x, Ui.baseline(g, dt, row, rowH), dt, Ui.DIM);
        Ui.text(g, time, x + innerW - Ui.width(g, time, dd), Ui.baseline(g, dd, row, rowH), dd, Ui.INK);
        row += rowH + 8;
        Ui.text(g, Lang.t("발사 횟수"), x, Ui.baseline(g, dt, row, rowH), dt, Ui.DIM);
        Ui.text(g, count, x + innerW - Ui.width(g, count, dd), Ui.baseline(g, dd, row, rowH), dd, Ui.INK);
        y += listH;
        g.setColor(Ui.LINE);
        g.draw(new Line2D.Double(x, y - 0.5, x + innerW, y - 0.5));
        y += 22;

        boolean last = !endless && levelIndex == Campaign.COUNT - 1;
        boolean chapterEnd = !endless && !last && Campaign.stageOf(levelIndex) == Campaign.STAGES - 1;
        String next = last ? Lang.t("무한 배달 시작") : endless ? Lang.t("다음 배달")
                : chapterEnd ? Lang.f("다음 단계: %s", Lang.t(Campaign.CHAPTERS[Campaign.chapterOf(levelIndex) + 1])) : Lang.t("다음 구역");
        String[] labels = {next, Lang.t("다시 배달"), Lang.t("구역 목록")};
        Runnable[] actions = {this::nextLevel, this::retry, this::showLevels};
        double bx = x;
        for (int i = 0; i < 3; i++) {
            double bw = Button.widthFor(g, labels[i], null, 15, i == 0 ? Font.BOLD : Ui.MEDIUM, 16);
            out.add(new Button(new Rectangle2D.Double(bx, y, bw, btnH), labels[i], null, 15, Ui.MEDIUM, i == 0, false, new Color(14, 15, 38, 217), actions[i]));
            bx += bw + 8;
        }
    }

    /** "항로 계산 중" 상자 (웹의 #busy) */
    protected void drawBusy(Graphics2D g, int w, int h) {
        Font f = Ui.font(13, Font.PLAIN, true, 0.2f);
        String s = Lang.t("항로 계산 중");
        double lh = 19, bw = Ui.width(g, s, f) + 40 + 2, bh = 12 + lh + 12 + 2, bx = (w - bw) / 2, by = (h - bh) / 2;
        g.setColor(new Color(7, 8, 24, 230));
        g.fill(new Rectangle2D.Double(bx, by, bw, bh));
        g.setColor(Ui.AMBER);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(bx + 0.5, by + 0.5, bw - 1, bh - 1));
        Ui.text(g, s, bx + 21, Ui.baseline(g, f, by + 13, lh), f, Ui.AMBER);
    }
}
