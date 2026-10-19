package screens;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.util.Random;

public class TitleArt {
    private static final String TITLE = "The Last Knight";
    private static final String SUBTITLE = "AN 8-BIT ADVENTURE";
    private static final int TITLE_TOP = 45;
    private static final int SUBTITLE_TOP = 158;
    private static final int PX = 6;
    private static final int LETTER_SPACING = 6;
    private static final int EMBER_COUNT = 22;
    private static final int EMBER_MIN_Y = 20;
    private static final int EMBER_MAX_Y = 200;

    private static final Color OUTLINE = new Color(20, 8, 4);
    private static final Color GOLD_TOP = new Color(255, 242, 170);
    private static final Color[] EMBER_COLORS = {
            new Color(255, 210, 90), new Color(255, 150, 40), new Color(255, 240, 170)
    };

    private final Font titleFont;
    private final Font subtitleFont;
    private final Random rng = new Random(5);

    private final float[] ex = new float[EMBER_COUNT];
    private final float[] ey = new float[EMBER_COUNT];
    private final float[] speed = new float[EMBER_COUNT];
    private final float[] phase = new float[EMBER_COUNT];
    private final int[] size = new int[EMBER_COUNT];
    private final Color[] color = new Color[EMBER_COUNT];

    private boolean emberInit = false;
    private long lastNano = System.nanoTime();

    public TitleArt(Font titleFont, Font subtitleFont) {
        this.titleFont = titleFont;
        this.subtitleFont = subtitleFont;
    }

    public void draw(Graphics2D g, int w, int h) {
        Composite oldComposite = g.getComposite();
        Paint oldPaint = g.getPaint();
        Font oldFont = g.getFont();
        Object oldTextAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

        long now = System.currentTimeMillis();
        long nanoNow = System.nanoTime();
        float dt = Math.min(0.1f, (nanoNow - lastNano) / 1_000_000_000f);
        lastNano = nanoNow;
        if (!emberInit) {
            initEmbers(w);
        }

        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 110), 0, 260, new Color(0, 0, 0, 0)));
        g.fillRect(0, 0, w, 260);

        drawTitle(g, w, now);

        drawSubtitle(g, w);

        updateAndDrawEmbers(g, w, dt, now);

        g.setComposite(oldComposite);
        g.setPaint(oldPaint);
        g.setFont(oldFont);
        if (oldTextAA != null) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAA);
        }
    }

    private void drawTitle(Graphics2D g, int w, long now) {
        g.setFont(titleFont);
        FontMetrics fm = g.getFontMetrics();
        int x = (w - fm.stringWidth(TITLE)) / 2;
        int base = TITLE_TOP + fm.getAscent();

        g.setColor(OUTLINE);
        for (int dx = -PX; dx <= PX; dx += PX) {
            for (int dy = -PX; dy <= 2 * PX; dy += PX) {
                g.drawString(TITLE, x + dx, base + dy);
            }
        }

        double f = 0.5 + 0.5 * Math.sin(now * 0.004);
        Color goldBottom = new Color(255, (int) (160 + 24 * f), 40);
        float size = titleFont.getSize2D();
        float top = TITLE_TOP + size * 0.12f;
        float bottom = TITLE_TOP + size * 0.85f;
        g.setPaint(new LinearGradientPaint(0, top, 0, bottom,
                new float[] {0f, 1f}, new Color[] {GOLD_TOP, goldBottom}));
        g.drawString(TITLE, x, base);
    }

    private void drawSubtitle(Graphics2D g, int w) {
        g.setFont(subtitleFont);
        FontMetrics fm = g.getFontMetrics();
        int base = SUBTITLE_TOP + fm.getAscent();

        int total = 0;
        for (char c : SUBTITLE.toCharArray()) {
            total += fm.charWidth(c) + LETTER_SPACING;
        }
        total -= LETTER_SPACING;

        int x = (w - total) / 2;
        for (char c : SUBTITLE.toCharArray()) {
            String s = String.valueOf(c);
            g.setColor(new Color(10, 4, 2));
            g.drawString(s, x + 2, base + 2);
            g.setColor(new Color(236, 190, 120));
            g.drawString(s, x, base);
            x += fm.charWidth(c) + LETTER_SPACING;
        }
    }

    private void initEmbers(int w) {
        for (int i = 0; i < EMBER_COUNT; i++) {
            respawn(i, w);
            ey[i] = EMBER_MIN_Y + rng.nextFloat() * (EMBER_MAX_Y - EMBER_MIN_Y);
        }
        emberInit = true;
    }

    private void respawn(int i, int w) {
        ex[i] = w * 0.22f + rng.nextFloat() * w * 0.56f;
        ey[i] = EMBER_MAX_Y + rng.nextFloat() * 30;
        speed[i] = 20 + rng.nextFloat() * 35;
        phase[i] = rng.nextFloat() * 6.28f;
        size[i] = rng.nextFloat() < 0.3f ? 8 : 4;
        color[i] = EMBER_COLORS[rng.nextInt(EMBER_COLORS.length)];
    }

    private void updateAndDrawEmbers(Graphics2D g, int w, float dt, long now) {
        for (int i = 0; i < EMBER_COUNT; i++) {
            ey[i] -= speed[i] * dt;
            ex[i] += (float) Math.sin(now * 0.002 + phase[i]) * 10 * dt;
            if (ey[i] < EMBER_MIN_Y) {
                respawn(i, w);
            }
            float alpha = Math.max(0f, Math.min(1f, (ey[i] - EMBER_MIN_Y) / 80f));
            int px = ((int) ex[i] / 4) * 4;
            int py = ((int) ey[i] / 4) * 4;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(color[i]);
            g.fillRect(px, py, size[i], size[i]);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }
}