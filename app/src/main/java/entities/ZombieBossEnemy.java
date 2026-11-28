package entities;

import de.gurkenlabs.litiengine.Direction;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.AnimationInfo;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;
import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.graphics.animation.Animation;
import de.gurkenlabs.litiengine.graphics.animation.AnimationController;
import de.gurkenlabs.litiengine.input.Input;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;

/**
 * ZombieBossEnemy — Boss 2: Titan Xác Sống (Elemental Titan Zombie).
 * Đã thay thế toàn bộ chiêu cũ bằng 4 kỹ năng nguyên tố từ pack PVFX Foundry:
 * - Chiêu 1 (Chuột Trái / SPACE / J): Ném Đá Khổng Lồ (Boulder Toss) - Gây 70 DMG (Pha 2: 95 DMG) nổ AoE khi chạm đất.
 * - Chiêu 2 (Chuột Phải / Q / K): Hàng Gai Đất (Earth Spike Row) - Gai nhọn trồi lên càn quét gây 55 DMG (Pha 2: 75 DMG) + làm chậm.
 * - Chiêu 3 (C / L): Vòng Xoáy Nước (Whirlpool) - Tạo hố xoáy hút tất cả kẻ địch xung quanh vào tâm, gây 14 DMG mỗi 0.35s liên tục.
 * - Chiêu 4 (SHIFT / E / H): Cơn Lốc Xoáy (Tornado) - Thổi ra lốc xoáy càn quét (Pha 2: 3 lốc xoáy tỏa quạt), hất tung và gây 35 DMG liên tục.
 * - Chiêu 5 (F): Cuồng Nộ Pha 2 (Enrage) - Hồi đầy máu, cường hóa cả 4 kỹ năng và triệu hồi 2 đệ tử Zombie.
 */
@AnimationInfo(spritePrefix = {"big_zombie"})
public class ZombieBossEnemy extends Enemy implements IRenderable, PlayableBoss {

    public enum BossActionState {
        CHASE,
        CASTING,
        BONK_STUNNED,
        PHASE_TRANSITION
    }

    private BossActionState bossState = BossActionState.CHASE;
    private int currentPhase = 1;

    private boolean playerControlled = false;
    private boolean isPlayerMoving = false;
    private boolean isFacingLeft = false;

    private long stateStartTime = 0;
    private long phaseTransitionEndTime = 0;
    private long lastBossDustTime = 0;
    private boolean overlayRegistered = false;

    // Cooldowns của 4 kỹ năng
    private long lastBoulderTime = 0;
    private long lastEarthSpikeTime = 0;
    private long lastWhirlpoolTime = 0;
    private long lastRockfallTime = 0;

    public ZombieBossEnemy() {
        super(
            "big_zombie",
            550,    // maxHealth
            30,     // contact damage
            70f,    // AI speed
            320,    // attackRange
            650,    // detectionRange
            1100    // cooldownMs
        );
        this.setTeam(2);
        this.setName("");
        this.setSize(32, 36);
        this.setScaling(false);
        initAnimations();
    }

    @Override
    public void setPlayerControlled(boolean controlled) {
        this.playerControlled = controlled;
        if (controlled) {
            this.setTeam(1);
            this.setVelocity(140f);
            resetBossState();
        }
    }

    @Override
    public void resetBossState() {
        this.bossState = BossActionState.CHASE;
        this.stateStartTime = 0;
        this.isPlayerMoving = false;
        if (this.animations() != null) {
            this.animations().play(isLookLeft() ? "big_zombie-idle-left" : "big_zombie-idle-right");
        }
    }

    @Override
    public void resurrect() {
        super.resurrect();
        resetBossState();
    }

    public void setFacingLeft(boolean left) {
        this.isFacingLeft = left;
        this.setFacingDirection(left ? Direction.LEFT : Direction.RIGHT);
        this.faceHorizontal(left);
    }

    @Override
    public void loaded(de.gurkenlabs.litiengine.environment.Environment env) {
        super.loaded(env);
        if (env != null && !overlayRegistered) {
            env.add(this, RenderType.OVERLAY);
            overlayRegistered = true;
        }
        if (this.animations() != null && this.animations().getCurrent() == null) {
            this.animations().play("big_zombie-idle-right");
        }
    }

