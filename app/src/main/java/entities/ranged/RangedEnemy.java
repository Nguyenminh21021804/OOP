package entities.ranged;

import de.gurkenlabs.litiengine.Direction;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import entities.Enemy;

import java.awt.geom.Point2D;
import java.util.Collection;

/**
 * RangedEnemy — Lớp trừu tượng trung gian cho tất cả kẻ địch tấn công từ xa.
 *
 * Kẻ địch tầm xa sẽ:
 *   1. Cố giữ khoảng cách an toàn (safeDistance) với mục tiêu (kiting/lùi lại nếu áp sát).
 *   2. Di chuyển qua lại trái - phải (strafe) khi ở tầm bắn.
 *   3. Khựng lại đứng yên (Attack Windup & Pause) khi tung đòn tấn công, quay mặt ngắm mục tiêu rồi mới bắn,
 *      sau đó tiếp tục di chuyển.
 */
public abstract class RangedEnemy extends Enemy {

    /** Khoảng cách an toàn tối thiểu — nếu mục tiêu đến gần hơn, kẻ địch sẽ chạy lùi */
    protected double safeDistance;

    /** Tốc độ đạn (pixel/giây) */
    protected double projectileSpeed;

    // === Cơ chế di chuyển trái - phải (strafe) ===
    protected int strafeDirection = 1;              // 1: sang phải, -1: sang trái
    protected long lastStrafeSwitchTime = 0;         // thời điểm đổi chiều gần nhất
    protected long strafeSwitchIntervalMs = 1400;    // chu kỳ đổi chiều (1.4 giây giúp di chuyển nhịp nhàng, linh hoạt)

    // === Cơ chế khựng lại khi tấn công (Attack Windup & Pause) giống Lizard ===
    protected boolean isAttacking = false;
    protected long attackStartTime = 0;
    protected long attackWindupMs = 120;     // Thời gian lấy đà / chuẩn bị trước khi phóng đạn (ms)
    protected long attackPauseTotalMs = 260; // Tổng thời gian khựng lại đứng yên (ms)
    protected boolean hasFiredInCurrentAttack = false;

    public RangedEnemy(String spritePrefix, int maxHealth, int damage, float speed,
                       double attackRange, double detectionRange, long cooldownMs,
                       double safeDistance, double projectileSpeed) {
        super(spritePrefix, maxHealth, damage, speed, attackRange, detectionRange, cooldownMs);
        this.safeDistance = safeDistance;
        this.projectileSpeed = projectileSpeed;
        // Giữ nguyên tỉ lệ pixel art 1:1, không bị kéo dãn (bè)
        this.setScaling(false);
        // Phân tán thời điểm đổi chiều ban đầu để các quái không di chuyển rập khuôn hoàn toàn
        this.lastStrafeSwitchTime = (long) (Math.random() * 1000);
    }

    /**
     * Không cho phép kích hoạt đòn đánh mới nếu đang trong trạng thái khựng lại ngắm bắn.
     */
    @Override
    public boolean canAttack() {
        if (isAttacking) {
            return false;
        }
        return super.canAttack();
    }

    /**
     * Không đổi mục tiêu giữa chừng khi đang trong nhịp khựng lại ngắm bắn.
     */
    @Override
    protected boolean canRetarget() {
        return !isAttacking;
    }

    /**
     * Không bị quái khác xô đẩy khi đang đứng yên ngắm bắn.
     */
    @Override
    protected boolean canBeSeparated() {
        return !isAttacking;
    }

    /**
     * Xoay mặt (hướng nhìn) về phía mục tiêu (Dummy / Player) khi tấn công.
     */
    protected void faceTarget(ICombatEntity target) {
        if (target == null) return;
        double dx = target.getCenter().getX() - this.getCenter().getX();
        if (dx < -1.0) {
            this.setFacingDirection(Direction.LEFT);
            this.faceHorizontal(true);
        } else if (dx > 1.0) {
            this.setFacingDirection(Direction.RIGHT);
            this.faceHorizontal(false);
        }
    }

    /**
     * Xoay mặt (hướng nhìn) theo hướng di chuyển (Trái / Phải) khi bước đi.
     */
    protected void faceMovement(double dx) {
        if (dx < -0.1) {
            this.setFacingDirection(Direction.LEFT);
            this.faceHorizontal(true);
        } else if (dx > 0.1) {
            this.setFacingDirection(Direction.RIGHT);
            this.faceHorizontal(false);
        }
    }

    /**
     * Tìm mục tiêu — lấy thực thể chiến đấu đầu tiên thuộc đội 1 (người chơi / bia tập)
     * từ danh sách các combat entity trong môi trường hiện tại.
     */
    protected void findTarget() {
        if (Game.world().environment() == null) return;

        Collection<ICombatEntity> entities = Game.world().environment().getCombatEntities();
        if (entities == null) return;

        for (ICombatEntity entity : entities) {
            // Đội 1 = người chơi / bia tập bắn
            if (entity.getTeam() == 1 && !entity.isDead()) {
                this.target = entity;
                return;
            }
        }
        this.target = null;
    }

