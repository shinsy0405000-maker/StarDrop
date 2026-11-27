import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * 화면을 덮는 판들을 그리는 층: 구역 목록(웹의 #levels + 단계 탭), 배달 완료 영수증(#win), "항로 계산 중" 상자(#busy).
 */
abstract class PanelPainter extends TitlePainter {
    private static final long serialVersionUID = 1L;

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
        List<String> noteLines = TextWrap.lines(g, note, noteFont, innerW);
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
