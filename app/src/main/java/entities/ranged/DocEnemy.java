package entities.ranged;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import entities.MonsterType;

public class DocEnemy extends RangedEnemy {

    public DocEnemy() {
        super(MonsterType.DOC);
    }

    /**
     * Override updateAI: nếu người chơi áp sát trong vùng panicZone,
     * Doc sẽ ưu tiên lùi lại hoặc khựng lại ném dao thoát thân (hit and run).
     */
    @Override
    protected void updateAI() {
        // 0. Nếu đang trong trạng thái khựng lại ném dao, thực thi xử lý đứng yên của RangedEnemy
        if (isAttacking) {
            super.updateAI();
            return;
        }

        if (target == null) {
            return;
        }

        if (!isInDetectionRange(target)) {
            return;
        }

        double distance = this.getCenter().distance(target.getCenter());

        // Vùng nguy hiểm: người chơi áp sát
        double panicZone = safeDistance * 1.5;
        if (distance < panicZone) {
            // Nếu có thể tấn công và không bị tường cản: Khựng lại ném dao phản công
            if (canAttack() && isInAttackRange(target) && hasAttackLine()) {
                this.isAttacking = true;
                this.hasFiredInCurrentAttack = false;
                this.attackStartTime = Game.time().now();
                faceTarget(target);
                return;
            }

            // Chạy lùi khẩn cấp nếu chưa đến lượt tấn công
            moveAway(target.getCenter());
            return;
        }

        super.updateAI();
    }

    /**
     * Phóng dao găm (weapon_knife) bay thẳng về phía mục tiêu.
     */
    @Override
    public void attack(ICombatEntity target) {
        if (target == null || target.isDead()) return;

        // Tạo dao găm bay thẳng về phía mục tiêu
        new Projectile(
                "weapon_knife",            // sprite dao trong game.litidata
                this.getCenter(),          // vị trí ném
                target.getCenter(),        // vị trí mục tiêu
                this.getProjectileSpeed(), // tốc độ bay
                this.getDamage(),          // sát thương
                TEAM_PLAYER,                         // mục tiêu Player
                null                       // sát thương vật lý
        );
    }
}