    private void registerOrUpdateAnimation(String name, Spritesheet sheet, boolean loop, int frameDuration) {
        if (this.animations() == null || sheet == null) return;
        Animation existing = this.animations().get(name);
        if (existing != null) {
            this.animations().remove(existing);
        }
        Animation anim = new Animation(name, sheet, loop);
        anim.setDurationForAllKeyFrames(frameDuration);
        this.animations().add(anim);
    }

    private boolean isLookLeft() {
        return isFacingLeft || getFacingDirection() == Direction.LEFT;
    }

    private void emitBossDust(double dirX, double dirY, long now) {
        if (now - lastBossDustTime < 130) return;
        lastBossDustTime = now;
        EnemyFx.dust(getCenter().getX(), getY() + getHeight() - 2, dirX, dirY);
    }

    private void initAnimations() {
        if (this.animations() == null) return;
        screens.GamePlayScreen.loadAllMonsterSprites();

        Spritesheet idleSheet = Resources.spritesheets().get("big_zombie-idle");
        Spritesheet idleLeftSheet = Resources.spritesheets().get("big_zombie-idle-left");
        Spritesheet walkSheet = Resources.spritesheets().get("big_zombie-walk");
        Spritesheet walkLeftSheet = Resources.spritesheets().get("big_zombie-walk-left");

        if (idleLeftSheet == null && idleSheet != null) {
            Animation temp = new Animation("temp-z-idle", idleSheet, true);
            Animation flipped = AnimationController.flippedAnimation(temp, "big_zombie-idle-left", false);
            idleLeftSheet = flipped.getSpritesheet();
        }
        if (walkLeftSheet == null && walkSheet != null) {
            Animation temp = new Animation("temp-z-walk", walkSheet, true);
            Animation flipped = AnimationController.flippedAnimation(temp, "big_zombie-walk-left", false);
            walkLeftSheet = flipped.getSpritesheet();
        }

        if (idleSheet != null) {
            registerOrUpdateAnimation("big_zombie-idle-right", idleSheet, true, 140);
            registerOrUpdateAnimation("big_zombie-idle", idleSheet, true, 140);
        }
        if (idleLeftSheet != null) {
            registerOrUpdateAnimation("big_zombie-idle-left", idleLeftSheet, true, 140);
        }

        if (walkSheet != null) {
            registerOrUpdateAnimation("big_zombie-walk-right", walkSheet, true, 115);
            registerOrUpdateAnimation("big_zombie-walk", walkSheet, true, 115);
            registerOrUpdateAnimation("big_zombie-move-right", walkSheet, true, 115);
            registerOrUpdateAnimation("big_zombie-move", walkSheet, true, 115);
        }
        if (walkLeftSheet != null) {
            registerOrUpdateAnimation("big_zombie-walk-left", walkLeftSheet, true, 115);
            registerOrUpdateAnimation("big_zombie-move-left", walkLeftSheet, true, 115);
        }

        Animation def = this.animations().get("big_zombie-idle-right");
        if (def != null) {
            this.animations().setDefault(def);
        }

        this.animations().addRule(
            c -> isMoving(),
            c -> isLookLeft() ? "big_zombie-move-left" : "big_zombie-move-right",
            0
        );
        this.animations().addRule(
            c -> !isMoving(),
            c -> isLookLeft() ? "big_zombie-idle-left" : "big_zombie-idle-right",
            1
        );

        this.animations().play("big_zombie-idle-right");
    }

    public boolean isMoving() {
        if (playerControlled) {
            return isPlayerMoving;
        }
        if (bossState == BossActionState.CHASE) {
            return target != null && getCenter().distance(target.getCenter()) > 60;
        }
        return false;
    }

    @Override
    protected void moveBy(double dx, double dy) {
        if (playerControlled) {
            double nx = Math.max(20, Math.min(1004 - getWidth(), getX() + dx));
            double ny = Math.max(20, Math.min(556 - getHeight(), getY() + dy));
            setLocation(nx, ny);
            return;
        }
        super.moveBy(dx, dy);
    }

    @Override
    public boolean isIdle() {
        return !isMoving();
    }

    @Override
    protected boolean canBeSeparated() {
        return false;
    }

