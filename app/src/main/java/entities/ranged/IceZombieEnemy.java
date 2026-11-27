package entities.ranged;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.entities.IMobileEntity;
import entities.MonsterType;

/**
 * IceZombieEnemy — Zombie băng bắn đạn băng (ice bullet) làm chậm đối thủ.
 * 
 * Ice Zombie di chuyển chậm rãi nhưng phóng đạn băng từ xa.
 * Khi đạn trúng người chơi, nó sẽ gây sát thương và giảm 50% tốc độ di chuyển
 * của người chơi trong 2 giây.
 */
public class IceZombieEnemy extends RangedEnemy {

    // === Hiệu ứng làm chậm ===
    private double slowDuration = 2000;  // thời gian làm chậm (ms)
    private double slowFactor = 0.5;     // hệ số giảm tốc (còn 50% tốc độ)

    /**
     * Khởi tạo Ice Zombie.
     * Sprite: "ice_zombie" (tự động nhận ice_zombie-idle và ice_zombie-walk)
     * Đạn: "ice bullet"
     */
    public IceZombieEnemy() {
        super(MonsterType.ICE_ZOMBIE);
    }

    /**
     * Bắn đạn băng (ice bullet) bay về phía mục tiêu.
     * Khi đạn chạm người chơi, gây sát thương và kích hoạt hiệu ứng làm chậm.
     */
    @Override
    public void attack(ICombatEntity target) {
        if (target == null || target.isDead()) {
            return;
        }

        // Bắn đạn băng thực sự vào thế giới game
        new Projectile(
                "ice-bullet",               // sprite đạn băng từ sprites/monsters/ice-bullet.png
                this.getCenter(),           // điểm bắn
                target.getCenter(),         // đích nhắm
                this.getProjectileSpeed(),  // tốc độ bay
                this.getDamage(),           // sát thương
                TEAM_PLAYER,                          // mục tiêu đội 1 (Player)
                this::applySlowEffect       // hiệu ứng làm chậm khi trúng
        );
    }

    /**
     * Áp dụng hiệu ứng làm chậm lên mục tiêu khi đạn băng bắn trúng.
     */
    private void applySlowEffect(ICombatEntity target) {
        if (!(target instanceof IMobileEntity)) {
            return;
        }

        IMobileEntity mobileTarget = (IMobileEntity) target;

        // Lưu tốc độ gốc
        float originalSpeed = mobileTarget.getVelocity().getModifiedValue();

        // Giảm tốc độ còn 50%
        float slowedSpeed = (float) (originalSpeed * slowFactor);
        mobileTarget.setVelocity(slowedSpeed);

        // Khôi phục lại tốc độ ban đầu sau slowDuration (2000ms)
        Game.loop().perform((int) slowDuration, () -> {
            if (!target.isDead()) {
                mobileTarget.setVelocity(originalSpeed);
            }
        });
    }

    // === Getter / Setter ===

    public double getSlowDuration() {
        return slowDuration;
    }

    public void setSlowDuration(double slowDuration) {
        this.slowDuration = slowDuration;
    }

    public double getSlowFactor() {
        return slowFactor;
    }

    public void setSlowFactor(double slowFactor) {
        this.slowFactor = slowFactor;
    }
}
