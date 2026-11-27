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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class FloorTrapEntity extends Entity implements IUpdateable, IRenderable {

    public enum TrapKind {
        SPIKE_CYCLE,
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

    private final double worldX;
    private final double worldY;
    private final double worldW;
    private final double worldH;
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
        super();
        this.player = player;
        this.kind = kind;
        this.debugTag = (debugTag != null && !debugTag.isEmpty()) ? debugTag : kind.name();
        this.worldX = x;
        this.worldY = y;
        this.worldW = Math.max(8.0, w);
        this.worldH = Math.max(8.0, h);
        this.frames = (frames != null) ? frames : new BufferedImage[0];
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
                } else if (kind == TrapKind.FIRE_PERMANENT) {
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
            System.err.println("[FloorTrapEntity] Khong tim thay sprite cho bay: " + this.debugTag);
            this.frameDurations = new int[0];
            this.frameDangerous = new boolean[0];
            this.totalAnimDurationMs = 2600L;
        }

        this.setRenderType(RenderType.SURFACE);
        this.setLocation(worldX, worldY);
        this.setWidth(worldW);
        this.setHeight(worldH);
    }

    private static boolean[] computeDangerFlags(double[] scores, TrapKind kind) {
        int n = scores.length;
        boolean[] flags = new boolean[n];
        if (kind == TrapKind.FIRE_PERMANENT || n <= 1) {
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
        } else if (kind == TrapKind.FIRE_PERMANENT) {
            currentFrameIdx = 0;
            currentlyDangerous = true;
        } else {
            long t = elapsed % 2600L;
            currentlyDangerous = (t >= 1400L);
            currentFrameIdx = 0;
        }

        if (currentlyDangerous && player != null && !player.isDead() && !player.isUntouchableByTrap()) {
            Rectangle2D foot = player.getHitbox().getFootBox();
            if (foot != null && foot.intersects(getDamageBox())) {
                double trapCenterX = worldX + worldW / 2.0;
                double trapCenterY = worldY + worldH / 2.0;
                player.hitByTrap(damage, trapCenterX, trapCenterY);
            }
        }
    }

    public Rectangle2D getDamageBox() {
        double padX = worldW * 0.14;
        double padY = worldH * 0.14;
        return new Rectangle2D.Double(
                worldX + padX,
                worldY + padY,
                Math.max(4.0, worldW - padX * 2.0),
                Math.max(4.0, worldH - padY * 2.0)
        );
    }

    @Override
    public void render(Graphics2D g) {
        if (Game.world().camera() == null) return;

        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(worldX, worldY));
        int rx = (int) Math.round(vp.getX());
        int ry = (int) Math.round(vp.getY());
        int rw = (int) Math.round(worldW);
        int rh = (int) Math.round(worldH);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        if (frames.length > 0 && frames[currentFrameIdx] != null) {
            g2.drawImage(frames[currentFrameIdx], rx, ry, rw, rh, null);
        }

        if (Game.config().debug().renderCollisionBoxes()) {
            Rectangle2D dmgBox = getDamageBox();
            Point2D dvp = Game.world().camera().getViewportLocation(new Point2D.Double(dmgBox.getX(), dmgBox.getY()));
            g2.setColor(currentlyDangerous ? new Color(255, 65, 50, 230) : new Color(150, 150, 165, 160));
            g2.drawRect(
                    (int) Math.round(dvp.getX()),
                    (int) Math.round(dvp.getY()),
                    (int) Math.round(dmgBox.getWidth()),
                    (int) Math.round(dmgBox.getHeight())
            );
        }

        g2.dispose();
    }
}