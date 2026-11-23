import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 미리 그려 두는 그림(캐시)을 만들고 그리는 도우미.
 *
 * Java2D 는 그라데이션이나 큰 글자를 매 프레임 새로 계산하면 느리다.
 * 그래서 한 번 그림(BufferedImage)으로 그려 두고, 다음부터는 그 그림을 붙여 넣기만 한다.
 */
final class ImageCache {
    private ImageCache() { }

    /** 그래픽 카드가 빠르게 그릴 수 있는 형식의 빈 그림 만들기 */
    static BufferedImage newImage(Graphics2D g, int w, int h, boolean transparent) {
        GraphicsConfiguration gc = g.getDeviceConfiguration();
        try {
            if (gc != null) return gc.createCompatibleImage(w, h, transparent ? Transparency.TRANSLUCENT : Transparency.OPAQUE);
        } catch (RuntimeException e) {
            // 아래의 기본 형식으로 만든다
        }
        return new BufferedImage(w, h, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    // ------------------------------------------------------------ 빛 번짐 (색마다 한 장)
    static final int GLOW_SIZE = 128;
    private static final Map<Integer, BufferedImage> GLOWS = new HashMap<>();

    /** 가운데가 밝고 가장자리가 투명한 원. 같은 색이면 처음 만든 그림을 계속 쓴다 */
    static BufferedImage glow(Graphics2D g, Color color) {
        BufferedImage img = GLOWS.get(color.getRGB());
        if (img == null) {
            img = newImage(g, GLOW_SIZE, GLOW_SIZE, true);
            Graphics2D ig = img.createGraphics();
            Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
            ig.setPaint(new RadialGradientPaint(GLOW_SIZE / 2f, GLOW_SIZE / 2f, GLOW_SIZE / 2f, new float[]{0f, 1f}, new Color[]{color, clear}));
            ig.fillRect(0, 0, GLOW_SIZE, GLOW_SIZE);
            ig.dispose();
            GLOWS.put(color.getRGB(), img);
        }
        return img;
    }

    // ------------------------------------------------------------ 화면 크기 그림 (배경, 처음 화면)
    /** 윈도우 배율(125%, 150% 등). 화면 픽셀 = 창 좌표 × 이 값 */
    static double deviceScale(Graphics2D screen) {
        return screen.getTransform().getScaleX();
    }

    /**
     * 창 크기(w × h) 그대로의 그림을 한 장 만들고 painter 로 그 위에 그린다.
     * painter 는 평소 화면에 그리듯 창 좌표로 그리면 되고, 윈도우 배율은 알아서 맞춰진다.
     */
    static BufferedImage screenLayer(Graphics2D screen, int w, int h, boolean transparent, Consumer<Graphics2D> painter) {
        double dev = deviceScale(screen);
        int pw = Math.max(2, (int) Math.ceil(w * dev)), ph = Math.max(2, (int) Math.ceil(h * dev));
        BufferedImage img = newImage(screen, pw, ph, transparent);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.scale(dev, dev);
        painter.accept(g);
        g.dispose();
        return img;
    }

    /** screenLayer 로 만든 그림을 창 왼쪽 위에 화면 픽셀 그대로(확대/축소 없이) 붙인다 */
    static void drawScreenLayer(Graphics2D g, BufferedImage img) {
        AffineTransform base = g.getTransform();
        g.setTransform(new AffineTransform());
        g.drawImage(img, (int) Math.round(base.getTranslateX()), (int) Math.round(base.getTranslateY()), null);
        g.setTransform(base);
    }
}
