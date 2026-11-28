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
import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * DemonBossEnemy — Boss Ma Vương Khổng Lồ (Demon Lord Gerald)
 * Lấy cảm hứng trực tiếp từ kiến trúc Boss của game "Deep and Deeper":
 * 
 * 1. HỆ THỐNG ĐA PHA (Multi-Phase System):
 *    - Pha 1 (100% -> 50% HP): Di chuyển tiếp cận, dùng đòn Vuốt Cào (Swipe) và Phun Lửa Chùm (Fire Spit).
 *    - Chuyển Pha (tại 50% HP): Bất tử 1.5s gầm thét, nháy đỏ cuồng nộ, triệu hồi 2 đệ tử Imp quỷ lửa hỗ trợ.
 *    - Pha 2 (<= 50% HP): Tốc độ tăng 45%, mở khóa kỹ năng tối thượng Húc Sấm Sét (Charge / Lunge).
 * 
 * 2. CƠ CHẾ BÁO TRƯỚC ĐÒN ĐÁNH (Telegraphing):
 *    - Trước khi tung chiêu nguy hiểm, Boss luôn khựng lại lấy đà và nhấp nháy đỏ dưới chân,
 *      giúp người chơi có thời gian phản xạ lộn/né đòn (Dodge).
 * 
 * 3. CƠ CHẾ ĐẶC TRƯNG "BONK MECHANIC" (Tự đâm tường bị choáng):
 *    - Khi Boss dùng đòn Húc (Charge) lao vụt tới cực nhanh, nếu người chơi khéo léo né tránh
 *      khiến Boss đâm sầm vào tường, Boss sẽ bị DỘI NGƯỢC LẠI và BỊ CHOÁNG (Stun) đơ người trong 2.2 giây!
 *      Đây là cơ hội vàng để người chơi phản công dồn sát thương.
 */
@AnimationInfo(spritePrefix = {"big_demon"})
public class DemonBossEnemy extends Enemy implements IRenderable, PlayableBoss {

    public enum BossActionState {
        CHASE,              // Đang di chuyển tìm góc tiếp cận mục tiêu
        TELEGRAPH,          // Khựng lại báo hiệu trước khi ra chiêu (Telegraph)
        BITE_WINDUP,        // Hiện tam giác cảnh báo trước khi cắn (Telegraph / Wind-up)
        BITE,               // Đòn Cắn Xé Tàn Bạo (Demonic Bite / Chomp)
        SWIPE,              // Đòn vuốt cào (giữ alias tương thích)
        FIRE_SPIT,          // Phun mưa cầu lửa tỏa hình nón
        CHARGE_WINDUP,      // Hiện hitbox hình chữ nhật cảnh báo trước khi Húc (2 ô rộng x 5 ô dài)
        CHARGE,             // Lao phi thân sấm sét (Lunge)
        BONK_STUNNED,       // Bị choáng do đâm đầu vào tường (Bonk)
        PHASE_TRANSITION    // Gầm thét chuyển pha, triệu hồi đệ tử
    }

    private BossActionState bossState = BossActionState.CHASE;
    private int currentPhase = 1;
    private final Random random = new Random();

    // === Player Control Mode ===
    private boolean playerControlled = false;
    private boolean isPlayerMoving = false;
    private boolean isFacingLeft = false;

    public void setPlayerControlled(boolean controlled) {
        this.playerControlled = controlled;
        if (controlled) {
            this.setTeam(1); // Cùng phe với người chơi
            this.setVelocity(140f);
            resetBossState();
        }
    }

    public void resetBossState() {
        this.bossState = BossActionState.CHASE;
        this.nextAttackAfterTelegraph = null;
        this.stateStartTime = 0;
        this.telegraphDuration = 0;
        this.isPlayerMoving = false;
        this.alreadyHitInCharge.clear();
    }

    @Override
    public void resurrect() {
        super.resurrect();
        resetBossState();
    }

    public boolean isPlayerControlled() {
        return playerControlled;
    }

    public void setFacingLeft(boolean left) {
        this.isFacingLeft = left;
        this.setFacingDirection(left ? Direction.LEFT : Direction.RIGHT);
        this.faceHorizontal(left);
    }

    public boolean isFacingLeft() {
        return isFacingLeft;
    }

    // === Timers & State Durations ===
    private long stateStartTime = 0;
    private long telegraphDuration = 0;
    private long stunEndTime = 0;
    private long phaseTransitionEndTime = 0;
    private BossActionState nextAttackAfterTelegraph = BossActionState.SWIPE;

    // === Charge (Lunge) parameters ===
    private double chargeDirX = 0;
    private double chargeDirY = 0;
    private double chargeSpeed = 460.0; // Tốc độ lao cực nhanh
    private long chargeStartTime = 0;
    private final long maxChargeDuration = 750; // Lao tối đa 750ms
    private double chargeStartX = 0;
    private double chargeStartY = 0;
    private final double chargeMaxDistance = 160.0; // Dài đúng 5 ô (5 * 32px = 160px)
    private final Set<ICombatEntity> alreadyHitInCharge = new HashSet<>();
    private final double chargeHitboxWidth = 64.0;  // Rộng đúng 2 ô (2 * 32px = 64px)

    // === Cooldowns ===
    private long lastSwipeTime = 0;
    private long lastSpitTime = 0;
    private long lastChargeTime = 0;

    // === Swipe Visual Arc ===
    private double swipeAngleDeg = 0;

