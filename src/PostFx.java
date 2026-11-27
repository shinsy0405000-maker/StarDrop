import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * 화면 전체 효과: 빛 번짐(블룸)과 화면 전환 때 어둡게(페이드).
 * (가장자리 어둡게(비네트)는 모양이 안 바뀌어서 배경 그림에 미리 넣어 둔다: WorldPainter)
 *
 * 순서
 *   1. 우주 화면(배경 + 게임 세계)을 화면에 바로 그리지 않고 frame 그림에 먼저 그린다
 *   2. frame 을 1/8 크기로 줄이면서 밝은 곳만 골라낸다
 *   3. 골라낸 빛을 흐리게 퍼뜨린다 (상자 흐림 3번 = 가우스 흐림과 거의 같다)
 *   4. 퍼진 빛을 다시 8배로 늘리면서(부드럽게 이어서) frame 에 직접 섞는다
 *   5. 완성된 frame 을 화면에 한 번에 붙인다
 *   6. 페이드 중이면 그 위에 반투명 검은 사각형을 덮는다
 *
 * 빛 섞는 공식: 결과 = 빛 + 원래 색 × (1 - 빛의 밝기)
 * Java2D 의 확대 그리기(drawImage)는 그래픽 카드 없이 돌면 느려서, 빛 섞기는 픽셀 배열로 직접 하고
 * 빛이 없는 칸은 건너뛴다.
 */
final class PostFx {
    private static final int DOWN = 8;                  // 빛 계산은 화면의 1/8 크기로
    private static final int THRESHOLD = 150;           // 이보다 밝은(0~255) 곳만 번진다
    private static final float STRENGTH = 1.2f;         // 번짐 세기
    private static final int RADIUS = 3;                // 1/8 크기에서의 흐림 반경 (화면에서는 약 24px)

    private BufferedImage frame;
    private int fw, fh, gw, gh;
    private float[] r, g, b, tmp;
    private int[] lr, lg, lb, la, rowR, rowG, rowB, rowA, x0, wx, xStart;

    /** 우주 화면을 그릴 그림을 준비해 돌려준다 (화면 픽셀 크기 pw × ph) */
    Graphics2D begin(int pw, int ph) {
        if (frame == null || fw != pw || fh != ph) resize(pw, ph);
        Graphics2D fg = frame.createGraphics();
        fg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        fg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        fg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return fg;
    }

    private void resize(int pw, int ph) {
        fw = pw;
        fh = ph;
        frame = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        gw = Math.max(2, (pw + DOWN - 1) / DOWN);
        gh = Math.max(2, (ph + DOWN - 1) / DOWN);
        int n = gw * gh;
        r = new float[n];
        g = new float[n];
        b = new float[n];
        tmp = new float[Math.max(gw, gh)];
        lr = new int[n];
        lg = new int[n];
        lb = new int[n];
        la = new int[n];
        rowR = new int[gw];
        rowG = new int[gw];
        rowB = new int[gw];
        rowA = new int[gw];
        // 늘릴 때 화면 x 마다 쓸 작은 그림의 두 칸(x0, x0+1)과 섞는 비율(0~256)
        x0 = new int[pw];
        wx = new int[pw];
        for (int x = 0; x < pw; x++) {
            double s = (x + 0.5) / DOWN - 0.5;
            int i = (int) Math.floor(s);
            double f = s - i;
            if (i < 0) { i = 0; f = 0; }
            if (i >= gw - 1) { i = gw - 2; f = 1; }
            x0[x] = i;
            wx[x] = (int) Math.round(f * 256);
        }
        xStart = new int[gw];                              // 작은 그림 칸 i 를 쓰는 첫 화면 x
        java.util.Arrays.fill(xStart, pw);
        for (int x = pw - 1; x >= 0; x--) xStart[x0[x]] = x;
        for (int i = gw - 2; i >= 0; i--) if (xStart[i] > xStart[i + 1]) xStart[i] = xStart[i + 1];
    }

