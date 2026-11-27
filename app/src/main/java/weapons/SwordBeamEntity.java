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

public class SwordBeamEntity extends Entity implements IUpdateable, IRenderable {

    private final Player owner;
    private final BufferedImage[] headFrames;
    private final BufferedImage[] bodyFrames;
    private final BufferedImage[] impactFrames;
    private final int damage;
    private final double angleRad;
    private final double originOffset;
    private final double extendSpeed;
    private final double maxLength;
    private final long activeDurationMs;

    private final double bodyTileW;
    private final double bodyDrawH;
    private final double headDrawW;
    private final double headDrawH;
    private final double impactDrawW;
    private final double impactDrawH;
    private final double impactPivotRatioX;
    private final double hitThickness;
    private final int frameDurationMs;
    private final int impactFrameDurationMs;

    private final long spawnTimeMs;
    private long lastUpdateNano;
    private double startX;
    private double startY;
    private double desiredLength;
    private double currentLength;
    private boolean hitWall = false;

    private boolean impacting = false;
    private long impactStartMs = 0;

    private final Set<ICombatEntity> hitTargets = new HashSet<>();

    public SwordBeamEntity(
            Player owner,
            BufferedImage[] headFrames,
            BufferedImage[] bodyFrames,
            BufferedImage[] impactFrames,
            int damage,
            double angleRad,
            double originOffset,
            double initialLength,
            double extendSpeed,
            double maxLength,
            long activeDurationMs,
            double bodyTileW,
            double bodyDrawH,
            double headDrawW,
            double headDrawH,
            double impactDrawW,
            double impactDrawH,
            double impactPivotRatioX,
            double hitThickness,
            int frameDurationMs,
            int impactFrameDurationMs
    ) {
        super();
        this.owner = owner;
        this.headFrames = headFrames;
        this.bodyFrames = bodyFrames;
        this.impactFrames = impactFrames;
        this.damage = damage;
        this.angleRad = angleRad;
        this.originOffset = originOffset;
        this.desiredLength = initialLength;
        this.extendSpeed = extendSpeed;
        this.maxLength = maxLength;
        this.activeDurationMs = activeDurationMs;
        this.bodyTileW = Math.max(4.0, bodyTileW);
        this.bodyDrawH = bodyDrawH;
        this.headDrawW = headDrawW;
        this.headDrawH = headDrawH;
        this.impactDrawW = impactDrawW;
        this.impactDrawH = impactDrawH;
        this.impactPivotRatioX = impactPivotRatioX;
        this.hitThickness = hitThickness;
        this.frameDurationMs = frameDurationMs;
        this.impactFrameDurationMs = impactFrameDurationMs;

        this.spawnTimeMs = System.currentTimeMillis();
        this.lastUpdateNano = System.nanoTime();

        this.setRenderType(RenderType.NORMAL);
        updateOriginAndClamp(Game.world().environment());
        if (hitWall) {
            startImpact();
        }
    }

    private void updateOriginAndClamp(Environment env) {
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.72;
        this.startX = cx + Math.cos(angleRad) * originOffset;
        this.startY = cy + Math.sin(angleRad) * originOffset;
        this.setLocation(startX - 4, startY - 4);

        if (env != null) {
            double targetFromCenter = originOffset + desiredLength;
            double maxClearFromCenter = WeaponCollision.raycastObstacleDistance(
                    env, cx, cy, angleRad, targetFromCenter, 2.2, owner
            );
            this.hitWall = (maxClearFromCenter < targetFromCenter - 1.5);
            double allowedLen = Math.max(0.0, maxClearFromCenter - originOffset);
            this.currentLength = Math.min(desiredLength, allowedLen);
        } else {
            this.hitWall = false;
            this.currentLength = desiredLength;
        }
    }

    private void startImpact() {
        if (impacting) return;
        this.impacting = true;
        this.impactStartMs = System.currentTimeMillis();
    }

    /** Giữ đại kiếm chĩa thẳng trong lúc tia đang bay và 2 frame nổ sáng đầu tiên */
    public boolean isHoldingSword() {
        if (!impacting) return true;
        if (impactFrames == null || impactFrames.length == 0) return false;
        long elapsed = System.currentTimeMillis() - impactStartMs;
        int idx = (int) (elapsed / Math.max(1, impactFrameDurationMs));
        return idx < 2;
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) return;

        if (owner.isDead() && !impacting) {
            env.remove(this);
            return;
        }

        if (impacting) {
            if (impactFrames == null || impactFrames.length == 0) {
                env.remove(this);
                return;
            }
            long impElapsed = System.currentTimeMillis() - impactStartMs;
            int idx = (int) (impElapsed / Math.max(1, impactFrameDurationMs));
            if (idx >= impactFrames.length) {
                env.remove(this);
                return;
            }

            // Trong 2 frame nổ sáng đầu vẫn tiếp tục gây sát thương nếu mục tiêu chạm tia
            if (idx < 2 && currentLength > 1.0) {
                applyBeamDamage(env);
            }
            return;
        }

        long nowNano = System.nanoTime();
        double dt = Math.min(0.05, (nowNano - lastUpdateNano) / 1_000_000_000.0);
        lastUpdateNano = nowNano;

        desiredLength = Math.min(maxLength, desiredLength + extendSpeed * dt);
        updateOriginAndClamp(env);

