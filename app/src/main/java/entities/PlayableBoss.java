package entities;

import java.awt.Graphics2D;

/**
 * Giao diện chung cho các Boss có thể điều khiển trực tiếp trong đấu trường thử nghiệm.
 */
public interface PlayableBoss {
    void setPlayerControlled(boolean controlled);
    void resetBossState();
    void playerPrimaryAttack();
    void playerFireSpit();
    void playerFireRing();
    void playerCharge();
    void toggleEnrage();
    void renderHealthBar(Graphics2D g, int screenW, int screenH);
}
