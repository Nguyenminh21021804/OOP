package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.util.geom.GeometricUtilities;
import level.PathField;
import level.RoomData;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Lớp trừu tượng cơ sở (Base Class) cho tất cả các loại Enemy trong game.
 * Được thiết kế dùng chung để hai người có thể phát triển độc lập:
 * - Người 1: Kế thừa để làm quái đánh gần (MeleeEnemy)
 * - Người 2: Kế thừa để làm quái đánh xa (RangedEnemy)
 */
public abstract class Enemy extends Creature implements IUpdateable {
    // Sát thương gây ra cho người chơi
    protected int damage;

    // Phạm vi tấn công (pixel). Đánh gần: ~40-60px, Đánh xa: ~200-300px
    protected double attackRange;

    // Phạm vi phát hiện người chơi để bắt đầu đuổi theo hoặc tấn công
    protected double detectionRange;

    // Thời gian hồi chiêu giữa 2 lần tấn công liên tiếp (mili-giây)
    protected long attackCooldownMs;

    // Thời điểm tấn công gần nhất
    protected long lastAttackTime;

    private static final long RETARGET_INTERVAL_MS = 400;

    protected ICombatEntity target;

    private long lastRetargetTime = 0;

    // Di chuyển tự quản (không dùng Game.physics().move)
    protected float moveSpeed;            // pixel mỗi giây
    private long lastTickTime = 0;
    protected double tickSeconds = 0;     // thời gian thật từ tick trước (giây), đã giới hạn


    private double footWidth = 10;
    private double footHeight = 8;

    private static final double SEPARATION_RADIUS = 12;   // khoảng cách tối thiểu giữa tâm hai thực thể (pixel)
    private static final double SEPARATION_SPEED = 60;    // tốc độ tách tối đa (pixel mỗi giây)

    private long knockbackMs = 100;              // thời gian bị đẩy lùi (nằm trong thời gian choáng)
    protected double knockbackDistance = 14;      // quãng bị đẩy lùi (pixel)

    private long knockbackBeginTime = 0;         // lúc bắt đầu đợt đẩy hiện tại
    private double knockbackActiveDistance = 0;  // quãng của đợt đẩy hiện tại (0 = không đẩy)
    private double knockbackAppliedDist = 0;     // đã đẩy được bao xa trong đợt này
    private double knockbackDirX;                // hướng đẩy (vector đơn vị), (0,0) = không đẩy
    private double knockbackDirY;

    private int lastKnownHp;                     // mốc máu để phát hiện bị đánh

    private boolean deathScheduled = false;

    protected MonsterType type;   // loại quái (phe, tên sprite); null thì không có hiệu ứng mặc định

    private static final long FLASH_MS = 80;

    // =====================================================================
    //  Tìm mục tiêu
    // =====================================================================

    /** Tìm lại mục tiêu định kỳ nếu chưa có hoặc mục tiêu cũ không còn hợp lệ. */
    private void refreshTarget(long now) {
        // Không đổi mục tiêu giữa chừng khi đang ra đòn
        if (!canRetarget()) {
            return;
        }
        if (hasValidTarget() && now - lastRetargetTime < RETARGET_INTERVAL_MS) {
            return;
        }
        if (!hasValidTarget() || now - lastRetargetTime >= RETARGET_INTERVAL_MS) {
            lastRetargetTime = now;
            this.target = findNearestTarget();
        }
    }

