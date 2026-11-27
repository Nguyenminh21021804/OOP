package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

public class HitEffectEntity extends Entity implements IUpdateable, IRenderable {

    private final BufferedImage[] frames;
    private final double centerX;
    private final double centerY;
    private final double drawW;
    private final double drawH;
    private final boolean flipX;
    private final int frameDurationMs;
    private final long spawnTimeMs;

    public HitEffectEntity(
            BufferedImage[] frames,
            double centerX,
            double centerY,
            double drawW,
            double drawH,
            boolean flipX,
            int frameDurationMs
    ) {
        super();
        this.frames = (frames != null) ? frames : new BufferedImage[0];
        this.centerX = centerX;
        this.centerY = centerY;
        this.drawW = drawW;
        this.drawH = drawH;
        this.flipX = flipX;
        this.frameDurationMs = Math.max(15, frameDurationMs);
        this.spawnTimeMs = System.currentTimeMillis();

        this.setRenderType(RenderType.OVERLAY);
        this.setWidth(drawW);
        this.setHeight(drawH);
        this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) return;

        if (frames.length == 0) {
            env.remove(this);
            return;
        }
        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        if (elapsed >= (long) frames.length * frameDurationMs) {
            env.remove(this);
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (frames.length == 0 || Game.world().camera() == null) return;

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        int idx = (int) Math.min(frames.length - 1, Math.max(0, elapsed / frameDurationMs));
        BufferedImage frame = frames[idx];
        if (frame == null) return;

        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(centerX, centerY));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());
        if (flipX) {
            g2.scale(-1, 1);
        }

        g2.drawImage(
                frame,
                (int) Math.round(-drawW / 2.0),
                (int) Math.round(-drawH / 2.0),
                (int) Math.round(drawW),
                (int) Math.round(drawH),
                null
        );
        g2.dispose();
    }
}