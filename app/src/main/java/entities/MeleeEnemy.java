package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.CollisionInfo;
import de.gurkenlabs.litiengine.entities.EntityInfo;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.util.geom.GeometricUtilities;
import level.RoomData;

import java.awt.geom.Point2D;
import java.util.concurrent.ThreadLocalRandom;
import java.awt.geom.Rectangle2D;

/**
 * Quái đánh gần (Melee Enemy).
 *
 * Hành vi: phát hiện người chơi -> đuổi theo -> áp sát -> BÁO HIỆU (windup)
 * -> ĐÁNH (strike, gây sát thương) -> HỒI (recover) -> đuổi tiếp.
 *
 * Pha báo hiệu là cố ý: người chơi có thời gian nhìn thấy đòn và né.
 * Hướng đánh bị khóa ngay lúc bắt đầu báo hiệu, nên chạy vòng ra sau lưng
 * quái trong lúc nó vung sẽ né được.
 *
 * Các hook onWindupStart / onStrike / onRecoverStart / onRecoverEnd để trống
 * dành cho phần hiệu ứng (nháy trắng, vệt chém, rung camera, âm thanh...).
 */
@EntityInfo(width = 16, height = 16)
@CollisionInfo(collisionBoxWidth = 12, collisionBoxHeight = 8, collision = true)
public class MeleeEnemy extends Enemy {

    /** Các trạng thái của máy trạng thái (state machine). */
    public enum State {
        IDLE,     // Chưa thấy mục tiêu, đứng yên
        CHASE,    // Đang đuổi mục tiêu
        WINDUP,   // Báo hiệu: đứng yên, lấy đà
        STRIKE,   // Khoảnh khắc đánh: kiểm tra trúng và gây sát thương
        RECOVER,  // Hồi sau đòn: đứng yên, người chơi có cửa phản công
        STUNNED ,  // [GĐ1] Bị đánh trúng: choáng ngắn, bị đẩy lùi
        ALERT,
        SEARCH,
        RETURN
    }

    // --- Giá trị mặc định ---
    private static final double DEFAULT_ATTACK_RANGE = 36;
    private static final double DEFAULT_DETECTION_RANGE = 200;
    private static final long DEFAULT_COOLDOWN_MS = 1200;

    private static final long DEFAULT_WINDUP_MS = 400;
    private static final long DEFAULT_STRIKE_MS = 120;
    private static final long DEFAULT_RECOVER_MS = 300;

    // Khoảng thời gian giữa 2 lần quét tìm mục tiêu mới (tránh quét mỗi frame)


    // --- Thông số riêng của quái cận chiến ---
    private long windupMs = DEFAULT_WINDUP_MS;
    private long strikeMs = DEFAULT_STRIKE_MS;
    private long recoverMs = DEFAULT_RECOVER_MS;

    /** Quãng lao tới (pixel) trong pha đánh. 0 = không lao. */
    private double lungeDistance = 0;

    /**
     * Sai số cho phép khi kiểm tra trúng đòn: lúc đánh, mục tiêu có thể đã
     * lùi ra một chút so với attackRange mà vẫn bị tính là trúng.
     */
    private double hitRangeTolerance = 6;

    // --- Trạng thái chạy ---
    private State state = State.IDLE;
    private long stateStartTime = 0;



    // --- [GĐ1] Phản ứng khi bị đánh ---
    private long hitStunMs = 250;                // thời gian choáng
    private boolean interruptsWindup = true;     // bị đánh lúc báo hiệu thì hủy đòn
    private double windupCommitRatio = 0.75;  // [GĐ5] báo hiệu quá mức này (0..1) thì đã chốt đòn, không bị ngắt nữa
    private long stunImmunityMs = 400;           // sau khi hết choáng, miễn choáng một lúc (chống khóa choáng)
    private long stunImmuneUntil = 0;


    // Điểm và hướng bị khóa lúc bắt đầu báo hiệu
    private Point2D lockedAimPoint;
    private double lockedAngle;     // độ, theo quy ước của LITIengine (chỉ để quay mặt)
    private double lockedAngleRad;  // radian, hệ tọa độ màn hình (dùng cho lao tới, hiệu ứng)

