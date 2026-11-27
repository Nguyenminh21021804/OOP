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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class BowSkillEntity extends Entity implements IUpdateable, IRenderable {

    private final Player owner;
    private final int level;
    private final BufferedImage[] skillFrames;
    private final BufferedImage[] impactFrames;

    private final double targetX;
    private final double targetY;
    private final double drawW;
    private final double drawH;
    private final double impactDrawW;
    private final double impactDrawH;
    private final double aoeRadiusX;
    private final double aoeRadiusY;
    private final boolean flipX;

    private final int frameDurationMs;
    private final int impactFrameMs;
    private final int[] waveTriggerFrames;
    private final int[] waveDamages;
    private final boolean[] waveTriggered;

    private final long spawnTimeMs;
    private boolean anyEnemyHit = false;

    public BowSkillEntity(
            Player owner,
            int level,
            BufferedImage[] skillFrames,
            BufferedImage[] impactFrames,
            double targetX,
            double targetY,
            double drawW,
            double drawH,
            double impactDrawW,
            double impactDrawH,
            double aoeRadiusX,
            double aoeRadiusY,
            boolean flipX,
            int frameDurationMs,
            int impactFrameMs,
            int baseTickDamage,
            int beamTickDamage
    ) {
        super();
        this.owner = owner;
        this.level = Math.max(1, Math.min(3, level));
        this.skillFrames = (skillFrames != null) ? skillFrames : new BufferedImage[0];
        this.impactFrames = (impactFrames != null) ? impactFrames : new BufferedImage[0];
        this.targetX = targetX;
        this.targetY = targetY;
        this.drawW = drawW;
        this.drawH = drawH;
        this.impactDrawW = impactDrawW;
        this.impactDrawH = impactDrawH;
        this.aoeRadiusX = Math.max(10.0, aoeRadiusX);
        this.aoeRadiusY = Math.max(7.0, aoeRadiusY);
        this.flipX = flipX;
        this.frameDurationMs = Math.max(20, frameDurationMs);
        this.impactFrameMs = Math.max(20, impactFrameMs);
        this.spawnTimeMs = System.currentTimeMillis();

        int n = Math.max(1, this.skillFrames.length);
        if (this.level == 1) {
            // Lv1: 3 đợt mưa tên rơi chạm đất
            this.waveTriggerFrames = new int[] {
                    clampFrame((int) Math.round(n * 0.36), n),
                    clampFrame((int) Math.round(n * 0.50), n),
                    clampFrame((int) Math.round(n * 0.64), n)
            };
            this.waveDamages = new int[] { baseTickDamage, baseTickDamage, baseTickDamage };
        } else if (this.level == 2) {
            // Lv2: 4 đợt mưa tên ma pháp liên hoàn
            this.waveTriggerFrames = new int[] {
                    clampFrame((int) Math.round(n * 0.28), n),
                    clampFrame((int) Math.round(n * 0.41), n),
                    clampFrame((int) Math.round(n * 0.54), n),
                    clampFrame((int) Math.round(n * 0.67), n)
            };
            this.waveDamages = new int[] { baseTickDamage, baseTickDamage, baseTickDamage, baseTickDamage };
        } else {
            // Lv3: 2 đợt tên vòng ngoài + 3 đợt giáng cột năng lượng ở tâm
            this.waveTriggerFrames = new int[] {
                    clampFrame((int) Math.round(n * 0.28), n),
                    clampFrame((int) Math.round(n * 0.40), n),
                    clampFrame((int) Math.round(n * 0.54), n),
                    clampFrame((int) Math.round(n * 0.63), n),
                    clampFrame((int) Math.round(n * 0.72), n)
            };
            this.waveDamages = new int[] {
                    baseTickDamage, baseTickDamage, beamTickDamage, beamTickDamage, beamTickDamage
            };
        }
        this.waveTriggered = new boolean[this.waveTriggerFrames.length];

        this.setRenderType(RenderType.OVERLAY);
        this.setWidth(drawW);
        this.setHeight(drawH);
        this.setLocation(targetX - drawW / 2.0, targetY - drawH * 0.88);
    }

    private static int clampFrame(int idx, int total) {
        return Math.max(0, Math.min(total - 1, idx));
    }

    @Override
    public void update() {
        Environment env = Game.world().environment();
        if (env == null) return;

        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        if (skillFrames.length == 0 || elapsed >= (long) skillFrames.length * frameDurationMs) {
            env.remove(this);
            return;
        }

        int frameIdx = (int) (elapsed / frameDurationMs);
        for (int i = 0; i < waveTriggerFrames.length; i++) {
            if (!waveTriggered[i] && frameIdx >= waveTriggerFrames[i]) {
                waveTriggered[i] = true;
                boolean isClimaxWave = (i == waveTriggerFrames.length - 1);
                applyWaveDamage(env, waveDamages[i], isClimaxWave);
            }
        }
    }

    private void applyWaveDamage(Environment env, int dmg, boolean isClimaxWave) {
        Ellipse2D aoe = new Ellipse2D.Double(
                targetX - aoeRadiusX,
                targetY - aoeRadiusY,
                aoeRadiusX * 2.0,
                aoeRadiusY * 2.0
        );

        boolean hitInWave = false;
        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (owner != null && target.getTeam() == owner.getTeam()) continue;

            Rectangle2D tb = target.getBoundingBox();
            if (aoe.intersects(tb)) {
                target.hit(dmg);
                hitInWave = true;
                anyEnemyHit = true;
                spawnImpact(env, tb.getCenterX(), tb.getY() + tb.getHeight() * 0.70);
            }
        }

        // Nếu đang test trong phòng chưa có quái -> bật 1 vòng nổ trúng đích ở tâm đất ở đợt cuối để dễ xem hiệu ứng
        if (isClimaxWave && !anyEnemyHit && !hitInWave) {
            spawnImpact(env, targetX, targetY);
        }
    }

    private void spawnImpact(Environment env, double x, double y) {
        if (impactFrames.length == 0) return;
        env.add(new HitEffectEntity(
                impactFrames,
                x, y,
                impactDrawW, impactDrawH,
                flipX,
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

        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(targetX, targetY));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());
        if (flipX) {
            g2.scale(-1, 1);
        }

        // Neo vùng tiếp đất (88% chiều cao sprite tính từ đỉnh cổng trời) đúng tại (targetX, targetY)
        g2.drawImage(
                frame,
                (int) Math.round(-drawW / 2.0),
                (int) Math.round(-drawH * 0.88),
                (int) Math.round(drawW),
                (int) Math.round(drawH),
                null
        );
        g2.dispose();

        if (Game.config().debug().renderCollisionBoxes()) {
            Graphics2D dg = (Graphics2D) g.create();
            dg.setColor(new Color(90, 220, 255, 220));
            dg.drawOval(
                    (int) Math.round(vp.getX() - aoeRadiusX),
                    (int) Math.round(vp.getY() - aoeRadiusY),
                    (int) Math.round(aoeRadiusX * 2.0),
                    (int) Math.round(aoeRadiusY * 2.0)
            );
            dg.dispose();
        }
    }
}