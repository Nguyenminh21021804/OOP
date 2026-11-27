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

public class SpearSkillEntity extends Entity implements IUpdateable, IRenderable {

    // Tọa độ tương đối 4 đỉnh zíc-zắc của Thương Lv3 (P0 -> P1 -> P2 -> P3) khớp với Hàng 2
    private static final double[] LV3_PX = { 0.08, 0.33, 0.58, 0.83 };
    private static final double[] LV3_PY = { 0.76, 0.24, 0.76, 0.24 };

    private final Player owner;
    private final int level;
    private final BufferedImage[] skillFrames;
    private final BufferedImage[] impactFrames;

    private final int pierceDamage;
    private final int burstDamage;
    private final double baseAngleRad;
    private final boolean flipY;

    private final double startX;
    private final double startY;
    private final double drawW;
    private final double drawH;
    private final double impactDrawW;
    private final double impactDrawH;
    private final int frameDurationMs;
    private final int impactFrameMs;

    private final double actualDashDist;
    private final double wallClipDist;
    private final boolean hitWall;

    // 4 điểm mốc trong tọa độ thế giới (LV1/LV2 dùng 2 điểm, LV3 dùng đủ 4 đỉnh zíc-zắc)
    private final double[] pathX;
    private final double[] pathY;

    private final long spawnTimeMs;
    private final long dashPhaseMs;
    private final int burstTriggerFrame;

    private boolean dashFinished = false;
    private boolean burstTriggered = false;
    private boolean wallImpactSpawned = false;
    private double prevLocalX;
    private double prevLocalY;
    private double currentSegmentAngle;

    private final Set<ICombatEntity> piercedTargets = new HashSet<>();
    private final Set<ICombatEntity> burstTargets = new HashSet<>();

    public SpearSkillEntity(
            Player owner,
            int level,
            BufferedImage[] skillFrames,
            BufferedImage[] impactFrames,
            int pierceDamage,
            int burstDamage,
            double angleRad,
            double targetDashDist,
            double drawW,
            double drawH,
            double impactDrawW,
            double impactDrawH,
            int frameDurationMs,
            int impactFrameMs
    ) {
        super();
        this.owner = owner;
        this.level = Math.max(1, Math.min(3, level));
        this.skillFrames = (skillFrames != null) ? skillFrames : new BufferedImage[0];
        this.impactFrames = (impactFrames != null) ? impactFrames : new BufferedImage[0];
        this.pierceDamage = Math.max(1, pierceDamage);
        this.burstDamage = Math.max(0, burstDamage);
        this.baseAngleRad = angleRad;
        this.flipY = Math.cos(angleRad) < 0;

        this.startX = owner.getX() + owner.getWidth() / 2.0;
        this.startY = owner.getY() + owner.getHeight() * 0.70;
        this.drawW = drawW;
        this.drawH = drawH;
        this.impactDrawW = impactDrawW;
        this.impactDrawH = impactDrawH;
        this.frameDurationMs = Math.max(20, frameDurationMs);
        this.impactFrameMs = Math.max(20, impactFrameMs);
        this.spawnTimeMs = System.currentTimeMillis();
        this.currentSegmentAngle = angleRad;

        Environment env = Game.world().environment();

        if (this.level == 3) {
            this.pathX = new double[4];
            this.pathY = new double[4];
            for (int i = 0; i < 4; i++) {
                double lx = (LV3_PX[i] - LV3_PX[0]) * drawW;
                double ly = (LV3_PY[i] - LV3_PY[0]) * drawH;
                Point2D.Double wp = localToWorld(lx, ly);
                this.pathX[i] = wp.x;
                this.pathY[i] = wp.y;
            }
            this.actualDashDist = targetDashDist;
            this.wallClipDist = drawW;
            this.hitWall = false;

            // LV3: 9 frame đầu lướt 3 chặng (3 frame/chặng), frame 9 kích nổ 3 đỉnh sao vàng
            int dashFrames = Math.min(9, Math.max(3, this.skillFrames.length / 2));
            this.dashPhaseMs = (long) dashFrames * this.frameDurationMs;
            this.burstTriggerFrame = dashFrames;
        } else {
            double clear = (env != null)
                    ? WeaponCollision.raycastObstacleDistance(env, startX, startY, angleRad, targetDashDist, 3.0, owner)
                    : targetDashDist;
            this.hitWall = (clear < targetDashDist - 1.5);
            this.actualDashDist = Math.max(0.0, Math.min(targetDashDist, clear));
            this.wallClipDist = clear + 6.0;

            this.pathX = new double[] { startX, startX + Math.cos(angleRad) * this.actualDashDist };
            this.pathY = new double[] { startY, startY + Math.sin(angleRad) * this.actualDashDist };

            int dashFrames = (this.level == 1) ? 5 : 5;
            this.dashPhaseMs = (long) dashFrames * this.frameDurationMs;
            this.burstTriggerFrame = (this.level == 2) ? 6 : -1;
        }

        this.prevLocalX = startX;
        this.prevLocalY = startY;

        this.setRenderType(RenderType.NORMAL);
        this.setWidth(drawW);
        this.setHeight(drawH);
        this.setLocation(startX - drawW / 2.0, startY - drawH / 2.0);

        owner.setWeaponDashing(true);
    }