    /**
     * Logic AI chính cho kẻ địch tầm xa:
     * 0. Nếu đang khựng lại tung đòn bắn: Đứng yên, quay mặt theo mục tiêu, bắn đúng nhịp rồi kết thúc.
     * 1. Tìm mục tiêu nếu chưa có hoặc mục tiêu đã chết.
     * 2. Nếu mục tiêu ngoài tầm phát hiện → đứng yên.
     * 3. Nếu trong tầm bắn và hết cooldown → KHỰNG LẠI ĐỂ BẮN (không di chuyển trong lúc tung đòn).
     * 4. Nếu quá gần (< safeDistance) → chạy lùi (kiting).
     * 5. Nếu quá xa (> attackRange) → tiến lại gần.
     * 6. Nếu trong cự ly thích hợp → di chuyển qua lại trái - phải (strafe).
     */
    @Override
    protected void updateAI() {
        // 0. Nếu đang trong trạng thái khựng lại tung đòn bắn:
        if (isAttacking) {
            long elapsed = Game.time().now() - attackStartTime;

            // Luôn xoay mặt về hướng mục tiêu trong lúc khựng lại tung đòn
            if (target != null && !target.isDead()) {
                faceTarget(target);
            }

            // Đến thời điểm lấy đà (attackWindupMs) và chưa phóng đạn -> Phóng đạn!
            if (!hasFiredInCurrentAttack && elapsed >= attackWindupMs) {
                hasFiredInCurrentAttack = true;
                if (target != null && !target.isDead()) {
                    attack(target);
                }
            }

            // Hết thời gian khựng lại: kết thúc đòn đánh, tính cooldown và tiếp tục di chuyển
            if (elapsed >= attackPauseTotalMs) {
                isAttacking = false;
                recordAttack();
            }

            return; // ĐỨNG YÊN TUYỆT ĐỐI (KHỰNG LẠI), KHÔNG DI CHUYỂN!
        }

        // Bước 1: Tìm mục tiêu
        if (target == null || target.isDead()) {
            findTarget();
        }

        if (target == null) {
            return;
        }

        // Bước 2: Ngoài tầm phát hiện
        if (!isInDetectionRange(target)) {
            return;
        }

        // Bước 3: Kiểm tra bắn trước khi di chuyển!
        // Nếu trong tầm bắn, có đường đạn thông thoáng và đã hết cooldown: Bắt đầu khựng lại để bắn!
        if (canAttack() && isInAttackRange(target) && hasAttackLine()) {
            this.isAttacking = true;
            this.hasFiredInCurrentAttack = false;
            this.attackStartTime = Game.time().now();
            // LÚC TẤN CÔNG: Luôn quay mặt về phía nhân vật (mục tiêu)
            faceTarget(target);
            return; // Khựng lại ngay từ frame này!
        }

        double distance = this.getCenter().distance(target.getCenter());

        // Bước 4: Quá gần → chạy lùi (moveAway tự quay mặt theo hướng di chuyển)
        if (distance < safeDistance) {
            moveAway(target.getCenter());
        }
        // Bước 5: Quá xa hoặc bị tường chắn mất tầm nhìn → tiến lại gần (moveTowards tự quay mặt theo hướng di chuyển)
        else if (distance > attackRange || !hasAttackLine()) {
            moveTowards(target.getCenter());
        }
        // Bước 6: Trong tầm bắn lý tưởng → Di chuyển qua lại trái - phải (quay mặt theo hướng lướt)
        else {
            strafeLeftRight(target);
        }
    }

    @Override
    public void moveTowards(Point2D destination) {
        if (destination != null) {
            faceMovement(destination.getX() - this.getCenter().getX());
        }
        super.moveTowards(destination);
    }

    /**
     * Di chuyển qua lại thuần túy TRÁI - PHẢI (Horizontal Strafe).
     * Đi hướng nào thì quay mặt về hướng đó.
     */
    protected void strafeLeftRight(ICombatEntity target) {
        if (target == null) return;

        long now = Game.time().now();
        // Đổi chiều di chuyển trái <-> phải sau mỗi chu kỳ
        if (now - lastStrafeSwitchTime >= strafeSwitchIntervalMs) {
            strafeDirection = -strafeDirection;
            lastStrafeSwitchTime = now;
        }

        Point2D myCenter = this.getCenter();
        // Nhích nhẹ trái - phải trong ô (±16px) giữ vững cự ly hàng ngũ cách nhau 1 ô vuông
        double targetX = myCenter.getX() + strafeDirection * 16.0;
        double targetY = myCenter.getY();

        stepToward(new Point2D.Double(targetX, targetY), this.moveSpeed * 0.5);
        // ĐI HƯỚNG NÀO THÌ QUAY MẶT VỀ HƯỚNG ĐÓ:
        faceMovement(strafeDirection);
    }

    /**
     * Di chuyển ra xa khỏi một điểm (chạy lùi / kiting).
     * Quay mặt theo hướng di chuyển lùi.
     */
    protected void moveAway(Point2D from) {
        if (from == null) return;

        Point2D myCenter = this.getCenter();

        double dx = myCenter.getX() - from.getX();
        double dy = myCenter.getY() - from.getY();

        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001) {
            dx = Math.random() - 0.5;
            dy = Math.random() - 0.5;
            length = Math.sqrt(dx * dx + dy * dy);
        }

        dx /= length;
        dy /= length;

        double fleeX = myCenter.getX() + dx * safeDistance;
        double fleeY = myCenter.getY() + dy * safeDistance;

        // Đi hướng nào thì quay mặt về hướng đó:
        faceMovement(dx);
        stepToward(new Point2D.Double(fleeX, fleeY), this.moveSpeed);
    }

    @Override
    public abstract void attack(ICombatEntity target);

    // ==================== Getter / Setter ====================

    public double getSafeDistance() {
        return safeDistance;
    }

    public void setSafeDistance(double safeDistance) {
        this.safeDistance = safeDistance;
    }

    public double getProjectileSpeed() {
        return projectileSpeed;
    }

    public void setProjectileSpeed(double projectileSpeed) {
        this.projectileSpeed = projectileSpeed;
    }
}
