import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 한 프레임을 화면에 그리는 순서를 정하는 층 (paintComponent).
 *   1. 우주 화면: 배경 → 흐르는 별·유성 → 게임 세계 → 빛 번짐·페이드 (PostFx)
 *   2. HUD (HudPainter)
 *   3. 화면을 덮는 판: 처음 화면(TitlePainter), 구역 목록·영수증(PanelPainter). 열릴 때 서서히 나타난다
 *   4. 버튼: 마우스를 올리면 부드럽게 빛나도록 버튼마다 빛 세기를 기억한다
 * 그림의 크기와 위치는 웹 버전의 CSS 숫자를 그대로 옮겼다 (padding, margin, 글자 크기, 줄 높이).
 */
abstract class ScreenPainter extends PanelPainter {
    private static final long serialVersionUID = 1L;

    // 화면 효과 (웹 버전에는 없는 그래픽 업그레이드)
    private final transient PostFx post = new PostFx();               // 빛 번짐, 비네트, 페이드
    private final transient SpaceLayer space = new SpaceLayer();       // 흐르는 별, 유성
    private final transient java.util.Map<String, Double> hoverGlow = new java.util.HashMap<>();   // 버튼마다 마우스를 올린 정도 (0~1)
    private long lastPaint = System.nanoTime();
    private double paintDt;
    private boolean wasTitle, wasLevels, wasWin;
    private double titleSince, levelsSince, winSince;                  // 각 판이 열린 시각 (나타나는 움직임용)

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

        long now = System.nanoTime();
        paintDt = Math.min(0.1, (now - lastPaint) / 1e9);
        lastPaint = now;
        noteOverlays();

        // 1층: 우주 화면 (배경 + 흐르는 별 + 게임 세계) → 빛 번짐·비네트·페이드를 입혀서 화면에 붙인다
        // 배경은 구역, 창 크기, 윈도우 배율이 바뀔 때만 새로 그린다
        double dev = ImageCache.deviceScale(g);
        Graphics2D fg = post.begin(Math.max(2, (int) Math.ceil(w * dev)), Math.max(2, (int) Math.ceil(h * dev)));
        fg.scale(dev, dev);
        ImageCache.drawScreenLayer(fg, background.get(() -> makeBackground(fg, w, h), level, w, h, dev));
        if (level != null) space.setSeed(level.getSeed());
        space.update(paintDt, mouseX < 0 ? 0 : (mouseX / w - 0.5) * 2, mouseY < 0 ? 0 : (mouseY / h - 0.5) * 2, w, h);
        space.draw(fg, w, h);
        if (level != null) {
            double sx = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0, sy = shake > 0 ? (fx.nextDouble() - 0.5) * shake * viewScale : 0;
            fg.translate(viewX + sx, viewY + sy);
            fg.scale(viewScale, viewScale);
            drawWorld(fg);
        }
        fg.dispose();
        post.finish(g, level == null ? 0 : Math.max(0, 1 - (anim - fadeStart) / 0.5), bloomOn);

        // HUD
        List<Widget> hud = new ArrayList<>(), overlay = new ArrayList<>();
        boolean covered = overlayOpen() && !(state == WIN && demo);
        if (level != null && !demo && state != LOADING) drawHud(g, w, h, hud);
        drawWidgets(g, hud, !covered);

        // 2층: 화면을 덮는 판 (웹처럼 HUD 버튼도 그 아래에 깔린다)
        // 판이 열릴 때 서서히 나타나게: 처음 화면·구역 목록은 투명도, 영수증은 투명도 + 살짝 커지며 떠오르기
        if (titleOpen) {
            // 처음 화면은 움직이지 않으므로 통째로 한 장 그려 두고 붙이기만 한다 (버튼만 매번 그린다)
            BufferedImage layer = titleLayer.get(() -> {
                titleButtons = new ArrayList<>();
                return ImageCache.screenLayer(g, w, h, true, lg -> drawTitle(lg, w, h, titleButtons));
            }, w, h, dev);
            java.awt.Composite old = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) ease(titleSince, 0.6)));
            ImageCache.drawScreenLayer(g, layer);
            drawWidgets(g, titleButtons, true);
            g.setComposite(old);
            overlay.addAll(titleButtons);
        }
        if (levelsOpen) {
            List<Widget> list = new ArrayList<>();
            java.awt.Composite old = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) ease(levelsSince, 0.25)));
            drawLevels(g, w, h, list);
            drawWidgets(g, list, true);
            g.setComposite(old);
            overlay.addAll(list);
        }
        if (state == WIN && !demo) {
            List<Widget> list = new ArrayList<>();
            double t = ease(winSince, 0.35);
            java.awt.Composite old = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) t));
            AffineTransform before = g.getTransform();
            double k = 0.94 + 0.06 * t;                 // 94% 크기에서 100% 로, 아래에서 위로 살짝
            g.translate(w / 2.0, h / 2.0 + (1 - t) * 18);
            g.scale(k, k);
            g.translate(-w / 2.0, -h / 2.0);
            drawReceipt(g, w, h, list);
            drawWidgets(g, list, true);
            g.setTransform(before);
            g.setComposite(old);
            overlay.addAll(list);
        }
        if (state == LOADING) drawBusy(g, w, h);
        if (showFps) Ui.text(g, "FPS " + fps, w - 90, h - 40, Ui.font(13, Font.BOLD, true, 0), Ui.MINT);
        widgets = covered ? overlay : hud;
    }

    /** 버튼들을 그린다. interactive 가 아니면(위에 다른 판이 덮였으면) 마우스를 올려도 반응하지 않는다 */
    private void drawWidgets(Graphics2D g, List<Widget> list, boolean interactive) {
        for (Widget wd : list) {
            wd.hover = interactive && wd.contains(mouseX, mouseY);
            // 마우스를 올리고 내릴 때 빛이 0.1초쯤에 걸쳐 부드럽게 켜지고 꺼진다
            String key = wd.key();
            double v = hoverGlow.getOrDefault(key, 0.0);
            v += ((wd.hover ? 1 : 0) - v) * Math.min(1, paintDt * 14);
            hoverGlow.put(key, v);
            wd.glow = v;
            wd.draw(g, anim, 0);
        }
    }

    /** 처음 화면, 구역 목록, 영수증이 새로 열린 순간을 기억한다 */
    private void noteOverlays() {
        boolean win = state == WIN && !demo;
        if (titleOpen && !wasTitle) titleSince = anim;
        if (levelsOpen && !wasLevels) levelsSince = anim;
        if (win && !wasWin) winSince = anim;
        wasTitle = titleOpen;
        wasLevels = levelsOpen;
        wasWin = win;
    }

    /** since 부터 dur 초 동안 0 → 1 로 부드럽게 (처음엔 빠르게, 끝에서 천천히) */
    private double ease(double since, double dur) {
        double t = Math.max(0, Math.min(1, (anim - since) / dur));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
