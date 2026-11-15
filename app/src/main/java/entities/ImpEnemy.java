package entities;

import de.gurkenlabs.litiengine.entities.ICombatEntity;

/**
 * ImpEnemy — Quỷ nhỏ bắn đạn lửa (Fire bullet), di chuyển lắt léo trái - phải liên tục.
 *
 * Tốc độ bắn rất nhanh (cooldown 600ms), khựng lại đứng yên 200ms khi khạc đạn lửa rồi lượn tiếp.
 */
public class ImpEnemy extends RangedEnemy {

    public ImpEnemy() {
        super(
            "imp",          // spritePrefix trong game.litidata
            60,             // maxHealth
            8,              // damage
            140f,           // speed — 140 pixel/giây (bay lướt cực nhanh)
            350,            // attackRange — tầm bắn cầu lửa
            500,            // detectionRange
            600,            // cooldownMs (0.6s)
            100,            // safeDistance
            150             // projectileSpeed (px/s) — cầu lửa bay chậm rãi, thấy rõ hiệu ứng
        );
        this.strafeSwitchIntervalMs = 1200; // Đổi hướng nhanh nhẹn sau 1.2s
        this.attackWindupMs = 80;          // Khựng lại lấy đà rất nhanh (80ms)
        this.attackPauseTotalMs = 200;      // Tổng thời gian dừng lại bắn: 200ms
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
                1,                         // mục tiêu đội 1
                null
        );
    }
}
