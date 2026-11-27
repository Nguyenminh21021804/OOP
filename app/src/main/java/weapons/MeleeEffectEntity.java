package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;
import de.gurkenlabs.litiengine.resources.Resources;
import player.Player;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MeleeEffectEntity extends Entity implements IUpdateable, IRenderable {

    private static final Map<String, BufferedImage[]> FRAME_CACHE = new HashMap<>();

    private final Player owner;
    private final BufferedImage[] frames;
    private final int damage;
    private final double angleRad;
    private final double offsetDist;
    private final double forwardSpeed;
    private final boolean followOwner;
    private final double drawW;
    private final double drawH;
    private final double hitW;
    private final double hitH;
    private final int frameDurationMs;

    private final long spawnTimeMs;
    private long lastUpdateNano;
    private double centerX;
    private double centerY;

    private final Set<ICombatEntity> hitTargets = new HashSet<>();

    public MeleeEffectEntity(
            Player owner,
            String stripPath,
            int frameCount,
            int damage,
            double angleRad,
            double offsetDist,
            double forwardSpeed,
            boolean followOwner,
            double drawW,
            double drawH,
            double hitW,
            double hitH,
            int frameDurationMs
    ) {
        super();
        this.owner = owner;
        this.frames = loadFrames(stripPath, frameCount);
        this.damage = damage;
        this.angleRad = angleRad;
        this.offsetDist = offsetDist;
        this.forwardSpeed = forwardSpeed;
        this.followOwner = followOwner;
        this.drawW = drawW;
        this.drawH = drawH;
        this.hitW = hitW;
        this.hitH = hitH;
        this.frameDurationMs = frameDurationMs;

        this.spawnTimeMs = System.currentTimeMillis();
        this.lastUpdateNano = System.nanoTime();

        this.setRenderType(RenderType.NORMAL);
        this.setWidth(drawW);
        this.setHeight(drawH);

        updatePositionFromOwner();
    }

    private static BufferedImage[] loadFrames(String path, int frameCount) {
        if (FRAME_CACHE.containsKey(path)) {
            return FRAME_CACHE.get(path);
        }

        BufferedImage strip = Resources.images().get(path);
        if (strip == null) {
            System.err.println("[MeleeEffectEntity] Khong tim thay anh hieu ung: " + path);
            return new BufferedImage[0];
        }

        int count = Math.max(1, frameCount);
        int cellW = Math.max(1, strip.getWidth() / count);
        int cellH = strip.getHeight();
        BufferedImage[] sliced = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            sliced[i] = strip.getSubimage(i * cellW, 0, cellW, cellH);
        }
        FRAME_CACHE.put(path, sliced);
        return sliced;
    }

    private void updatePositionFromOwner() {
        double ox = owner.getX() + owner.getWidth() / 2.0;
        double oy = owner.getY() + owner.getHeight() * 0.55;
        this.centerX = ox + Math.cos(angleRad) * offsetDist;
        this.centerY = oy + Math.sin(angleRad) * offsetDist;
        syncEntityBounds();
    }

    private void syncEntityBounds() {
        this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) return;

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        long totalDuration = (long) Math.max(1, frames.length) * frameDurationMs;
        if (elapsed >= totalDuration) {
            env.remove(this);
            return;
        }

        long nowNano = System.nanoTime();
        double dt = Math.min(0.05, (nowNano - lastUpdateNano) / 1_000_000_000.0);
        lastUpdateNano = nowNano;

        if (followOwner) {
            updatePositionFromOwner();
        } else if (forwardSpeed != 0) {
            double step = forwardSpeed * dt;
            double nextX = centerX + Math.cos(angleRad) * step;
            double nextY = centerY + Math.sin(angleRad) * step;
            Rectangle2D probe = new Rectangle2D.Double(nextX - 2.5, nextY - 2.5, 5.0, 5.0);
            if (WeaponCollision.isBlocked(env, probe, owner)) {
                env.remove(this);
                return;
            }
            centerX = nextX;
            centerY = nextY;
            syncEntityBounds();
        }

        double ox = owner.getX() + owner.getWidth() / 2.0;
        double oy = owner.getY() + owner.getHeight() * 0.65;

        Rectangle2D attackBox = getAttackHitbox();
        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;
            if (hitTargets.contains(target)) continue;

            Rectangle2D tb = target.getBoundingBox();
            if (tb.intersects(attackBox)
                    && WeaponCollision.hasLineOfSight(env, ox, oy, tb.getCenterX(), tb.getCenterY(), owner)) {
                target.hit(damage);
                hitTargets.add(target);
            }
        }
    }

    public Rectangle2D getAttackHitbox() {
        return new Rectangle2D.Double(centerX - hitW / 2.0, centerY - hitH / 2.0, hitW, hitH);
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
        g2.rotate(angleRad);

        if (Math.cos(angleRad) < 0) {
            g2.scale(1, -1);
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

        if (Game.config().debug().renderCollisionBoxes()) {
            Rectangle2D box = getAttackHitbox();
            Point2D boxVp = Game.world().camera().getViewportLocation(new Point2D.Double(box.getX(), box.getY()));
            Graphics2D dg = (Graphics2D) g.create();
            dg.setColor(new Color(255, 220, 40, 210));
            dg.drawRect(
                    (int) Math.round(boxVp.getX()),
                    (int) Math.round(boxVp.getY()),
                    (int) Math.round(box.getWidth()),
                    (int) Math.round(box.getHeight())
            );
            dg.dispose();
        }
    }
}