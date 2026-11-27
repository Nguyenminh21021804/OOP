package entities.ranged;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import entities.Enemy;
import entities.MonsterType;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * NecromancerEnemy — Phù thủy bóng tối bắn phép nhắm theo (homing) và triệu hồi lính xương.
 * 
 * Necromancer giữ khoảng cách xa, bắn đạn tối (dark_bolt) tự động
 * bám theo mục tiêu (homing), và định kỳ triệu hồi SkeletonEnemy
 * để gây áp lực lên người chơi.
 */
public class NecromancerEnemy extends RangedEnemy {

    // === Triệu hồi lính xương ===
    private long lastSummonTime = 0;                            // thời điểm triệu hồi lần cuối
    private long summonCooldownMs = 8000;                       // hồi chiêu triệu hồi: 8 giây
    private int maxSummons = 2;                                 // số lính xương tối đa cùng lúc
    private List<SkeletonEnemy> summonedMinions = new ArrayList<>();  // danh sách lính đã triệu hồi

    /**
     * Khởi tạo Necromancer với chỉ số cố định.
     * - Máu: 100, Sát thương: 18, Tốc độ: 1.5
     * - Tầm đánh: 280, Tầm phát hiện: 500, Hồi chiêu: 2.5 giây
     * - Khoảng cách an toàn: 180, Tốc độ đạn: 200
     */
    public NecromancerEnemy() {
        super(MonsterType.NECROMANCER);
    }

    /**
     * Tấn công — bắn đạn tối (dark_bolt) có khả năng bám theo mục tiêu (homing).
     * 
     * Đạn tối liên tục điều chỉnh hướng bay về phía mục tiêu mỗi frame,
     * khiến việc né tránh trở nên rất khó. Sử dụng Game.loop().attach()
     * để cập nhật vị trí đạn mỗi frame.
     */
    @Override
    public void attack(ICombatEntity target) {
        if (target == null || target.isDead()) {
            return;
        }

        // Vị trí xuất phát của đạn tối (từ tâm Necromancer)
        Point2D startPos = new Point2D.Double(this.getCenter().getX(), this.getCenter().getY());

        // Tạo đạn homing — cập nhật hướng bay mỗi frame
        launchHomingDarkBolt(startPos, target);
    }

    /**
     * Phóng đạn tối homing (dark_bolt) từ vị trí bắt đầu đến mục tiêu.
     * 
     * Cơ chế homing đơn giản:
     *   - Mỗi frame, tính lại góc từ vị trí đạn đến mục tiêu
     *   - Cập nhật vận tốc đạn theo hướng mới
     *   - Nếu đạn đến đủ gần mục tiêu → gây sát thương
     *   - Đạn tự hủy sau khi trúng hoặc bay quá xa
     */
    private void launchHomingDarkBolt(Point2D startPos, ICombatEntity target) {
        // Mảng lưu vị trí đạn (dùng mảng để có thể thay đổi trong lambda)
        final double[] bulletPos = {startPos.getX(), startPos.getY()};

        // Khoảng cách tối đa đạn có thể bay trước khi tự hủy
        final double maxTravelDistance = 600;
        final double[] traveledDistance = {0};

        // Bán kính va chạm — nếu đạn gần mục tiêu hơn giá trị này → trúng
        final double hitRadius = 15.0;

        // Đánh dấu đạn còn hoạt động
        final boolean[] active = {true};

        int sát_thương = getDamage();
        final var spawnEnv = Game.world().environment();

        // Đăng ký cập nhật mỗi frame cho đạn homing
        Game.loop().attach(() -> {
            // Đạn đã hết hoạt động
            if (!active[0]) {
                return;
            }

            // Mục tiêu đã chết, chủ thể đã chết hoặc đã chuyển map — hủy đạn
            if (target.isDead() || this.isDead() || Game.world().environment() != spawnEnv) {
                active[0] = false;
                return;
            }

            // === Tính hướng homing: từ đạn đến mục tiêu hiện tại ===
            double dx = target.getCenter().getX() - bulletPos[0];
            double dy = target.getCenter().getY() - bulletPos[1];
            double distToTarget = Math.sqrt(dx * dx + dy * dy);

            // Kiểm tra va chạm — đạn đã trúng mục tiêu
            if (distToTarget <= hitRadius) {
                target.hit(sát_thương);
                active[0] = false;
                return;
            }

            // Chuẩn hóa hướng và di chuyển đạn
            double dirX = dx / distToTarget;
            double dirY = dy / distToTarget;

            // Tính quãng đường di chuyển trong frame này
            // Game.loop().getDeltaTime() trả về thời gian giữa 2 frame (ms)
            double deltaSeconds = Game.loop().getDeltaTime() / 1000.0;
            double moveStep = projectileSpeed * deltaSeconds;

            // Cập nhật vị trí đạn theo hướng homing
            bulletPos[0] += dirX * moveStep;
            bulletPos[1] += dirY * moveStep;

            // Cộng dồn quãng đường đã bay
            traveledDistance[0] += moveStep;

            // Đạn bay quá xa — tự hủy
            if (traveledDistance[0] >= maxTravelDistance) {
                active[0] = false;
            }
        });
    }