    private Point2D.Double localToWorld(double lx, double ly) {
        double sy = flipY ? -ly : ly;
        double cos = Math.cos(baseAngleRad);
        double sin = Math.sin(baseAngleRad);
        return new Point2D.Double(
                startX + lx * cos - sy * sin,
                startY + lx * sin + sy * cos
        );
    }

    public boolean isDashingPhase() {
        return !dashFinished;
    }

    public double getCurrentAimAngle() {
        return currentSegmentAngle;
    }

    public void finishDashEarly() {
        if (!dashFinished) {
            dashFinished = true;
            if (owner != null) {
                owner.setWeaponDashing(false);
            }
        }
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) {
            finishDashEarly();
            return;
        }

        if (owner == null || owner.isDead()) {
            finishDashEarly();
            env.remove(this);
            return;
        }

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        int totalFrames = skillFrames.length;
        if (totalFrames == 0 || elapsed >= (long) totalFrames * frameDurationMs) {
            finishDashEarly();
            env.remove(this);
            return;
        }

        int frameIdx = (int) (elapsed / frameDurationMs);

        // 1. Pha lướt nhân vật theo đúng quỹ đạo của skill (thẳng cho Lv1/Lv2, zíc-zắc 3 chặng cho Lv3)
        if (!dashFinished) {
            double t = Math.min(1.0, (double) elapsed / Math.max(1L, dashPhaseMs));
            Point2D.Double targetPos = samplePathPosition(t);

            double stepX = targetPos.x - prevLocalX;
            double stepY = targetPos.y - prevLocalY;
            if (Math.hypot(stepX, stepY) > 0.01) {
                currentSegmentAngle = Math.atan2(stepY, stepX);
                owner.moveByWeaponDash(stepX, stepY);
            }
            prevLocalX = targetPos.x;
            prevLocalY = targetPos.y;

            applyPierceDamage(env);

            if (t >= 1.0) {
                finishDashEarly();
                if (level == 1 && hitWall && !wallImpactSpawned) {
                    wallImpactSpawned = true;
                    spawnImpact(env, pathX[1], pathY[1]);
                }
            }
        }