    @Override
    protected void updateAI() {
        long now = Game.time().now();
        if (playerControlled) {
            updatePlayerControl(now);
            return;
        }

        double hpRatio = (double) getHitPoints().getValue() / getHitPoints().getMax();
        if (currentPhase == 1 && hpRatio <= 0.50 && !isDead()) {
            triggerPhase2(now);
            return;
        }

        if (bossState == BossActionState.PHASE_TRANSITION) {
            if (now >= phaseTransitionEndTime) {
                this.bossState = BossActionState.CHASE;
            }
            return;
        }

        if (bossState == BossActionState.CASTING) {
            if (now - stateStartTime >= 300) {
                this.bossState = BossActionState.CHASE;
            }
            return;
        }

        if (target == null || target.isDead()) {
            this.target = findNearest(detectionRange, false);
        }
        if (target == null) return;

        double dist = getCenter().distance(target.getCenter());
        Point2D targetPos = target.getCenter();
        Point2D myCenter = getCenter();
        double dx = targetPos.getX() - myCenter.getX();
        double dy = targetPos.getY() - myCenter.getY();
        double len = Math.hypot(dx, dy);
        double dirX = len > 0.001 ? dx / len : 1.0;
        double dirY = len > 0.001 ? dy / len : 0.0;

        // AI dùng 4 kỹ năng dựa trên khoảng cách
        if (canAttack()) {
            // 1. Khoảng cách xa (> 160px): Ném đá (Boulder Toss)
            if (dist > 160 && now - lastBoulderTime >= (currentPhase == 1 ? 2400 : 1600)) {
                castBoulderToss(dirX, dirY, now);
                return;
            }

            // 2. Khoảng cách gần/trung (<= 110px): Mưa Đá (Rockfall) quanh Boss hoặc Vòng Xoáy Nước (Whirlpool)
            if (dist <= 110 && now - lastRockfallTime >= (currentPhase == 1 ? 4200 : 2800)) {
                castRockfall(now);
                return;
            }
            if (dist >= 80 && dist <= 220 && now - lastWhirlpoolTime >= (currentPhase == 1 ? 4500 : 3200)) {
                castWhirlpool(targetPos, now);
                return;
            }

            // 3. Khoảng cách gần (< 170px): Đập Hàng Gai Đất (Earth Spike Row)
            if (dist <= 170 && now - lastEarthSpikeTime >= (currentPhase == 1 ? 2200 : 1400)) {
                castEarthSpike(dirX, dirY, now);
                return;
            }
        }

        if (dist > 65) {
            faceMovement(dx);
            moveTowards(targetPos);
        } else {
            faceTarget(target);
        }
    }

    private void updatePlayerControl(long now) {
        if (bossState == BossActionState.PHASE_TRANSITION) {
            if (now >= phaseTransitionEndTime) {
                this.bossState = BossActionState.CHASE;
            }
            isPlayerMoving = false;
            return;
        }

        if (bossState == BossActionState.CASTING) {
            if (now - stateStartTime >= 180) {
                this.bossState = BossActionState.CHASE;
            }
        }

        double moveX = 0;
        double moveY = 0;
        if (Input.keyboard().isPressed(KeyEvent.VK_W) || Input.keyboard().isPressed(KeyEvent.VK_UP)) moveY -= 1;
        if (Input.keyboard().isPressed(KeyEvent.VK_S) || Input.keyboard().isPressed(KeyEvent.VK_DOWN)) moveY += 1;
        if (Input.keyboard().isPressed(KeyEvent.VK_A) || Input.keyboard().isPressed(KeyEvent.VK_LEFT)) moveX -= 1;
        if (Input.keyboard().isPressed(KeyEvent.VK_D) || Input.keyboard().isPressed(KeyEvent.VK_RIGHT)) moveX += 1;

        double len = Math.hypot(moveX, moveY);
        if (len > 0.001) {
            isPlayerMoving = true;
            double speed = (currentPhase == 1) ? 145.0 : 190.0;
            double dt = Math.min(0.05, Math.max(0.001, Game.loop().getDeltaTime() / 1000.0));
            moveBy((moveX / len) * speed * dt, (moveY / len) * speed * dt);
            emitBossDust(moveX / len, moveY / len, now);
        } else {
            isPlayerMoving = false;
        }

        Point2D mousePos = Input.mouse().getMapLocation();
        if (mousePos != null && Math.abs(mousePos.getX() - getCenter().getX()) > 8) {
            setFacingLeft(mousePos.getX() - getCenter().getX() < 0);
        } else if (Math.abs(moveX) > 0.1) {
            faceMovement(moveX);
        }
    }

    /**
     * Chiêu 1: Ném Tảng Đá Khổng Lồ (Boulder Toss) - Phím Chuột Trái / SPACE / J
     */
    @Override
    public void playerPrimaryAttack() {
        if (bossState == BossActionState.PHASE_TRANSITION) return;
        long now = Game.time().now();
        if (now - lastBoulderTime < 400) return;

        Point2D dir = getAimDirection();
        castBoulderToss(dir.getX(), dir.getY(), now);
    }

