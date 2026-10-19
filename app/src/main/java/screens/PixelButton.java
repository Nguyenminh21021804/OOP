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

    private static final int OFFSET_X = 9;
    private static final int OFFSET_Y = 9;
    private static final int TEXT_OFFSET_Y = 0;
    private static final Color SHADOW = new Color(12, 6, 4);
    private static final Color TEXT_NORMAL = new Color(226, 216, 196);
    private static final Color TEXT_HOVER = Color.WHITE;
    private static final Color TEXT_PRESSED = new Color(255, 214, 90);

    private static BufferedImage normalImg, hoverImg, pressedImg;
    private static Font font;

    private final String label;

    public PixelButton(double x, double y, String label) {
        super(x, y, BODY_W, BODY_H, label);
        this.label = label;
        loadAssets();
    }

    private static void loadAssets() {
        if (normalImg == null) {
            normalImg = Resources.images().get("sprites/ui/button_normal.png");
            hoverImg = Resources.images().get("sprites/ui/button_hover.png");
            pressedImg = Resources.images().get("sprites/ui/button_pressed.png");
            font = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont(32f);
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

        int x = (int) getX();
        int y = (int) getY();

        g.drawImage(img, x - OFFSET_X, y - OFFSET_Y, null);

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (BODY_W - fm.stringWidth(label)) / 2;
        int ty = y + (BODY_H - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent()
                + TEXT_OFFSET_Y + (pressed ? 3 : 0);

        g.setColor(SHADOW);
        g.drawString(label, tx + 2, ty + 2);
        g.setColor(pressed ? TEXT_PRESSED : (hovered ? TEXT_HOVER : TEXT_NORMAL));
        g.drawString(label, tx, ty);

        if (oldAA != null) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldAA);
        }
    }
}