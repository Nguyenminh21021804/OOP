package traps;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

public class TiledObjectEntity extends Entity implements IRenderable {

    private final BufferedImage[] frames;
    private final int[] frameDurations;
    private final long totalDurationMs;
    private final long startTimeMs;

    // Tọa độ gốc góc DƯỚI-TRÁI (anchorX, anchorY) đúng chuẩn Tile Object của Tiled
    private final double anchorX;
    private final double anchorY;
    private final double worldW;
    private final double worldH;
    private final boolean flipH;
    private final boolean flipV;
    private final double rotationDeg;

    public TiledObjectEntity(
            double anchorX,
            double anchorY,
            double w,
            double h,
            BufferedImage[] frames,
            int[] frameDurations,
            boolean flipH,
            boolean flipV,
            double rotationDeg,
            RenderType renderType
    ) {
        super();
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.worldW = Math.max(1.0, w);
        this.worldH = Math.max(1.0, h);
        this.frames = (frames != null) ? frames : new BufferedImage[0];
        this.flipH = flipH;
        this.flipV = flipV;
        this.rotationDeg = rotationDeg;
        this.startTimeMs = System.currentTimeMillis();

        int count = this.frames.length;
        if (count > 1) {
            this.frameDurations = new int[count];
            long sum = 0;
            for (int i = 0; i < count; i++) {
                int d = (frameDurations != null && i < frameDurations.length && frameDurations[i] > 0)
                        ? frameDurations[i]
                        : 150;
                this.frameDurations[i] = d;
                sum += d;
            }
            this.totalDurationMs = Math.max(1L, sum);
        } else {
            this.frameDurations = new int[0];
            this.totalDurationMs = 1L;
        }

        this.setRenderType(renderType != null ? renderType : RenderType.NORMAL);
        this.setLocation(anchorX, anchorY - this.worldH);
        this.setWidth(this.worldW);
        this.setHeight(this.worldH);
    }

    private BufferedImage getCurrentFrame() {
        if (frames.length == 0) return null;
        if (frames.length == 1) return frames[0];

        long t = (System.currentTimeMillis() - startTimeMs) % totalDurationMs;
        long acc = 0;
        for (int i = 0; i < frames.length; i++) {
            acc += frameDurations[i];
            if (t < acc) {
                return frames[i];
            }
        }
        return frames[frames.length - 1];
    }

    @Override
    public void render(Graphics2D g) {
        BufferedImage img = getCurrentFrame();
        if (img == null || Game.world().camera() == null) return;

        Point2D vpAnchor = Game.world().camera().getViewportLocation(new Point2D.Double(anchorX, anchorY));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vpAnchor.getX(), vpAnchor.getY());
        if (Math.abs(rotationDeg) > 0.01) {
            g2.rotate(Math.toRadians(rotationDeg));
        }

        // Sau khi xoay quanh góc dưới-trái, khung ảnh nằm ở [0 .. worldW] và [-worldH .. 0]
        g2.translate(worldW / 2.0, -worldH / 2.0);
        if (flipH || flipV) {
            g2.scale(flipH ? -1 : 1, flipV ? -1 : 1);
        }

        g2.drawImage(
                img,
                (int) Math.round(-worldW / 2.0),
                (int) Math.round(-worldH / 2.0),
                (int) Math.round(worldW),
                (int) Math.round(worldH),
                null
        );
        g2.dispose();
    }
}