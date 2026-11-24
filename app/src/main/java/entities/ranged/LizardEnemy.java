package entities.ranged;

import de.gurkenlabs.litiengine.Direction;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.AnimationInfo;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.ImageRenderer;
import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.graphics.animation.Animation;
import de.gurkenlabs.litiengine.resources.Resources;
import entities.MonsterType;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * LizardEnemy — Thằn lằn cung thủ (Lizard Archer) sử dụng cung bắn tên.
 *
 * Chu trình bắn cung nhanh nhẹn, dứt khoát:
 *   1. Giương cung (0 - 60ms): Đứng yên, quay mặt và cầm cung hướng về mục tiêu.
 *   2. Kéo cung & Gài tên (60 - 220ms): Kéo căng dây cung (Frame 1), hiện rõ mũi tên gài trên dây.
 *   3. Phóng tên (tại 220ms): Dây buông, mũi tên trên cung BIẾN MẤT NGAY LẬP TỨC và bay đi thành Projectile.
 *   4. Giật nảy cung (220 - 310ms): Cung giật nhẹ (Frame 0) sau khi buông tên rồi hạ xuống.
 *
 * Chỉ số:
 *   - Máu: 110
 *   - Sát thương: 18 (mũi tên xuyên giáp)
 *   - Tốc độ: 55 pixel/giây
 *   - Tầm bắn: 270px
 *   - Tầm phát hiện: 480px
 *   - Cooldown: 1300ms (1.3s)
 *   - Cung: "bow-shoot"
 *   - Tên: "weapon_arrow"
 * Hoạt họa di chuyển:
 *   - Khi chạy/di chuyển: phát hoạt họa chạy "lizard-f-walk" 4 khung hình.
 *   - Khi đứng yên/ngắm bắn: phát hoạt họa đứng "lizard-f-idle".
 */
@AnimationInfo(spritePrefix = {"lizard-f", "lizard"})
public class LizardEnemy extends RangedEnemy implements IRenderable {

    // Thời lượng các giai đoạn bắn cung (mili-giây) 
    private static final long DRAW_DURATION = 220;    // Thời gian kéo cung và nạp tên ngắm bắn (220ms)
    private static final long RECOIL_DURATION = 90;   // Thời gian cung giật nảy sau khi buông tên (90ms)

    // Quản lý trạng thái chu trình bắn cung
    private boolean isAiming = false;       // Đang trong chu kỳ ngắm bắn
    private boolean arrowReleased = false;  // Mũi tên đã được buông tay phóng đi chưa
    private long bowDrawStartTime = 0;      // Thời điểm bắt đầu giương cung
    private double bowAngle = 0;            // Góc ngắm bắn hướng về mục tiêu

    private Spritesheet bowSpritesheet;
    private Spritesheet arrowSpritesheet;

    public LizardEnemy() {
        super(MonsterType.LIZARD);

        // Đăng ký quy tắc hoạt họa di chuyển walk và đứng idle rõ ràng
        initAnimations();
    }

    /**
     * Khởi tạo hoạt họa chạy (walk) và đứng (idle) đảm bảo chuyển động mượt mà và xoay mặt đúng hướng (Trái / Phải).
     */
    private void initAnimations() {
        if (this.animations() != null) {
            registerAnimation("lizard-f-walk-left");
            registerAnimation("lizard-f-walk-right");
            registerAnimation("lizard-f-walk");
            registerAnimation("lizard-f-idle-left");
            registerAnimation("lizard-f-idle-right");
            registerAnimation("lizard-f-idle");

            // Quy tắc hoạt họa động: quay mặt về đúng hướng (left/right) theo vị trí mục tiêu
            this.animations().addRule(
                c -> !this.isIdle() && !this.isAiming,
                c -> "lizard-f-walk-" + (this.getFacingDirection() == Direction.LEFT ? "left" : "right")
            );
            this.animations().addRule(
                c -> this.isIdle() || this.isAiming,
                c -> "lizard-f-idle-" + (this.getFacingDirection() == Direction.LEFT ? "left" : "right")
            );
        }
    }

