package entities.ranged;

import de.gurkenlabs.litiengine.entities.ICombatEntity;
import entities.MonsterType;

/**
 * SkeletonEnemy — Bộ xương cung thủ/xạ thủ.
 * Di chuyển qua lại trái - phải ngắm bắn những mũi tên thẳng vào mục tiêu.
 */
public class SkeletonEnemy extends RangedEnemy {

    public SkeletonEnemy() {
        super(MonsterType.SKELET);
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
                TEAM_PLAYER,
                null
        );
    }
}
