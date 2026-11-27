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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

public class SwordSkillProjectile extends Entity implements IUpdateable, IRenderable {

    private enum Phase {
        LAUNCH, FLY, FADE, IMPACT
    }

    private final Player owner;
    private final BufferedImage[] launchFrames;
    private final BufferedImage[] flyFrames;
    private final BufferedImage[] fadeFrames;
    private final BufferedImage[] impactFrames;
    private final BufferedImage[] hitSparkFrames;

    private final int damage;
    private final double angleRad;
    private final double speed;
    private final double maxDistance;
    private final double drawW;
    private final double drawH;
    private final double hitSparkW;
    private final double hitSparkH;
    private final double hitW;
    private final double hitH;
    private final int launchFrameMs;
    private final int flyFrameMs;
    private final int fadeFrameMs;
    private final int impactFrameMs;
    private final boolean piercing;

    private Phase phase;
    private long phaseStartMs;
    private long lastUpdateNano;
    private double centerX;
    private double centerY;
    private double traveled = 0.0;

    private final Set<ICombatEntity> hitTargets = new HashSet<>();

    public SwordSkillProjectile(
            Player owner,
            BufferedImage[] launchFrames,
            BufferedImage[] flyFrames,
            BufferedImage[] fadeFrames,
            BufferedImage[] impactFrames,
            BufferedImage[] hitSparkFrames,
            int damage,
            double startX,
            double startY,
            double angleRad,
            double speed,
            double maxDistance,
            double drawW,
            double drawH,
            double hitSparkW,
            double hitSparkH,
            double hitW,
            double hitH,
            int launchFrameMs,
            int flyFrameMs,
            int fadeFrameMs,
            int impactFrameMs,
            boolean piercing,
            boolean startImpacting
    ) {
        super();
        this.owner = owner;
        this.launchFrames = (launchFrames != null) ? launchFrames : new BufferedImage[0];
        this.flyFrames = (flyFrames != null) ? flyFrames : new BufferedImage[0];
        this.fadeFrames = (fadeFrames != null) ? fadeFrames : new BufferedImage[0];
        this.impactFrames = (impactFrames != null) ? impactFrames : new BufferedImage[0];
        this.hitSparkFrames = (hitSparkFrames != null) ? hitSparkFrames : new BufferedImage[0];

        this.damage = damage;
        this.centerX = startX;
        this.centerY = startY;
        this.angleRad = angleRad;
        this.speed = speed;
        this.maxDistance = maxDistance;
        this.drawW = drawW;
        this.drawH = drawH;
        this.hitSparkW = hitSparkW;
        this.hitSparkH = hitSparkH;
        this.hitW = hitW;
        this.hitH = hitH;
        this.launchFrameMs = Math.max(15, launchFrameMs);
        this.flyFrameMs = Math.max(15, flyFrameMs);
        this.fadeFrameMs = Math.max(20, fadeFrameMs);
        this.impactFrameMs = Math.max(20, impactFrameMs);
        this.piercing = piercing;

        this.phaseStartMs = System.currentTimeMillis();
        this.lastUpdateNano = System.nanoTime();
        if (startImpacting) {
            this.phase = Phase.IMPACT;
        } else {
            this.phase = (this.launchFrames.length > 0) ? Phase.LAUNCH : Phase.FLY;
        }

        this.setRenderType(RenderType.NORMAL);
        this.setWidth(drawW);
        this.setHeight(drawH);
        this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);
    }

    private void switchPhase(Phase next, Environment env) {
        if (next == Phase.IMPACT && impactFrames.length == 0) {
            env.remove(this);
            return;
        }
        if (next == Phase.FADE && fadeFrames.length == 0) {
            env.remove(this);
            return;
        }
        this.phase = next;
        this.phaseStartMs = System.currentTimeMillis();
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) return;

        long nowMs = System.currentTimeMillis();
        long phaseElapsed = nowMs - phaseStartMs;

        // 1. Pha nổ khi chạm tường / mục tiêu -> đứng yên chạy hết 5 frame rồi tự xóa
        if (phase == Phase.IMPACT) {
            if (impactFrames.length == 0 || phaseElapsed >= (long) impactFrames.length * impactFrameMs) {
                env.remove(this);
            }
            return;
        }

        long nowNano = System.nanoTime();
        double dt = Math.min(0.05, (nowNano - lastUpdateNano) / 1_000_000_000.0);
        lastUpdateNano = nowNano;

        // 2. Pha tự tan dần khi bay hết tầm -> giảm tốc nhẹ trong lúc chạy frame tan chiêu
        if (phase == Phase.FADE) {
            if (fadeFrames.length == 0 || phaseElapsed >= (long) fadeFrames.length * fadeFrameMs) {
                env.remove(this);
                return;
            }
            double fadeStep = speed * 0.42 * dt;
            double nx = centerX + Math.cos(angleRad) * fadeStep;
            double ny = centerY + Math.sin(angleRad) * fadeStep;
            Rectangle2D probe = new Rectangle2D.Double(nx - 2.5, ny - 2.5, 5.0, 5.0);
            if (!WeaponCollision.isBlocked(env, probe, owner)) {
                centerX = nx;
                centerY = ny;
                this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);
            }
            return;
        }

        // Chuyển từ pha khởi tạo (3 frame đầu) sang pha bay lặp
        if (phase == Phase.LAUNCH && phaseElapsed >= (long) launchFrames.length * launchFrameMs) {
            switchPhase(Phase.FLY, env);
        }

        // 3. Bay tới theo từng bước nhỏ <= 2.5px để không xuyên qua tường/object mỏng
        double totalStep = speed * dt;
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        int subSteps = Math.max(1, (int) Math.ceil(totalStep / 2.5));
        double subStep = totalStep / subSteps;

        for (int i = 0; i < subSteps; i++) {
            double nextX = centerX + cos * subStep;
            double nextY = centerY + sin * subStep;

            Rectangle2D wallProbe = new Rectangle2D.Double(nextX - 2.5, nextY - 2.5, 5.0, 5.0);
            if (WeaponCollision.isBlocked(env, wallProbe, owner)) {
                centerX = nextX;
                centerY = nextY;
                this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);
                switchPhase(Phase.IMPACT, env);
                return;
            }

            centerX = nextX;
            centerY = nextY;
            traveled += subStep;
            this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);

            // Kiểm tra đánh trúng quái
            Rectangle2D hitBox = getHitbox();
            for (ICombatEntity target : env.getCombatEntities()) {
                if (target == owner || target.isDead()) continue;
                if (target.getTeam() == owner.getTeam()) continue;
                if (hitTargets.contains(target)) continue;

                Rectangle2D tb = target.getBoundingBox();
                if (tb.intersects(hitBox)) {
                    target.hit(damage);
                    hitTargets.add(target);
                    spawnHitSpark(env, tb.getCenterX(), tb.getCenterY());

                    if (!piercing) {
                        centerX = tb.getCenterX();
                        centerY = tb.getCenterY();
                        this.setLocation(centerX - drawW / 2.0, centerY - drawH / 2.0);
                        switchPhase(Phase.IMPACT, env);
                        return;
                    }
                }
            }

            if (traveled >= maxDistance) {
                switchPhase(Phase.FADE, env);
                return;
            }
        }
    }

    private void spawnHitSpark(Environment env, double tx, double ty) {
        if (hitSparkFrames.length == 0) return;
        env.add(new HitEffectEntity(
                hitSparkFrames,
                tx, ty,
                hitSparkW, hitSparkH,
                Math.cos(angleRad) < 0,
                45
        ));
    }

    public Rectangle2D getHitbox() {
        return new Rectangle2D.Double(centerX - hitW / 2.0, centerY - hitH / 2.0, hitW, hitH);
    }

    private BufferedImage getCurrentFrame() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - phaseStartMs);
        return switch (phase) {
            case LAUNCH -> (launchFrames.length > 0)
                    ? launchFrames[(int) Math.min(launchFrames.length - 1, elapsed / launchFrameMs)]
                    : null;
            case FLY -> (flyFrames.length > 0)
                    ? flyFrames[(int) ((elapsed / flyFrameMs) % flyFrames.length)]
                    : null;
            case FADE -> (fadeFrames.length > 0)
                    ? fadeFrames[(int) Math.min(fadeFrames.length - 1, elapsed / fadeFrameMs)]
                    : null;
            case IMPACT -> (impactFrames.length > 0)
                    ? impactFrames[(int) Math.min(impactFrames.length - 1, elapsed / impactFrameMs)]
                    : null;
        };
    }

    @Override
    public void render(Graphics2D g) {
        if (Game.world().camera() == null) return;
        BufferedImage frame = getCurrentFrame();
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

        if ((phase == Phase.LAUNCH || phase == Phase.FLY)
                && Game.config().debug().renderCollisionBoxes()) {
            Rectangle2D box = getHitbox();
            Point2D bvp = Game.world().camera().getViewportLocation(new Point2D.Double(box.getX(), box.getY()));
            Graphics2D dg = (Graphics2D) g.create();
            dg.setColor(new Color(255, 220, 40, 210));
            dg.drawRect(
                    (int) Math.round(bvp.getX()),
                    (int) Math.round(bvp.getY()),
                    (int) Math.round(box.getWidth()),
                    (int) Math.round(box.getHeight())
            );
            dg.dispose();
        }
    }
}