package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Creature;

import static entities.Enemy.TEAM_PLAYER;

/**
 * TrainingDummy — Bia tập bắn / Bù nhìn đứng yên cho quái luyện tập.
 * 
 * Thuộc Team 1 (Đội người chơi), đứng yên tuyệt đối không di chuyển,
 * có lượng máu khổng lồ (99,999 HP) và tự động hồi phục khi bị trúng đòn.
 * Toàn bộ quái đánh xa (Team 2) sẽ tự động nhận diện bù nhìn này làm mục tiêu
 * để di chuyển trái - phải và xả đạn.
 */
public class TrainingDummy extends Creature implements IUpdateable {

    public TrainingDummy(double x, double y) {
        super("doc"); // Sử dụng sprite doc làm hình đại diện bia tập bắn

        this.setLocation(x, y);
        this.setTeam(TEAM_PLAYER); // Team 1 (cùng phe người chơi) để quái vật nhắm bắn
        this.setVelocity(0f); // Tốc độ = 0: đứng yên tuyệt đối

        this.getHitPoints().setMax(99999);
        this.getHitPoints().setValue(99999);
        this.setIndestructible(false); // Vẫn nhận đòn và trừ máu để hiển thị sát thương

        Game.loop().attach(this);
    }

    @Override
    public void update() {
        // Tự động hồi phục nếu máu bị tụt xuống để luôn tồn tại cho quái bắn
        if (this.isDead() || this.getHitPoints().getValue() < 50000) {
            this.resurrect();
            this.getHitPoints().setValue(this.getHitPoints().getMax());
        }
    }
}

