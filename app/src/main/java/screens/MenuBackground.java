package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class MenuBackground {
    private static MenuBackground instance;

    public static MenuBackground get() {
        if (instance  == null) {
            instance = new MenuBackground();
        }
        return instance;
    }

    private static final double[][] FIRES = {
            {2,  66.8,  67.4,   0},  // đuốc tường trái
            {2, 194.6,  67.0, 180},  // đuốc tường phải
            {1, 109.7,  91.1, 300},  // lửa nhỏ trên bệ đá giữa
            {4,  84.5,  98.2, 120},  // chậu lửa vừa bên trái
            {4, 172.9,  98.9, 240},  // chậu lửa vừa bên phải
            {3,  70.0, 122.2,  60},  // chậu lửa lớn tiền cảnh
    };

    private BufferedImage background;
    private final FireAnimation[] fires = new FireAnimation[4];

    private MenuBackground() {
        String path = "sprites/menubackground.png";
        if (MenuBackground.class.getClassLoader().getResource(path) == null) {
            System.err.println("[MenuBackground] KHONG TIM THAY ANH: " + path);
        } else {
            background = Resources.images().get(path);
        }
        for (int i = 0; i < 4; i++) {
            fires[i] = FireAnimation.load(i + 1);
        }
    }

    public void draw(Graphics2D g, int w, int h) {
        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        if (background != null) {
            g.drawImage(background, 0, 0, w, h, null);
        }

        double scale = w / 256.0;
        long now = Game.time().now();
        for (double[] f : FIRES) {
            FireAnimation anim = fires[(int) f[0] - 1];
            if (anim != null) {
                anim.draw(g, f[1] * scale, f[2] * scale, scale, now, (long) f[3]);
            }
        }

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
    }
}