    public DemonBossEnemy() {
        super(
            "big_demon",    // spritePrefix (big_demon-idle, big_demon-walk)
            400,            // maxHealth (máu trâu chuẩn Boss)
            25,             // damage va chạm cơ bản
            70f,            // speed cơ bản (Pha 1: 70 px/s)
            260,            // attackRange
            600,            // detectionRange
            1000            // attackCooldownMs
        );
        this.setTeam(2);    // Team 2 (phe quái)
        this.setName("");   // Ẩn tên khỏi màn hình debug
        this.setSize(32, 36);
        this.setScaling(false);
        initAnimations();
    }

    private boolean overlayRegistered = false;

    @Override
    public void loaded(de.gurkenlabs.litiengine.environment.Environment env) {
        super.loaded(env);
        if (env != null && !overlayRegistered) {
            env.add(this, RenderType.OVERLAY);
            overlayRegistered = true;
        }
    }

    private void initAnimations() {
        if (this.animations() == null) return;
        screens.GamePlayScreen.loadAllMonsterSprites();

        Spritesheet idleSheet = Resources.spritesheets().get("big_demon-idle");
        Spritesheet walkSheet = Resources.spritesheets().get("big_demon-walk");
        Spritesheet biteSheet = Resources.spritesheets().get("big_demon-bite");
        Spritesheet biteLeftSheet = Resources.spritesheets().get("big_demon-bite-left");

        if (idleSheet != null) {
            Animation idleRight = new Animation("big_demon-idle-right", idleSheet, true);
            idleRight.setDurationForAllKeyFrames(130);

            Animation idleLeft = AnimationController.flippedAnimation(idleRight, "big_demon-idle-left", false);
            idleLeft.setDurationForAllKeyFrames(130);

            Animation idleBase = new Animation("big_demon-idle", idleSheet, true);
            idleBase.setDurationForAllKeyFrames(130);

            this.animations().add(idleRight);
            this.animations().add(idleLeft);
            this.animations().add(idleBase);
            this.animations().setDefault(idleRight);
        }

        if (walkSheet != null) {
            Animation walkRight = new Animation("big_demon-walk-right", walkSheet, true);
            walkRight.setDurationForAllKeyFrames(110);

            Animation walkLeft = AnimationController.flippedAnimation(walkRight, "big_demon-walk-left", false);
            walkLeft.setDurationForAllKeyFrames(110);

            Animation walkBase = new Animation("big_demon-walk", walkSheet, true);
            walkBase.setDurationForAllKeyFrames(110);

            this.animations().add(walkRight);
            this.animations().add(walkLeft);
            this.animations().add(walkBase);
        }

        // Hoạt họa đòn cắn xé (Bite / Chomp 4 khung hình)
        if (biteSheet != null) {
            Animation biteRight = new Animation("big_demon-bite-right", biteSheet, false);
            Animation biteLeft = (biteLeftSheet != null)
                ? new Animation("big_demon-bite-left", biteLeftSheet, false)
                : AnimationController.flippedAnimation(biteRight, "big_demon-bite-left", false);
            biteRight.setDurationForAllKeyFrames(70);
            biteLeft.setDurationForAllKeyFrames(70);
            this.animations().add(biteRight);
            this.animations().add(biteLeft);
            this.animations().add(new Animation("big_demon-bite", biteSheet, false));
        }

        // Quy tắc chuyển hoạt họa:
        // Đòn cắn (Bite) ưu tiên hàng đầu khi đang cắn
        this.animations().addRule(
            c -> (bossState == BossActionState.BITE),
            c -> (isFacingLeft || getFacingDirection() == Direction.LEFT) ? "big_demon-bite-left" : "big_demon-bite-right"
        );
        this.animations().addRule(
            c -> isMoving(),
            c -> (isFacingLeft || getFacingDirection() == Direction.LEFT) ? "big_demon-walk-left" : "big_demon-walk-right"
        );
        this.animations().addRule(
            c -> !isMoving(),
            c -> (isFacingLeft || getFacingDirection() == Direction.LEFT) ? "big_demon-idle-left" : "big_demon-idle-right"
        );

        this.animations().play("big_demon-idle-right");
    }

    /**
     * Xác định trạng thái di chuyển chuẩn xác để chạy animation walk mượt mà:
     * - Khi đang HÚC (CHARGE): Luôn luôn chạy animation walk!
     * - Khi người chơi điều khiển: Dựa vào phím di chuyển isPlayerMoving!
     * - Khi AI đang đuổi theo mục tiêu (CHASE) và chưa áp sát (> 60px): Chạy animation walk!
     * - Các trường hợp còn lại: Chạy IDLE!
     */
    public boolean isMoving() {
        if (bossState == BossActionState.CHARGE) {
            return true;
        }
        if (bossState == BossActionState.BITE || bossState == BossActionState.BITE_WINDUP || bossState == BossActionState.CHARGE_WINDUP) {
            return false;
        }
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
            // Giới hạn an toàn trong đấu trường 1024x576, đảm bảo luôn di chuyển 100% mượt mà
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
        return false; // Boss Ma Vương uy nghi, không bị quái nhỏ xô đẩy gây rung giật
    }

    @Override
    public boolean canAttack() {
        if (bossState != BossActionState.CHASE) return false;
        return super.canAttack();
    }

