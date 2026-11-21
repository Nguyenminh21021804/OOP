package entities.ranged;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.ImageRenderer;
import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class Projectile extends Creature implements IUpdateable, IRenderable {

    private final int damage;
    private final int targetTeam;
    private final double speed;
    private final double dirX;
    private final double dirY;
    private final double maxDistance;
    private double traveledDistance = 0;
    private final Consumer<ICombatEntity> onHitEffect;
    private boolean active = true;

    // Quản lý sprite và góc xoay hướng bay
    private final String spriteName;
    private Spritesheet spritesheet;
    private final double rotationAngle;

    /**
     * Khởi tạo và bắn một viên đạn vào thế giới game.
     *
     * @param spritePrefix Tên sprite trong game.litidata (ví dụ: "ice bullet", "Fire bullet", "weapon_knife", "weapon_arrow")
     * @param startPos     Vị trí bắn
     * @param targetPos    Vị trí mục tiêu
     * @param speed        Tốc độ bay (px/s)
     * @param damage       Sát thương khi bắn trúng
     * @param targetTeam   Đội của mục tiêu nhận sát thương (thường là 1 - Người chơi)
     * @param onHitEffect  Hiệu ứng phụ khi trúng (ví dụ: làm chậm, thiêu đốt, ...), có thể truyền null
     */
    public Projectile(String spritePrefix, Point2D startPos, Point2D targetPos,
                      double speed, int damage, int targetTeam,
                      Consumer<ICombatEntity> onHitEffect) {
        super(spritePrefix);

        this.spriteName = spritePrefix;
        this.speed = speed;
        this.damage = damage;
        this.targetTeam = targetTeam;
        this.onHitEffect = onHitEffect;
        this.maxDistance = 600.0; // Khoảng cách bay tối đa trước khi tự biến mất

        // Đặt kích thước đạn và căn tâm đạn trùng đúng startPos
        this.setSize(16, 16);
        this.setLocation(startPos.getX() - this.getWidth() / 2.0, startPos.getY() - this.getHeight() / 2.0);

        // Tính toán vector hướng bay chuẩn hóa
        double dx = targetPos.getX() - startPos.getX();
        double dy = targetPos.getY() - startPos.getY();
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 0.001) {
            this.dirX = 1.0;
            this.dirY = 0.0;
        } else {
            this.dirX = dx / len;
            this.dirY = dy / len;
        }

        // Tính góc xoay hướng đạn (độ)
        double angleDeg = Math.toDegrees(Math.atan2(this.dirY, this.dirX));

        // Điều chỉnh góc xoay theo hình dạng ban đầu của sprite:
        // - weapon_arrow, weapon_knife, ice: hình vẽ gốc hướng đầu thẳng đứng LÊN TRÊN (UP / -Y) -> cần xoay +90 độ
        // - Fire bullet: hình vẽ tròn xoay
        String lower = spritePrefix.toLowerCase();
        if (lower.contains("arrow") || lower.contains("knife") || lower.contains("ice")) {
            this.rotationAngle = angleDeg + 90.0;
        } else {
            this.rotationAngle = angleDeg;
        }

        // Tìm Spritesheet phù hợp trong Resources
        Spritesheet sheet = Resources.spritesheets().get(spritePrefix);
        if (sheet == null) {
            sheet = Resources.spritesheets().get(spritePrefix + "-idle");
        }
        if (sheet == null) {
            sheet = Resources.spritesheets().get(spritePrefix.replace(" ", "_"));
        }
        if (sheet == null) {
            sheet = Resources.spritesheets().get(spritePrefix.replace(" ", "-"));
        }
        this.spritesheet = sheet;

        // Cấu hình Creature
        this.setScaling(false);
        this.setIndestructible(true);
        this.setCollision(false); // Không bị kẹt bởi địa hình
        this.setTeam(2);          // Cùng team với quái để không bị xô đẩy hoặc nhắm trúng

        // Tắt animation mặc định không xoay của Creature để render tự vẽ xoay trong render()
        if (this.animations() != null) {
            this.animations().setEnabled(false);
        }

        // Thêm vào vòng lặp và môi trường game
        Game.loop().attach(this);
        if (Game.world().environment() != null) {
            Game.world().environment().add(this);
        }
    }

    @Override
    public void update() {
        if (!active) {
            return;
        }

        if (Game.world().environment() == null) {
            destroy();
            return;
        }

        // Tính toán quãng đường di chuyển dựa trên delta time (giây)
        double dt = Game.loop().getDeltaTime() / 1000.0;
        double step = speed * dt;

        double nextX = getX() + dirX * step;
        double nextY = getY() + dirY * step;
        this.setLocation(nextX, nextY);
        traveledDistance += step;

        // Kiểm tra va chạm với các thực thể chiến đấu thuộc targetTeam
        for (ICombatEntity entity : Game.world().environment().getCombatEntities()) {
            if (entity.getTeam() == targetTeam && !entity.isDead()) {
                double distance = this.getCenter().distance(entity.getCenter());
                // Bán kính va chạm 20 pixel
                if (distance <= 20.0) {
                    // Gây sát thương
                    entity.hit(damage);

                    // Kích hoạt hiệu ứng đặc biệt khi trúng đạn (nếu có)
                    if (onHitEffect != null) {
                        onHitEffect.accept(entity);
                    }

                    destroy();
                    return;
                }
            }
        }

        // Hết tầm bắn tối đa -> tự hủy
        if (traveledDistance >= maxDistance) {
            destroy();
        }
    }

    /**
     * Tự vẽ đạn với góc xoay chính xác hướng về phía mục tiêu.
     */
    @Override
    public void render(Graphics2D g) {
        if (!active || isDead()) return;

        if (spritesheet == null) {
            spritesheet = Resources.spritesheets().get(spriteName);
            if (spritesheet == null) {
                spritesheet = Resources.spritesheets().get(spriteName + "-idle");
            }
            if (spritesheet == null) return;
        }

        int total = spritesheet.getTotalNumberOfSprites();
        // Đạn nhiều frame (như cầu lửa, đạn băng) sẽ tự động chạy hoạt họa (90ms/frame)
        int frame = total > 1 ? (int) ((Game.time().now() / 90) % total) : 0;
        BufferedImage img = spritesheet.getSprite(frame);
        if (img == null) return;

        // Lấy tọa độ hiển thị trên màn hình qua camera
        Point2D viewLoc = Game.world().camera().getViewportLocation(this.getCenter());
        double rx = viewLoc.getX() - img.getWidth() / 2.0;
        double ry = viewLoc.getY() - img.getHeight() / 2.0;

        ImageRenderer.renderRotated(g, img, rx, ry, rotationAngle);
    }

    /**
     * Hủy viên đạn khỏi vòng lặp và môi trường game.
     */
    public void destroy() {
        if (!active) return;
        active = false;
        Game.loop().detach(this);
        if (Game.world().environment() != null) {
            Game.world().environment().remove(this);
        }
    }

    public double getRotationAngle() {
        return rotationAngle;
    }
}