    // Nhận biết, mất dấu, quay về
    private long alertMs = 450;          // khựng khi vừa nhận ra mục tiêu (mỗi quái nhân 0,7–1,3)
    private long loseSightMs = 600;      // không còn nhận biết được mục tiêu quá lâu này thì chuyển sang tìm kiếm
    private long searchMs = 3000;        // tìm kiếm tối đa bấy lâu rồi bỏ cuộc
    private long lastSeenTime = 0;
    private Point2D lastSeenFeet;        // chỗ thấy mục tiêu lần cuối (tâm chân)

    // [GĐ7] Tín hiệu chốt đòn và giật lùi lấy đà
    private boolean commitSignaled = false;
    private double windupRecoilPx = 2;     // lùi bấy nhiêu pixel trong lúc lấy đà, rồi bật tới khi chém
    private double recoilApplied = 0;

    // [GĐ9] Chuỗi đòn
    private int comboLeft = 0;                 // số nhát nối còn lại sau nhát hiện tại
    private int comboTotal = 1;                // tổng số nhát của lượt đánh này
    private long currentWindupMs = 400;        // thời gian lấy đà của nhát hiện tại (nhát nối ngắn hơn)
    private long currentRecoverMs = 300;       // thời gian hồi sau lượt đánh này

    // [GĐ10] Hành vi lúc chờ
    private Point2D idleSpot;                  // điểm đang đi tới (tâm chân), null nếu đang đứng
    private long idleNextActionAt = 0;         // lúc được làm hành động tiếp theo
    private long idleSpotDeadline = 0;         // quá hạn này mà chưa tới thì bỏ (phòng kẹt vật cản)
    private double patrolRadius = 56;                    // "lãnh thổ": bán kính quanh nhà mà quái đi tuần (pixel)
    private static final double PATROL_MIN_STEP = 18;    // mỗi chặng tuần tra cách nhà tối thiểu bấy nhiêu pixel
    private static final double PATROL_SPEED_SCALE = 0.5;

    private static final double ALLY_ALERT_RADIUS = 80;   // [GĐ10]

    /** Dùng giá trị mặc định cho tầm đánh, tầm phát hiện, hồi chiêu. */
    public MeleeEnemy(String spritePrefix, int maxHealth, int damage, float speed) {
        this(spritePrefix, maxHealth, damage, speed,
                DEFAULT_ATTACK_RANGE, DEFAULT_DETECTION_RANGE, DEFAULT_COOLDOWN_MS);
    }

    public MeleeEnemy(String spritePrefix, int maxHealth, int damage, float speed,
                      double attackRange, double detectionRange, long cooldownMs) {
        super(spritePrefix, maxHealth, damage, speed, attackRange, detectionRange, cooldownMs);
    }

    // =====================================================================
    //  AI: máy trạng thái
    // =====================================================================

    @Override
    protected void updateAI() {
        long now = Game.time().now();

        switch (state) {
            case IDLE:
                updateIdle(now);
                break;
            case CHASE:
                updateChase(now);
                break;
            case WINDUP:
                updateWindup(now);
                break;
            case STRIKE:
                updateStrike(now);
                break;
            case RECOVER:
                updateRecover(now);
                break;
            case STUNNED:        // [GĐ1]
                updateStunned(now);
                break;
            case ALERT:
                updateAlert(now);
                break;
            case SEARCH:
                updateSearch(now);
                break;
            case RETURN:
                updateReturn(now);
                break;
        }
        applySeparation();   // [GĐ2] tách khỏi quái khác và người chơi
    }

    private void updateIdle(long now) {
        if (hasValidTarget() && canPerceive(target)) {
            beginAlert(now);
            return;
        }
        idleBehavior(now);   // [GĐ10]
    }

