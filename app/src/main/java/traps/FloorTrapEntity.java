package traps;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;
import player.Player;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class FloorTrapEntity extends Entity implements IUpdateable, IRenderable {

    public enum TrapKind {
        SPIKE_CYCLE,
        SPIKE_PERMANENT,
        FIRE_CYCLE,
        FIRE_PERMANENT
    }

    private final Player player;
    private final TrapKind kind;
    private final String debugTag;
    private final BufferedImage[] frames;
    private final int[] frameDurations;
    private final boolean[] frameDangerous;
    private final long totalAnimDurationMs;
    private final boolean renderSprite;

    private final double anchorX;
    private final double anchorY;
    private final boolean anchorAtBottom;
    private final double worldW;
    private final double worldH;
    private final boolean flipH;
    private final boolean flipV;
    private final double rotationDeg;
    private final int damage;
    private final long phaseOffsetMs;
    private final long startTimeMs;

    private boolean currentlyDangerous = false;
    private int currentFrameIdx = 0;

    public FloorTrapEntity(
            Player player,
            TrapKind kind,
            String debugTag,
            double x,
            double y,
            double w,
            double h,
            BufferedImage[] frames,
            int[] frameDurations,
            int damage,
            long phaseOffsetMs
    ) {
        this(player, kind, debugTag, x, y, false, w, h, frames, frameDurations,
                false, false, 0.0, damage, phaseOffsetMs, true);
    }

    public FloorTrapEntity(
            Player player,
            TrapKind kind,
            String debugTag,
            double anchorX,
            double anchorY,
            boolean anchorAtBottom,
            double w,
            double h,
            BufferedImage[] frames,
            int[] frameDurations,
            boolean flipH,
            boolean flipV,
            double rotationDeg,
            int damage,
            long phaseOffsetMs,
            boolean renderSprite
    ) {
        super();
        this.player = player;
        this.kind = kind;
        this.debugTag = (debugTag != null && !debugTag.isEmpty()) ? debugTag : kind.name();
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorAtBottom = anchorAtBottom;
        this.worldW = Math.max(8.0, w);
        this.worldH = Math.max(8.0, h);
        this.frames = (frames != null) ? frames : new BufferedImage[0];
        this.flipH = flipH;
        this.flipV = flipV;
        this.rotationDeg = rotationDeg;
        this.renderSprite = renderSprite;
        // Cố định mỗi lần giẫm gai / lửa chỉ trừ đúng 1 Giáp (hết Giáp mới trừ 1 HP)
        this.damage = 1;
        this.phaseOffsetMs = phaseOffsetMs;
        this.startTimeMs = System.currentTimeMillis();

        int count = this.frames.length;
        if (count > 0) {
            double[] scores = new double[count];
            int minIdx = 0;
            int maxIdx = 0;
            for (int i = 0; i < count; i++) {
                scores[i] = measureVisualEnergy(this.frames[i]);
                if (scores[i] < scores[minIdx]) minIdx = i;
                if (scores[i] > scores[maxIdx]) maxIdx = i;
            }

            this.frameDangerous = computeDangerFlags(scores, kind);
            this.frameDurations = new int[count];

            boolean hasExplicitDurs = (frameDurations != null && frameDurations.length == count);
            long sum = 0;
            for (int i = 0; i < count; i++) {
                int d;
                if (hasExplicitDurs && frameDurations[i] > 0) {
                    d = frameDurations[i];
                } else if (kind == TrapKind.FIRE_PERMANENT || kind == TrapKind.SPIKE_PERMANENT) {
                    d = 120;
                } else if (i == minIdx) {
                    d = 1050;
                } else if (i == maxIdx) {
                    d = 1100;
                } else {
                    d = 150;
                }
                this.frameDurations[i] = d;
                sum += d;
            }
            this.totalAnimDurationMs = Math.max(1L, sum);
        } else {
            if (renderSprite) {
                System.err.println("[FloorTrapEntity] Khong tim thay sprite cho bay: " + this.debugTag);
            }
            this.frameDurations = new int[0];
            this.frameDangerous = new boolean[0];
            this.totalAnimDurationMs = 2600L;
        }

        this.setRenderType(RenderType.SURFACE);
        this.setLocation(anchorX, anchorAtBottom ? (anchorY - this.worldH) : anchorY);
        this.setWidth(worldW);
        this.setHeight(worldH);
    }

    private boolean isPermanentKind() {
        return kind == TrapKind.FIRE_PERMANENT || kind == TrapKind.SPIKE_PERMANENT;
    }

    private static boolean[] computeDangerFlags(double[] scores, TrapKind kind) {
        int n = scores.length;
        boolean[] flags = new boolean[n];
        if (kind == TrapKind.FIRE_PERMANENT || kind == TrapKind.SPIKE_PERMANENT || n <= 1) {
            for (int i = 0; i < n; i++) flags[i] = true;
            return flags;
        }

        double minScore = Double.MAX_VALUE;
        double maxScore = -Double.MAX_VALUE;
        for (double s : scores) {
            if (s < minScore) minScore = s;
            if (s > maxScore) maxScore = s;
        }

        double span = maxScore - minScore;
        if (span < 0.5) {
            for (int i = 0; i < n; i++) {
                flags[i] = (i >= n / 2);
            }
        } else {
            double threshold = minScore + span * 0.30;
            for (int i = 0; i < n; i++) {
                flags[i] = (scores[i] >= threshold);
            }
        }
        return flags;
    }

    private static double measureVisualEnergy(BufferedImage img) {
        if (img == null) return 0.0;
        double sum = 0.0;
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 20) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                if (lum > 105) {
                    sum += lum;
                }
            }
        }
        return sum;
    }

    @Override
    public void update() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startTimeMs + phaseOffsetMs);

        if (frames.length > 1) {
            long t = elapsed % totalAnimDurationMs;
            long acc = 0;
            int idx = frames.length - 1;
            for (int i = 0; i < frames.length; i++) {
                acc += frameDurations[i];
                if (t < acc) {
                    idx = i;
                    break;
                }
            }
            currentFrameIdx = idx;
            currentlyDangerous = frameDangerous[idx];
        } else if (frames.length == 1 || isPermanentKind()) {
            currentFrameIdx = 0;
            currentlyDangerous = true;
        } else {
            long t = elapsed % 2600L;
            currentlyDangerous = (t >= 1400L);
            currentFrameIdx = 0;
        }

        if (currentlyDangerous && player != null && !player.isDead() && !player.isUntouchableByTrap()) {
            Rectangle2D foot = player.getHitbox().getFootBox();
            if (foot != null) {
                Shape dmgShape = getDamageShape();
                if (dmgShape.intersects(foot)) {
                    Rectangle2D bounds = dmgShape.getBounds2D();
                    player.hitByTrap(damage, bounds.getCenterX(), bounds.getCenterY());
                }
            }
        }
    }

    private AffineTransform buildTransform(double txX, double txY) {
        AffineTransform tx = new AffineTransform();
        tx.translate(txX, txY);
        if (Math.abs(rotationDeg) > 0.01) {
            tx.rotate(Math.toRadians(rotationDeg));
        }
        tx.translate(worldW / 2.0, anchorAtBottom ? (-worldH / 2.0) : (worldH / 2.0));
        return tx;
    }

    public Shape getDamageShape() {
        double padX = worldW * 0.14;
        double padY = worldH * 0.14;
        double bw = Math.max(4.0, worldW - padX * 2.0);
        double bh = Math.max(4.0, worldH - padY * 2.0);
        Rectangle2D local = new Rectangle2D.Double(-bw / 2.0, -bh / 2.0, bw, bh);
        return buildTransform(anchorX, anchorY).createTransformedShape(local);
    }

    public Rectangle2D getDamageBox() {
        return getDamageShape().getBounds2D();
    }

    @Override
    public void render(Graphics2D g) {
        if (Game.world().camera() == null) return;

        Point2D vpAnchor = Game.world().camera().getViewportLocation(new Point2D.Double(anchorX, anchorY));

        if (renderSprite && frames.length > 0 && frames[currentFrameIdx] != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.transform(buildTransform(vpAnchor.getX(), vpAnchor.getY()));
            if (flipH || flipV) {
                g2.scale(flipH ? -1 : 1, flipV ? -1 : 1);
            }
            g2.drawImage(
                    frames[currentFrameIdx],
                    (int) Math.round(-worldW / 2.0),
                    (int) Math.round(-worldH / 2.0),
                    (int) Math.round(worldW),
                    (int) Math.round(worldH),
                    null
            );
            g2.dispose();
        }

        if (Game.config().debug().renderCollisionBoxes()) {
            Graphics2D dg = (Graphics2D) g.create();
            dg.setColor(currentlyDangerous ? new Color(255, 65, 50, 230) : new Color(150, 150, 165, 160));
            double padX = worldW * 0.14;
            double padY = worldH * 0.14;
            double bw = Math.max(4.0, worldW - padX * 2.0);
            double bh = Math.max(4.0, worldH - padY * 2.0);
            dg.transform(buildTransform(vpAnchor.getX(), vpAnchor.getY()));
            dg.drawRect(
                    (int) Math.round(-bw / 2.0),
                    (int) Math.round(-bh / 2.0),
                    (int) Math.round(bw),
                    (int) Math.round(bh)
            );
            dg.dispose();
        }
    }
}