import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RadialGradientPaint;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** 그리기 도우미: HSL 색, 빛 번짐, 원 그리기 */
final class Paints {
    private Paints() { }

    /** HSL 색(색상 0~360, 채도 0~1, 밝기 0~1)과 투명도(0~255)로 Color 만들기 */
    static Color hsl(double h, double s, double l, int alpha) {
        h = ((h % 360) + 360) % 360 / 360.0;
        s = Math.max(0, Math.min(1, s));
        l = Math.max(0, Math.min(1, l));
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new Color(channel(p, q, h + 1.0 / 3), channel(p, q, h), channel(p, q, h - 1.0 / 3), Math.max(0, Math.min(255, alpha)));
    }

    private static int channel(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        double v = t < 1.0 / 6 ? p + (q - p) * 6 * t : t < 0.5 ? q : t < 2.0 / 3 ? p + (q - p) * (2.0 / 3 - t) * 6 : p;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** 가운데가 밝고 가장자리로 갈수록 투명해지는 빛 */
    static void glow(Graphics2D g, double x, double y, double radius, Color color) {
        // 그라데이션을 매 프레임 새로 계산하면 느리므로, 색마다 한 번만 그려 두고 크기만 바꿔 쓴다
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
        AffineTransform at = AffineTransform.getTranslateInstance(x - radius, y - radius);
        at.scale(radius * 2 / GLOW_SIZE, radius * 2 / GLOW_SIZE);
        g.drawImage(img, at, null);
    }

    private static final int GLOW_SIZE = 128;
    private static final Map<Integer, BufferedImage> GLOWS = new HashMap<>();

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

    static void circle(Graphics2D g, double x, double y, double r) {
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    static void ring(Graphics2D g, double x, double y, double r) {
        g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }
}