    private void registerAnimation(String name) {
        Spritesheet sheet = Resources.spritesheets().get(name);
        if (sheet != null && !this.animations().hasAnimation(name)) {
            this.animations().add(new Animation(sheet, true));
        }
    }

    /**
     * Không cho phép kích hoạt đòn đánh mới nếu đang trong chu kỳ kéo cung ngắm bắn.
     */
    @Override
    public boolean canAttack() {
        if (isAiming) {
            return false;
        }
        return super.canAttack();
    }

    @Override
    protected boolean canRetarget() {
        return !isAiming && super.canRetarget();
    }

    @Override
    protected boolean canBeSeparated() {
        return !isAiming && super.canBeSeparated();
    }

    /**
     * Vòng lặp AI:
     * - Trong lúc giương cung và kéo tên (isAiming == true), Lizard đứng yên, bám sát góc ngắm theo mục tiêu.
     * - Khi kéo căng xong (elapsed >= DRAW_DURATION), lập tức phóng mũi tên và xóa tên trên cung.
     * - Sau khi cung giật nảy xong (elapsed >= DRAW_DURATION + RECOIL_DURATION), kết thúc chu kỳ ngắm bắn.
     */
    @Override
    protected void updateAI() {
        if (isAiming) {
            long elapsed = Game.time().now() - bowDrawStartTime;

            // 1. Liên tục cập nhật góc ngắm theo mục tiêu trong lúc kéo cung
            if (target != null && !target.isDead()) {
                updateAimAngle(target);
            }

            // 2. Đúng thời điểm kéo căng xong (220ms) và chưa bắn: PHÓNG MŨI TÊN ĐI NGAY
            if (!arrowReleased && elapsed >= DRAW_DURATION) {
                arrowReleased = true; // Đánh dấu đã bắn: Mũi tên trên cung biến mất NGAY LẬP TỨC
                shootArrow();         // Sinh thực thể đạn bay (Projectile)
            }

            // 3. Hết thời gian giật cung (220 + 90 = 310ms): Hạ cung, tính cooldown và tiếp tục di chuyển
            if (elapsed >= DRAW_DURATION + RECOIL_DURATION) {
                isAiming = false;
                recordAttack(); // Bắt đầu tính cooldown từ thời điểm mũi tên đã rời cung
            }

            return; // Đứng yên trong suốt chu kỳ bắn, không di chuyển hỗn loạn
        }

        // Khi di chuyển bình thường: Đi hướng nào thì quay mặt về hướng đó (theo logic của RangedEnemy)
        super.updateAI();
    }

    /**
     * Bắt đầu động tác giương cung, kéo căng dây cung và nạp mũi tên.
     */
    @Override
    public void attack(ICombatEntity target) {
        if (target == null || target.isDead() || isAiming) return;

        this.isAiming = true;
        this.arrowReleased = false; // Đặt tên lên dây cung
        this.bowDrawStartTime = Game.time().now();

        updateAimAngle(target);
    }

    /**
     * Cập nhật góc ngắm của cung và hướng nhìn (Trái / Phải) theo mục tiêu.
     */
    private void updateAimAngle(ICombatEntity target) {
        double dx = target.getCenter().getX() - this.getCenter().getX();
        double dy = target.getCenter().getY() - this.getCenter().getY();
        this.bowAngle = Math.toDegrees(Math.atan2(dy, dx));

        if (dx < 0) {
            this.setFacingDirection(Direction.LEFT);
            this.faceHorizontal(true);
        } else {
            this.setFacingDirection(Direction.RIGHT);
            this.faceHorizontal(false);
        }
    }

    /**
     * Phóng mũi tên bay về phía mục tiêu.
     * Tọa độ xuất phát đúng bằng vị trí đầu mũi tên trên cây cung.
     */
    private void shootArrow() {
        if (target == null || target.isDead() || Game.world().environment() == null) return;

        double rad = Math.toRadians(bowAngle);
        // Vị trí mũi tên rời cung cách tâm Lizard 10px theo hướng ngắm (sát vào tay)
        Point2D spawnPos = new Point2D.Double(
                this.getCenter().getX() + Math.cos(rad) * 10.0,
                this.getCenter().getY() + Math.sin(rad) * 10.0
        );

        new Projectile(
                "weapon_arrow",            // sprite mũi tên từ game.litidata
                spawnPos,                  // vị trí đầu cung bắn ra
                target.getCenter(),        // vị trí mục tiêu
                this.getProjectileSpeed(), // 190 px/s
                this.getDamage(),          // sát thương
                TEAM_PLAYER,                         // đội nhận sát thương (Player / Dummy)
                null                       // sát thương vật lý
        );
    }

