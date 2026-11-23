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
import java.util.ArrayList;
import java.util.List;

/** 화면 위 UI 를 그리는 층: HUD, 처음 화면, 구역 목록, 배달 완료 영수증. paintComponent 가 여기 있다 */
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

        List<Widget> next = new ArrayList<>();
        if (level != null) {
            double sx = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0, sy = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0;
            g.translate(viewX + sx, viewY + sy);
            g.scale(viewScale, viewScale);
            drawWorld(g);
            g.setTransform(base);
            if (!demo && state != LOADING) drawHud(g, w, h, next);
        }
        if (titleOpen) {
            // 처음 화면은 움직이지 않으므로 통째로 한 장 그려 두고 붙이기만 한다 (버튼만 매번 그린다)
            BufferedImage layer = titleLayer.get(() -> {
                titleButtons = new ArrayList<>();
                return ImageCache.screenLayer(g, w, h, true, lg -> drawTitle(lg, w, h, titleButtons));
            }, w, h, dev);
            ImageCache.drawScreenLayer(g, layer);
            next.addAll(titleButtons);
        }
        if (levelsOpen) drawLevels(g, w, h, next);
        if (state == WIN && !demo) drawReceipt(g, w, h, next);
        if (state == LOADING) drawBusy(g, w, h);
        if (showFps) Ui.text(g, "FPS " + fps, w - 90, h - 40, Ui.font(13, Font.BOLD, true, 0), Ui.MINT);

        for (Widget wd : next) {                       // 버튼은 맨 위에 그린다
            wd.hover = wd.contains(mouseX, mouseY);
            wd.draw(g, anim, 0);
        }
        widgets = next;
    }

    // ------------------------------------------------------------ 화면 위 UI (화면 좌표, 웹 버전 CSS 와 같은 크기)
    protected void drawHud(Graphics2D g, int w, int h, List<Widget> out) {
        // 왼쪽 위: 운송장
        int total = LevelGenerator.CAMPAIGN.length;
        String no = endless ? "No. " + endlessSeed : String.format("No. %02d / %02d", levelIndex + 1, total);
        String[][] cells = {{"운송장", no, "mono"}, {"구역", level.getName(), ""}, {"별", stars(starCount()), "star"}, {"발사", tries + "회", "mono"}};
        Font label = Ui.font(10, Font.PLAIN, false, 0.14f);
        double x = 16, y = 14, cellH = 44, waybillW = 0;
        double[] widths = new double[cells.length];
        for (int i = 0; i < cells.length; i++) {
            Font vf = valueFont(cells[i][2]);
            widths[i] = Math.max(Ui.width(g, cells[i][0], label), Ui.width(g, cells[i][1], vf)) + 24;
            waybillW += widths[i];
        }
        g.setColor(Ui.PANEL_SEE);
        g.fill(new Rectangle2D.Double(x, y, waybillW, cellH));
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(x + 0.5, y + 0.5, waybillW - 1, cellH - 1));
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) g.draw(new Line2D.Double(x + 0.5, y, x + 0.5, y + cellH));
            Ui.text(g, cells[i][0], x + 12, y + 17, label, Ui.DIM);
            Ui.text(g, cells[i][1], x + 12, y + 36, valueFont(cells[i][2]), cells[i][2].equals("star") ? Ui.AMBER : Ui.INK);
            x += widths[i];
        }

        // 오른쪽 위: 버튼들 (자리가 모자라면 운송장 아래 줄로)
        String[][] tools = {{"다시 조준", "R"}, {"정답 방향 보기", "H"}, {Sound.isMuted() ? "소리 켜기" : "소리 끄기", null}, {"구역 목록", null}};
        Runnable[] actions = {this::retry, () -> showHint = !showHint, () -> Sound.setMuted(!Sound.isMuted()), this::showLevels};
        Font tf = Ui.font(13, Font.PLAIN, false, 0);
        double toolsW = -6;
        double[] tw = new double[tools.length];
        for (int i = 0; i < tools.length; i++) {
            tw[i] = Ui.width(g, tools[i][0], tf) + 24 + (tools[i][1] == null ? 0 : Ui.width(g, tools[i][1], Ui.font(11, Font.PLAIN, true, 0)) + 5);
            toolsW += tw[i] + 6;
        }
        double tx = w - 16 - toolsW, ty = 14;
        if (tx < 16 + waybillW + 8) {
            tx = Math.max(16, w - 16 - toolsW);
            ty = 14 + cellH + 8;
        }
        for (int i = 0; i < tools.length; i++) {
            out.add(new Button(new Rectangle2D.Double(tx, ty, tw[i], 31), tools[i][0], tools[i][1], 13, false, i == 1 && showHint, actions[i]));
            tx += tw[i] + 6;
        }

        // 왼쪽 아래: 조준 값
        double deg = ((-Math.toDegrees(aimAngle)) % 360 + 360) % 360;
        Ui.text(g, String.format("각도 %.1f°  ·  세기 %d%%", deg, Math.round(aimPower * 100)), 16, h - 18, Ui.font(12, Font.PLAIN, true, 0), Ui.DIM);

        // 오른쪽 아래: 추진 연료
        Font ff = Ui.font(11, Font.PLAIN, false, 0.12f);
        double barX = w - 16 - 120, barY = h - 14 - 10;
        Ui.text(g, "추진 연료", barX - 8 - Ui.width(g, "추진 연료", ff), barY + 7, ff, Ui.DIM);
        g.setColor(Ui.PANEL_SEE);
        g.fill(new Rectangle2D.Double(barX, barY, 120, 6));
        g.setColor(new Color(127, 220, 255));
        g.fill(new Rectangle2D.Double(barX, barY, 120 * Math.max(0, fuel / Rules.FUEL), 6));
        g.setColor(Ui.LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(barX - 0.5, barY - 0.5, 121, 7));

        // 아래 가운데: 안내 문구
        if (tip != null && state == AIM) {
            Font f = Ui.font(14, Font.PLAIN, false, 0);
            double boxW = Math.min(500, w - 32);
            List<String> lines = Ui.wrap(g, tip, f, boxW - 28 - 3);
            double boxH = 20 + lines.size() * 22.4, bx = (w - boxW) / 2, by = h - 44 - boxH;
            g.setColor(new Color(14, 15, 38, 230));
            g.fill(new Rectangle2D.Double(bx, by, boxW, boxH));
            g.setColor(Ui.AMBER);
            g.fill(new Rectangle2D.Double(bx, by, 3, boxH));
            for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), bx + 17, by + 10 + 16 + i * 22.4, f, Ui.INK);
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

    protected void drawTitle(Graphics2D g, int w, int h, List<Widget> out) {
        // 왼쪽을 어둡게 덮어 글자가 잘 보이게 (웹과 같은 그라데이션)
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, w, (float) (w * Math.tan(Math.toRadians(10))), new float[]{0f, 0.34f, 0.68f},
                new Color[]{new Color(7, 8, 24, 240), new Color(7, 8, 24, 184), new Color(7, 8, 24, 0)}));
        g.fillRect(0, 0, w, h);

        double padX = Ui.clamp(w * 0.05, 16, 72), padB = Ui.clamp(h * 0.06, 20, 64);
        double h1 = Ui.clamp(w * 0.13, 64, 168), ledeSize = Ui.clamp(w * 0.015, 15, 18);
        Font ledeFont = Ui.font((float) ledeSize, Font.PLAIN, false, 0);
        List<String> lede = Ui.wrap(g, "엔진은 1.2초치뿐입니다. 행성의 중력을 빌려 소포를 휘돌려 보내세요. "
                + "5개 구역은 전부 자동 풀이기가 검증한 항로가 있고, 그 뒤로는 무한히 새 구역이 만들어집니다.", ledeFont, ledeSize * 26);

        // 아래에서부터 쌓아 올린다
        double keysY = h - padB;
        double menuH = 46, menuY = keysY - 12 - 22 - menuH;
        double ledeLine = ledeSize * 1.65, ledeBottom = menuY - 26, ledeTop = ledeBottom - lede.size() * ledeLine;
        double h1Bottom = ledeTop - 20, line2 = h1Bottom - h1 * 0.2, line1 = line2 - h1 * 0.92;
        double stampY = line1 - h1 * 0.78 - 18 - 26;

        // 도장
        Font stampFont = Ui.font(12, Font.PLAIN, true, 0.22f);
        String stamp = "취급주의 · 블랙홀 근처 배송";
        double stampW = Ui.width(g, stamp, stampFont) + 20;
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-2), padX, stampY + 13);
        g.setColor(Ui.AMBER);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
        g.draw(new Rectangle2D.Double(padX, stampY, stampW, 26));
        Ui.text(g, stamp, padX + 10, stampY + 17, stampFont, Ui.AMBER);
        g.setTransform(old);

        // 제목: 뒤에 주황빛 번짐을 깐다
        Font titleFont = Ui.font((float) h1, Font.BOLD, false, -0.01f);
        drawTitleGlow(g, padX, line1, line2, titleFont, h1);
        Ui.text(g, "별똥", padX, line1, titleFont, Ui.AMBER);
        Ui.text(g, "택배", padX, line2, titleFont, Ui.INK);
        for (int i = 0; i < lede.size(); i++) Ui.text(g, lede.get(i), padX, ledeTop + ledeLine * (i + 0.72), ledeFont, Ui.SOFT_INK);

        // 메뉴 버튼
        String[] labels = {"배달 시작", "구역 목록", "무한 배달"};
        Runnable[] actions = {() -> {
            int first = 0;
            while (first < progress.length && progress[first] > 0) first++;
            startCampaign(first < progress.length ? first : 0);
        }, this::showLevels, this::startEndless};
        double bx = padX;
        for (int i = 0; i < 3; i++) {
            Font bf = Ui.font(16, i == 0 ? Font.BOLD : Font.PLAIN, false, 0);
            double bw = Ui.width(g, labels[i], bf) + 44;
            out.add(new Button(new Rectangle2D.Double(bx, menuY, bw, menuH), labels[i], null, 16, i == 0, false, actions[i]));
            bx += bw + 10;
        }

        // 조작 안내 (키 모양 상자)
        String[][] keys = {{"마우스", "조준"}, {"클릭", "발사"}, {"Space", "추진"}, {"←→↑↓", "미세 조정 (Shift로 더 세밀하게)"}};
        Font kf = Ui.font(12, Font.PLAIN, false, 0), km = Ui.font(12, Font.PLAIN, true, 0);
        double kx = padX;
        for (String[] k : keys) {
            double kw = Ui.width(g, k[0], km) + 12;
            g.setColor(Ui.LINE);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Rectangle2D.Double(kx, keysY - 13, kw, 18));
            Ui.text(g, k[0], kx + 6, keysY, km, Ui.INK);
            kx += kw + 3;
            Ui.text(g, k[1], kx, keysY, kf, Ui.DIM);
            kx += Ui.width(g, k[1], kf) + 6;
        }
    }

    /** 제목 글자의 빛 번짐: 작게 그렸다가 크게 늘리면 흐려지는 원리 (처음 화면 그림을 만들 때 한 번만 불린다) */
    protected void drawTitleGlow(Graphics2D g, double x, double line1, double line2, Font f, double size) {
        int pad = (int) (size * 0.6), down = 12;
        double textW = Math.max(Ui.width(g, "별똥", f), Ui.width(g, "택배", f));
        int iw = (int) ((textW + pad * 2) / down) + 2, ih = (int) ((line2 - line1 + size + pad * 2) / down) + 2;
        BufferedImage small = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tg = small.createGraphics();
        tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        tg.scale(1.0 / down, 1.0 / down);
        tg.setFont(f);
        tg.setColor(new Color(255, 170, 60, 90));
        tg.drawString("별똥", pad, (float) (pad + size * 0.8));
        tg.drawString("택배", pad, (float) (pad + size * 0.8 + (line2 - line1)));
        tg.dispose();
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(small, (int) (x - pad), (int) (line1 - size * 0.8 - pad), iw * down, ih * down, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    /** 구역 목록 화면 */
    protected void drawLevels(Graphics2D g, int w, int h, List<Widget> out) {
        g.setColor(new Color(7, 8, 24, 237));
        g.fillRect(0, 0, w, h);
        double padX = Ui.clamp(w * 0.05, 16, 72), padY = Ui.clamp(h * 0.05, 20, 56);
        Font hf = Ui.font((float) Ui.clamp(w * 0.05, 32, 52), Font.BOLD, false, 0);
        double headY = padY + hf.getSize2D() * 0.85;
        Ui.text(g, "배달 구역", padX, headY, hf, Ui.INK);
        int collected = 0;
        for (int p : progress) collected += Math.max(0, p - 1);
        Font tf = Ui.font(14, Font.PLAIN, true, 0);
        String total = "모은 별 " + collected + " / " + progress.length * 3;
        Ui.text(g, total, w - padX - Ui.width(g, total, tf), headY, tf, Ui.AMBER);

        double gridY = headY + 22 + 10, avail = w - padX * 2;
        int cols = Math.max(1, (int) ((avail + 10) / (168 + 10)));
        double tileW = (avail - (cols - 1) * 10) / cols;
        for (int i = 0; i < progress.length; i++) {
            final int index = i;
            boolean locked = i > 0 && progress[i - 1] == 0 && progress[i] == 0;
            String status = locked ? "잠김" : progress[i] > 0 ? stars(progress[i] - 1) : "미배달";
            double tx = padX + (i % cols) * (tileW + 10), ty = gridY + (i / cols) * (108 + 10);
            out.add(new LevelTile(new Rectangle2D.Double(tx, ty, tileW, 108), String.format("%02d", i + 1),
                    LevelGenerator.CAMPAIGN[i].name, status, progress[i] > 0, locked, () -> startCampaign(index)));
        }
        int rows = (progress.length + cols - 1) / cols;
        double by = gridY + rows * 118 - 10 + 22, bx = padX;
        String[] labels = {"처음 화면", "무한 배달"};
        Runnable[] actions = {this::showTitle, this::startEndless};
        for (int i = 0; i < 2; i++) {
            double bw = Ui.width(g, labels[i], Ui.font(16, Font.PLAIN, false, 0)) + 44;
            out.add(new Button(new Rectangle2D.Double(bx, by, bw, 46), labels[i], null, 16, false, false, actions[i]));
            bx += bw + 8;
        }
    }

    /** 배달 완료 영수증 */
    protected void drawReceipt(Graphics2D g, int w, int h, List<Widget> out) {
        g.setColor(new Color(7, 8, 24, 140));
        g.fillRect(0, 0, w, h);
        Font noteFont = Ui.font(14, Font.PLAIN, false, 0);
        double cw = Math.min(440, w - 32), innerW = cw - 52;
        int n = starCount();
        String note = n == 3 ? "별 세 개를 모두 지났습니다." : "별 " + (3 - n) + "개를 놓쳤습니다. 별은 세 개를 모두 지나는 궤도 위에 놓여 있습니다.";
        List<String> noteLines = Ui.wrap(g, note, noteFont, innerW);
        double ch = 28 + 16 + 18 + 54 + 6 + noteLines.size() * 22.4 + 18 + 14 + 14 * 2 + 8 + 14 + 22 + 40 + 24;
        double cx = (w - cw) / 2, cy = (h - ch) / 2;
        g.setColor(Ui.PANEL);
        g.fill(new Rectangle2D.Double(cx, cy, cw, ch));
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(cx + 0.5, cy + 0.5, cw - 1, ch - 1));

        // 도장
        Font stampFont = Ui.font(22, Font.BOLD, false, 0.06f);
        double sw = Ui.width(g, "배달 완료", stampFont) + 24, sx = cx + cw - 18 - sw, sy = cy + 16;
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(7), sx + sw / 2, sy + 18);
        g.setColor(Ui.MINT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Rectangle2D.Double(sx, sy, sw, 36));
        Ui.text(g, "배달 완료", sx + 12, sy + 26, stampFont, Ui.MINT);
        g.setTransform(old);

        double x = cx + 26, y = cy + 28 + 12;
        String title = endless ? "무한 배달 No. " + endlessSeed : String.format("운송장 No. %02d · %s", levelIndex + 1, level.getName());
        Ui.text(g, title, x, y, Ui.font(12, Font.PLAIN, true, 0.08f), Ui.DIM);
        y += 18 + 46;
        Ui.text(g, stars(n), x, y, Ui.font(54, Font.PLAIN, false, 0.08f), Ui.AMBER);
        y += 20;
        for (String line : noteLines) {
            y += 22.4;
            Ui.text(g, line, x, y - 6, noteFont, Ui.SOFT_INK);
        }
        y += 18;
        g.setColor(Ui.LINE);
        g.setStroke(Ui.DASHED);
        g.draw(new Line2D.Double(x, y, x + innerW, y));
        Font dt = Ui.font(14, Font.PLAIN, false, 0), dd = Ui.font(14, Font.PLAIN, true, 0);
        String time = String.format("%.2f초", ship.getTime()), count = tries + "회";
        y += 14 + 14;
        Ui.text(g, "비행 시간", x, y, dt, Ui.DIM);
        Ui.text(g, time, x + innerW - Ui.width(g, time, dd), y, dd, Ui.INK);
        y += 8 + 14 + 4;
        Ui.text(g, "발사 횟수", x, y, dt, Ui.DIM);
        Ui.text(g, count, x + innerW - Ui.width(g, count, dd), y, dd, Ui.INK);
        y += 14;
        g.draw(new Line2D.Double(x, y, x + innerW, y));
        y += 22;

        boolean last = !endless && levelIndex == LevelGenerator.CAMPAIGN.length - 1;
        String[] labels = {last ? "무한 배달 시작" : endless ? "다음 배달" : "다음 구역", "다시 배달", "구역 목록"};
        Runnable[] actions = {this::nextLevel, this::retry, this::showLevels};
        double bx = x;
        for (int i = 0; i < 3; i++) {
            double bw = Ui.width(g, labels[i], Ui.font(15, i == 0 ? Font.BOLD : Font.PLAIN, false, 0)) + 32;
            out.add(new Button(new Rectangle2D.Double(bx, y, bw, 40), labels[i], null, 15, i == 0, false, actions[i]));
            bx += bw + 8;
        }
    }

    /** "항로 계산 중" 상자 */
    protected void drawBusy(Graphics2D g, int w, int h) {
        Font f = Ui.font(13, Font.PLAIN, true, 0.2f);
        String s = "항로 계산 중";
        double bw = Ui.width(g, s, f) + 40, bh = 42, bx = (w - bw) / 2, by = (h - bh) / 2;
        g.setColor(new Color(7, 8, 24, 230));
        g.fill(new Rectangle2D.Double(bx, by, bw, bh));
        g.setColor(Ui.AMBER);
        g.setStroke(Ui.DASHED);
        g.draw(new Rectangle2D.Double(bx + 0.5, by + 0.5, bw - 1, bh - 1));
        Ui.text(g, s, bx + 20, by + 26, f, Ui.AMBER);
    }
}
