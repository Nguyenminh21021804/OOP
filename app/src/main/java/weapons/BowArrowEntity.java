package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;
import player.Player;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

public class BowArrowEntity extends Entity implements IUpdateable, IRenderable {

    private final Player owner;
    private final BufferedImage[] arrowFrames;
    private final BufferedImage sigilImg;
    private final int damage;
    private final double angleRad;
    private final double speed;
    private final double maxDistance;
    private final double drawW;
    private final double drawH;
    private final double sigilSize;
    private final double hitW;
    private final double hitH;
    private final int frameDurationMs;
    private final boolean piercing;

    private final long spawnTimeMs;
    private long lastUpdateNano;
    private double centerX;
    private double centerY;
    private double traveled = 0.0;
    private boolean immediateWallHit;

    private final Set<ICombatEntity> hitTargets = new HashSet<>();

    public BowArrowEntity(
            Player owner,
            BufferedImage[] arrowFrames,
            BufferedImage sigilImg,
            int damage,
            double startX,
            double startY,
            double angleRad,
            double speed,
            double maxDistance,
            double drawW,
            double drawH,
            double sigilSize,
            double hitW,
            double hitH,
            int frameDurationMs,
            boolean piercing,
            boolean immediateWallHit
    ) {
        super();
        this.owner = owner;
        this.arrowFrames = (arrowFrames != null) ? arrowFrames : new BufferedImage[0];
        this.sigilImg = sigilImg;
        this.damage = damage;
        this.centerX = startX;
        this.centerY = startY;
        this.angleRad = angleRad;
        this.speed = speed;
        this.maxDistance = maxDistance;
        this.drawW = drawW;
        this.drawH = drawH;
        this.sigilSize = sigilSize;
        this.hitW = hitW;
        this.hitH = hitH;
        this.frameDurationMs = Math.max(20, frameDurationMs);
        this.piercing = piercing;
        this.immediateWallHit = immediateWallHit;

        this.spawnTimeMs = System.currentTimeMillis();
        this.lastUpdateNano = System.nanoTime();

        this.setRenderType(RenderType.NORMAL);
        this.setWidth(drawW);
        this.setHeight(drawH);
        this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) return;

        if (immediateWallHit) {
            immediateWallHit = false;
            spawnSigilBurst(env, centerX, centerY);
            env.remove(this);
            return;
        }

        long nowNano = System.nanoTime();
        double dt = Math.min(0.05, (nowNano - lastUpdateNano) / 1_000_000_000.0);
        lastUpdateNano = nowNano;

        double totalStep = speed * dt;
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        // Chia bước <= 2.5px để mũi tên bay nhanh không xuyên qua tường/cột mỏng
        int subSteps = Math.max(1, (int) Math.ceil(totalStep / 2.5));
        double subStep = totalStep / subSteps;

        for (int i = 0; i < subSteps; i++) {
            double nextX = centerX + cos * subStep;
            double nextY = centerY + sin * subStep;

            Rectangle2D wallProbe = new Rectangle2D.Double(nextX - 2.0, nextY - 2.0, 4.0, 4.0);
            if (WeaponCollision.isBlocked(env, wallProbe, owner)) {
                spawnSigilBurst(env, nextX, nextY);
                env.remove(this);
                return;
            }

            centerX = nextX;
            centerY = nextY;
            traveled += subStep;
            this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);

            Rectangle2D hitBox = getHitbox();
            for (ICombatEntity target : env.getCombatEntities()) {
                if (target == owner || target.isDead()) continue;
                if (target.getTeam() == owner.getTeam()) continue;
                if (hitTargets.contains(target)) continue;

                Rectangle2D tb = target.getBoundingBox();
                if (tb.intersects(hitBox)) {
                    target.hit(damage);
                    hitTargets.add(target);
                    spawnSigilBurst(env, tb.getCenterX(), tb.getCenterY());

                    if (!piercing) {
                        env.remove(this);
                        return;
                    }
                }
            }

            if (traveled >= maxDistance) {
                env.remove(this);
                return;
            }
        }
    }

    private void spawnSigilBurst(Environment env, double x, double y) {
        if (sigilImg == null) return;
        env.add(new SigilImpactEntity(sigilImg, x, y, sigilSize, 220L));
    }

    public Rectangle2D getHitbox() {
        return new Rectangle2D.Double(centerX - hitW / 2.0, centerY - hitH / 2.0, hitW, hitH);
    }

    @Override
    public void render(Graphics2D g) {
        if (arrowFrames.length == 0 || Game.world().camera() == null) return;

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        int idx = (int) ((elapsed / frameDurationMs) % arrowFrames.length);
        BufferedImage frame = arrowFrames[idx];
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
            Rectangle2D box = getHitbox();
            Point2D bvp = Game.world().camera().getViewportLocation(new Point2D.Double(box.getX(), box.getY()));
            Graphics2D dg = (Graphics2D) g.create();
            dg.setColor(new Color(90, 220, 255, 220));
            dg.drawRect(
                    (int) Math.round(bvp.getX()),
                    (int) Math.round(bvp.getY()),
                    (int) Math.round(box.getWidth()),
                    (int) Math.round(box.getHeight())
            );
            dg.dispose();
        }
    }

    /** Hiệu ứng ấn ký va chạm (cột 3): nở nhẹ, xoay chậm và mờ dần tại điểm trúng đòn */
    private static class SigilImpactEntity extends Entity implements IUpdateable, IRenderable {
        private final BufferedImage img;
        private final double centerX;
        private final double centerY;
        private final double baseSize;
        private final long durationMs;
        private final long spawnTimeMs;

        SigilImpactEntity(BufferedImage img, double x, double y, double baseSize, long durationMs) {
            super();
            this.img = img;
            this.centerX = x;
            this.centerY = y;
            this.baseSize = baseSize;
            this.durationMs = Math.max(80L, durationMs);
            this.spawnTimeMs = System.currentTimeMillis();

            this.setRenderType(RenderType.OVERLAY);
            this.setWidth(baseSize);
            this.setHeight(baseSize);
            this.setLocation(x - baseSize / 2.0, y - baseSize / 2.0);
        }

        @Override
        public void update() {
            Environment env = Game.world().environment();
            if (env == null) return;
            if (System.currentTimeMillis() - spawnTimeMs >= durationMs) {
                env.remove(this);
            }
        }

        @Override
        public void render(Graphics2D g) {
            if (img == null || Game.world().camera() == null) return;

            double t = Math.min(1.0, Math.max(0.0, (System.currentTimeMillis() - spawnTimeMs) / (double) durationMs));
            double scale = 0.65 + 0.55 * (1.0 - (1.0 - t) * (1.0 - t));
            float alpha = (float) Math.max(0.0, 1.0 - t * t);
            if (alpha <= 0.01f) return;

            double drawSize = baseSize * scale;
            Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(centerX, centerY));

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.translate(vp.getX(), vp.getY());
            g2.rotate(Math.toRadians(28.0 * t));
            g2.drawImage(
                    img,
                    (int) Math.round(-drawSize / 2.0),
                    (int) Math.round(-drawSize / 2.0),
                    (int) Math.round(drawSize),
                    (int) Math.round(drawSize),
                    null
            );
            g2.dispose();
        }
    }
}