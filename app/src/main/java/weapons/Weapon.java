package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.resources.Resources;
import player.Player;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

public abstract class Weapon {
    protected String name;
    protected int level;
    protected int damage;
    protected int skillDamage;
    protected long attackCooldownMs;
    protected long skillCooldownMs;
    protected int skillManaCost;

    protected double handDrawHeight = 15.0;
    protected BufferedImage sprite;

    private long lastAttackTimeMs = 0;
    private long lastSkillTimeMs = 0;

    private long lastSwingTimeMs = 0;
    private long swingDurationMs = 150;
    private boolean swingUp = true;

    public Weapon(String name, int level, int damage, int skillDamage,
                  long attackCooldownMs, long skillCooldownMs, int skillManaCost,
                  String spritePath) {
        this.name = name;
        this.level = level;
        this.damage = damage;
        this.skillDamage = skillDamage;
        this.attackCooldownMs = attackCooldownMs;
        this.skillCooldownMs = skillCooldownMs;
        this.skillManaCost = skillManaCost;
        if (spritePath != null && !spritePath.isEmpty()) {
            loadSprite(spritePath);
        }
    }

    protected void loadSprite(String path) {
        this.sprite = Resources.images().get(path);
        if (this.sprite == null) {
            System.err.println("[Weapon] Khong tim thay anh vu khi: " + path);
        }
    }

    /** Cập nhật hoạt ảnh và hitbox của vũ khí mỗi frame (gọi từ Player.update) */
    public void update(Player owner) {
    }

    /** Hủy hoạt ảnh đang chạy dở khi đổi map hoặc nhân vật chết */
    public void resetState() {
    }

    /** Trả về true nếu vũ khí đang bận chạy hoạt ảnh ra chiêu */
    public boolean isBusy() {
        return false;
    }

    /** Đánh thường (phím J) */
    public boolean attack(Player owner) {
        if (isBusy()) return false;
        long now = System.currentTimeMillis();
        if (now - lastAttackTimeMs < attackCooldownMs) return false;

        lastAttackTimeMs = now;
        triggerSwing(140);
        performAttack(owner);
        return true;
    }

    /** Kỹ năng vũ khí (phím U) */
    public String useSkill(Player owner) {
        if (isBusy()) return "";
        long now = System.currentTimeMillis();
        if (now - lastSkillTimeMs < skillCooldownMs) {
            return "Skill dang hoi chieu!";
        }
        if (!owner.consumeMana(skillManaCost)) {
            return "Khong du Mana! (Can " + skillManaCost + ")";
        }

        lastSkillTimeMs = now;
        triggerSwing(210);
        performSkill(owner);
        return "";
    }

    protected void triggerSwing(long durationMs) {
        this.lastSwingTimeMs = System.currentTimeMillis();
        this.swingDurationMs = durationMs;
        this.swingUp = !this.swingUp;
    }

    protected abstract void performAttack(Player owner);

    protected abstract void performSkill(Player owner);

    public void cycleLevel() {
    }

    /** Vẽ mặc định trên tay cho các vũ khí dùng ảnh đơn */
    public void renderInHand(Graphics2D g, Player owner) {
        if (sprite == null || Game.world().camera() == null) return;

        double aimX = owner.getAimDirX();
        double aimY = owner.getAimDirY();
        double aimAngle = Math.atan2(aimY, aimX);
        boolean facingRight = owner.isFacingRight();

        double handX = owner.getX() + owner.getWidth() / 2.0 + (facingRight ? 4.5 : -4.5);
        double handY = owner.getY() + owner.getHeight() * 0.65;
        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(handX, handY));

        double ratio = (double) sprite.getWidth() / Math.max(1, sprite.getHeight());
        double drawH = handDrawHeight;
        double drawW = drawH * ratio;

        double drawAngle;
        long swingElapsed = System.currentTimeMillis() - lastSwingTimeMs;
        if (swingElapsed < swingDurationMs) {
            double t = (double) swingElapsed / swingDurationMs;
            double startDeg = swingUp ? -75.0 : 75.0;
            double endDeg = swingUp ? 75.0 : -75.0;
            double swingOffset = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            if (!facingRight) swingOffset = -swingOffset;
            drawAngle = aimAngle + Math.PI / 2.0 + swingOffset;
        } else {
            drawAngle = facingRight ? Math.toRadians(22) : Math.toRadians(-22);
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());
        g2.rotate(drawAngle);
        if (!facingRight) {
            g2.scale(-1, 1);
        }

        int px = (int) Math.round(-drawW / 2.0);
        int py = (int) Math.round(-drawH * 0.82);
        g2.drawImage(sprite, px, py, (int) Math.round(drawW), (int) Math.round(drawH), null);
        g2.dispose();
    }

    public String getName() { return name; }
    public int getLevel() { return level; }
    public int getDamage() { return damage; }
    public int getSkillDamage() { return skillDamage; }
    public int getSkillManaCost() { return skillManaCost; }

    public long getSkillCooldownRemainingMs() {
        return Math.max(0, skillCooldownMs - (System.currentTimeMillis() - lastSkillTimeMs));
    }
}