    /**
     * 효과를 frame 에 섞고 화면에 붙인다.
     * fade = 0(그대로) ~ 1(새까맣게), bloom = 빛 번짐을 켤지
     */
    void finish(Graphics2D screen, double fade, boolean bloom) {
        int[] px = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        int n = gw * gh;
        if (bloom) {
            // 1/8 로 줄이며 밝은 곳만 고르기 (8×8 칸에서 한 칸 건너 16곳을 평균 → 3px 짜리 작은 별도 놓치지 않는다)
            for (int y = 0; y < gh; y++) {
                for (int x = 0; x < gw; x++) {
                    int rr = 0, gg = 0, bb = 0;
                    for (int j = 1; j < DOWN; j += 2) {
                        int sy = Math.min(fh - 1, y * DOWN + j) * fw;
                        for (int i = 1; i < DOWN; i += 2) {
                            int p = px[sy + Math.min(fw - 1, x * DOWN + i)];
                            rr += (p >> 16) & 255;
                            gg += (p >> 8) & 255;
                            bb += p & 255;
                        }
                    }
                    float fr = rr / 16f, fgr = gg / 16f, fb = bb / 16f, m = Math.max(fr, Math.max(fgr, fb));
                    float k = m > THRESHOLD ? (m - THRESHOLD) / (255 - THRESHOLD) : 0;
                    k = k * (2 - k);                        // 문턱을 넘자마자 갑자기 밝아지지 않게 부드럽게
                    int i = y * gw + x;
                    r[i] = fr * k;
                    g[i] = fgr * k;
                    b[i] = fb * k;
                }
            }
            for (int pass = 0; pass < 3; pass++) {
                blur(r);
                blur(g);
                blur(b);
            }
        }
        if (bloom) {
            // 작은 그림 한 칸마다 섞을 빛(0~255)과 밝기(0~256)
            boolean any = false;
            for (int i = 0; i < n; i++) {
                int cr = (int) Math.min(255, r[i] * STRENGTH), cg = (int) Math.min(255, g[i] * STRENGTH), cb = (int) Math.min(255, b[i] * STRENGTH);
                lr[i] = cr;
                lg[i] = cg;
                lb[i] = cb;
                la[i] = Math.max(cr, Math.max(cg, cb)) * 256 / 255;
                any |= la[i] > 0;
            }
            if (any) composite(px);
        }

        AffineTransform base = screen.getTransform();
        int tx = (int) Math.round(base.getTranslateX()), ty = (int) Math.round(base.getTranslateY());
        screen.setTransform(new AffineTransform());
        screen.drawImage(frame, tx, ty, null);
        double f = Math.max(0, Math.min(1, fade));
        if (f > 0.003) {
            screen.setColor(new java.awt.Color(0, 0, 0, (int) Math.round(f * 255)));
            screen.fillRect(tx, ty, fw, fh);
        }
        screen.setTransform(base);
    }

    /** 작은 빛 그림을 부드럽게 8배로 늘리면서 frame 픽셀에 섞는다: 결과 = 빛 + 원래 색 × (1 - 투명도) */
    private void composite(int[] px) {
        for (int y = 0; y < fh; y++) {
            double s = (y + 0.5) / DOWN - 0.5;
            int y0 = (int) Math.floor(s);
            double f = s - y0;
            if (y0 < 0) { y0 = 0; f = 0; }
            if (y0 >= gh - 1) { y0 = gh - 2; f = 1; }
            int wy = (int) Math.round(f * 256), a0 = y0 * gw, a1 = a0 + gw;
            for (int x = 0; x < gw; x++) {                // 먼저 세로로 섞은 한 줄을 만든다
                rowR[x] = (lr[a0 + x] * (256 - wy) + lr[a1 + x] * wy) >> 8;
                rowG[x] = (lg[a0 + x] * (256 - wy) + lg[a1 + x] * wy) >> 8;
                rowB[x] = (lb[a0 + x] * (256 - wy) + lb[a1 + x] * wy) >> 8;
                rowA[x] = (la[a0 + x] * (256 - wy) + la[a1 + x] * wy) >> 8;
            }
            int row = y * fw;
            for (int c = 0; c < gw - 1; c++) {            // 그 줄을 가로로 섞으며 픽셀에 덮는다 (빛이 없는 칸은 건너뛴다)
                if (rowA[c] == 0 && rowA[c + 1] == 0) continue;
                int end = c + 1 < gw - 1 ? xStart[c + 1] : fw;
                for (int x = xStart[c]; x < end; x++) {
                int i = x0[x], w = wx[x], iw = 256 - w;
                int a = (rowA[i] * iw + rowA[i + 1] * w) >> 8;
                if (a == 0) continue;
                int cr = (rowR[i] * iw + rowR[i + 1] * w) >> 8;
                int cg = (rowG[i] * iw + rowG[i + 1] * w) >> 8;
                int cb = (rowB[i] * iw + rowB[i + 1] * w) >> 8;
                int p = px[row + x], inv = 256 - a;
                int nr = cr + ((((p >> 16) & 255) * inv) >> 8);
                int ng = cg + ((((p >> 8) & 255) * inv) >> 8);
                int nb = cb + (((p & 255) * inv) >> 8);
                px[row + x] = (Math.min(255, nr) << 16) | (Math.min(255, ng) << 8) | Math.min(255, nb);
                }
            }
        }
    }

    /** 가로 한 번, 세로 한 번 상자 흐림 (이동 합계로 반경과 상관없이 빠르게) */
    private void blur(float[] a) {
        int rad = RADIUS;
        float inv = 1f / (rad * 2 + 1);
        for (int y = 0; y < gh; y++) {
            int row = y * gw;
            float sum = 0;
            for (int k = -rad; k <= rad; k++) sum += a[row + Math.max(0, Math.min(gw - 1, k))];
            for (int x = 0; x < gw; x++) {
                tmp[x] = sum * inv;
                sum += a[row + Math.min(gw - 1, x + rad + 1)] - a[row + Math.max(0, x - rad)];
            }
            System.arraycopy(tmp, 0, a, row, gw);
        }
        for (int x = 0; x < gw; x++) {
            float sum = 0;
            for (int k = -rad; k <= rad; k++) sum += a[Math.max(0, Math.min(gh - 1, k)) * gw + x];
            for (int y = 0; y < gh; y++) {
                tmp[y] = sum * inv;
                sum += a[Math.min(gh - 1, y + rad + 1) * gw + x] - a[Math.max(0, y - rad) * gw + x];
            }
            for (int y = 0; y < gh; y++) a[y * gw + x] = tmp[y];
        }
    }
}