        // 2. Pha kích nổ đợt 2 (Lv2: nổ chữ X dọc đường lướt; Lv3: nổ đồng loạt 3 đỉnh sao vàng)
        if (burstTriggerFrame >= 0 && !burstTriggered && frameIdx >= burstTriggerFrame) {
            burstTriggered = true;
            if (level == 2) {
                triggerLv2Explosion(env);
            } else if (level == 3) {
                triggerLv3TripleExplosion(env);
            }
        }
    }

    private Point2D.Double samplePathPosition(double t) {
        if (level == 3) {
            double scaled = t * 3.0;
            int seg = Math.min(2, (int) Math.floor(scaled));
            double localT = Math.min(1.0, Math.max(0.0, scaled - seg));
            double x = pathX[seg] + (pathX[seg + 1] - pathX[seg]) * localT;
            double y = pathY[seg] + (pathY[seg + 1] - pathY[seg]) * localT;
            return new Point2D.Double(x, y);
        }

        double ease = 1.0 - (1.0 - t) * (1.0 - t);
        double x = pathX[0] + (pathX[1] - pathX[0]) * ease;
        double y = pathY[0] + (pathY[1] - pathY[0]) * ease;
        return new Point2D.Double(x, y);
    }

    private void applyPierceDamage(Environment env) {
        double px = owner.getX() + owner.getWidth() / 2.0;
        double py = owner.getY() + owner.getHeight() * 0.68;
        double radius = (level == 1) ? 12.0 : (level == 2 ? 14.0 : 16.0);
        Rectangle2D box = new Rectangle2D.Double(px - radius, py - radius, radius * 2.0, radius * 2.0);

        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;
            if (piercedTargets.contains(target)) continue;

            Rectangle2D tb = target.getBoundingBox();
            if (tb.intersects(box)) {
                target.hit(pierceDamage);
                piercedTargets.add(target);
                if (level == 1) {
                    spawnImpact(env, tb.getCenterX(), tb.getCenterY());
                }
            }
        }
    }

    private void triggerLv2Explosion(Environment env) {
        double len = Math.max(12.0, actualDashDist);
        Rectangle2D local = new Rectangle2D.Double(0.0, -14.0, len, 28.0);
        AffineTransform tx = new AffineTransform();
        tx.translate(startX, startY);
        tx.rotate(baseAngleRad);
        Shape blastShape = tx.createTransformedShape(local);

        boolean anyEnemyHit = false;
        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;
            if (burstTargets.contains(target)) continue;

            Rectangle2D tb = target.getBoundingBox();
            if (blastShape.intersects(tb) || piercedTargets.contains(target)) {
                target.hit(burstDamage);
                burstTargets.add(target);
                spawnImpact(env, tb.getCenterX(), tb.getCenterY());
                anyEnemyHit = true;
            }
        }

        if (!anyEnemyHit && hitWall && !wallImpactSpawned) {
            wallImpactSpawned = true;
            spawnImpact(env, pathX[1], pathY[1]);
        }
    }

    private void triggerLv3TripleExplosion(Environment env) {
        double blastRadius = 22.0;

        for (int i = 1; i <= 3; i++) {
            double vx = pathX[i];
            double vy = pathY[i];
            Rectangle2D area = new Rectangle2D.Double(
                    vx - blastRadius, vy - blastRadius, blastRadius * 2.0, blastRadius * 2.0
            );

            for (ICombatEntity target : env.getCombatEntities()) {
                if (target == owner || target.isDead()) continue;
                if (target.getTeam() == owner.getTeam()) continue;
                if (burstTargets.contains(target)) continue;

                Rectangle2D tb = target.getBoundingBox();
                if (tb.intersects(area)) {
                    target.hit(burstDamage);
                    burstTargets.add(target);
                    spawnImpact(env, tb.getCenterX(), tb.getCenterY());
                }
            }
        }

        // Gây sát thương kích nổ cho cả những mục tiêu đã bị lướt ngang qua ở chặng 1..3
        for (ICombatEntity target : piercedTargets) {
            if (target.isDead() || burstTargets.contains(target)) continue;
            target.hit(burstDamage);
            burstTargets.add(target);
            Rectangle2D tb = target.getBoundingBox();
            spawnImpact(env, tb.getCenterX(), tb.getCenterY());
        }
    }

    private void spawnImpact(Environment env, double x, double y) {
        if (impactFrames.length == 0) return;
        env.add(new HitEffectEntity(
                impactFrames,
                x, y,
                impactDrawW, impactDrawH,
                flipY,
                impactFrameMs
        ));
    }

    @Override
    public void render(Graphics2D g) {
        if (skillFrames.length == 0 || Game.world().camera() == null) return;

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        int idx = (int) Math.min(skillFrames.length - 1, Math.max(0, elapsed / frameDurationMs));
        BufferedImage frame = skillFrames[idx];
        if (frame == null) return;

        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(startX, startY));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());
        g2.rotate(baseAngleRad);

        if (level == 3) {
            if (flipY) {
                g2.scale(1, -1);
            }
            int dx = (int) Math.round(-LV3_PX[0] * drawW);
            int dy = (int) Math.round(-LV3_PY[0] * drawH);
            g2.drawImage(frame, dx, dy, (int) Math.round(drawW), (int) Math.round(drawH), null);
        } else {
            // Cắt gọn vệt lướt nếu đâm sát tường
            g2.clipRect(
                    -8,
                    (int) Math.floor(-drawH),
                    (int) Math.ceil(wallClipDist + 10.0),
                    (int) Math.ceil(drawH * 2.0)
            );
            if (flipY) {
                g2.scale(1, -1);
            }
            g2.drawImage(
                    frame,
                    -4,
                    (int) Math.round(-drawH / 2.0),
                    (int) Math.round(drawW),
                    (int) Math.round(drawH),
                    null
            );
        }

        if (Game.config().debug().renderCollisionBoxes()) {
            g2.setColor(new Color(255, 225, 60, 210));
            if (level == 3) {
                for (int i = 0; i < 3; i++) {
                    int x1 = (int) Math.round((LV3_PX[i] - LV3_PX[0]) * drawW);
                    int y1 = (int) Math.round((LV3_PY[i] - LV3_PY[0]) * drawH);
                    int x2 = (int) Math.round((LV3_PX[i + 1] - LV3_PX[0]) * drawW);
                    int y2 = (int) Math.round((LV3_PY[i + 1] - LV3_PY[0]) * drawH);
                    g2.drawLine(x1, y1, x2, y2);
                }
            } else {
                g2.drawRect(0, -10, (int) Math.round(Math.max(8.0, actualDashDist)), 20);
            }
        }

        g2.dispose();
    }
}