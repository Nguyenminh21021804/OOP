package entities.ranged;

import de.gurkenlabs.litiengine.entities.ICombatEntity;

/**
 * SkeletonEnemy — Bộ xương cung thủ/xạ thủ.
 * Di chuyển qua lại trái - phải ngắm bắn những mũi tên thẳng vào mục tiêu.
 */
public class SkeletonEnemy extends RangedEnemy {

    public SkeletonEnemy() {
        super(
            "skelet",       // spritePrefix trong game.litidata
            100,            // maxHealth
            14,             // damage
            50f,            // speed — 50 pixel/giây (bước đi vừa phải)
            250,            // attackRange
            450,            // detectionRange
            1500,           // cooldownMs (1.5s)
            90,             // safeDistance
            180             // projectileSpeed — mũi tên bay vừa phải
        );
        this.attackWindupMs = 120;    // Dừng lại giương tên 120ms
        this.attackPauseTotalMs = 260; // Tổng thời gian khựng lại: 260ms
    }

    /**
     * Bắn mũi tên (weapon_arrow) bay thẳng về phía mục tiêu.
     */
    @Override
    public void attack(ICombatEntity target) {
        if (target == null || target.isDead()) return;

        new Projectile(
                "weapon_arrow",            // sprite mũi tên từ game.litidata
                this.getCenter(),
                target.getCenter(),
                this.getProjectileSpeed(),
                this.getDamage(),
                1,
                null
        );
    }
}