    @Override
    protected void updateAI() {
        long now = Game.time().now();

        // Nếu người chơi đang trực tiếp điều khiển Boss:
        if (playerControlled) {
            updatePlayerControl(now);
            return;
        }

        // 1. Kiểm tra chuyển pha (Phase Transition) khi máu tụt xuống <= 50%
        double hpRatio = (double) getHitPoints().getValue() / getHitPoints().getMax();
        if (currentPhase == 1 && hpRatio <= 0.50 && !isDead()) {
            triggerPhase2(now);
            return;
        }

        // 2. Xử lý trạng thái chuyển pha (bất tử gầm rú)
        if (bossState == BossActionState.PHASE_TRANSITION) {
            if (now >= phaseTransitionEndTime) {
                this.bossState = BossActionState.CHASE;
            }
            return; // Đứng yên gầm thét
        }

        // 3. Xử lý trạng thái bị choáng do đâm đầu vào tường (BONK MECHANIC)
        if (bossState == BossActionState.BONK_STUNNED) {
            if (now >= stunEndTime) {
                this.bossState = BossActionState.CHASE;
            }
            return; // Đơ người hoàn toàn không cử động
        }

        // 4. Xử lý giai đoạn báo trước đòn đánh (TELEGRAPH)
        if (bossState == BossActionState.TELEGRAPH) {
            if (target != null && !target.isDead()) {
                faceTarget(target);
            }
            if (now - stateStartTime >= telegraphDuration) {
                // Hết thời gian lấy đà -> TUNG CHIÊU THẬT
                executeAttack(nextAttackAfterTelegraph);
            }
            return; // Dừng lại lấy đà, không di chuyển
        }

        // 5. Xử lý đòn lao phi thân (CHARGE / LUNGE)
        if (bossState == BossActionState.CHARGE) {
            updateCharge(now);
            return;
        }

        // 6. Xử lý hồi phục sau đòn cắn / cào / phun đạn
        if (bossState == BossActionState.BITE || bossState == BossActionState.SWIPE || bossState == BossActionState.FIRE_SPIT) {
            if (now - stateStartTime >= 280) {
                this.bossState = BossActionState.CHASE;
                recordAttack();
            }
            return;
        }

        // 7. Di chuyển bình thường và lựa chọn chiêu thức (CHASE)
        if (target == null || target.isDead()) {
            this.target = findNearest(detectionRange, false);
        }
        if (target == null) return;

        double dist = getCenter().distance(target.getCenter());

        // Lựa chọn đòn tấn công nếu đủ điều kiện
        if (canAttack()) {
            // Chiêu 1: HÚC (Charge / Lunge) - ƯU TIÊN HÀNG ĐẦU khi ở cự ly xa (>= 100px)
            // Pha 1: 3.2s một lần | Pha 2: 2.0s một lần (húc dồn dập)
            if (dist >= 100 && now - lastChargeTime >= (currentPhase == 1 ? 3200 : 2000)) {
                startTelegraph(BossActionState.CHARGE, 600); // Báo hiệu 600ms trước khi phóng
                return;
            }

            // Chiêu 2: Cắn xé tàn bạo (Bite / Chomp) khi áp sát (cự ly <= 95px)
            if (dist <= 95 && now - lastSwipeTime >= 1500) {
                startTelegraph(BossActionState.BITE, 1000); // Vẽ tam giác lấy đà 1.0s trước khi cắn
                return;
            }

            // Chiêu 3: Phun lửa chùm (Fire Spit) ở cự ly trung bình (cự ly <= 380px)
            if (dist <= 380 && now - lastSpitTime >= 2400) {
                startTelegraph(BossActionState.FIRE_SPIT, 400); // Lấy đà tụ lửa 400ms
                return;
            }
        }

        // Di chuyển tiếp cận mục tiêu: Chỉ tiến tới khi chưa áp sát (> 55px)
        // Khi đã áp sát cự ly cận chiến (<= 55px): Đứng yên tại chỗ và quay mặt về mục tiêu
        if (dist > 55) {
            Point2D targetPos = target.getCenter();
            double dx = targetPos.getX() - getCenter().getX();
            faceMovement(dx);
            moveTowards(targetPos);
        } else {
            faceTarget(target);
        }
    }

    /**
     * Kích hoạt chuyển pha 2 (Cuồng Nộ - Enrage).
     */
    private void triggerPhase2(long now) {
        this.currentPhase = 2;
        this.bossState = BossActionState.PHASE_TRANSITION;
        this.phaseTransitionEndTime = now + 1500; // Khựng 1.5s
        this.moveSpeed = 100f; // Tăng tốc 45% (từ 70 lên 100 px/s)

        // Triệu hồi 2 đệ tử quỷ nhỏ Imp ở 2 cánh
        if (Game.world().environment() != null) {
            ImpEnemy imp1 = new ImpEnemy();
            imp1.setLocation(getX() - 48, getY());
            imp1.setTeam(2);
            Game.world().environment().add(imp1);

            ImpEnemy imp2 = new ImpEnemy();
            imp2.setLocation(getX() + 64, getY());
            imp2.setTeam(2);
            Game.world().environment().add(imp2);
        }
    }

    /**
     * Bắt đầu giai đoạn báo trước đòn đánh (Telegraph).
     */
    private void startTelegraph(BossActionState attack, long durationMs) {
        this.bossState = BossActionState.TELEGRAPH;
        this.nextAttackAfterTelegraph = attack;
        this.stateStartTime = Game.time().now();
        this.telegraphDuration = durationMs;
        if (target != null) {
            faceTarget(target);
            if (attack == BossActionState.CHARGE) {
                double dx = target.getCenter().getX() - getCenter().getX();
                double dy = target.getCenter().getY() - getCenter().getY();
                double len = Math.hypot(dx, dy);
                if (len > 0.001) {
                    chargeDirX = dx / len;
                    chargeDirY = dy / len;
                    faceMovement(chargeDirX);
                }
            }
        }
    }