    private void castBoulderToss(double dirX, double dirY, long now) {
        this.bossState = BossActionState.CASTING;
        this.stateStartTime = now;
        this.lastBoulderTime = now;
        recordAttack();

        Point2D spawnPos = new Point2D.Double(
            getCenter().getX() + dirX * 48,
            getCenter().getY() + dirY * 48
        );

        new SkillEffectEntity(
            SkillEffectEntity.SkillType.BOULDER_TOSS,
            spawnPos,
            dirX,
            dirY,
            getTeam(),
            currentPhase == 2
        );

        // Ở Pha 2: Ném thêm một tảng đá phụ chệch góc nhẹ
        if (currentPhase == 2) {
            double angle = Math.atan2(dirY, dirX) + 0.28;
            new SkillEffectEntity(
                SkillEffectEntity.SkillType.BOULDER_TOSS,
                spawnPos,
                Math.cos(angle),
                Math.sin(angle),
                getTeam(),
                true
            );
        }
    }

    /**
     * Chiêu 2: Hàng Gai Đất Trồi Lên (Earth Spike Row) - Phím Chuột Phải / Q / K
     */
    @Override
    public void playerFireSpit() {
        if (bossState == BossActionState.PHASE_TRANSITION) return;
        long now = Game.time().now();
        if (now - lastEarthSpikeTime < 450) return;

        Point2D dir = getAimDirection();
        castEarthSpike(dir.getX(), dir.getY(), now);
    }

    private void castEarthSpike(double dirX, double dirY, long now) {
        this.bossState = BossActionState.CASTING;
        this.stateStartTime = now;
        this.lastEarthSpikeTime = now;
        recordAttack();

        Point2D center = getCenter();
        // Đặt tâm hàng gai cách boss ~105px để mép đầu hàng gai xuất phát ngay trước mặt boss và vươn về phía con trỏ
        Point2D spawnPos = new Point2D.Double(
            center.getX() + dirX * 105,
            center.getY() + dirY * 105
        );

        // Bắn đúng 1 hàng gai đất duy nhất theo hướng từ Boss đến con trỏ chuột
        new SkillEffectEntity(
            SkillEffectEntity.SkillType.EARTH_SPIKE,
            spawnPos,
            dirX,
            dirY,
            getTeam(),
            currentPhase == 2
        );
    }

    /**
     * Chiêu 3: Vòng Xoáy Nước Hút Mục Tiêu (Whirlpool) - Phím C / L
     */
    @Override
    public void playerFireRing() {
        if (bossState == BossActionState.PHASE_TRANSITION) return;
        long now = Game.time().now();
        if (now - lastWhirlpoolTime < 850) return;

        Point2D targetPos = Input.mouse().getMapLocation();
        if (targetPos == null) {
            Point2D dir = getAimDirection();
            targetPos = new Point2D.Double(getCenter().getX() + dir.getX() * 120, getCenter().getY() + dir.getY() * 120);
        }
        castWhirlpool(targetPos, now);
    }

    private void castWhirlpool(Point2D pos, long now) {
        this.bossState = BossActionState.CASTING;
        this.stateStartTime = now;
        this.lastWhirlpoolTime = now;
        recordAttack();

        new SkillEffectEntity(
            SkillEffectEntity.SkillType.WHIRLPOOL,
            pos,
            0,
            0,
            getTeam(),
            currentPhase == 2
        );
    }

    /**
     * Chiêu 4: Mưa Đá Rơi (Rockfall) - Tạo vùng tròn 6x6 lấy tâm là Boss, đá rơi ngẫu nhiên bên trong - Phím SHIFT / E / R / H
     */
    @Override
    public void playerCharge() {
        if (bossState == BossActionState.PHASE_TRANSITION) return;
        long now = Game.time().now();
        if (now - lastRockfallTime < 1200) return;

        castRockfall(now);
    }

    private void castRockfall(long now) {
        this.bossState = BossActionState.CASTING;
        this.stateStartTime = now;
        this.lastRockfallTime = now;
        recordAttack();

        new SkillEffectEntity(
            SkillEffectEntity.SkillType.ROCKFALL_ZONE,
            getCenter(),
            0,
            0,
            getTeam(),
            currentPhase == 2
        );
    }

