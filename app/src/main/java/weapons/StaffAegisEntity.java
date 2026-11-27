package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.CollisionBox;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.entities.IEntity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;
import player.Player;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StaffAegisEntity extends Entity implements IUpdateable, IRenderable {

    private static final List<StaffAegisEntity> ACTIVE_SHIELDS = new ArrayList<>();

    private enum Phase {
        STARTUP, ACTIVE, FADEOUT
    }

    private final Environment boundEnv;
    private final Player owner;
    private final int level;
    private final BufferedImage[] frames;
    private final boolean[] beamFrameFlags;
    private final BufferedImage[] impactFrames;

    private final int startupEndIdx;
    private final int fadeStartIdx;

    private final double drawW;
    private final double drawH;
    private final double pivotRatioX;
    private final double impactDrawW;
    private final double impactDrawH;

    private final double shieldRadius;
    private final double auraAttackRadius;
    private final double beamLength;
    private final double beamThickness;

    private final int auraDamage;
    private final int beamDamage;
    private final long auraTickMs;
    private final long beamTickMs;
    private final long activeDurationMs;
    private final int frameDurationMs;
    private final int impactFrameMs;

    private Phase phase = Phase.STARTUP;
    private long phaseStartMs;
    private int currentFrameIdx = 0;

    private double centerX;
    private double centerY;
    private double aimAngleRad;
    private double beamClearDist;

    private boolean wasBeamFrame = false;
    private boolean wallSparkInThisBurst = false;
    private boolean removed = false;

    private final Map<ICombatEntity, Long> lastAuraHitMs = new HashMap<>();
    private final Map<ICombatEntity, Long> lastBeamHitMs = new HashMap<>();

    public StaffAegisEntity(
            Environment env,
            Player owner,
            int level,
            BufferedImage[] frames,
            boolean[] beamFrameFlags,
            BufferedImage[] impactFrames,
            int startupEndIdx,
            int fadeStartIdx,
            double drawW,
            double drawH,
            double pivotRatioX,
            double impactDrawW,
            double impactDrawH,
            double shieldRadius,
            double auraAttackRadius,
            double beamLength,
            double beamThickness,
            int auraDamage,
            int beamDamage,
            long auraTickMs,
            long beamTickMs,
            long activeDurationMs,
            int frameDurationMs,
            int impactFrameMs,
            double initialAngleRad
    ) {
        super();
        this.boundEnv = env;
        this.owner = owner;
        this.level = Math.max(1, Math.min(3, level));
        this.frames = (frames != null) ? frames : new BufferedImage[0];
        this.beamFrameFlags = (beamFrameFlags != null && beamFrameFlags.length == this.frames.length)
                ? beamFrameFlags
                : new boolean[this.frames.length];
        this.impactFrames = (impactFrames != null) ? impactFrames : new BufferedImage[0];

        int total = this.frames.length;
        this.startupEndIdx = Math.max(0, Math.min(total, startupEndIdx));
        this.fadeStartIdx = Math.max(this.startupEndIdx, Math.min(total, fadeStartIdx));

        this.drawW = drawW;
        this.drawH = drawH;
        this.pivotRatioX = Math.max(0.1, Math.min(0.9, pivotRatioX));
        this.impactDrawW = impactDrawW;
        this.impactDrawH = impactDrawH;
        this.shieldRadius = Math.max(10.0, shieldRadius);
        this.auraAttackRadius = Math.max(this.shieldRadius, auraAttackRadius);
        this.beamLength = Math.max(0.0, beamLength);
        this.beamThickness = Math.max(6.0, beamThickness);
        this.auraDamage = Math.max(1, auraDamage);
        this.beamDamage = Math.max(1, beamDamage);
        this.auraTickMs = Math.max(120L, auraTickMs);
        this.beamTickMs = Math.max(100L, beamTickMs);
        this.activeDurationMs = Math.max(600L, activeDurationMs);
        this.frameDurationMs = Math.max(25, frameDurationMs);
        this.impactFrameMs = Math.max(20, impactFrameMs);
        this.aimAngleRad = initialAngleRad;
        this.beamClearDist = this.beamLength;

        this.phaseStartMs = System.currentTimeMillis();
        this.phase = (this.startupEndIdx > 0) ? Phase.STARTUP : Phase.ACTIVE;

        this.setRenderType(RenderType.OVERLAY);
        this.setWidth(drawW);
        this.setHeight(drawH);
        syncPositionWithOwner();

        ACTIVE_SHIELDS.add(this);
    }

    /**
     * Hàm tiện ích cho code đạn của quái gọi trực tiếp:
     * trả về true (và tự bật tia nổ chặn đạn) nếu tọa độ (x, y) chạm vào khiên Aegis đang hoạt động.
     */
    public static boolean tryBlockProjectile(Environment env, double x, double y, int attackerTeam) {
        if (env == null) return false;
        for (int i = ACTIVE_SHIELDS.size() - 1; i >= 0; i--) {
            StaffAegisEntity shield = ACTIVE_SHIELDS.get(i);
            if (shield.removed || shield.boundEnv != env || !shield.isBlocking()) continue;
            if (shield.owner != null && attackerTeam == shield.owner.getTeam()) continue;

            if (Math.hypot(x - shield.centerX, y - shield.centerY) <= shield.shieldRadius + 2.0) {
                shield.spawnImpactEffect(env, x, y);
                return true;
            }
        }
        return false;
    }

    public boolean isBlocking() {
        return !removed && (phase == Phase.STARTUP || phase == Phase.ACTIVE);
    }

    public void cancel() {
        cleanup(Game.world().environment());
    }

    private void cleanup(Environment env) {
        if (removed) return;
        removed = true;
        ACTIVE_SHIELDS.remove(this);
        if (env != null) {
            env.remove(this);
        } else if (boundEnv != null) {
            boundEnv.remove(this);
        }
    }

    private void syncPositionWithOwner() {
        if (owner == null) return;
        this.centerX = owner.getX() + owner.getWidth() / 2.0;
        this.centerY = owner.getY() + owner.getHeight() * 0.65;
        this.setLocation(centerX - drawW * pivotRatioX, centerY - drawH / 2.0);
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null || env != boundEnv || owner == null || owner.isDead() || frames.length == 0) {
            cleanup(env);
            return;
        }

        syncPositionWithOwner();

        long now = System.currentTimeMillis();
        long elapsed = now - phaseStartMs;

        // 1. Cập nhật pha hoạt ảnh: STARTUP -> ACTIVE (lặp vòng khiên/tia) -> FADEOUT
        if (phase == Phase.STARTUP) {
            int f = (int) (elapsed / frameDurationMs);
            if (f >= startupEndIdx) {
                phase = Phase.ACTIVE;
                phaseStartMs = now;
                currentFrameIdx = Math.min(frames.length - 1, startupEndIdx);
            } else {
                currentFrameIdx = f;
            }
        } else if (phase == Phase.ACTIVE) {
            if (elapsed >= activeDurationMs) {
                if (fadeStartIdx < frames.length) {
                    phase = Phase.FADEOUT;
                    phaseStartMs = now;
                    currentFrameIdx = fadeStartIdx;
                } else {
                    cleanup(env);
                    return;
                }
            } else {
                int loopLen = Math.max(1, fadeStartIdx - startupEndIdx);
                int offset = (int) ((elapsed / frameDurationMs) % loopLen);
                currentFrameIdx = Math.min(frames.length - 1, startupEndIdx + offset);
            }
        } else {
            int fadeLen = Math.max(1, frames.length - fadeStartIdx);
            int offset = (int) (elapsed / frameDurationMs);
            if (offset >= fadeLen) {
                cleanup(env);
                return;
            }
            currentFrameIdx = fadeStartIdx + offset;
        }

        if (!isBlocking()) return;

        // 2. Chặn & hủy đạn địch bay vào vòng khiên
        interceptHostileProjectiles(env);

        // 3. Tự động khóa hướng tia vào quái gần nhất (Lv2/Lv3) hoặc theo hướng nhân vật ngắm
        updateAimAndWallClip(env);

        // 4. Tấn công quái đến gần vòng khiên (cả 3 cấp)
        applyAuraAttack(env, now);

        // 5. Khi Lv2/Lv3 vào các frame bắn tia pha lê -> gây sát thương dọc luồng tia
        boolean isBeamNow = (level >= 2 && currentFrameIdx < beamFrameFlags.length && beamFrameFlags[currentFrameIdx]);
        if (isBeamNow) {
            if (!wasBeamFrame) {
                wallSparkInThisBurst = false;
            }
            applyBeamAttack(env, now);
        }
        wasBeamFrame = isBeamNow;
    }

    /** Quét và chặn toàn bộ thực thể đạn của kẻ địch đi vào vùng khiên */
    private void interceptHostileProjectiles(Environment env) {
        List<IEntity> toRemove = null;
        double blockDistSq = (shieldRadius + 3.0) * (shieldRadius + 3.0);

        for (IEntity entity : env.getEntities()) {
            if (entity == null || entity == this || entity == owner) continue;
            if (entity instanceof CollisionBox || entity instanceof ICombatEntity) continue;
            if (!isHostileProjectileCandidate(entity)) continue;

            Rectangle2D bb = entity.getBoundingBox();
            double ex = (bb != null) ? bb.getCenterX() : entity.getX();
            double ey = (bb != null) ? bb.getCenterY() : entity.getY();

            double dx = ex - centerX;
            double dy = ey - centerY;
            if (dx * dx + dy * dy <= blockDistSq) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(entity);
                spawnImpactEffect(env, ex, ey);
            }
        }

        if (toRemove != null) {
            for (IEntity proj : toRemove) {
                env.remove(proj);
            }
        }
    }

    /** Nhận diện các thực thể đạn của địch (loại trừ toàn bộ vũ khí của người chơi và bẫy/đồ map) */
    private boolean isHostileProjectileCandidate(IEntity entity) {
        Class<?> cls = entity.getClass();
        String pkg = (cls.getPackage() != null) ? cls.getPackage().getName().toLowerCase(Locale.ROOT) : "";
        if (pkg.startsWith("weapons") || pkg.startsWith("traps") || pkg.startsWith("player") || pkg.startsWith("screens")) {
            return false;
        }
        String name = cls.getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("projectile") || name.contains("bullet") || name.contains("arrow")
                || name.contains("fireball") || name.contains("bolt") || name.contains("missile")
                || name.contains("shot") || name.contains("orb") || name.contains("beam")
                || name.contains("dagger") || name.contains("shuriken") || name.contains("magic");
    }

    private void updateAimAndWallClip(Environment env) {
        if (level >= 2 && beamLength > 0.0) {
            double searchRange = beamLength + 24.0;
            ICombatEntity nearest = null;
            double bestDist = Double.MAX_VALUE;

            for (ICombatEntity target : env.getCombatEntities()) {
                if (target == owner || target.isDead()) continue;
                if (target.getTeam() == owner.getTeam()) continue;

                Rectangle2D tb = target.getBoundingBox();
                double dist = Math.hypot(tb.getCenterX() - centerX, tb.getCenterY() - centerY);
                if (dist <= searchRange && dist < bestDist
                        && WeaponCollision.hasLineOfSight(env, centerX, centerY, tb.getCenterX(), tb.getCenterY(), owner)) {
                    bestDist = dist;
                    nearest = target;
                }
            }

            if (nearest != null) {
                Rectangle2D tb = nearest.getBoundingBox();
                aimAngleRad = Math.atan2(tb.getCenterY() - centerY, tb.getCenterX() - centerX);
            } else {
                double ax = owner.getAimDirX();
                double ay = owner.getAimDirY();
                if (Math.hypot(ax, ay) > 0.01) {
                    aimAngleRad = Math.atan2(ay, ax);
                } else {
                    aimAngleRad = owner.isFacingRight() ? 0.0 : Math.PI;
                }
            }

            beamClearDist = WeaponCollision.raycastObstacleDistance(
                    env, centerX, centerY, aimAngleRad, beamLength, 2.0, owner
            );
        }
    }

    private void applyAuraAttack(Environment env, long nowMs) {
        Ellipse2D aura = new Ellipse2D.Double(
                centerX - auraAttackRadius,
                centerY - auraAttackRadius,
                auraAttackRadius * 2.0,
                auraAttackRadius * 2.0
        );

        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;

            Rectangle2D tb = target.getBoundingBox();
            if (!aura.intersects(tb)) continue;

            Long lastHit = lastAuraHitMs.get(target);
            if (lastHit != null && nowMs - lastHit < auraTickMs) continue;

            if (WeaponCollision.hasLineOfSight(env, centerX, centerY, tb.getCenterX(), tb.getCenterY(), owner)) {
                target.hit(auraDamage);
                lastAuraHitMs.put(target, nowMs);
                spawnImpactEffect(env, tb.getCenterX(), tb.getCenterY());
            }
        }
    }

    private void applyBeamAttack(Environment env, long nowMs) {
        double activeLen = Math.min(beamLength, beamClearDist + 2.0);
        if (activeLen <= 4.0) return;

        Rectangle2D local = new Rectangle2D.Double(0.0, -beamThickness / 2.0, activeLen, beamThickness);
        AffineTransform tx = new AffineTransform();
        tx.translate(centerX, centerY);
        tx.rotate(aimAngleRad);
        Shape beamShape = tx.createTransformedShape(local);

        boolean hitEnemy = false;
        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;

            Rectangle2D tb = target.getBoundingBox();
            if (!beamShape.intersects(tb)) continue;

            Long lastHit = lastBeamHitMs.get(target);
            if (lastHit != null && nowMs - lastHit < beamTickMs) continue;

            if (WeaponCollision.hasLineOfSight(env, centerX, centerY, tb.getCenterX(), tb.getCenterY(), owner)) {
                target.hit(beamDamage);
                lastBeamHitMs.put(target, nowMs);
                spawnImpactEffect(env, tb.getCenterX(), tb.getCenterY());
                hitEnemy = true;
            }
        }

        // Bật 1 tia nổ trên mặt tường/cột khi bắn tia trúng vật cản (dễ quan sát hiệu ứng khi test phòng trống)
        if (!hitEnemy && !wallSparkInThisBurst && beamClearDist < beamLength - 2.0) {
            wallSparkInThisBurst = true;
            double wx = centerX + Math.cos(aimAngleRad) * (beamClearDist + 1.0);
            double wy = centerY + Math.sin(aimAngleRad) * (beamClearDist + 1.0);
            spawnImpactEffect(env, wx, wy);
        }
    }

    private void spawnImpactEffect(Environment env, double x, double y) {
        if (impactFrames.length == 0) return;
        env.add(new HitEffectEntity(
                impactFrames,
                x, y,
                impactDrawW, impactDrawH,
                Math.cos(aimAngleRad) < 0,
                impactFrameMs
        ));
    }

    @Override
    public void render(Graphics2D g) {
        if (removed || frames.length == 0 || Game.world().camera() == null) return;

        BufferedImage frame = frames[Math.max(0, Math.min(frames.length - 1, currentFrameIdx))];
        if (frame == null) return;

        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(centerX, centerY));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());

        if (level >= 2) {
            g2.rotate(aimAngleRad);

            // Cắt gọn đầu tia pha lê nếu đâm vào tường (giữ nguyên toàn vẹn vòng khiên quanh người)
            boolean isBeamNow = (currentFrameIdx < beamFrameFlags.length && beamFrameFlags[currentFrameIdx]);
            if (isBeamNow && beamClearDist < beamLength - 1.5) {
                double leftExtent = drawW * pivotRatioX + 4.0;
                double rightClip = Math.max(shieldRadius + 3.0, beamClearDist + 2.0);
                g2.clipRect(
                        (int) Math.floor(-leftExtent),
                        (int) Math.floor(-drawH),
                        (int) Math.ceil(leftExtent + rightClip),
                        (int) Math.ceil(drawH * 2.0)
                );
            }

            if (Math.cos(aimAngleRad) < 0) {
                g2.scale(1, -1);
            }
        }

        int dx = (int) Math.round(-drawW * pivotRatioX);
        int dy = (int) Math.round(-drawH / 2.0);
        g2.drawImage(frame, dx, dy, (int) Math.round(drawW), (int) Math.round(drawH), null);
        g2.dispose();

        // Bấm F3 -> hiển thị vòng chặn đạn (tím) và vùng tia tấn công (hồng)
        if (isBlocking() && Game.config().debug().renderCollisionBoxes()) {
            Graphics2D dg = (Graphics2D) g.create();
            dg.setColor(new Color(205, 110, 255, 220));
            dg.drawOval(
                    (int) Math.round(vp.getX() - shieldRadius),
                    (int) Math.round(vp.getY() - shieldRadius),
                    (int) Math.round(shieldRadius * 2.0),
                    (int) Math.round(shieldRadius * 2.0)
            );

            if (level >= 2 && currentFrameIdx < beamFrameFlags.length && beamFrameFlags[currentFrameIdx]) {
                dg.translate(vp.getX(), vp.getY());
                dg.rotate(aimAngleRad);
                dg.setColor(new Color(255, 120, 230, 220));
                dg.drawRect(
                        0,
                        (int) Math.round(-beamThickness / 2.0),
                        (int) Math.round(Math.min(beamLength, beamClearDist + 2.0)),
                        (int) Math.round(beamThickness)
                );
            }
            dg.dispose();
        }
    }
}