package entities.ranged;

import de.gurkenlabs.litiengine.entities.ICombatEntity;
import entities.MonsterType;

/**
 * ImpEnemy — Quỷ nhỏ bắn đạn lửa (Fire bullet), di chuyển lắt léo trái - phải liên tục.
 *
 * Tốc độ bắn rất nhanh (cooldown 600ms), khựng lại đứng yên 200ms khi khạc đạn lửa rồi lượn tiếp.
 */
public class ImpEnemy extends RangedEnemy {

    public ImpEnemy() {
        super(MonsterType.IMP);
    }

    /**
     * Bắn đạn lửa (Fire bullet) về phía mục tiêu.
     */
    @Override
    public void attack(ICombatEntity target) {
        if (target == null || target.isDead()) return;

        new Projectile(
                "fire-bullet",             // sprite đạn lửa
                this.getCenter(),          // vị trí bắn
                target.getCenter(),        // vị trí mục tiêu
                this.getProjectileSpeed(), // tốc độ đạn
                this.getDamage(),          // sát thương
                TEAM_PLAYER,                         // mục tiêu đội 1
                null
        );
    }
}
