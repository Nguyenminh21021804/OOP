package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import java.awt.geom.Point2D;

/**
 * Lớp trừu tượng cơ sở (Base Class) cho tất cả các loại Enemy trong game.
 * Được thiết kế dùng chung để hai người có thể phát triển độc lập:
 * - Người 1: Kế thừa để làm quái đánh gần (MeleeEnemy)
 * - Người 2: Kế thừa để làm quái đánh xa (RangedEnemy)
 */
public abstract class Enemy extends Creature implements IUpdateable {
    // Sát thương gây ra cho người chơi
    protected int damage;

    // Phạm vi tấn công (pixel). Đánh gần: ~40-60px, Đánh xa: ~200-300px
    protected double attackRange;

    // Phạm vi phát hiện người chơi để bắt đầu đuổi theo hoặc tấn công
    protected double detectionRange;

    // Thời gian hồi chiêu giữa 2 lần tấn công liên tiếp (mili-giây)
    protected long attackCooldownMs;

    // Thời điểm tấn công gần nhất
    protected long lastAttackTime;

    /**
     * Khởi tạo quái vật với các thuộc tính cơ bản.
     *
     * @param spritePrefix    Tên tiền tố của sprite animation (ví dụ: "goblin", "skeleton")
     * @param maxHealth       Máu tối đa
     * @param damage          Sát thương gây ra
     * @param speed           Tốc độ di chuyển
     * @param attackRange     Tầm đánh
     * @param detectionRange  Tầm phát hiện người chơi
     * @param cooldownMs      Thời gian hồi chiêu đòn đánh (ms)
     */
    public Enemy(String spritePrefix, int maxHealth, int damage, float speed,
                 double attackRange, double detectionRange, long cooldownMs) {
        super(spritePrefix);

        this.damage = damage;
        this.attackRange = attackRange;
        this.detectionRange = detectionRange;
        this.attackCooldownMs = cooldownMs;
        this.lastAttackTime = 0;

        // Cấu hình Creature trong LITIengine
        this.setScaling(true);
        this.setVelocity(speed);
        this.getHitPoints().setMax(maxHealth);
        this.getHitPoints().setValue(maxHealth);
        this.setIndestructible(false);

        // Mặc định gán Team cho Enemy (ví dụ: 2 = Quái, 1 = Player)
        this.setTeam(2);

        // Đăng ký nhận vòng lặp cập nhật từ Game Loop
        Game.loop().attach(this);
    }

    /**
     * Vòng lặp cập nhật mỗi frame của quái.
     */
    @Override
    public void update() {
        if (isDead()) {
            return;
        }

        // Gọi logic AI cụ thể của từng loại quái
        updateAI();
    }

    /**
     * Logic trí tuệ nhân tạo (AI) và cách thức di chuyển:
     * - Quái cận chiến: Tiếp cận mục tiêu (áp sát).
     * - Quái đánh xa: Giữ cự ly an toàn (kiting/thả diều).
     */
    protected abstract void updateAI();

    /**
     * Hành vi tấn công mục tiêu (Tính đa hình - Polymorphism):
     * - Quái cận chiến: Gây sát thương trực tiếp lên hitbox mục tiêu.
     * - Quái đánh xa: Bắn đạn/phép (Projectile) về phía mục tiêu.
     *
     * @param target Mục tiêu bị tấn công (thường là Player)
     */
    public abstract void attack(ICombatEntity target);

    /**
     * Kiểm tra xem quái có đủ điều kiện để tấn công hay không (đã hồi chiêu xong chưa).
     */
    public boolean canAttack() {
        if (isDead()) {
            return false;
        }
        long now = Game.time().now();
        return (now - lastAttackTime) >= attackCooldownMs;
    }

    /**
     * Đánh dấu vừa thực hiện tấn công để bắt đầu tính hồi chiêu.
     */
    protected void recordAttack() {
        this.lastAttackTime = Game.time().now();
    }

    /**
     * Kiểm tra mục tiêu có nằm trong một bán kính khoảng cách nhất định hay không.
     */
    public boolean isInRange(ICombatEntity target, double range) {
        if (target == null) {
            return false;
        }
        return this.getCenter().distance(target.getCenter()) <= range;
    }

    /**
     * Kiểm tra mục tiêu đã vào tầm tấn công (attackRange) chưa.
     */
    public boolean isInAttackRange(ICombatEntity target) {
        return isInRange(target, this.attackRange);
    }

    /**
     * Kiểm tra mục tiêu đã vào tầm phát hiện (detectionRange) chưa.
     */
    public boolean isInDetectionRange(ICombatEntity target) {
        return isInRange(target, this.detectionRange);
    }

    /**
     * Nhận sát thương và tự động kích hoạt chết nếu hết máu.
     */
    public void takeDamage(int amount) {
        if (isDead()) {
            return;
        }
        this.hit(amount);
    }

    /**
     * Xử lý khi quái chết: hủy đăng ký khỏi game loop.
     */
    @Override
    public void die() {
        super.die();
        Game.loop().detach(this);
    }

    // --- Helper di chuyển cơ bản ---
    /**
     * Di chuyển hướng về phía một tọa độ.
     */
    public void moveTowards(Point2D destination) {
        if (destination == null || this.movement() == null) return;
        // Bật di chuyển của LITIengine theo hướng đích
        Game.physics().move(this, destination);
    }

    // --- Các phương thức lấy thông tin (Getter & Setter) ---
    public int getCurrentHealth() {
        return this.getHitPoints().getValue();
    }

    public int getMaxHealth() {
        return this.getHitPoints().getMax();
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public double getAttackRange() {
        return attackRange;
    }

    public void setAttackRange(double attackRange) {
        this.attackRange = attackRange;
    }

    public double getDetectionRange() {
        return detectionRange;
    }

    public void setDetectionRange(double detectionRange) {
        this.detectionRange = detectionRange;
    }

    public long getAttackCooldownMs() {
        return attackCooldownMs;
    }

    public void setAttackCooldownMs(long attackCooldownMs) {
        this.attackCooldownMs = attackCooldownMs;
    }
}