    /**
     * Cập nhật AI mỗi frame.
     * 
     * Ngoài hành vi ranged bình thường (di chuyển, tấn công),
     * Necromancer còn:
     *   1. Dọn dẹp lính xương đã chết khỏi danh sách
     *   2. Kiểm tra điều kiện triệu hồi (hồi chiêu + số lượng)
     *   3. Triệu hồi lính xương mới nếu đủ điều kiện
     */
    @Override
    protected void updateAI() {
        // Gọi AI ranged bình thường — di chuyển, giữ khoảng cách, tấn công
        super.updateAI();

        // === Dọn dẹp lính xương đã chết ===
        cleanUpDeadMinions();

        // === Kiểm tra và triệu hồi lính xương mới ===
        long now = Game.time().now();
        boolean cooldownElapsed = (now - lastSummonTime) >= summonCooldownMs;
        boolean canSummonMore = getAliveMinionsCount() < maxSummons;

        if (cooldownElapsed && canSummonMore && target != null && !target.isDead()) {
            summonMinion();
            lastSummonTime = now;
        }
    }

    /**
     * Triệu hồi một SkeletonEnemy tại vị trí của Necromancer.
     * 
     * Lính xương được tạo ra ngay bên cạnh Necromancer và
     * tự động tấn công người chơi theo AI riêng của nó.
     */
    private void summonMinion() {
        // Tạo lính xương mới
        SkeletonEnemy skeleton = new SkeletonEnemy();

        // Đặt vị trí lính xương tại vị trí Necromancer (lệch nhẹ để không chồng)
        double offsetX = (Math.random() - 0.5) * 40; // lệch ngẫu nhiên ±20px
        double offsetY = (Math.random() - 0.5) * 40;
        skeleton.setLocation(
                this.getX() + offsetX,
                this.getY() + offsetY
        );
        skeleton.scaleForMap(
                Math.min(skeleton.getMaxHealth(), this.getMaxHealth()),
                Math.min(skeleton.getDamage(), this.getDamage())
        );

        // Thêm vào danh sách quản lý
        summonedMinions.add(skeleton);

        // Thêm vào thế giới game để hoạt động
        Game.world().environment().add(skeleton);
    }

    /**
     * Dọn dẹp lính xương đã chết khỏi danh sách.
     * Sử dụng Iterator để tránh ConcurrentModificationException.
     */
    private void cleanUpDeadMinions() {
        summonedMinions.removeIf(Enemy::isDead);
    }

    /**
     * Đếm số lính xương còn sống.
     */
    private int getAliveMinionsCount() {
        int count = 0;
        for (SkeletonEnemy minion : summonedMinions) {
            if (!minion.isDead()) {
                count++;
            }
        }
        return count;
    }

    // === Getter / Setter ===

    /** Lấy hồi chiêu triệu hồi (ms). */
    public long getSummonCooldownMs() {
        return summonCooldownMs;
    }

    /** Đặt hồi chiêu triệu hồi (ms). */
    public void setSummonCooldownMs(long summonCooldownMs) {
        this.summonCooldownMs = summonCooldownMs;
    }

    /** Lấy số lính xương tối đa. */
    public int getMaxSummons() {
        return maxSummons;
    }

    /** Đặt số lính xương tối đa. */
    public void setMaxSummons(int maxSummons) {
        this.maxSummons = maxSummons;
    }

    /** Lấy danh sách lính xương đã triệu hồi. */
    public List<SkeletonEnemy> getSummonedMinions() {
        return summonedMinions;
    }
}
