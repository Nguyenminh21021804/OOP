package entities.ranged;

import de.gurkenlabs.litiengine.Direction;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.ImageRenderer;
import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.resources.Resources;
import entities.Enemy;
import entities.MonsterType;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ShamanEnemy extends RangedEnemy implements IRenderable {

    private final Random random = new Random();

    // === Trạng thái niệm chú triệu hồi ===
    private boolean isCasting = false;
    private long castStartTime = 0;
    private final long castDuration = 800; // Niệm chú 800ms (vừa đủ thấy rõ động tác niệm)
    private Point2D targetSpawnPos = null;  // Vị trí vòng ma thuật triệu hồi

    // === Quản lý đệ tử triệu hồi ===
    private final int maxMinions = 3;
    private final List<Enemy> summonedMinions = new ArrayList<>();

    // === Quản lý hình ảnh gậy phép và vòng triệu hồi ===
    private Spritesheet staffSpritesheet;
    private Spritesheet summonSpritesheet;

    public ShamanEnemy() {
        super(MonsterType.ORC_SHAMAN);
    }

    /**
     * Không cho phép bắt đầu niệm đợt mới nếu đang trong quá trình niệm chú.
     */
    @Override
    public boolean canAttack() {
        if (isCasting) {
            return false;
        }
        return super.canAttack();
    }

    @Override
    protected boolean canRetarget() {
        return !isCasting && super.canRetarget();
    }

    @Override
    protected boolean canBeSeparated() {
        return !isCasting && super.canBeSeparated();
    }


    /**
     * Vòng lặp AI chính của Shaman:
     * - Dọn dẹp đệ tử đã chết.
     * - Nếu đang niệm chú (isCasting): đứng yên, quay mặt về mục tiêu, chờ đủ 800ms rồi triệu hồi hoặc bắn.
     * - Nếu chưa niệm và đủ điều kiện: bắt đầu niệm chú ngay!
     */
    @Override
    protected void updateAI() {
        cleanUpDeadMinions();

        // 1. Đang trong chu kỳ niệm chú triệu hồi (800ms) - LÚC TẤN CÔNG: quay mặt về nhân vật
        if (isCasting) {
            long elapsed = Game.time().now() - castStartTime;

            // Xoay mặt theo mục tiêu (nhân vật)
            if (target != null && !target.isDead()) {
                faceTarget(target);
            }

            // Hoàn thành niệm chú đúng 800ms
            if (elapsed >= castDuration) {
                finishCasting();
                isCasting = false;
                recordAttack(); // Bắt đầu tính cooldown từ khi triệu hồi/bắn xong
            }

            return; // Khựng lại đứng yên giơ gậy trong suốt thời gian niệm chú
        }

        if (target == null) {
            return;
        }

        if (!isInDetectionRange(target)) {
            return;
        }

        // Bước 2: Kiểm tra bắt đầu niệm chú triệu hồi (LÚC TẤN CÔNG: quay mặt về nhân vật)
        if (canAttack() && isInAttackRange(target) && hasAttackLine()) {
            startCasting(target);
            return;
        }

        double distance = this.getCenter().distance(target.getCenter());

        // Bước 3: Di chuyển giữ cự ly an toàn (kiting) hoặc tiến lại gần (ĐI HƯỚNG NÀO QUAY MẶT HƯỚNG ĐÓ)
        if (distance < safeDistance) {
            moveAway(target.getCenter());
        } else if (distance > attackRange) {
            moveTowards(target.getCenter());
        } else {
            strafeLeftRight(target);
        }
    }

    /**
     * Bắt đầu giơ gậy phép niệm chú triệu hồi.
     */
    @Override
    public void attack(ICombatEntity target) {
        startCasting(target);
    }

    /**
     * Kích hoạt niệm chú: Xác định vị trí xuất hiện vòng ma thuật bên cạnh Shaman.
     */
    private void startCasting(ICombatEntity target) {
        if (target == null || target.isDead() || isCasting) return;

        this.isCasting = true;
        this.castStartTime = Game.time().now();
        faceTarget(target);

        // Vị trí vòng ma thuật xuất hiện lệch bên cạnh Shaman ~35-45px
        double side = (random.nextBoolean() ? 1 : -1) * (35 + random.nextInt(15));
        this.targetSpawnPos = new Point2D.Double(this.getX() + side, this.getY() + 10);
    }

    /**
     * Hoàn tất niệm chú: Triệu hồi Skeleton hoặc Doc nếu chưa đủ 3 đệ tử,
     * nếu đã đủ đệ tử thì phóng cầu lửa ma pháp tấn công mục tiêu.
     */
    private void finishCasting() {
        if (Game.world().environment() == null) return;

        if (summonedMinions.size() < maxMinions) {
            // Triệu hồi ngẫu nhiên SkeletonEnemy hoặc DocEnemy
            Enemy minion;
            if (random.nextBoolean()) {
                minion = new SkeletonEnemy();
            } else {
                minion = new DocEnemy();
            }

            Point2D spawnLoc = (targetSpawnPos != null) ? targetSpawnPos : new Point2D.Double(getX() + 35, getY() + 10);
            minion.setLocation(spawnLoc);
            minion.setTeam(TEAM_ENEMY); // Cùng phe Enemy

            summonedMinions.add(minion);
            Game.world().environment().add(minion);
        } else {
            // Đã đủ đệ tử -> phóng đạn lửa ma pháp từ gậy phép vào mục tiêu
            if (target != null && !target.isDead()) {
                new Projectile(
                        "Fire bullet",
                        this.getCenter(),
                        target.getCenter(),
                        this.getProjectileSpeed(),
                        this.getDamage(),
                        TEAM_PLAYER,
                        null
                );
            }
        }
    }

    /**
     * Dọn dẹp những đệ tử đã chết khỏi danh sách quản lý.
     */
    private void cleanUpDeadMinions() {
        summonedMinions.removeIf(Enemy::isDead);
    }

    /**
     * Vẽ chi tiết Shaman cầm gậy phép đỏ và vòng ma thuật triệu hồi:
     * - Gậy phép được thu nhỏ tỉ lệ 0.65x (~19.5px chiều cao, vừa vặn chuẩn mực với thân Shaman 23px).
     * - Vị trí gậy neo chặt chẽ, không bị giật trái phải khi lượn qua lại.
     * - Khi niệm chú (isCasting): Gậy giơ cao lên, vòng ma thuật (summon) quay sáng dưới đất.
     */
    @Override
    public void render(Graphics2D g) {
        if (isDead()) return;

        if (staffSpritesheet == null) {
            staffSpritesheet = Resources.spritesheets().get("weapon_red_magic_staff");
        }
        if (summonSpritesheet == null) {
            summonSpritesheet = Resources.spritesheets().get("summon");
            if (summonSpritesheet == null) {
                summonSpritesheet = Resources.spritesheets().get("summon-idle");
            }
        }

        Point2D centerLoc = Game.world().camera().getViewportLocation(this.getCenter());

        // 1. Vẽ cây gậy phép đỏ (weapon_red_magic_staff) với tỉ lệ vừa vặn (thu nhỏ 0.65x, chống giật)
        if (staffSpritesheet != null) {
            BufferedImage staffImg = staffSpritesheet.getSprite(0);
            if (staffImg != null) {
                Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                double staffScale = 0.45; // Thu nhỏ còn 45% (~13.5px chiều cao, thon gọn vừa tay cầm của Shaman)

                // Vị trí gậy: khi niệm chú giơ cao lên (-8px), bình thường cầm bên hông (+1px)
                double staffOffsetY = isCasting ? -8.0 : 1.0;
                double staffOffsetX = isFacingLeft() ? -8.0 : 4.0;

                double sx = centerLoc.getX() + staffOffsetX;
                double sy = centerLoc.getY() + staffOffsetY;

                // Độ nghiêng của gậy: giơ gậy nghiêng về phía trước khi niệm chú
                double staffAngle = isCasting ? (isFacingLeft() ? -16.0 : 16.0) : (isFacingLeft() ? -5.0 : 5.0);

                AffineTransform tx = new AffineTransform();
                tx.translate(sx, sy);
                tx.scale(staffScale, staffScale);
                tx.rotate(Math.toRadians(staffAngle), staffImg.getWidth() / 2.0, staffImg.getHeight() / 2.0);

                g.drawImage(staffImg, tx, null);

                if (oldInterpolation != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
                }
            }
        }

        // 2. Vẽ vòng ma thuật triệu hồi dưới đất khi đang niệm chú (800ms)
        if (isCasting && targetSpawnPos != null && summonSpritesheet != null) {
            Point2D summonViewLoc = Game.world().camera().getViewportLocation(targetSpawnPos);

            // Chạy 4 frame hoạt họa của vòng ma thuật xoay tròn
            int totalFrames = summonSpritesheet.getTotalNumberOfSprites();
            int frame = totalFrames > 1 ? (int) ((Game.time().now() / 90) % totalFrames) : 0;
            BufferedImage summonImg = summonSpritesheet.getSprite(frame);

            if (summonImg != null) {
                double cx = summonViewLoc.getX() - summonImg.getWidth() / 2.0;
                double cy = summonViewLoc.getY() - summonImg.getHeight() / 2.0;
                ImageRenderer.render(g, summonImg, cx, cy);
            }
        }
    }

    // === Getter / Setter ===

    public boolean isCasting() {
        return isCasting;
    }

    public List<Enemy> getSummonedMinions() {
        return summonedMinions;
    }
}
