package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import static entities.Enemy.TEAM_PLAYER;

/**
 * TrainingDummy — Bia tập bắn / Bù nhìn đứng yên cho quái luyện tập.
 * 
 * Thuộc Team 1 (Đội người chơi), đứng yên tuyệt đối không di chuyển,
 * có lượng máu khổng lồ (99,999 HP) và tự động hồi phục khi bị trúng đòn.
 * Toàn bộ quái đánh xa (Team 2) sẽ tự động nhận diện bù nhìn này làm mục tiêu
 * để di chuyển trái - phải và xả đạn.
 */
public class TrainingDummy extends Creature implements IUpdateable, IRenderable {

    private long lastHitTime = 0;
    private int lastDamageTaken = 0;

    public TrainingDummy(double x, double y) {
        super("doc"); // Sử dụng sprite doc làm hình đại diện bia tập bắn

        this.setLocation(x, y);
        this.setTeam(TEAM_PLAYER); // Team 1 (cùng phe người chơi) để quái vật nhắm bắn
        this.setVelocity(0f); // Tốc độ = 0: đứng yên tuyệt đối

        this.getHitPoints().setMax(99999);
        this.getHitPoints().setValue(99999);
        this.setIndestructible(false); // Vẫn nhận đòn và trừ máu để hiển thị sát thương
        this.setName(""); // Ẩn tên khỏi màn hình debug

        Game.loop().attach(this);
    }

    @Override
    public void loaded(de.gurkenlabs.litiengine.environment.Environment env) {
        super.loaded(env);
        if (env != null) {
            env.add(this, RenderType.OVERLAY);
        }
    }

    @Override
    public void hit(int damage) {
        super.hit(damage);
        long now = Game.time().now();
        if (now - this.lastHitTime < 200) {
            this.lastDamageTaken += damage;
        } else {
            this.lastDamageTaken = damage;
        }
        this.lastHitTime = now;
    }

    @Override
    public void update() {
        // Tự động hồi phục nếu máu bị tụt xuống để luôn tồn tại cho quái bắn
        if (this.isDead() || this.getHitPoints().getValue() < 50000) {
            this.resurrect();
            this.getHitPoints().setValue(this.getHitPoints().getMax());
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (isDead()) return;

        long now = Game.time().now();
        long elapsed = now - lastHitTime;

        // Hiển thị chữ sát thương đỏ/cam bay lên khi trúng đạn trong 600ms
        if (elapsed < 600 && lastDamageTaken > 0) {
            java.awt.geom.Point2D viewCenter = (Game.world().camera() != null)
                ? Game.world().camera().getViewportLocation(getCenter())
                : getCenter();
            int floatY = (int) Math.round(viewCenter.getY() - 18 - (elapsed / 25.0));
            int dx = (int) Math.round(viewCenter.getX() - 12);

            g.setFont(new Font("Monospaced", Font.BOLD, 13));
            g.setColor(new Color(255, 60, 40, (int) Math.max(40, 255 - elapsed * 0.4)));
            g.drawString("-" + lastDamageTaken + " HP", dx, floatY);
        }
    }
}