    /** Đứng canh quanh nhà: thỉnh thoảng đổi hướng nhìn hoặc đi vài bước tới một điểm gần đó. */
    /** Tuần tra lãnh thổ quanh nhà: đi tới một điểm trong bán kính, đứng nghỉ, rồi chọn điểm khác. */
    private void idleBehavior(long now) {
        RoomData room = RoomData.current();
        if (home == null || room == null || patrolRadius <= 0) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (idleNextActionAt == 0) {
            idleNextActionAt = now + 500 + r.nextInt(1500);   // chặng đầu tiên bắt đầu sớm
        }

        double speed = moveSpeed * PATROL_SPEED_SCALE;
        if (idleSpot != null) {
            boolean arrived = moveToPoint(idleSpot, speed, 2, true);
            if (arrived || now > idleSpotDeadline) {
                idleSpot = null;
                idleNextActionAt = now + 1500 + r.nextInt(2500);   // nghỉ rồi đi tiếp
                if (arrived) {
                    faceHorizontal(r.nextBoolean());               // tới nơi thì nhìn quanh một chút
                }
            }
            return;
        }
        if (now < idleNextActionAt) {
            return;
        }

        if (r.nextInt(100) < 20) {   // thỉnh thoảng chỉ đứng nhìn hướng khác
            faceHorizontal(r.nextBoolean());
            idleNextActionAt = now + 1500 + r.nextInt(2500);
            return;
        }

        // Chọn điểm tự do trong lãnh thổ mà đi thẳng từ chỗ đang đứng tới được
        Point2D me = getFeetCenter();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = r.nextDouble(0, Math.PI * 2);
            double dist = PATROL_MIN_STEP + r.nextDouble() * (patrolRadius - PATROL_MIN_STEP);
            Point2D p = new Point2D.Double(home.getX() + Math.cos(angle) * dist, home.getY() + Math.sin(angle) * dist);
            boolean free = !room.blocks(new Rectangle2D.Double(
                    p.getX() - getFootWidth() / 2, p.getY() - getFootHeight() / 2, getFootWidth(), getFootHeight()));
            if (free && room.isPathClear(me.getX(), me.getY(), p.getX(), p.getY(), getFootWidth(), getFootHeight())) {
                idleSpot = p;
                double travelMs = me.distance(p) / Math.max(1, speed) * 1000;
                idleSpotDeadline = now + (long) (travelMs * 1.5) + 1000;   // quá hạn thì bỏ, phòng kẹt vật cản
                return;
            }
        }
        idleNextActionAt = now + 1500;   // không chọn được điểm nào: thử lại sau
    }

    private void updateChase(long now) {
        if (!hasValidTarget()) {
            beginReturn(now);
            return;
        }

        // [GĐ6] Còn nhận biết được mục tiêu thì ghi nhớ chỗ thấy; mất dấu quá lâu thì chuyển sang tìm kiếm
        if (canPerceive(target, 1.3)) {
            lastSeenTime = now;
            lastSeenFeet = targetFeet();
        } else if (now - lastSeenTime > loseSightMs) {
            changeState(State.SEARCH, now);
            return;
        }

        // [GĐ3] Đã đủ quái đang ra đòn: giữ khoảng cách, đi vòng chờ tới lượt
        if (!attackSlotAvailable()) {
            hoverStep();
            return;
        }

        // [GĐ2] Chỉ coi là "trong tầm đánh" khi không có tường chắn giữa quái và mục tiêu
        if (isInAttackRange(target) && hasAttackLine()) {
            // Đủ gần: hết hồi chiêu thì bắt đầu đòn
            if (canAttack()) {
                beginWindup(now);
                return;
            }
            // Chưa hết hồi chiêu: vẫn bám sát mục tiêu, chỉ dừng khi đã đủ gần
            if (!isInRange(target, attackRange * 0.8)) {
                chaseStep();
            } else {
                faceTarget();
            }
            return;
        }

        chaseStep();
    }

    private void updateWindup(long now) {
        double progress = getWindupProgress();

        // [GĐ7] Lùi nhẹ ngược hướng đòn trong lúc lấy đà
        double want = windupRecoilPx * progress;
        if (want - recoilApplied > 0) {
            double delta = want - recoilApplied;
            moveBy(-Math.cos(lockedAngleRad) * delta, -Math.sin(lockedAngleRad) * delta);
            recoilApplied = want;
        }

        // [GĐ7] Quá ngưỡng chốt đòn: phát sáng báo "đòn sắp tới" (một lần)
        if (!commitSignaled && progress >= windupCommitRatio) {
            commitSignaled = true;
            onCommit();
        }

        if (now - stateStartTime >= currentWindupMs) {
            // Bật tới trước khi chém (bù lại phần đã lùi)
            moveBy(Math.cos(lockedAngleRad) * recoilApplied, Math.sin(lockedAngleRad) * recoilApplied);
            recoilApplied = 0;
            changeState(State.STRIKE, now);
            performStrike();
        }
    }

    private void updateStrike(long now) {
        // Lao tới theo hướng đã khóa (nếu có cấu hình)
        if (lungeDistance > 0 && lockedAimPoint != null) {
            // [GĐ1b] tốc độ vừa đủ để đi hết lungeDistance trong strikeMs
            stepToward(lockedAimPoint, lungeDistance * 1000.0 / Math.max(1, strikeMs));
        }
        if (now - stateStartTime >= strikeMs) {
            // [GĐ9] Còn nhát trong chuỗi và mục tiêu vẫn trong tầm: nối nhát tiếp theo với lấy đà ngắn
            if (comboLeft > 0 && hasValidTarget() && isInRange(target, effectReach() * 1.3) && hasAttackLine()) {
                comboLeft--;
                beginWindup(now, true);
                return;
            }
            // Hồi lâu hơn sau chuỗi nhiều nhát: cộng thêm 50% cho mỗi nhát thêm
            currentRecoverMs = (long) (recoverMs * (1 + 0.5 * (comboTotal - 1)));
            changeState(State.RECOVER, now);
            onRecoverStart();
        }
    }

    private void updateRecover(long now) {
        if (now - stateStartTime >= currentWindupMs) {
            onRecoverEnd();
            changeState(hasValidTarget() ? State.CHASE : State.IDLE, now);
        }
    }


    // [GĐ5] ---------------- Hiệu ứng mặc định theo loại quái ----------------
    private static final long SLASH_DURATION_MS = 180;

    private MonsterType.Profile profile = MonsterType.ORC_WARRIOR.getProfile();   // [GĐ8]

    /** Tầm thật của vùng đòn. Dải thẳng cộng thêm quãng lao vì quái lao tới theo hướng đó. */
    private double effectReach() {
        double base = (attackRange + hitRangeTolerance) * profile.reachScale;
        return profile.shape == MonsterType.Shape.LINE ? base + lungeDistance : base;
    }

    // [GĐ3] ---------------- Thẻ tấn công: giới hạn số quái đánh cùng lúc ----------------

    private static int maxAttackers = 2;                    // số quái tối đa được WINDUP/STRIKE cùng lúc
    private static final double WAIT_RADIUS_FACTOR = 1.6;   // quái chờ lượt đứng cách ~1,6 lần tầm đánh
    private boolean usesAttackSlot = true;                  // boss nên tắt (setUsesAttackSlot(false))

    public static void setMaxAttackers(int max) { maxAttackers = Math.max(1, max); }

    public void setUsesAttackSlot(boolean usesAttackSlot) { this.usesAttackSlot = usesAttackSlot; }

    private boolean attackSlotAvailable() {
        if (!usesAttackSlot || Game.world().environment() == null) {
            return true;
        }
        int active = 0;
        for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
            if (e != this && e instanceof MeleeEnemy && !e.isDead()) {
                MeleeEnemy other = (MeleeEnemy) e;
                if (other.target == target && (other.state == State.WINDUP || other.state == State.STRIKE)) {
                    active++;
                }
            }
        }
        return active < maxAttackers;
    }

    private void hoverStep() {
        faceTarget();
        if (tickSeconds <= 0) {
            return;
        }
        double ring = attackRange * WAIT_RADIUS_FACTOR;
        Point2D me = getCenter();
        Point2D t = target.getCenter();
        double dx = me.getX() - t.getX();
        double dy = me.getY() - t.getY();
        double d = Math.hypot(dx, dy);
        if (d < 1e-6) {
            return;
        }
        if (d > ring + 6) {          // còn xa vòng chờ: tiến vào
            chaseStep();
            return;
        }
        double ux = dx / d;          // hướng từ mục tiêu ra quái
        double uy = dy / d;
        double radial = d < ring - 6 ? 1 : 0;                              // quá gần thì lùi
        double side = (System.identityHashCode(this) & 1) == 0 ? 1 : -1;   // mỗi quái đi vòng một chiều
        double vx = ux * radial - uy * side * 0.5;
        double vy = uy * radial + ux * side * 0.5;
        double vlen = Math.hypot(vx, vy);
        if (vlen < 1e-6) {
            return;
        }
        double step = moveSpeed * 0.6 * tickSeconds;
        moveBy(vx / vlen * step, vy / vlen * step);
    }

    /** [GĐ9] Quái vây: khi còn xa thì nhắm tới điểm trên vòng tròn quanh mục tiêu thay vì lao thẳng. */
    @Override
    protected void chaseStep() {
        if (profile.flank && hasValidTarget()) {
            Point2D flank = flankPoint();
            if (flank != null) {
                faceTarget();
                moveToPoint(flank, moveSpeed, 3, false);
                return;
            }
        }
        super.chaseStep();
    }

    /**
     * Điểm vây của quái này: mỗi quái cùng loại đang đuổi cùng mục tiêu được chia một góc riêng
     * (theo thứ tự mã định danh nên ổn định). Trả về null khi đã gần hoặc đi một mình: lao thẳng vào.
     */
    private Point2D flankPoint() {
        Point2D goal = targetFeet();
        double ring = attackRange * 1.6;
        if (feetCenter().distance(goal) < ring * 1.5 || Game.world().environment() == null) {
            return null;
        }
        int total = 0;
        int index = 0;
        int myId = System.identityHashCode(this);
        for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
            if (e instanceof MeleeEnemy && !e.isDead()) {
                MeleeEnemy other = (MeleeEnemy) e;
                if (other.type == type && other.target == target) {
                    total++;
                    if (System.identityHashCode(other) < myId) {
                        index++;
                    }
                }
            }
        }
        if (total < 2) {
            return null;
        }
        double angle = 2 * Math.PI * index / total;
        return new Point2D.Double(goal.getX() + Math.cos(angle) * ring, goal.getY() + Math.sin(angle) * ring);
    }

    @Override
    protected void onDamaged(int amount, long now) {
        onHurt(amount);   // hook: nháy trắng, âm thanh... (luôn chạy)

        wakeAllies(now);

        // [GĐ6] Bị đánh bất ngờ khi chưa chiến đấu: nhận ra kẻ gần nhất dù chưa nhìn thấy, rồi đuổi ngay không khựng
        if (!hasValidTarget() || state == State.IDLE || state == State.ALERT
                || state == State.SEARCH || state == State.RETURN) {
            ICombatEntity attacker = findNearest(detectionRange * 1.5, false);
            if (attacker != null) {
                target = attacker;
                lastSeenTime = now;
                lastSeenFeet = targetFeet();
                changeState(State.CHASE, now);
            }
        }

        // Quái LUÔN bị đẩy lùi khi trúng đòn. Chỉ việc "choáng và bị ngắt đòn" mới có
        // miễn trừ (chống khóa choáng); khi đó đẩy nhẹ hơn (một nửa) và không đổi trạng thái.
        boolean immune = now < stunImmuneUntil;                           // vừa choáng xong
        boolean alreadyStunned = state == State.STUNNED;
        boolean strikingNow = state == State.STRIKE;                      // đòn đã tung, không ngắt giữa chừng
        // Bị đánh lúc báo hiệu: ngắt được nếu cho phép VÀ chưa quá ngưỡng chốt đòn
        // (ngưỡng nên khớp lúc vùng cảnh báo chuyển trắng, để người chơi học được tín hiệu).
        boolean windupIgnored = state == State.WINDUP
                && (!interruptsWindup || getWindupProgress() >= windupCommitRatio);
        boolean staggers = !(immune || alreadyStunned || strikingNow || windupIgnored);

        beginKnockback(now, staggers ? knockbackDistance : knockbackDistance * 0.5);
        if (staggers) {
            changeState(State.STUNNED, now);
        }
    }

    private void updateStunned(long now) {
        // Việc đẩy lùi do applyKnockback lo; ở đây chỉ đứng choáng cho hết giờ
        if (now - stateStartTime >= hitStunMs) {
            stunImmuneUntil = now + stunImmunityMs;
            changeState(hasValidTarget() ? State.CHASE : State.IDLE, now);
        }
    }

    // =====================================================================
    //  Tấn công
    // =====================================================================

    private void beginWindup(long now) {   // đòn mở đầu
        beginWindup(now, false);
    }

    private void beginWindup(long now, boolean followUp) {
        // [GĐ9] nhát mở đầu đặt số nhát của lượt; nhát nối lấy đà ngắn hơn
        if (!followUp) {
            comboTotal = profile.comboHits;
            comboLeft = profile.comboHits - 1;
        }
        currentWindupMs = followUp ? Math.max(60, (long) (windupMs * profile.comboWindupScale)) : windupMs;

        commitSignaled = false;
        recoilApplied = 0;

        // Khóa hướng và điểm nhắm: người chơi né được nếu rời khỏi hướng này
        Point2D targetCenter = target.getCenter();
        Point2D from = this.getCenter();

        // Góc dùng cho lao tới và hiệu ứng: tự tính bằng atan2, theo hệ tọa độ
        // màn hình (0 = sang phải, tăng dần theo chiều kim đồng hồ vì y hướng xuống).
        this.lockedAngleRad = Math.atan2(targetCenter.getY() - from.getY(),
                targetCenter.getX() - from.getX());

        // Góc của thư viện chỉ dùng để quay mặt quái (chọn sprite hướng nào).
        this.lockedAngle = GeometricUtilities.calcRotationAngleInDegrees(from, targetCenter);
        faceTarget();

        if (lungeDistance > 0) {
            this.lockedAimPoint = new Point2D.Double(
                    from.getX() + Math.cos(lockedAngleRad) * lungeDistance,
                    from.getY() + Math.sin(lockedAngleRad) * lungeDistance);
        } else {
            this.lockedAimPoint = null;
        }

        changeState(State.WINDUP, now);
        onWindupStart();
    }

    /** Khoảnh khắc đánh: kiểm tra trúng rồi gây sát thương. */
    private void performStrike() {
        // Tính hồi chiêu từ lúc đánh, bất kể có trúng hay không
        recordAttack();
        onStrike();

        if (hasValidTarget() && isInStrikeZone(target) && hasAttackLine()) {
            attack(target);
        }
    }

    /**
     * Gây sát thương trực tiếp lên mục tiêu (xem Enemy.attack).
     * Có thể gọi trực tiếp từ bên ngoài, nhưng bình thường chỉ được gọi
     * từ pha STRIKE sau khi đã kiểm tra tầm.
     */
    @Override
    public void attack(ICombatEntity target) {
        if (target == null || target.isDead() || this.isDead()) {
            return;
        }
        // Bản LITIengine này: hit(int damage, Ability ability), tham số thứ hai
        // là Ability chứ không phải entity gây đòn, nên dùng bản một tham số.
        target.hit(this.damage);
    }


    // =====================================================================
    //  Máy trạng thái: tiện ích
    // =====================================================================

    private void changeState(State newState, long now) {
        this.state = newState;
        this.stateStartTime = now;
    }

    public State getState() {
        return state;
    }

    @Override
    protected boolean canRetarget() {
        return state != State.WINDUP && state != State.STRIKE;   // không đổi mục tiêu giữa lúc ra đòn
    }

    @Override
    protected boolean canBeSeparated() {
        return state != State.STRIKE;   // không bị đẩy lệch giữa lúc lao tới
    }

    /** Tiến độ của pha báo hiệu, 0.0 -> 1.0. Dùng để vẽ vòng cảnh báo lớn dần. */
    public double getWindupProgress() {
        if (state != State.WINDUP || currentWindupMs <= 0) {
            return 0;
        }
        long elapsed = Game.time().now() - stateStartTime;
        return Math.max(0, Math.min(1, (double) elapsed / currentWindupMs));
    }

    /** Hướng (độ) đã bị khóa lúc báo hiệu; dùng để đặt vệt chém đúng hướng. */
    public double getLockedAngle() {
        return lockedAngle;
    }

    /**
     * Hướng đòn đã khóa, tính bằng radian trong hệ tọa độ màn hình
     * (0 = sang phải, tăng theo chiều kim đồng hồ). Dùng để vẽ hiệu ứng.
     */
    public double getLockedAngleRad() {
        return lockedAngleRad;
    }

    // [GĐ6] ---------------- Nhận ra, tìm kiếm, quay về ----------------

    private void beginAlert(long now) {
        beginAlert(now, true);
    }

    private void beginAlert(long now, boolean propagate) {
        idleSpot = null;   // [GĐ10] ngừng đi dạo
        lastSeenTime = now;
        lastSeenFeet = targetFeet();
        faceTarget();
        changeState(State.ALERT, now);
        onAlert();
        if (propagate) {
            wakeAllies(now);
        }
    }

    /** [GĐ10] Đánh thức đồng bọn gần đó (đang chờ hoặc đang về nhà): chúng nhận ra cùng mục tiêu. */
    private void wakeAllies(long now) {
        if (!hasValidTarget() || Game.world().environment() == null) {
            return;
        }
        for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
            if (e == this || e.isDead() || !(e instanceof MeleeEnemy) || e.getTeam() != getTeam()) {
                continue;
            }
            MeleeEnemy ally = (MeleeEnemy) e;
            if ((ally.state == State.IDLE || ally.state == State.RETURN)
                    && getCenter().distance(ally.getCenter()) <= ALLY_ALERT_RADIUS) {
                ally.target = target;
                ally.beginAlert(now, false);   // false: con được đánh thức không báo động tiếp
            }
        }
    }

    private void updateAlert(long now) {
        if (!hasValidTarget()) {
            changeState(State.IDLE, now);
            return;
        }
        faceTarget();
        // mỗi quái một độ trễ phản ứng riêng (0,7–1,3 lần alertMs) để cả đàn không cùng lao lên một nhịp
        double jitter = 0.7 + (Math.abs(System.identityHashCode(this)) % 7) * 0.1;
        if (now - stateStartTime >= alertMs * jitter) {
            changeState(State.CHASE, now);
        }
    }

    private void updateSearch(long now) {
        // Thấy lại mục tiêu thì đuổi tiếp luôn, không khựng
        if (hasValidTarget() && canPerceive(target)) {
            lastSeenTime = now;
            changeState(State.CHASE, now);
            return;
        }
        if (now - stateStartTime > searchMs || lastSeenFeet == null) {
            beginReturn(now);
            return;
        }
        // Đi tới chỗ thấy lần cuối, tới nơi thì đứng nhìn quanh (đổi hướng mỗi 0,6 giây)
        if (moveToPoint(lastSeenFeet, moveSpeed, 6, true)) {
            faceHorizontal(((now - stateStartTime) / 600) % 2 == 0);
        }
    }

    private void beginReturn(long now) {
        target = null;
        changeState(State.RETURN, now);
    }

    private void updateReturn(long now) {
        // Thấy người chơi trên đường về: phản ứng như lần đầu
        if (hasValidTarget() && canPerceive(target)) {
            beginAlert(now);
            return;
        }
        if (home == null || moveToPoint(home, moveSpeed * 0.7, 4, true)) {
            changeState(State.IDLE, now);
        }
    }

    /** [GĐ6] Vừa nhận ra mục tiêu: dấu "!" trên đầu quái. Có thể thêm âm thanh. */
    protected void onAlert() {
        MeleeAttackVfx.alert(this);
    }

    /** Mục tiêu có nằm trong vùng đòn đã khóa lúc báo hiệu không (đúng hình dạng vùng cảnh báo)? */
    private boolean isInStrikeZone(ICombatEntity t) {
        Point2D me = getCenter();
        Point2D c = t.getCenter();
        double dx = c.getX() - me.getX();
        double dy = c.getY() - me.getY();
        double dist = Math.hypot(dx, dy);
        double reach = effectReach();

        switch (profile.shape) {
            case RING:
                return dist <= reach;
            case LINE: {
                double ux = Math.cos(lockedAngleRad);
                double uy = Math.sin(lockedAngleRad);
                double along = dx * ux + dy * uy;                 // dọc theo hướng đòn
                double across = Math.abs(-dx * uy + dy * ux);     // lệch ngang
                return along >= -4 && along <= reach && across <= profile.lineWidth / 2 + 4;   // +4: nửa thân mục tiêu
            }
            default: {   // ARC
                if (dist > reach) {
                    return false;
                }
                if (dist < 6) {
                    return true;   // đứng sát hoặc đè lên quái thì luôn trúng
                }
                double diff = angleDifference(Math.atan2(dy, dx), lockedAngleRad);
                return diff <= Math.toRadians(profile.arcDeg) / 2 + Math.toRadians(8);   // +8°: nửa thân mục tiêu
            }
        }
    }

    private static double angleDifference(double a, double b) {
        return Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
    }

    // =====================================================================
    //  Hook cho hiệu ứng: ghi đè ở lớp con hoặc điền vào đây
    // =====================================================================

    /** Bắt đầu báo hiệu: nháy trắng/đỏ, hiện vùng cảnh báo, âm thanh lấy đà. */
    protected void onWindupStart() {
        playAction("windup", (int) currentWindupMs + 120, 4);   // [GĐ11] +120ms: chưa hết animation khi sang chém
        if (type != null) {
            MeleeAttackVfx.telegraph(this, type.getFaction().getEffectColor(), effectReach(),
                    profile.shape, profile.arcDeg, profile.lineWidth);
        }
    }


    /** Khoảnh khắc đánh: vệt chém, tia lửa, rung camera, âm thanh va chạm. */
    protected void onStrike() {
        playAction("strike", (int) Math.max(strikeMs, 120), 3);   // [GĐ11]
        if (type != null) {
            MeleeAttackVfx.slash(this, type.getFaction().getEffectColor(), effectReach(),
                    profile.shape, profile.arcDeg, profile.lineWidth, SLASH_DURATION_MS);
        }
    }

    /** [GĐ7] Đã chốt đòn (không còn ngắt được): quái phát sáng màu phe. */
    protected void onCommit() {
        if (type != null) {
            SpriteFlashVfx.glow(this, type.getFlashImagePath(), type.getFaction().getEffectColor(),
                    () -> state == State.WINDUP);
        }
    }

    /** Bắt đầu hồi sau đòn. */
    protected void onRecoverStart() {
    }

    /** Hết hồi, trước khi quay lại đuổi. */
    protected void onRecoverEnd() {
    }

    @Override
    protected void onDeath() {
        state = State.IDLE;
        super.onDeath();
    }

    @Override
    protected void onHurt(int amount) {
        super.onHurt(amount);   // nháy trắng và tia lửa của lớp cha
        if (state != State.STRIKE) {   // [GĐ11] đang chém thì giữ nguyên tư thế chém, không bị giật ngửa
            playAction("hurt", 160, 2);
        }
    }

    // =====================================================================
    //  Getter / Setter cho cân bằng game
    // =====================================================================

    public long getWindupMs() {
        return windupMs;
    }

    public void setWindupMs(long windupMs) {
        this.windupMs = windupMs;
    }

    public long getStrikeMs() {
        return strikeMs;
    }

    public void setStrikeMs(long strikeMs) {
        this.strikeMs = strikeMs;
    }

    public long getRecoverMs() {
        return recoverMs;
    }

    public void setRecoverMs(long recoverMs) {
        this.recoverMs = recoverMs;
    }

    public double getLungeDistance() {
        return lungeDistance;
    }

    public void setLungeDistance(double lungeDistance) {
        this.lungeDistance = lungeDistance;
    }

    public double getHitRangeTolerance() {
        return hitRangeTolerance;
    }

    public void setHitRangeTolerance(double hitRangeTolerance) {
        this.hitRangeTolerance = hitRangeTolerance;
    }


    // [GĐ1] Cấu hình phản ứng khi bị đánh
    public void setHitStunMs(long hitStunMs) { this.hitStunMs = hitStunMs; }
    public void setInterruptsWindup(boolean interruptsWindup) { this.interruptsWindup = interruptsWindup; }
    public void setWindupCommitRatio(double windupCommitRatio) { this.windupCommitRatio = windupCommitRatio; }
    public void setStunImmunityMs(long stunImmunityMs) { this.stunImmunityMs = stunImmunityMs; }

    public void setWindupRecoilPx(double px) { this.windupRecoilPx = px; }

    @Override
    public void setType(MonsterType type) {
        super.setType(type);
        if (type != null) {
            applyProfile(type.getProfile());
        }
    }

    private void applyProfile(MonsterType.Profile p) {
        profile = p;
        interruptsWindup = p.interruptible;
        windupCommitRatio = p.commitRatio;
        hitStunMs = p.hitStunMs;
        knockbackDistance = 14 * p.knockbackScale;
    }

    /** Bán kính lãnh thổ; đặt 0 để quái đứng yên canh gác không đi tuần. */
    public void setPatrolRadius(double patrolRadius) { this.patrolRadius = patrolRadius; }
}