    /**
     * Thực thi đòn đánh sau khi lấy đà xong.
     */
    private void executeAttack(BossActionState attack) {
        this.bossState = attack;
        this.stateStartTime = Game.time().now();

        if (attack == BossActionState.BITE || attack == BossActionState.SWIPE) {
            lastSwipeTime = stateStartTime;
            if (target != null) {
                double dx = target.getCenter().getX() - getCenter().getX();
                double dy = target.getCenter().getY() - getCenter().getY();
                swipeAngleDeg = Math.toDegrees(Math.atan2(dy, dx));
                faceMovement(dx);
            }
            // Phát hoạt họa cắn ngay lập tức
            String animName = isFacingLeft ? "big_demon-bite-left" : "big_demon-bite-right";
            if (this.animations() != null && this.animations().hasAnimation(animName)) {
                this.animations().play(animName);
            }
            // Vùng tam giác cung tròn Hitbox hình quạt 80 độ, bán kính 105px vươn ra phía trước mặt
            double rad = Math.toRadians(swipeAngleDeg);
            double originX = getCenter().getX() + Math.cos(rad) * 16.0;
            double originY = getCenter().getY() + Math.sin(rad) * 16.0;
            double biteRadius = 105.0;
            double biteArcAngle = 80.0;
            double startAngle = -swipeAngleDeg - biteArcAngle / 2.0;
            Arc2D.Double biteSector = new Arc2D.Double(
                originX - biteRadius, originY - biteRadius,
                biteRadius * 2, biteRadius * 2,
                startAngle, biteArcAngle, Arc2D.PIE
            );
            if (target != null && !target.isDead()) {
                if (biteSector.intersects(target.getBoundingBox()) || biteSector.contains(target.getCenter())) {
                    target.hit(45);
                }
            }
        } else if (attack == BossActionState.FIRE_SPIT) {
            lastSpitTime = stateStartTime;
            shootFireSpread();
        } else if (attack == BossActionState.CHARGE) {
            lastChargeTime = stateStartTime;
            chargeStartTime = stateStartTime;
            chargeStartX = getX();
            chargeStartY = getY();
            alreadyHitInCharge.clear();
            // Khóa vector hướng lao thẳng về phía người chơi
            if (target != null) {
                double dx = target.getCenter().getX() - getCenter().getX();
                double dy = target.getCenter().getY() - getCenter().getY();
                double len = Math.hypot(dx, dy);
                if (len > 0.001) {
                    chargeDirX = dx / len;
                    chargeDirY = dy / len;
                    faceMovement(chargeDirX);
                }
            }
        }
    }

    /**
     * Đòn Phun Lửa Chùm: Bắn 3 quả cầu lửa tỏa hình nón về phía mục tiêu.
     */
    private void shootFireSpread() {
        if (target == null || target.isDead() || Game.world().environment() == null) return;

        Point2D start = getCenter();
        Point2D goal = target.getCenter();
        double baseAngle = Math.atan2(goal.getY() - start.getY(), goal.getX() - start.getX());

        // Bắn 3 tia: -18 độ, 0 độ, +18 độ
        double[] spreadAngles = {-0.31, 0.0, 0.31};
        for (double offset : spreadAngles) {
            double angle = baseAngle + offset;
            Point2D spreadTarget = new Point2D.Double(
                start.getX() + Math.cos(angle) * 300,
                start.getY() + Math.sin(angle) * 300
            );

            new Projectile(
                "fire-bullet",
                start,
                spreadTarget,
                160.0, // Tốc độ đạn lửa
                15,    // Sát thương 15
                1,     // Mục tiêu: Team 1 (Player)
                null
            );
        }
    }

    /**
     * Kích hoạt ép Boss tung đòn Húc (Charge) ngay lập tức (phục vụ test thử / combo).
     */
    public void forceCharge() {
        if (bossState != BossActionState.BONK_STUNNED && bossState != BossActionState.PHASE_TRANSITION) {
            startTelegraph(BossActionState.CHARGE, 500);
        }
    }

    /**
     * Xử lý di chuyển phi thân của kỹ năng Charge (Húc) và kiểm tra va chạm tường (Bonk Mechanic).
     */
    private void updateCharge(long now) {
        double dt = Game.loop().getDeltaTime() / 1000.0;
        double speed = (currentPhase == 1) ? 460.0 : 560.0; // Tốc độ lao nhanh và dứt khoát
        double step = speed * dt;

        double currentDist = Math.hypot(getX() - chargeStartX, getY() - chargeStartY);
        double remainingDist = chargeMaxDistance - currentDist;
        if (remainingDist <= 0) {
            this.bossState = BossActionState.CHASE;
            recordAttack();
            return;
        }
        if (step > remainingDist) {
            step = remainingDist;
        }

        double nextX = getX() + chargeDirX * step;
        double nextY = getY() + chargeDirY * step;

        // KIỂM TRA ĐÂM VÀO TƯỜNG HOẶC RÌA MAP (BONK MECHANIC)
        // Kích thước map 1024x576: giới hạn trong [32, 960] x [32, 520]
        boolean hitWall = !canOccupy(nextX, nextY) || nextX <= 32 || nextX >= 960 || nextY <= 32 || nextY >= 520;
        if (hitWall) {
            // ĐẬP ĐẦU VÀO TƯỜNG -> BỊ CHOÁNG (STUNNED) TRONG 2.5 GIÂY!
            this.bossState = BossActionState.BONK_STUNNED;
            this.stunEndTime = now + 2500; // Choáng 2.5s
            this.stateStartTime = now;
            recordAttack();
            return;
        }

        setLocation(nextX, nextY);

        // Kiểm tra húc trúng mục tiêu trong hành lang rộng 2 ô (64px = 2 * 32px)
        if (Game.world().environment() != null) {
            int enemyTeam = (getTeam() == 1) ? 2 : 1;
            for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
                if (e != this && !e.isDead() && (e.getTeam() == enemyTeam || e.getTeam() != getTeam())) {
                    if (!alreadyHitInCharge.contains(e)) {
                        double dx = e.getCenter().getX() - getCenter().getX();
                        double dy = e.getCenter().getY() - getCenter().getY();
                        if (Math.hypot(dx, dy) <= 48.0 || getBoundingBox().intersects(e.getBoundingBox())) {
                            e.hit(playerControlled ? 55 : 35); // Đòn húc cực mạnh 55 DMG
                            alreadyHitInCharge.add(e);
                        }
                    }
                }
            }
        }

