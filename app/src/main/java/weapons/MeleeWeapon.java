package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.environment.Environment;
import player.Player;

public abstract class MeleeWeapon extends Weapon {

    protected String slashSpritePath;
    protected double slashOffsetDist;
    protected double slashDrawW;
    protected double slashDrawH;
    protected double slashHitW;
    protected double slashHitH;
    protected int slashFrameMs = 45;

    protected String skillSpritePath;
    protected double skillOffsetDist;
    protected double skillForwardSpeed;
    protected double skillDrawW;
    protected double skillDrawH;
    protected double skillHitW;
    protected double skillHitH;
    protected int skillFrameMs = 80;

    public MeleeWeapon(String name, int level, int damage, int skillDamage,
                       long attackCooldownMs, long skillCooldownMs, int skillManaCost,
                       String spritePath) {
        super(name, level, damage, skillDamage, attackCooldownMs, skillCooldownMs, skillManaCost, spritePath);
    }

    @Override
    protected void performAttack(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        double angle = Math.atan2(owner.getAimDirY(), owner.getAimDirX());
        MeleeEffectEntity slash = new MeleeEffectEntity(
                owner,
                slashSpritePath,
                4,
                damage,
                angle,
                slashOffsetDist,
                0.0,
                true, // Nhát chém thường bám theo nhân vật
                slashDrawW,
                slashDrawH,
                slashHitW,
                slashHitH,
                slashFrameMs
        );
        env.add(slash);
    }

    @Override
    protected void performSkill(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        double angle = Math.atan2(owner.getAimDirY(), owner.getAimDirX());
        MeleeEffectEntity skillFx = new MeleeEffectEntity(
                owner,
                skillSpritePath,
                4,
                skillDamage,
                angle,
                skillOffsetDist,
                skillForwardSpeed,
                false, // Kỹ năng bung sóng năng lượng về phía trước
                skillDrawW,
                skillDrawH,
                skillHitW,
                skillHitH,
                skillFrameMs
        );
        env.add(skillFx);
    }
}