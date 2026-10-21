package screens;

import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class FireAnimation {
    public static final int FPS = 60;
    private static final int FRAMES = 48;
    private static final int COLS = 8;

    private static final double[][] DEF = {
            {26, 26, 12.51, 12.90},
            {28, 28, 14.18, 14.50},
            {39, 39, 19.26, 23.50},
            {35, 35, 17.44, 20.32},
    };

    private final BufferedImage[] frames = new BufferedImage[FRAMES];
    private final int cellW;
    private final int cellH;
    private final double anchorX;
    private final double anchorY;

    private FireAnimation(BufferedImage atlas, double[] def) {
        this.cellW = (int) def[0];
        this.cellH = (int) def[1];
        this.anchorX = def[2];
        this.anchorY = def[3];
        for (int i = 0; i < FRAMES; i++) {
            frames[i] = atlas.getSubimage((i % COLS) * cellW, (i / COLS) * cellH, cellW, cellH);
        }
    }

    public static FireAnimation load(int type) {
        String path = "sprites/fx/fire" + type + ".png";
        if (FireAnimation.class.getClassLoader().getResource(path) == null) {
            System.err.println("[FireAnimation] KHONG TIM THAY FILE: " + path);
            return null;
        }
        try {
            return new FireAnimation(Resources.images().get(path), DEF[type - 1]);
        } catch (Exception e) {
            System.err.println("[FireAnimation] LOI NAP " + path + ": " + e.getMessage());
            return null;
        }
    }

    public void draw(Graphics2D g, double screenX, double screenY, double scale,
                     long nowMs, long offsetMs) {
        int idx = (int) Math.floorMod((nowMs + offsetMs) * FPS / 60, (long) FRAMES);
        int w = (int) Math.round(cellW * scale);
        int h = (int) Math.round(cellH * scale);
        int x = (int) Math.round(screenX - anchorX * scale);
        int y = (int) Math.round(screenY - anchorY * scale);
        g.drawImage(frames[idx], x, y, w, h, null);
    }
}