    /**
     * Vẽ hiệu ứng cung và mũi tên đồng bộ hoàn toàn với logic game:
     * - 0 - 60ms: Cầm cung giương lên (Frame 0: relaxed).
     * - 60 - 220ms: Dây cung kéo căng (Frame 1) VÀ HIỆN MŨI TÊN GÀI TRÊN DÂY.
     * - Khi arrowReleased == true: MŨI TÊN TRÊN DÂY BIẾN MẤT HOÀN TOÀN (chỉ còn đạn Projectile bay đi).
     * - 220 - 310ms: Cung bật về Frame 0 tạo hiệu ứng giật nảy sau phát bắn trong 90ms rồi biến mất.
     */
    @Override
    public void render(Graphics2D g) {
        if (isDead() || !isAiming) return;

        long elapsed = Game.time().now() - bowDrawStartTime;

        if (bowSpritesheet == null) {
            bowSpritesheet = Resources.spritesheets().get("bow-shoot");
            if (bowSpritesheet == null) {
                bowSpritesheet = Resources.spritesheets().get("bow-shoot-idle");
            }
        }
        if (arrowSpritesheet == null) {
            arrowSpritesheet = Resources.spritesheets().get("weapon_arrow");
            if (arrowSpritesheet == null) {
                arrowSpritesheet = Resources.spritesheets().get("weapon_arrow-idle");
            }
        }

        if (bowSpritesheet == null) return;

        Point2D centerLoc = Game.world().camera().getViewportLocation(this.getCenter());
        double rad = Math.toRadians(bowAngle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // Frame của cây cung:
        // - Khi đã bắn (arrowReleased == true): Cung giật nảy về Frame 0
        // - Khi đang kéo căng (elapsed >= 60 && !arrowReleased): Frame 1
        // - Khi mới giương cung (elapsed < 60): Frame 0
        int totalFrames = bowSpritesheet.getTotalNumberOfSprites();
        int frame = (!arrowReleased && elapsed >= 60 && totalFrames > 1) ? 1 : 0;
        BufferedImage bowImg = bowSpritesheet.getSprite(frame);

        if (bowImg != null) {
            double bowDist = 8.0; // Nằm sát gần tay Lizard (giảm từ 16px xuống 8px)
            double bx = centerLoc.getX() + cos * bowDist - bowImg.getWidth() / 2.0;
            double by = centerLoc.getY() + sin * bowDist - bowImg.getHeight() / 2.0;

            // 1. Vẽ cây cung xoay theo góc ngắm
            ImageRenderer.renderRotated(g, bowImg, bx, by, bowAngle);

            // 2. Vẽ mũi tên trên dây cung:
            // CHỈ VẼ KHI MŨI TÊN CHƯA BẮN ĐI (!arrowReleased) VÀ ĐÃ KÉO CĂNG DÂY (elapsed >= 60)
            // Ngay khi arrowReleased = true, mũi tên trên cung biến mất 100% không còn lưu lại
            if (!arrowReleased && elapsed >= 60 && arrowSpritesheet != null) {
                BufferedImage arrowImg = arrowSpritesheet.getSprite(0);
                if (arrowImg != null) {
                    double arrowDist = bowDist + 2.0;
                    double ax = centerLoc.getX() + cos * arrowDist - arrowImg.getWidth() / 2.0;
                    double ay = centerLoc.getY() + sin * arrowDist - arrowImg.getHeight() / 2.0;

                    // Xoay góc bowAngle + 90 để đầu tên hướng thẳng vào mục tiêu
                    ImageRenderer.renderRotated(g, arrowImg, ax, ay, bowAngle + 90.0);
                }
            }
        }
    }
}
