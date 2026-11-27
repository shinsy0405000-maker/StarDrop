import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * 게임 화면 위 정보(HUD)를 그리는 층 (웹의 #hud, #readout, #fuel, #tip).
 *   왼쪽 위: 운송장 (단계, 번호, 구역, 별, 발사 횟수)    오른쪽 위: 도구 버튼
 *   왼쪽 아래: 조준 각도와 세기                        오른쪽 아래: 추진 연료
 *   아래 가운데: 안내 문구
 */
abstract class HudPainter extends WorldPainter {
    private static final long serialVersionUID = 1L;

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
            List<String> lines = TextWrap.lines(g, Lang.t(tip), f, boxW - 3 - 28);
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
}