    /** Mục tiêu gần nhất thuộc team khác, còn sống, nằm trong tầm phát hiện. */
    private ICombatEntity findNearestTarget() {
        if (Game.world().environment() == null) {
            return null;
        }
        ICombatEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
            if (e == this || e.isDead() || e.getTeam() == this.getTeam()) {
                continue;
            }
            double d = this.getCenter().distance(e.getCenter());
            if (d <= detectionRange && d < bestDist) {
                best = e;
                bestDist = d;
            }
        }
        return best;
    }

    protected boolean hasValidTarget() {
        return target != null && !target.isDead();
    }

    protected void faceTarget() {
        if (hasValidTarget()) {
            this.setAngle(GeometricUtilities.calcRotationAngleInDegrees(
                    this.getCenter(), target.getCenter()));
        }
    }

    // =====================================================================
    //  Di chuyển
    // =====================================================================

    /**
     * Di chuyển hướng về phía một tọa độ.
     */
    public void moveTowards(Point2D destination) {
        stepToward(destination, moveSpeed);
    }

    protected void stepToward(Point2D destination, double pixelsPerSecond) {
        if (destination == null || tickSeconds <= 0) {
            return;
        }
        Point2D c = getCenter();
        double dx = destination.getX() - c.getX();
        double dy = destination.getY() - c.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return;
        }

        double step = Math.min(len, pixelsPerSecond * tickSeconds);
        moveBy(dx / len * step, dy / len * step);
    }

    /** Di chuyển một đoạn (dx, dy), tôn trọng tường và trượt dọc tường khi bị chặn. */
    protected void moveBy(double dx, double dy) {
        double nx = getX() + dx;
        double ny = getY() + dy;
        if (canOccupy(nx, ny)) {
            setLocation(nx, ny);
        } else if (canOccupy(nx, getY())) {      // trượt dọc theo tường
            setLocation(nx, getY());
        } else if (canOccupy(getX(), ny)) {
            setLocation(getX(), ny);
        }
    }

    private void stepTowardFeet(Point2D feetDestination, double pixelsPerSecond) {
        if (tickSeconds <= 0) {
            return;
        }
        Point2D me = feetCenter();
        double dx = feetDestination.getX() - me.getX();
        double dy = feetDestination.getY() - me.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return;
        }
        double step = Math.min(len, pixelsPerSecond * tickSeconds);
        moveBy(dx / len * step, dy / len * step);
    }

    /**
     * Hook va chạm: trả về false nếu quái đặt tại (x, y) sẽ đè lên tường.
     * Dùng hộp chân của quái so với các hộp va chạm của phòng hiện tại (RoomData).
     */
    protected boolean canOccupy(double x, double y) {
        RoomData room = RoomData.current();
        return room == null || !room.blocks(footprintAt(x, y));
    }

    // =====================================================================
    //  Feetbox, tầm nhìn
    // =====================================================================

    /** Tâm hộp chân của quái (toạ độ mà bản đồ khoảng cách dùng). */
    private Point2D feetCenter() {
        return new Point2D.Double(getX() + getWidth() / 2.0, getY() + getHeight() - footHeight / 2.0);
    }

    /**
     * Tâm hộp chân của mục tiêu. Giả định mục tiêu có cùng kích thước thực thể với quái
     * (đúng với TestDummy; cần chỉnh nếu Player của nhóm có kích thước khác).
     */
    private Point2D targetFeet() {
        Point2D c = target.getCenter();
        return new Point2D.Double(c.getX(), c.getY() + (getHeight() - footHeight) / 2.0);
    }

    /** Không có tường chắn giữa quái và mục tiêu (kiểm tra bằng tia mảnh 2x2). */
    protected boolean hasAttackLine() {
        RoomData room = RoomData.current();
        if (room == null || !hasValidTarget()) {
            return true;
        }
        Point2D me = feetCenter();
        Point2D goal = targetFeet();
        return room.isPathClear(me.getX(), me.getY(), goal.getX(), goal.getY(), 2, 2);
    }

    /** [GĐ2] Hộp chân của quái nếu thực thể đặt tại góc trên trái (x, y). */
    private Rectangle2D footprintAt(double x, double y) {
        return new Rectangle2D.Double(
                x + (getWidth() - footWidth) / 2.0,
                y + getHeight() - footHeight,
                footWidth, footHeight);
    }

    /** [GĐ2] Kích thước hộp chân; nên khớp với @CollisionInfo của lớp quái. */
    public void setFootprint(double width, double height) {
        this.footWidth = width;
        this.footHeight = height;
    }

    /** [GĐ2] Đặt quái sao cho TÂM hộp chân nằm đúng tại (px, py), như điểm spawn trong Tiled. */
    public void placeFeetAt(double px, double py) {
        setLocation(px - getWidth() / 2.0, py - getHeight() + footHeight / 2.0);
    }

    // =====================================================================
    // Đuổi, tách
    // =====================================================================

    /** Bước đuổi theo: đi thẳng nếu thông, vòng theo bản đồ khoảng cách nếu bị vật cản chắn. */
    protected void chaseStep() {
        faceTarget();
        RoomData room = RoomData.current();
        if (room == null) {
            moveTowards(target.getCenter());
            return;
        }

        Point2D me = feetCenter();
        Point2D goal = targetFeet();
        if (room.isPathClear(me.getX(), me.getY(), goal.getX(), goal.getY(), footWidth, footHeight)) {
            moveTowards(target.getCenter());
            return;
        }

        Point2D waypoint = PathField.toward(room, goal, footWidth, footHeight).nextWaypoint(me);
        if (waypoint != null) {
            stepTowardFeet(waypoint, moveSpeed);
        } else {
            moveTowards(target.getCenter());   // không có đường: thử đi thẳng
        }
    }

    /**
     * Đẩy nhẹ quái ra khỏi các thực thể khác (quái khác, người chơi) đang đè lên nó.
     * Chỉ đẩy chính quái này; mỗi bên tự đẩy mình nên hai quái chồng nhau sẽ tách ra đối xứng.
     */
    protected void applySeparation() {
        if (!canBeSeparated() || tickSeconds <= 0 || Game.world().environment() == null) {
            return;
        }
        Point2D me = getCenter();
        double pushX = 0;
        double pushY = 0;
        for (ICombatEntity other : Game.world().environment().getCombatEntities()) {
            if (other == this || other.isDead()) {
                continue;
            }
            Point2D c = other.getCenter();
            double dx = me.getX() - c.getX();
            double dy = me.getY() - c.getY();
            double d = Math.hypot(dx, dy);
            if (d >= SEPARATION_RADIUS) {
                continue;
            }
            if (d < 1e-3) {   // trùng khít: mỗi thực thể tự chọn một hướng riêng để cùng tách ra
                double angle = Math.toRadians(Math.abs(System.identityHashCode(this) % 360));
                dx = Math.cos(angle);
                dy = Math.sin(angle);
                d = 1;
            }
            double weight = (SEPARATION_RADIUS - d) / SEPARATION_RADIUS;   // càng gần càng mạnh
            pushX += dx / d * weight;
            pushY += dy / d * weight;
        }
        double len = Math.hypot(pushX, pushY);
        if (len < 1e-6) {
            return;
        }
        double step = SEPARATION_SPEED * tickSeconds * Math.min(1.0, len);
        moveBy(pushX / len * step, pushY / len * step);
    }

    // =====================================================================
    // Phản ứng chịu đòn
    // =====================================================================

    /** So máu với lần trước: giảm nghĩa là vừa bị đánh (bắt được mọi nguồn sát thương). */
    private void detectDamage(long now) {
        int hp = getHitPoints().getValue();
        if (hp < lastKnownHp) {
            int lost = lastKnownHp - hp;
            lastKnownHp = hp;
            if (hp > 0) {            // hết máu thì để die() xử lý
                onDamaged(lost, now);
            }
        } else {
            lastKnownHp = hp;        // hồi máu thì cập nhật mốc
        }
    }

    /** Bắt đầu một đợt đẩy lùi: ra xa kẻ đánh (mục tiêu hiện tại). Không có mục tiêu thì không đẩy. */
    protected void beginKnockback(long now, double distance) {
        knockbackDirX = 0;
        knockbackDirY = 0;
        if (hasValidTarget()) {
            Point2D me = getCenter();
            Point2D from = target.getCenter();
            double dx = me.getX() - from.getX();
            double dy = me.getY() - from.getY();
            double len = Math.hypot(dx, dy);
            if (len > 1e-6) {
                knockbackDirX = dx / len;
                knockbackDirY = dy / len;
            } else {
                knockbackDirX = 1;   // chồng khít nhau: đẩy sang phải
            }
        }
        knockbackBeginTime = now;
        knockbackActiveDistance = distance;
        knockbackAppliedDist = 0;
    }

    /**
     * Áp dụng đợt đẩy lùi theo từng bước nhỏ (giảm tốc dần), cộng dồn lên vị trí hiện tại
     * nên chạy song song được với chuyển động khác mà không giật về chỗ cũ.
     */
    private void applyKnockback(long now) {
        if (knockbackActiveDistance <= 0 || knockbackMs <= 0) {
            return;
        }
        double t = Math.min(1.0, (double) (now - knockbackBeginTime) / knockbackMs);
        double eased = 1.0 - (1.0 - t) * (1.0 - t);
        double delta = knockbackActiveDistance * eased - knockbackAppliedDist;
        if (delta > 0) {
            double nx = getX() + knockbackDirX * delta;
            double ny = getY() + knockbackDirY * delta;
            if (canOccupy(nx, ny)) {
                setLocation(nx, ny);
                knockbackAppliedDist += delta;
            }
        }
        if (t >= 1.0) {
            knockbackActiveDistance = 0;   // xong đợt đẩy
        }
    }

    // =====================================================================
    //   Dead
    // =====================================================================

    /** Hoãn tới tick sau để không gỡ quái ngay giữa lúc engine còn đang xử lý đòn đánh. */
    private void scheduleDeath() {
        if (deathScheduled) {
            return;
        }
        deathScheduled = true;
        Game.loop().perform(1, this::handleDeath);
    }

    private void handleDeath() {
        target = null;
        onDeath();   // hook chạy khi quái còn trong màn chơi, để hiệu ứng chụp được hình ảnh cuối
        Game.loop().detach(this);
        if (Game.world().environment() != null) {
            Game.world().environment().remove(this);
        }
    }

    /**
     * Khởi tạo quái vật với các thuộc tính cơ bản.
     *
     * @param spritePrefix    Tên tiền tố của sprite animation (ví dụ: "goblin", "skeleton")
     * @param maxHealth       Máu tối đa
     * @param damage          Sát thương gây ra
     * @param speed           Tốc độ di chuyển
     * @param attackRange     Tầm đánh
     * @param detectionRange  Tầm phát hiện người chơi
     * @param cooldownMs      Thời gian hồi chiêu đòn đánh (ms)
     */
    public Enemy(String spritePrefix, int maxHealth, int damage, float speed,
                 double attackRange, double detectionRange, long cooldownMs) {
        super(spritePrefix);

        this.damage = damage;
        this.lastKnownHp = maxHealth;
        this.attackRange = attackRange;
        this.detectionRange = detectionRange;
        this.attackCooldownMs = cooldownMs;
        this.lastAttackTime = 0;

        // Cấu hình Creature trong LITIengine
        this.setScaling(true);
        this.setVelocity(speed);
        this.moveSpeed = speed;
        this.getHitPoints().setMax(maxHealth);
        this.getHitPoints().setValue(maxHealth);
        this.setIndestructible(false);

        // Mặc định gán Team cho Enemy (ví dụ: 2 = Quái, 1 = Player)
        this.setTeam(2);

        // Đăng ký nhận vòng lặp cập nhật từ Game Loop
        Game.loop().attach(this);
    }

    /**
     * Vòng lặp cập nhật mỗi frame của quái.
     */
    @Override
    public void update() {
        if (isDead()) {
            scheduleDeath();   // xử lý cái chết đúng một lần, dù engine gọi die() lúc nào
            return;
        }
        long now = Game.time().now();
        tickSeconds = lastTickTime == 0 ? 0 : Math.min(50, Math.max(0, now - lastTickTime)) / 1000.0;
        lastTickTime = now;
        refreshTarget(now);
        detectDamage(now);     // mất máu -> gọi onDamaged
        applyKnockback(now);   // đẩy lùi chạy độc lập với trạng thái

        updateAI();            // logic riêng của từng loại quái

        applySeparation();     // tách khỏi quái khác và người chơi
    }

    /**
     * Logic trí tuệ nhân tạo (AI) và cách thức di chuyển:
     * - Quái cận chiến: Tiếp cận mục tiêu (áp sát).
     * - Quái đánh xa: Giữ cự ly an toàn (kiting/thả diều).
     */
    protected abstract void updateAI();

    /**
     * Hành vi tấn công mục tiêu (Tính đa hình - Polymorphism):
     * - Quái cận chiến: Gây sát thương trực tiếp lên hitbox mục tiêu.
     * - Quái đánh xa: Bắn đạn/phép (Projectile) về phía mục tiêu.
     *
     * @param target Mục tiêu bị tấn công (thường là Player)
     */
    public abstract void attack(ICombatEntity target);

    /**
     * Kiểm tra xem quái có đủ điều kiện để tấn công hay không (đã hồi chiêu xong chưa).
     */
    public boolean canAttack() {
        if (isDead()) {
            return false;
        }
        long now = Game.time().now();
        return (now - lastAttackTime) >= attackCooldownMs;
    }

    /**
     * Đánh dấu vừa thực hiện tấn công để bắt đầu tính hồi chiêu.
     */
    protected void recordAttack() {
        this.lastAttackTime = Game.time().now();
    }

    /**
     * Kiểm tra mục tiêu có nằm trong một bán kính khoảng cách nhất định hay không.
     */
    public boolean isInRange(ICombatEntity target, double range) {
        if (target == null) {
            return false;
        }
        return this.getCenter().distance(target.getCenter()) <= range;
    }

    /**
     * Kiểm tra mục tiêu đã vào tầm tấn công (attackRange) chưa.
     */
    public boolean isInAttackRange(ICombatEntity target) {
        return isInRange(target, this.attackRange);
    }

    /**
     * Kiểm tra mục tiêu đã vào tầm phát hiện (detectionRange) chưa.
     */
    public boolean isInDetectionRange(ICombatEntity target) {
        return isInRange(target, this.detectionRange);
    }

    /**
     * Nhận sát thương và tự động kích hoạt chết nếu hết máu.
     */
    public void takeDamage(int amount) {
        if (isDead()) {
            return;
        }
        this.hit(amount);
    }

    /**
     * Xử lý khi quái chết: hủy đăng ký khỏi game loop.
     */
    @Override
    public void die() {
        super.die();
        scheduleDeath();
    }

    /** Có được đổi mục tiêu lúc này không (vd quái cận chiến không đổi giữa lúc ra đòn). */
    protected boolean canRetarget() {
        return true;
    }

    /** Có được đẩy tách khỏi thực thể khác lúc này không. */
    protected boolean canBeSeparated() {
        return true;
    }


    // --- Các phương thức lấy thông tin (Getter & Setter) ---
    public int getCurrentHealth() {
        return this.getHitPoints().getValue();
    }

    public int getMaxHealth() {
        return this.getHitPoints().getMax();
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public double getAttackRange() {
        return attackRange;
    }

    public void setAttackRange(double attackRange) {
        this.attackRange = attackRange;
    }

    public double getDetectionRange() {
        return detectionRange;
    }

    public void setDetectionRange(double detectionRange) {
        this.detectionRange = detectionRange;
    }

    public long getAttackCooldownMs() {
        return attackCooldownMs;
    }

    public void setAttackCooldownMs(long attackCooldownMs) {
        this.attackCooldownMs = attackCooldownMs;
    }

    public ICombatEntity getTarget() {
        return target;
    }

    /** Gán mục tiêu thủ công (ví dụ ép quái nhắm vào Player). */
    public void setTarget(ICombatEntity target) {
        this.target = target;
    }

    public void setType(MonsterType type) { this.type = type; }

    public MonsterType getType() { return type; }

    public void setKnockbackMs(long knockbackMs) { this.knockbackMs = knockbackMs; }
    public void setKnockbackDistance(double knockbackDistance) { this.knockbackDistance = knockbackDistance; }

    /**  Vừa bị đánh (kể cả khi không bị choáng): nháy trắng, âm thanh trúng đòn. */
    protected void onHurt(int amount) {
        if (type != null) {   // [GĐ5]
            SpriteFlashVfx.flash(this, type.getSpritePrefix(), FLASH_MS);
        }
    }

    /**  Quái vừa chết (gọi một lần, trước khi gỡ khỏi màn chơi): hiệu ứng, âm thanh, rơi đồ. */
    protected void onDeath() {
        if (type != null) {   // [GĐ5]
            SpriteFlashVfx.burst(this, type.getSpritePrefix(), type.getFaction().getEffectColor());
        }
    }

    /** Vừa mất máu. Mặc định: nháy trắng và đẩy lùi. Lớp con ghi đè nếu cần thêm (vd choáng, ngắt đòn). */
    protected void onDamaged(int amount, long now) {
        onHurt(amount);
        beginKnockback(now, knockbackDistance);
    }
}
