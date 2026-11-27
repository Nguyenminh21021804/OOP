package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.environment.Environment;
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
import java.util.HashSet;
import java.util.Set;

public class SpearThrustEntity extends Entity implements IUpdateable, IRenderable {

    private final Player owner;
    private final BufferedImage[] thrustFrames;
    private final BufferedImage[] hitFrames;
    private final int damage;
    private final double angleRad;
    private final double originOffset;
    private final double drawW;
    private final double drawH;
    private final double hitEffectW;
    private final double hitEffectH;
    private final double hitboxLength;
    private final double hitboxThickness;
    private final int thrustFrameMs;
    private final int hitFrameMs;

    private final long spawnTimeMs;
    private double ownerCenterX;
    private double ownerCenterY;
    private double originX;
    private double originY;
    private double clearFromCenter;
    private double clippedForward;
    private boolean wallSparkSpawned = false;

    private final Set<ICombatEntity> hitTargets = new HashSet<>();

    public SpearThrustEntity(
            Player owner,
            BufferedImage[] thrustFrames,
            BufferedImage[] hitFrames,
            int damage,
            double angleRad,
            double originOffset,
            double drawW,
            double drawH,
            double hitEffectW,
            double hitEffectH,
            double hitboxLength,
            double hitboxThickness,
            int thrustFrameMs,
            int hitFrameMs
    ) {
        super();
        this.owner = owner;
        this.thrustFrames = (thrustFrames != null) ? thrustFrames : new BufferedImage[0];
        this.hitFrames = (hitFrames != null) ? hitFrames : new BufferedImage[0];
        this.damage = damage;
        this.angleRad = angleRad;
        this.originOffset = originOffset;
        this.drawW = drawW;
        this.drawH = drawH;
        this.hitEffectW = hitEffectW;
        this.hitEffectH = hitEffectH;
        this.hitboxLength = hitboxLength;
        this.hitboxThickness = hitboxThickness;
        this.thrustFrameMs = Math.max(16, thrustFrameMs);
        this.hitFrameMs = Math.max(16, hitFrameMs);
        this.spawnTimeMs = System.currentTimeMillis();

        this.setRenderType(RenderType.NORMAL);
        this.setWidth(drawW);
        this.setHeight(drawH);
        updateOriginFromOwner(Game.world().environment());
    }

    private void updateOriginFromOwner(Environment env) {
        this.ownerCenterX = owner.getX() + owner.getWidth() / 2.0;
        this.ownerCenterY = owner.getY() + owner.getHeight() * 0.68;
        this.originX = ownerCenterX + Math.cos(angleRad) * originOffset;
        this.originY = ownerCenterY + Math.sin(angleRad) * originOffset;
        this.setLocation(originX - drawW / 2.0, originY - drawH / 2.0);

        double maxFromCenter = originOffset + Math.max(drawW, hitboxLength);
        if (env != null) {
            this.clearFromCenter = WeaponCollision.raycastObstacleDistance(
                    env, ownerCenterX, ownerCenterY, angleRad, maxFromCenter, 2.0, owner
            );
            this.clippedForward = Math.max(0.0, clearFromCenter - originOffset);
        } else {
            this.clearFromCenter = maxFromCenter;
            this.clippedForward = Math.max(drawW, hitboxLength);
        }
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) return;

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        if (thrustFrames.length == 0 || elapsed >= (long) thrustFrames.length * thrustFrameMs) {
            env.remove(this);
            return;
        }

        updateOriginFromOwner(env);

        int frameIdx = (int) (elapsed / thrustFrameMs);
        if (frameIdx >= 1 && frameIdx <= 5) {
            double activeLen = Math.min(hitboxLength, clippedForward + 2.0);
            if (activeLen > 2.0) {
                Shape thrustShape = getThrustWorldShape(activeLen);

                for (ICombatEntity target : env.getCombatEntities()) {
                    if (target == owner || target.isDead()) continue;
                    if (target.getTeam() == owner.getTeam()) continue;
                    if (hitTargets.contains(target)) continue;

                    Rectangle2D tb = target.getBoundingBox();
                    if (thrustShape.intersects(tb)
                            && WeaponCollision.hasLineOfSight(env, ownerCenterX, ownerCenterY, tb.getCenterX(), tb.getCenterY(), owner)) {
                        target.hit(damage);
                        hitTargets.add(target);
                        spawnHitEffect(env, tb.getCenterX(), tb.getCenterY());
                    }
                }
            }

            // Đâm trúng tường / cột đá -> đặt tia nổ đúng tại bề mặt vật cản ở phía trước mũi thương
            if (!wallSparkSpawned && clippedForward < hitboxLength - 1.5) {
                wallSparkSpawned = true;
                double sparkDist = Math.max(originOffset + 3.0, clearFromCenter + 1.0);
                double wx = ownerCenterX + Math.cos(angleRad) * sparkDist;
                double wy = ownerCenterY + Math.sin(angleRad) * sparkDist;
                spawnHitEffect(env, wx, wy);
            }
        }
    }

    private void spawnHitEffect(Environment env, double x, double y) {
        if (hitFrames.length == 0) return;
        env.add(new HitEffectEntity(
                hitFrames,
                x, y,
                hitEffectW, hitEffectH,
                Math.cos(angleRad) < 0,
                hitFrameMs
        ));
    }

    private Shape getThrustWorldShape(double length) {
        Rectangle2D local = new Rectangle2D.Double(0.0, -hitboxThickness / 2.0, length, hitboxThickness);
        AffineTransform tx = new AffineTransform();
        tx.translate(originX, originY);
        tx.rotate(angleRad);
        return tx.createTransformedShape(local);
    }

    @Override
    public void render(Graphics2D g) {
        if (thrustFrames.length == 0 || Game.world().camera() == null) return;

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        int idx = (int) Math.min(thrustFrames.length - 1, Math.max(0, elapsed / thrustFrameMs));
        BufferedImage frame = thrustFrames[idx];
        if (frame == null) return;

        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(originX, originY));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());
        g2.rotate(angleRad);

        double clipLen = Math.max(2.0, Math.min(drawW, clippedForward + 2.0));
        g2.clipRect(-4, (int) Math.floor(-drawH), (int) Math.ceil(clipLen + 4.0), (int) Math.ceil(drawH * 2.0));

        if (Math.cos(angleRad) < 0) {
            g2.scale(1, -1);
        }

        g2.drawImage(
                frame,
                0,
                (int) Math.round(-drawH / 2.0),
                (int) Math.round(drawW),
                (int) Math.round(drawH),
                null
        );

        if (idx >= 1 && idx <= 5 && Game.config().debug().renderCollisionBoxes()) {
            double activeLen = Math.min(hitboxLength, clippedForward + 2.0);
            g2.setColor(new Color(255, 220, 40, 210));
            g2.drawRect(
                    0,
                    (int) Math.round(-hitboxThickness / 2.0),
                    (int) Math.round(activeLen),
                    (int) Math.round(hitboxThickness)
            );
        }

        g2.dispose();
    }
}