package screens;

import de.gurkenlabs.litiengine.gui.ImageComponent;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class PixelButton extends ImageComponent {
    public static final int BODY_W = 300;
    public static final int BODY_H = 54;

    private static final int OFFSET = 9;
    private static final int IMG_W = 318;
    private static final int IMG_H = 72;

    private static final Color SHADOW = new Color(12, 6, 4);
    private static final Color TEXT_NORMAL = new Color(226, 216, 196);
    private static final Color TEXT_HOVER = Color.WHITE;
    private static final Color TEXT_PRESSED = new Color(255, 214, 90);

    private static BufferedImage normalImg, hoverImg, pressedImg;
    private static Font font;

    private static boolean loaded = false;
    private double scale = 1.0;
    private int FontSize = -1;
    private final String label;

    public PixelButton(double x, double y, String label) {
        super(x, y, BODY_W, BODY_H, label);
        this.label = label;
        if(!loaded) {
            normalImg = Resources.images().get("sprites/ui/button_normal.png");
            hoverImg = Resources.images().get("sprites/ui/button_hover.png");
            pressedImg = Resources.images().get("sprites/ui/button_pressed.png");
            loaded = true;
        }
    }

    public void setScale(double k) {
        this.scale = k;
        setDimension(BODY_W * k, BODY_H * k);
    }

    public static BufferedImage loadImg(String path) {
        if (PixelButton.class.getClassLoader().getResource(path) == null) {
            System.err.println("[PixelButton] KHONG TIM THAY FILE: " + path);
            return null;
        }
        try {
            return Resources.images().get(path);
        } catch (Exception e) {
            System.err.println("[PixelButton] LOI NAP " + path + ": " + e.getMessage());
            return null;
        }
    }

    private static Font loadFont(String path, int size) {
        Font fallback = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont(32f);
        if (PixelButton.class.getClassLoader().getResource(path) == null) {
            System.err.println("[PixelButton] KHONG TIM THAY FONT: " + path);
            return fallback;
        }
        try {
            return Resources.fonts().get(path).deriveFont((float) size);
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (!isVisible()) {
            return;
        }
        boolean pressed = isPressed();
        boolean hovered = isHovered();
        BufferedImage img = pressed ? pressedImg : (hovered ? hoverImg : normalImg);

        int x = (int) Math.round(getX());
        int y = (int) Math.round(getY());
        int bw = (int) Math.round(BODY_W * scale);
        int bh = (int) Math.round(BODY_H * scale);
        int off = (int) Math.round(OFFSET * scale);

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Object oldTextAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        if (img != null) {
            g.drawImage(img, x - off, y - off,
                    (int) Math.round(IMG_W * scale), (int) Math.round(IMG_H * scale), null);
        } else {
            g.setColor(pressed ? new Color(40, 30, 20) : (hovered ? new Color(120, 105, 95) : new Color(70, 66, 62)));
            g.fillRect(x, y, bw, bh);
        }

        int size = 16 * Math.max(1, (int) Math.floor(2 * scale + 0.25));
        if (font == null || FontSize != size) {
            font = loadFont("font/PeaberryBase.ttf", size);
            FontSize = size;
        }
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (bw - fm.stringWidth(label)) / 2;
        int ty = y + (bh - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent()
                + (pressed ? (int) Math.round(3 * scale) : 0);
        int sh = Math.max(1, (int) Math.round(2 * scale));

        g.setColor(SHADOW);
        g.drawString(label, tx + sh, ty + sh);
        g.setColor(pressed ? TEXT_PRESSED : (hovered ? TEXT_HOVER : TEXT_NORMAL));
        g.drawString(label, tx, ty);

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
        if (oldTextAA != null) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAA);
        }
    }
}