        if (currentLength > 1.0) {
            applyBeamDamage(env);
        }

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        // Chạm tường / đạt tầm tối đa / hết thời gian duy trì -> chuyển sang hoạt ảnh chạm vật thể & tan tia
        if (hitWall || desiredLength >= maxLength || elapsed >= activeDurationMs) {
            startImpact();
        }
    }

    private void applyBeamDamage(Environment env) {
        Shape beamHitShape = getBeamWorldShape();
        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;
            if (hitTargets.contains(target)) continue;

            if (beamHitShape.intersects(target.getBoundingBox())) {
                target.hit(damage);
                hitTargets.add(target);
            }
        }
    }

    private Shape getBeamWorldShape() {
        Rectangle2D local = new Rectangle2D.Double(0, -hitThickness / 2.0, Math.max(4.0, currentLength), hitThickness);
        AffineTransform tx = new AffineTransform();
        tx.translate(startX, startY);
        tx.rotate(angleRad);
        return tx.createTransformedShape(local);
    }

    private void drawBodySpan(Graphics2D g2, BufferedImage bodyImg, double endX) {
        if (bodyImg == null || endX <= 0.25) return;

        double step = Math.max(2.0, bodyTileW - 0.35);
        int dy1 = (int) Math.round(-bodyDrawH / 2.0);
        int dy2 = (int) Math.round(bodyDrawH / 2.0);

        for (double x = 0.0; x < endX; x += step) {
            double segW = Math.min(bodyTileW, endX - x);
            if (segW <= 0.25) break;

            int srcW = Math.max(1, Math.min(bodyImg.getWidth(), (int) Math.round(bodyImg.getWidth() * (segW / bodyTileW))));
            int dx1 = (int) Math.floor(x);
            int dx2 = (int) Math.ceil(x + segW);
            g2.drawImage(bodyImg, dx1, dy1, dx2, dy2, 0, 0, srcW, bodyImg.getHeight(), null);
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (Game.world().camera() == null) return;
        if (!impacting && currentLength <= 1.0) return;

        Point2D vpStart = Game.world().camera().getViewportLocation(new Point2D.Double(startX, startY));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vpStart.getX(), vpStart.getY());
        g2.rotate(angleRad);
        if (Math.cos(angleRad) < 0) {
            g2.scale(1, -1);
        }

        if (impacting) {
            if (impactFrames != null && impactFrames.length > 0) {
                long impElapsed = System.currentTimeMillis() - impactStartMs;
                int idx = (int) Math.min(impactFrames.length - 1, Math.max(0, impElapsed / Math.max(1, impactFrameDurationMs)));
                BufferedImage impFrame = impactFrames[idx];

                double impactX = currentLength - impactDrawW * impactPivotRatioX;
                double clipHalfH = Math.max(impactDrawH, bodyDrawH);

                // Giữ phần cắt từ gốc mũi kiếm trở đi để tia không bị lòi ngược ra sau lưng khi đứng sát tường
                g2.clipRect(
                        -6,
                        (int) Math.floor(-clipHalfH),
                        (int) Math.ceil(currentLength + impactDrawW + 12.0),
                        (int) Math.ceil(clipHalfH * 2.0)
                );

                // Ở 2 frame đầu (cột 1 & 2 của ảnh): vẫn nối phần thân sóng từ mũi kiếm tới đầu vụ nổ
                if (idx < 2 && bodyFrames != null && bodyFrames.length > 0 && impactX > 0.5) {
                    long elapsed = System.currentTimeMillis() - spawnTimeMs;
                    int bodyIdx = (int) ((elapsed / Math.max(1, frameDurationMs)) % bodyFrames.length);
                    drawBodySpan(g2, bodyFrames[bodyIdx], impactX + 1.5);
                }

                if (impFrame != null) {
                    g2.drawImage(
                            impFrame,
                            (int) Math.round(impactX),
                            (int) Math.round(-impactDrawH / 2.0),
                            (int) Math.round(impactDrawW),
                            (int) Math.round(impactDrawH),
                            null
                    );
                }
            }
            g2.dispose();
            return;
        }

        if (headFrames == null || headFrames.length == 0
                || bodyFrames == null || bodyFrames.length == 0) {
            g2.dispose();
            return;
        }

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        int bodyIdx = (int) ((elapsed / Math.max(1, frameDurationMs)) % bodyFrames.length);
        int headIdx = (int) ((elapsed / Math.max(1, frameDurationMs)) % headFrames.length);
        BufferedImage bodyImg = bodyFrames[bodyIdx];
        BufferedImage headImg = headFrames[headIdx];

        double clipHalfH = Math.max(headDrawH, bodyDrawH);
        g2.clipRect(0, (int) Math.floor(-clipHalfH), (int) Math.ceil(currentLength + 1.0), (int) Math.ceil(clipHalfH * 2.0));

        double headX = currentLength - headDrawW;
        double bodyEnd = Math.min(currentLength, headX + headDrawW * 0.72);
        drawBodySpan(g2, bodyImg, bodyEnd);

        g2.drawImage(
                headImg,
                (int) Math.round(headX),
                (int) Math.round(-headDrawH / 2.0),
                (int) Math.round(headDrawW),
                (int) Math.round(headDrawH),
                null
        );

        if (Game.config().debug().renderCollisionBoxes()) {
            g2.setColor(new Color(255, 220, 40, 210));
            g2.drawRect(
                    0,
                    (int) Math.round(-hitThickness / 2.0),
                    (int) Math.round(currentLength),
                    (int) Math.round(hitThickness)
            );
        }

        g2.dispose();
    }
}