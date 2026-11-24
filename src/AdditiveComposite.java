import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * 빛을 "더하는" 그리기 방식 (웹 캔버스의 globalCompositeOperation = 'lighter').
 * 보통 그리기는 아래 색을 덮지만, 이 방식은 아래 색에 새 색을 더한다. 성운과 별이 겹치면 더 밝아진다.
 * 자바에는 이 방식이 없어서 픽셀 계산을 직접 한다 (한 줄씩 묶어서 계산해 빠르게).
 */
final class AdditiveComposite implements Composite {
    static final AdditiveComposite INSTANCE = new AdditiveComposite();

    private AdditiveComposite() { }

    /** 픽셀 하나가 int 하나에 0xAARRGGBB(또는 0xRRGGBB) 순서로 들어 있는 그림인가 */
    private static boolean packed(ColorModel cm, Raster r) {
        if (!(cm instanceof DirectColorModel dcm) || r.getTransferType() != DataBuffer.TYPE_INT) return false;
        return dcm.getRedMask() == 0xFF0000 && dcm.getGreenMask() == 0xFF00 && dcm.getBlueMask() == 0xFF
                && (!dcm.hasAlpha() || dcm.getAlphaMask() == 0xFF000000);
    }

    @Override
    public CompositeContext createContext(ColorModel srcModel, ColorModel dstModel, RenderingHints hints) {
        return new CompositeContext() {
            @Override
            public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                int w = Math.min(src.getWidth(), dstIn.getWidth()), h = Math.min(src.getHeight(), dstIn.getHeight());
                if (packed(srcModel, src) && packed(dstModel, dstIn)) {
                    composePacked(src, dstIn, dstOut, w, h);
                    return;
                }
                int sb = src.getNumBands(), db = dstIn.getNumBands();
                boolean srcAlpha = srcModel.hasAlpha(), premultiplied = srcModel.isAlphaPremultiplied();
                int[] s = new int[w * sb], d = new int[w * db];
                for (int y = 0; y < h; y++) {
                    // 세 그림판은 시작 좌표(minX, minY)가 서로 다를 수 있다
                    src.getPixels(src.getMinX(), src.getMinY() + y, w, 1, s);
                    dstIn.getPixels(dstIn.getMinX(), dstIn.getMinY() + y, w, 1, d);
                    for (int x = 0; x < w; x++) {
                        int a = srcAlpha ? s[x * sb + 3] : 255;
                        if (a == 0) continue;
                        for (int c = 0; c < 3; c++) {          // 빨강, 초록, 파랑: 투명도를 곱한 색을 더한다
                            int add = premultiplied ? s[x * sb + c] : s[x * sb + c] * a / 255;
                            d[x * db + c] = Math.min(255, d[x * db + c] + add);
                        }
                    }
                    dstOut.setPixels(dstOut.getMinX(), dstOut.getMinY() + y, w, 1, d);
                }
            }

            /** 흔한 경우(한 픽셀이 int 하나 = 0xAARRGGBB)는 줄 단위 int 배열로 바로 계산한다 */
            private void composePacked(Raster src, Raster dstIn, WritableRaster dstOut, int w, int h) {
                boolean srcAlpha = srcModel.hasAlpha(), premultiplied = srcModel.isAlphaPremultiplied();
                int[] s = new int[w], d = new int[w];
                for (int y = 0; y < h; y++) {
                    src.getDataElements(src.getMinX(), src.getMinY() + y, w, 1, s);
                    dstIn.getDataElements(dstIn.getMinX(), dstIn.getMinY() + y, w, 1, d);
                    for (int x = 0; x < w; x++) {
                        int p = s[x], a = srcAlpha ? p >>> 24 : 255;
                        if (a == 0) continue;
                        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                        if (!premultiplied) {
                            r = r * a / 255;
                            g = g * a / 255;
                            b = b * a / 255;
                        }
                        int q = d[x];
                        r = Math.min(255, ((q >> 16) & 255) + r);
                        g = Math.min(255, ((q >> 8) & 255) + g);
                        b = Math.min(255, (q & 255) + b);
                        d[x] = (q & 0xFF000000) | (r << 16) | (g << 8) | b;
                    }
                    dstOut.setDataElements(dstOut.getMinX(), dstOut.getMinY() + y, w, 1, d);
                }
            }

            @Override
            public void dispose() { }
        };
    }
}