        // Hết quãng đường húc 5 ô (160px) mà không va tường
        if (Math.hypot(getX() - chargeStartX, getY() - chargeStartY) >= chargeMaxDistance) {
            this.bossState = BossActionState.CHASE;
            recordAttack();
        }
    }

    /**
     * Vòng lặp điều khiển trực tiếp của người chơi khi chơi vai Boss Ma Vương.
     */
    private void updatePlayerControl(long now) {
        // 1. Kiểm tra chuyển pha
        if (bossState == BossActionState.PHASE_TRANSITION) {
            if (now >= phaseTransitionEndTime) {
                this.bossState = BossActionState.CHASE;
            }
            isPlayerMoving = false;
            return;
        }

        // 2. Xử lý trạng thái Choáng khi đâm đầu vào tường (BONK)
        if (bossState == BossActionState.BONK_STUNNED) {
            if (now >= stunEndTime) {
                this.bossState = BossActionState.CHASE;
            }
            isPlayerMoving = false;
            return;
        }

        // 3. Xử lý đòn lao phi thân (CHARGE_WINDUP & CHARGE): 200ms (0.20s) ngang đòn cắn rồi lao
        if (bossState == BossActionState.CHARGE_WINDUP) {
            if (now - stateStartTime >= 200) {
                executePlayerCharge(now);
            }
            isPlayerMoving = false;
            return;
        }
        if (bossState == BossActionState.CHARGE) {
            updateCharge(now);
            return;
        }

        // 4. Xử lý giai đoạn lấy đà đòn cắn (BITE_WINDUP): Khóa hướng cắn đã chọn, 200ms (0.20s) rồi cắn
        if (bossState == BossActionState.BITE_WINDUP) {
            if (now - stateStartTime >= 200) {
                executePlayerBite(now);
            }
        }

        // Đảm bảo không bao giờ bị kẹt ở TELEGRAPH của AI khi người chơi điều khiển
        if (bossState == BossActionState.TELEGRAPH) {
            this.bossState = BossActionState.CHASE;
        }

        // 5. Kết thúc vung đòn cắn / cào / phun lửa
        if (bossState == BossActionState.BITE || bossState == BossActionState.SWIPE || bossState == BossActionState.FIRE_SPIT) {
            if (now - stateStartTime >= 200) {
                this.bossState = BossActionState.CHASE;
            }
        }

        // 6. Di chuyển tự do bằng WASD / Mũi tên (luôn luôn điều khiển được)
        double moveX = 0;
        double moveY = 0;
        if (Input.keyboard().isPressed(KeyEvent.VK_W) || Input.keyboard().isPressed(KeyEvent.VK_UP)) {
            moveY -= 1;
        }
        if (Input.keyboard().isPressed(KeyEvent.VK_S) || Input.keyboard().isPressed(KeyEvent.VK_DOWN)) {
            moveY += 1;
        }
        if (Input.keyboard().isPressed(KeyEvent.VK_A) || Input.keyboard().isPressed(KeyEvent.VK_LEFT)) {
            moveX -= 1;
        }
        if (Input.keyboard().isPressed(KeyEvent.VK_D) || Input.keyboard().isPressed(KeyEvent.VK_RIGHT)) {
            moveX += 1;
        }

        double len = Math.hypot(moveX, moveY);
        if (len > 0.001) {
            isPlayerMoving = true;
            double speed = (currentPhase == 1) ? 150.0 : 200.0;
            double dt = Math.min(0.05, Math.max(0.001, Game.loop().getDeltaTime() / 1000.0));
            moveBy((moveX / len) * speed * dt, (moveY / len) * speed * dt);
        } else {
            isPlayerMoving = false;
        }

        // 7. Quay mặt theo vị trí con trỏ chuột (khi đang lấy đà cắn hoặc gồng húc thì giữ nguyên góc đã khóa)
        if (bossState != BossActionState.BITE_WINDUP && bossState != BossActionState.CHARGE_WINDUP && bossState != BossActionState.CHARGE) {
            Point2D mousePos = Input.mouse().getMapLocation();
            if (mousePos != null) {
                double dx = mousePos.getX() - getCenter().getX();
                if (dx < -6) {
                    setFacingLeft(true);
                } else if (dx > 6) {
                    setFacingLeft(false);
                }
            } else if (Math.abs(moveX) > 0.1) {
                faceMovement(moveX);
            }
        }
    }

    /**
     * Chiêu 1: Cắn Xé Tàn Bạo (Demonic Bite / Chomp) - Vẽ tam giác lấy đà 1.0 giây rồi táp cực mạnh, gây 50 DMG!
     */
    public void playerBite() {
        if (bossState == BossActionState.BONK_STUNNED || bossState == BossActionState.PHASE_TRANSITION 
                || bossState == BossActionState.CHARGE || bossState == BossActionState.BITE_WINDUP 
                || bossState == BossActionState.BITE) {
            return;
        }
        long now = Game.time().now();
        Point2D mousePos = Input.mouse().getMapLocation();
        Point2D center = getCenter();
        double angle;
        if (mousePos != null) {
            angle = Math.toDegrees(Math.atan2(mousePos.getY() - center.getY(), mousePos.getX() - center.getX()));
            faceMovement(mousePos.getX() - center.getX());
        } else {
            angle = isFacingLeft ? 180 : 0;
        }
        this.swipeAngleDeg = angle;
        // Bắt đầu vẽ tam giác cảnh báo trước (Wind-up 1.0 giây)
        this.bossState = BossActionState.BITE_WINDUP;
        this.stateStartTime = now;
        this.isPlayerMoving = false;
    }

    private void executePlayerBite(long now) {
        this.bossState = BossActionState.BITE;
        this.stateStartTime = now;
        this.lastSwipeTime = now;

        // Phát hoạt họa cắn (big_demon-bite-left / right) ngay lập tức từ frame đầu
        String animName = isFacingLeft ? "big_demon-bite-left" : "big_demon-bite-right";
        if (this.animations() != null && this.animations().hasAnimation(animName)) {
            this.animations().play(animName);
        }

        // Vùng tam giác cung tròn Hitbox hình quạt 80 độ, bán kính 105px vươn xa ra phía trước mặt
        double rad = Math.toRadians(swipeAngleDeg);
        double originX = getCenter().getX() + Math.cos(rad) * 16.0;
        double originY = getCenter().getY() + Math.sin(rad) * 16.0;
        double biteRadius = 105.0;
        double biteArcAngle = 80.0;
        double startAngle = -swipeAngleDeg - biteArcAngle / 2.0;
        Arc2D.Double biteSector = new Arc2D.Double(
            originX - biteRadius, originY - biteRadius,
            biteRadius * 2, biteRadius * 2,
            startAngle, biteArcAngle, Arc2D.PIE
        );

        // Táp trúng toàn bộ mục tiêu khác phe nằm trong vùng tam giác cung tròn
        int targetTeam = (getTeam() == 1) ? 2 : 1;
        if (Game.world().environment() != null) {
            for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
                if (e != this && !e.isDead() && (e.getTeam() == targetTeam || e.getTeam() != getTeam())) {
                    if (biteSector.intersects(e.getBoundingBox()) || biteSector.contains(e.getCenter())) {
                        e.hit(50); // 50 sát thương cắn xé cực đau
                    }
                }
            }
        }
    }

    /** Alias giữ tương thích */
    public void playerSwipe() {
        playerBite();
    }

    @Override
    public void playerPrimaryAttack() {
        playerBite();
    }

    /**
     * Chiêu 2: Phun Mưa Cầu Lửa (Hellfire Spit) - Bắn 5 quả cầu lửa tỏa hình quạt.
     */
    public void playerFireSpit() {
        if (bossState == BossActionState.BONK_STUNNED || bossState == BossActionState.PHASE_TRANSITION || bossState == BossActionState.CHARGE) {
            return;
        }
        long now = Game.time().now();
        Point2D mousePos = Input.mouse().getMapLocation();
        Point2D start = getCenter();
        double baseAngle;
        if (mousePos != null) {
            baseAngle = Math.atan2(mousePos.getY() - start.getY(), mousePos.getX() - start.getX());
            faceMovement(mousePos.getX() - start.getX());
        } else {
            baseAngle = isFacingLeft ? Math.PI : 0;
        }

        this.bossState = BossActionState.FIRE_SPIT;
        this.stateStartTime = now;
        this.lastSpitTime = now;

        double[] spreadAngles = {-0.34, -0.17, 0.0, 0.17, 0.34};
        double[] angles = new double[spreadAngles.length];
        for (int i = 0; i < spreadAngles.length; i++) {
            angles[i] = baseAngle + spreadAngles[i];
        }
        spawnFireProjectiles(start, angles, 220.0, 20);
    }

    /**
     * Chiêu 2b: Vòng Tròn Lửa Địa Ngục (Hellfire Ring) - Bắn 12 quả cầu lửa tỏa đều 360 độ thành hình vòng tròn.
     */
    public void playerFireRing() {
        if (bossState == BossActionState.BONK_STUNNED || bossState == BossActionState.PHASE_TRANSITION || bossState == BossActionState.CHARGE) {
            return;
        }
        long now = Game.time().now();
        Point2D start = getCenter();

        this.bossState = BossActionState.FIRE_SPIT;
        this.stateStartTime = now;
        this.lastSpitTime = now;

        int bulletCount = (currentPhase == 2) ? 16 : 12;
        double[] angles = new double[bulletCount];
        for (int i = 0; i < bulletCount; i++) {
            angles[i] = (2.0 * Math.PI * i) / bulletCount;
        }
        spawnFireProjectiles(start, angles, 200.0, 20);
    }

    private void spawnFireProjectiles(Point2D start, double[] angles, double speed, int damage) {
        int targetTeam = (getTeam() == 1) ? 2 : 1;
        for (double angle : angles) {
            Point2D spreadTarget = new Point2D.Double(
                start.getX() + Math.cos(angle) * 360,
                start.getY() + Math.sin(angle) * 360
            );

            new Projectile(
                "fire-bullet",
                start,
                spreadTarget,
                speed,
                damage,
                targetTeam,
                null
            );
        }
    }

    /**
     * Chiêu 3: Lao Húc Hủy Diệt (Charge / Bonk) - Báo trước hình chữ nhật rộng 2 ô, dài 5 ô rồi lao vụt tới!
     */
    public void playerCharge() {
        if (bossState == BossActionState.BONK_STUNNED || bossState == BossActionState.PHASE_TRANSITION 
                || bossState == BossActionState.CHARGE || bossState == BossActionState.CHARGE_WINDUP
                || bossState == BossActionState.BITE_WINDUP || bossState == BossActionState.BITE) {
            return;
        }
        long now = Game.time().now();
        Point2D mousePos = Input.mouse().getMapLocation();
        Point2D start = getCenter();
        if (mousePos != null) {
            double dx = mousePos.getX() - start.getX();
            double dy = mousePos.getY() - start.getY();
            double len = Math.hypot(dx, dy);
            if (len > 0.001) {
                chargeDirX = dx / len;
                chargeDirY = dy / len;
                faceMovement(chargeDirX);
            }
        } else {
            chargeDirX = isFacingLeft ? -1 : 1;
            chargeDirY = 0;
        }

        // Bắt đầu giai đoạn gồng báo trước hình chữ nhật 2x5 ô (200ms)
        this.bossState = BossActionState.CHARGE_WINDUP;
        this.stateStartTime = now;
        this.chargeStartTime = now;
        this.isPlayerMoving = false;
    }

    private void executePlayerCharge(long now) {
        this.bossState = BossActionState.CHARGE;
        this.chargeStartTime = now;
        this.stateStartTime = now;
        this.lastChargeTime = now;
        this.chargeStartX = getX();
        this.chargeStartY = getY();
        this.alreadyHitInCharge.clear();
    }

    /**
     * Chiêu 4: Bật Cuồng Nộ Pha 2 (Enrage) - Tăng tốc, hồi đầy máu, triệu hồi đệ tử!
     */
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
        // Ủy quyền điều khiển thông qua updateAI
    }

    @Override
    protected void onDamaged(int amount, long now) {
        // Trong lúc chuyển pha, Boss bất tử
        if (bossState == BossActionState.PHASE_TRANSITION) {
            getHitPoints().setValue(getHitPoints().getValue() + amount); // hoàn lại máu
            return;
        }
        super.onDamaged(amount, now);
    }

    private void faceMovement(double dx) {
        if (dx < -1.5) {
            setFacingLeft(true);
        } else if (dx > 1.5) {
            setFacingLeft(false);
        }
    }

    private void faceTarget(ICombatEntity target) {
        if (target == null) return;
        double dx = target.getCenter().getX() - getCenter().getX();
        faceMovement(dx);
    }

    // =========================================================================
    //  RENDER VÀ HIỆU ỨNG HÌNH ẢNH (TELEGRAPH, BONK, HEALTH BAR)
    // =========================================================================

    @Override
    public void render(Graphics2D g) {
        if (isDead()) return;

        // Đổi tọa độ thế giới sang tọa độ viewport/màn hình để khớp 100% với sprite của Boss
        Point2D viewCenter = (Game.world().camera() != null)
            ? Game.world().camera().getViewportLocation(getCenter())
            : getCenter();
        int cx = (int) Math.round(viewCenter.getX());
        int cy = (int) Math.round(viewCenter.getY());

        // 1. Vẽ vùng hitbox hình chữ nhật cảnh báo trước khi Húc (Rộng 2 ô = 64px, Dài 5 ô = 160px)
        boolean isChargeTelegraph = (bossState == BossActionState.CHARGE_WINDUP)
            || (bossState == BossActionState.TELEGRAPH && nextAttackAfterTelegraph == BossActionState.CHARGE);

        if (isChargeTelegraph) {
            long elapsed = Game.time().now() - stateStartTime;
            long totalDuration = (bossState == BossActionState.CHARGE_WINDUP) ? 200 : telegraphDuration;

            if (elapsed >= 0 && elapsed < totalDuration && totalDuration > 0) {
                float progress = Math.min(1.0f, Math.max(0.0f, (float) elapsed / totalDuration));

                Graphics2D g2 = (Graphics2D) g.create();
                double angleRad = Math.atan2(chargeDirY, chargeDirX);
                double startDist = 16.0; // Bắt đầu từ mép trước của Boss
                double originX = cx + chargeDirX * startDist;
                double originY = cy + chargeDirY * startDist;
                double rectLength = 160.0; // Dài 5 ô (5 * 32px)
                double rectWidth = 64.0;   // Rộng 2 ô (2 * 32px)

                g2.translate(originX, originY);
                g2.rotate(angleRad);

                RoundRectangle2D.Double hitRect = new RoundRectangle2D.Double(0, -rectWidth / 2.0, rectLength, rectWidth, 6, 6);

                // Nền đỏ cam trong suốt, sáng dần
                int fillAlpha = (int) (45 + 75 * progress);
                g2.setColor(new Color(255, 30, 20, fillAlpha));
                g2.fill(hitRect);

                // Viền vàng sắc nét
                int strokeAlpha = (int) (180 + 75 * progress);
                g2.setColor(new Color(255, 215, 0, strokeAlpha));
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(hitRect);

                g2.dispose();
            }
        } else if (bossState == BossActionState.TELEGRAPH && nextAttackAfterTelegraph != BossActionState.BITE) {
            // Vòng cảnh báo cho các chiêu khác của AI (như Phun Lửa)
            long pulse = (Game.time().now() / 80) % 2;
            g.setColor(pulse == 0 ? new Color(255, 30, 30, 210) : new Color(255, 140, 0, 160));
            g.setStroke(new BasicStroke(3f));

            Point2D warnPos = (target == null)
                ? viewCenter
                : ((Game.world().camera() != null) ? Game.world().camera().getViewportLocation(target.getCenter()) : target.getCenter());
            int wx = (int) Math.round(warnPos.getX());
            int wy = (int) Math.round(warnPos.getY());
            int r = 30;

            g.drawOval(wx - r, wy - r / 2, r * 2, r);
            g.setFont(new Font("Monospaced", Font.BOLD, 13));
            g.drawString("⚠ DANGER!", wx - 32, wy - r / 2 - 8);
        }

        // 2. Vẽ vùng tam giác cung tròn cảnh báo trước khi cắn (chỉ hiện trong lúc lấy đà BITE_WINDUP)
        boolean isBiteTelegraph = (bossState == BossActionState.BITE_WINDUP)
            || (bossState == BossActionState.TELEGRAPH && nextAttackAfterTelegraph == BossActionState.BITE);

        if (isBiteTelegraph) {
            long elapsed = Game.time().now() - stateStartTime;
            long totalDuration = (bossState == BossActionState.BITE_WINDUP) ? 200 : telegraphDuration;

            // BẮT BUỘC: Chỉ vẽ khi còn trong thời gian lấy đà (elapsed < totalDuration).
            // Ngay khi cắn hoặc khi không tung chiêu thì biến mất hoàn toàn, không bao giờ bị dính trên màn hình!
            if (elapsed >= 0 && elapsed < totalDuration && totalDuration > 0) {
                float progress = Math.min(1.0f, Math.max(0.0f, (float) elapsed / totalDuration));

                Graphics2D g2 = (Graphics2D) g.create();
                double rad = Math.toRadians(swipeAngleDeg);
                double originX = cx + Math.cos(rad) * 16.0;
                double originY = cy + Math.sin(rad) * 16.0;
                double biteRadius = 105.0;
                double biteArcAngle = 80.0;
                double startAngle = -swipeAngleDeg - biteArcAngle / 2.0;
                Arc2D.Double biteSector = new Arc2D.Double(
                    originX - biteRadius, originY - biteRadius,
                    biteRadius * 2, biteRadius * 2,
                    startAngle, biteArcAngle, Arc2D.PIE
                );

                // Vùng tam giác cung tròn màu đỏ cam trong suốt, sáng dần theo thời gian chuẩn bị
                int fillAlpha = (int) (40 + 80 * progress);
                g2.setColor(new Color(255, 30, 20, fillAlpha));
                g2.fill(biteSector);

                // Viền vàng cam sắc nét của tam giác cung tròn
                g2.setColor(new Color(255, 210, 60, (int) (160 + 95 * progress)));
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(biteSector);

                g2.dispose();
            }
        }

        // 3. Vẽ hiệu ứng [BONK / STUNNED] khi đâm đầu vào tường
        if (bossState == BossActionState.BONK_STUNNED) {
            g.setFont(new Font("Monospaced", Font.BOLD, 13));
            g.setColor(Color.YELLOW);
            g.drawString("⭐ BONK! STUNNED! ⭐", cx - 65, cy - 40);
        } else if (bossState == BossActionState.PHASE_TRANSITION) {
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            g.setColor(Color.RED);
            g.drawString("🔥 ENRAGED! PHASE 2 🔥", cx - 75, cy - 40);
        }
    }

    /**
     * Vẽ Thanh Máu Boss Hoành Tráng ở Cạnh Dưới Màn Hình (HUD Screen Coordinates).
     */
    public void renderHealthBar(Graphics2D g, int screenW, int screenH) {
        if (isDead()) return;

        int barW = 460;
        int barH = 18;
        int barX = (screenW - barW) / 2;
        int barY = screenH - 45;

        // Khung nền đen mờ
        g.setColor(new Color(15, 15, 20, 230));
        g.fillRect(barX - 10, barY - 22, barW + 20, barH + 30);

        // Viền vàng kim sang trọng
        g.setColor(new Color(212, 175, 55));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(barX, barY, barW, barH);

        // Máu hiện tại
        double hpRatio = Math.max(0, (double) getHitPoints().getValue() / getHitPoints().getMax());
        int fillW = (int) (barW * hpRatio);

        // Màu thanh máu: Pha 1 màu tím sẫm -> Pha 2 màu đỏ rực
        Color hpColor = (currentPhase == 1) ? new Color(170, 40, 220) : new Color(230, 30, 30);
        g.setColor(hpColor);
        g.fillRect(barX + 2, barY + 2, Math.max(0, fillW - 4), barH - 4);

        // Vạch phân chia 50% HP (mốc kích hoạt Pha 2)
        g.setColor(new Color(255, 255, 255, 180));
        g.drawLine(barX + barW / 2, barY, barX + barW / 2, barY + barH);

        // Tên Boss và thông số
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        String title = (playerControlled ? "👑 BẠN: " : "") + "DEMON LORD GERALD " + (currentPhase == 1 ? "[PHASE 1]" : "[ENRAGED PHASE 2]");
        g.drawString(title, barX + 5, barY - 6);

        String hpText = getHitPoints().getValue() + " / " + getHitPoints().getMax() + " HP";
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.drawString(hpText, barX + barW - 105, barY - 6);
    }

    public BossActionState getBossState() {
        return bossState;
    }

    public int getCurrentPhase() {
        return currentPhase;
    }
}