    private Point2D getAimDirection() {
        Point2D mousePos = Input.mouse().getMapLocation();
        Point2D center = getCenter();
        if (mousePos != null) {
            double dx = mousePos.getX() - center.getX();
            double dy = mousePos.getY() - center.getY();
            double len = Math.hypot(dx, dy);
            if (len > 0.001) {
                return new Point2D.Double(dx / len, dy / len);
            }
        }
        return new Point2D.Double(isFacingLeft ? -1.0 : 1.0, 0.0);
    }

    private void triggerPhase2(long now) {
        this.currentPhase = 2;
        this.bossState = BossActionState.PHASE_TRANSITION;
        this.phaseTransitionEndTime = now + 1500;
        this.moveSpeed = 95f;

        if (Game.world().environment() != null) {
            int allyTeam = getTeam();
            IceZombieEnemy minion1 = new IceZombieEnemy();
            minion1.setLocation(getX() - 48, getY());
            minion1.setTeam(allyTeam);
            Game.world().environment().add(minion1);

            IceZombieEnemy minion2 = new IceZombieEnemy();
            minion2.setLocation(getX() + 64, getY());
            minion2.setTeam(allyTeam);
            Game.world().environment().add(minion2);
        }
    }

    @Override
    public void toggleEnrage() {
        long now = Game.time().now();
        if (currentPhase == 1) {
            triggerPhase2(now);
            getHitPoints().setValue(getHitPoints().getMax());
        } else {
            getHitPoints().setValue(getHitPoints().getMax());
            this.bossState = BossActionState.PHASE_TRANSITION;
            this.phaseTransitionEndTime = now + 800;
        }
    }

    @Override
    public void attack(ICombatEntity target) {
        // Điều khiển qua updateAI
    }

    @Override
    protected void onDamaged(int amount, long now) {
        if (bossState == BossActionState.PHASE_TRANSITION) {
            getHitPoints().setValue(getHitPoints().getValue() + amount);
            return;
        }
        super.onDamaged(amount, now);
    }

    private void faceMovement(double dx) {
        if (dx < -0.1) {
            setFacingLeft(true);
        } else if (dx > 0.1) {
            setFacingLeft(false);
        }
    }

    private void faceTarget(ICombatEntity target) {
        if (target == null) return;
        faceMovement(target.getCenter().getX() - getCenter().getX());
    }

    @Override
    public void render(Graphics2D g) {
        if (isDead()) return;

        Point2D viewCenter = (Game.world().camera() != null)
            ? Game.world().camera().getViewportLocation(getCenter())
            : getCenter();
        int cx = (int) Math.round(viewCenter.getX());
        int cy = (int) Math.round(viewCenter.getY());

        if (bossState == BossActionState.PHASE_TRANSITION) {
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            g.setColor(new Color(80, 240, 180));
            g.drawString("🌪 ENRAGED TITAN! PHASE 2 🌪", cx - 95, cy - 42);
        }
    }

    @Override
    public void renderHealthBar(Graphics2D g, int screenW, int screenH) {
        if (isDead()) return;

        int barW = 500;
        int barH = 18;
        int barX = (screenW - barW) / 2;
        int barY = screenH - 45;

        g.setColor(new Color(15, 20, 25, 235));
        g.fillRect(barX - 10, barY - 22, barW + 20, barH + 30);

        g.setColor(new Color(80, 220, 180));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(barX, barY, barW, barH);

        double hpRatio = Math.max(0, (double) getHitPoints().getValue() / getHitPoints().getMax());
        int fillW = (int) (barW * hpRatio);

        Color hpColor = (currentPhase == 1) ? new Color(40, 190, 140) : new Color(30, 210, 240);
        g.setColor(hpColor);
        g.fillRect(barX + 2, barY + 2, Math.max(0, fillW - 4), barH - 4);

        g.setColor(new Color(255, 255, 255, 180));
        g.drawLine(barX + barW / 2, barY, barX + barW / 2, barY + barH);

        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        String title = (playerControlled ? "👑 BẠN: " : "") + "ELEMENTAL TITAN ZOMBIE "
                + (currentPhase == 1 ? "[PHASE 1]" : "[ENRAGED PHASE 2]");
        g.drawString(title, barX + 5, barY - 6);

        String hpText = getHitPoints().getValue() + " / " + getHitPoints().getMax() + " HP";
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.drawString(hpText, barX + barW - 105, barY - 6);
    }
}
