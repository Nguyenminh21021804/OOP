package weapons;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class SlashEffect {
    private final double x, y;
    private final double angleRad;
    private final BufferedImage[] frames;
    private final double drawHeight;
    private final long frameDurationMs;
    private final long startTime;
    private boolean finished = false;

    public SlashEffect(double x, double y, double dirX, double dirY,
                       BufferedImage[] frames, double drawHeight, long totalDurationMs) {
        this.x = x;
        this.y = y;
        this.angleRad = Math.atan2(dirY, dirX);
        this.frames = frames;
        this.drawHeight = drawHeight;
        int count = (frames != null && frames.length > 0) ? frames.length : 4;
        this.frameDurationMs = Math.max(25, totalDurationMs / count);
        this.startTime = System.currentTimeMillis();
    }

    public void update(double dt) {
        if (frames == null || frames.length == 0) {
            finished = true;
            return;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= frames.length * frameDurationMs) {
            finished = true;
        }
    }

    public void render(Graphics2D g, double uiScale, double offsetY) {
        if (finished || frames == null || frames.length == 0) return;

        long elapsed = System.currentTimeMillis() - startTime;
        int idx = (int) (elapsed / frameDurationMs);
        if (idx >= frames.length) return;

        BufferedImage img = frames[idx];
        if (img == null) return;

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        double screenX = x * uiScale;
        double screenY = offsetY + y * uiScale;

        int h = (int) Math.max(8, Math.round(drawHeight * uiScale));
        double ratio = (double) img.getWidth() / Math.max(1, img.getHeight());
        int w = (int) Math.max(8, Math.round(h * ratio));

        AffineTransform old = g.getTransform();
        g.translate(screenX, screenY);
        g.rotate(angleRad);
        g.drawImage(img, -w / 2, -h / 2, w, h, null);
        g.setTransform(old);

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
    }

    public boolean isFinished() {
        return finished;
    }
}