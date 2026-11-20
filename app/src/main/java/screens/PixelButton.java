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
    private static boolean loaded = false;

    private final int bodyW;
    private final int bodyH;
    private double scale = 1.0;
    private Font font;
    private int fontSize = -1;
    private String label;

    public PixelButton(double x, double y, String label) {
        this(x, y, BODY_W, BODY_H, label);
    }

    public PixelButton(double x, double y, int bodyW, int bodyH, String label) {
        super(x, y, bodyW, bodyH, label);
        this.bodyW = bodyW;
        this.bodyH = bodyH;
        this.label = label;
        if (!loaded) {
            normalImg = loadImg("sprites/ui/button_normal.png");
            hoverImg = loadImg("sprites/ui/button_hover.png");
            pressedImg = loadImg("sprites/ui/button_pressed.png");
            loaded = true;
        }
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setScale(double k) {
        this.scale = k;
        setDimension(bodyW * k, bodyH * k);
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
        Font fallback = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont((float) size);
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

    private void drawNineSlice(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        int sw = img.getWidth();
        int sh = img.getHeight();
        int sx = 26;
        int sy = 24;
        int bx = Math.min((int) Math.round(sx * scale), w / 2);
        int by = Math.min((int) Math.round(sy * scale), h / 2);

        int[] dx = {x, x + bx, x + w - bx, x + w};
        int[] dy = {y, y + by, y + h - by, y + h};
        int[] srcX = {0, sx, sw - sx, sw};
        int[] srcY = {0, sy, sh - sy, sh};

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (dx[c + 1] > dx[c] && dy[r + 1] > dy[r]) {
                    g.drawImage(img,
                            dx[c], dy[r], dx[c + 1], dy[r + 1],
                            srcX[c], srcY[r], srcX[c + 1], srcY[r + 1], null);
                }
            }
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
        int bw = (int) Math.round(bodyW * scale);
        int bh = (int) Math.round(bodyH * scale);
        int off = (int) Math.round(OFFSET * scale);

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Object oldTextAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        if (img != null) {
            if (bodyW == BODY_W && bodyH == BODY_H) {
                g.drawImage(img, x - off, y - off,
                        (int) Math.round(IMG_W * scale), (int) Math.round(IMG_H * scale), null);
            } else {
                int dw = (int) Math.round((bodyW + 2 * OFFSET) * scale);
                int dh = (int) Math.round((bodyH + 2 * OFFSET) * scale);
                drawNineSlice(g, img, x - off, y - off, dw, dh);
            }
        } else {
            g.setColor(pressed ? new Color(40, 30, 20) : (hovered ? new Color(120, 105, 95) : new Color(70, 66, 62)));
            g.fillRect(x, y, bw, bh);
        }

        int baseMult = (bodyH < BODY_H) ? 1 : 2;
        int size = (bodyH < BODY_H)
                ? (int) Math.max(14, Math.round(24 * scale))
                : 16 * Math.max(1, (int) Math.floor(baseMult * scale + 0.25));
        if (font == null || fontSize != size) {
            font = loadFont("font/PeaberryBase.ttf", size);
            fontSize = size;
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