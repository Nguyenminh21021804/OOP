package player;

import de.gurkenlabs.litiengine.Align;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.Valign;
import de.gurkenlabs.litiengine.attributes.AttributeModifier;
import de.gurkenlabs.litiengine.attributes.Modification;
import de.gurkenlabs.litiengine.entities.CollisionInfo;
import de.gurkenlabs.litiengine.entities.CombatInfo;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.entities.EntityInfo;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.entities.MovementInfo;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.animation.Animation;
import weapons.Bow;
import weapons.Spear;
import weapons.Staff;
import weapons.Sword;
import weapons.Weapon;
import weapons.WeaponCollision;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EntityInfo(width = 16, height = 20)
@MovementInfo(velocity = 85)
@CollisionInfo(
    collisionBoxWidth = 10,
    collisionBoxHeight = 6,
    collision = true,
    align = Align.CENTER,
    valign = Valign.DOWN
)
@CombatInfo(hitpoints = 6)
public class Player extends Creature implements IUpdateable, IRenderable {

    public enum State {
        SPAWN, IDLE, RUN, DASH, HURT, FALL, DIE
    }

    private PlayerSkin skin = PlayerSkin.KNIGHT_01;
    private State state = State.SPAWN;
    private boolean facingRight = true;
    private final PlayerHitbox hitbox;

    // --- Kho vũ khí ---
    private Weapon currentWeapon;
    private final List<Weapon> weaponArsenal = new ArrayList<>();
    private int currentWeaponIndex = 0;

    // --- Chỉ số nhân vật ---
    private int maxArmor = 5;
    private int armor = 5;
    private int maxMana = 200;
    private int mana = 200;

    private static final long ARMOR_REGEN_DELAY_MS = 3000;
    private static final long ARMOR_REGEN_INTERVAL_MS = 1200;
    private long lastDamageTimeMs = 0;
    private long lastArmorRegenTimeMs = 0;

    // --- Di chuyển & Dash ---
    private final double moveSpeed = 85.0;
    private final double dashSpeed = 240.0;
    private final long dashDurationMs = 190;
    private final long dashCooldownMs = 1200;
    private static final int DASH_MANA_COST = 15;

    private boolean dashing = false;
    private boolean weaponDashing = false;
    private long dashStartTimeMs = 0;
    private long lastDashTimeMs = -dashCooldownMs;
    private double dashDirX = 1.0;
    private double dashDirY = 0.0;

    // --- Đẩy lùi khi trúng bẫy (Knockback) ---
    private static final long KNOCKBACK_DURATION_MS = 170;
    private static final double KNOCKBACK_SPEED = 175.0;
    private long knockbackStartTimeMs = 0;
    private double knockbackDirX = 0.0;
    private double knockbackDirY = 0.0;

    // --- Hố bẫy (Pit) & Bẫy sàn (Trap) ---
    private List<Rectangle2D> mapPits = Collections.emptyList();
    private double lastSafeX = 0;
    private double lastSafeY = 0;
    private double fallStartX = 0;
    private double fallStartY = 0;
    private long fallStartTimeMs = 0;
    private static final long FALL_DURATION_MS = 560;
    private static final int PIT_DAMAGE = 1;
    private long trapInvulnUntilMs = 0;
    private static final long TRAP_INVULN_MS = 650;

    // --- Thông báo ngắn trên HUD ---
    private String lastActionNotice = "";
    private long actionNoticeUntilMs = 0;

    // --- Thời gian hoạt ảnh ---
    private long spawnStartTimeMs = 0;
    private long spawnDurationMs = 510;
    private long hurtStartTimeMs = 0;
    private static final long HURT_DURATION_MS = 420;
    private long dieStartTimeMs = 0;
    private long dieHoldDelayMs = 390;

    private double aimDirX = 1.0;
    private double aimDirY = 0.0;
    private static final double AUTO_AIM_RANGE = 240.0;
    private boolean autoAimEnabled = true;
    private ICombatEntity autoAimTarget = null;

    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean dashKeyLocked = false;
    private boolean attackKeyLocked = false;
    private boolean skillKeyLocked = false;
    private boolean upgradeKeyLocked = false;
    private boolean weaponSwitchKeyLocked = false;
    private long lastUpdateNano = System.nanoTime();

    public Player(double x, double y, PlayerSkin initialSkin) {
        this(x, y, initialSkin, null);
    }

    public Player(double x, double y, PlayerSkin initialSkin, List<Weapon> initialWeapons) {
        super(initialSkin.getId());
        this.skin = initialSkin;
        this.hitbox = new PlayerHitbox(this);
        this.setTeam(1);

        if (initialWeapons != null && !initialWeapons.isEmpty()) {
            this.weaponArsenal.addAll(initialWeapons);
        } else {
            this.weaponArsenal.add(new Sword(1));
            this.weaponArsenal.add(new Spear(1));
            this.weaponArsenal.add(new Bow(1));
            this.weaponArsenal.add(new Staff(1));
        }
        this.currentWeaponIndex = 0;
        this.currentWeapon = this.weaponArsenal.get(0);

        applySkinMetrics();
        this.setLocation(x, y);
        recordSafePosition();
        triggerSpawn();
    }

    @Override
    protected PlayerAnimationController createAnimationController() {
        return new PlayerAnimationController(this);
    }

    private void applySkinMetrics() {
        if (this.animations() instanceof PlayerAnimationController ctrl) {
            int fw = ctrl.getFrameWidth(skin);
            int fh = ctrl.getFrameHeight(skin);
            hitbox.configureForFrame(fw, fh);
            spawnDurationMs = ctrl.getSpawnDurationMs(skin);
            dieHoldDelayMs = ctrl.getDieHoldDelayMs(skin);
        }
    }

    public void setMapPits(List<Rectangle2D> pits) {
        this.mapPits = (pits != null) ? pits : Collections.emptyList();
    }

    public void recordSafePosition() {
        this.lastSafeX = getX();
        this.lastSafeY = getY();
    }

    public void setWeaponDashing(boolean active) {
        this.weaponDashing = active;
        if (active) {
            this.dashing = false;
            this.knockbackStartTimeMs = 0;
        }
    }

    public boolean isWeaponDashing() {
        return weaponDashing;
    }

    public void moveByWeaponDash(double dx, double dy) {
        if (dx > 0.05) facingRight = true;
        else if (dx < -0.05) facingRight = false;
        moveByPhysics(dx, dy);
    }

    private boolean isKnockedBack(long nowMs) {
        return (nowMs - knockbackStartTimeMs) < KNOCKBACK_DURATION_MS;
    }

    // ==================== XỬ LÝ PHÍM BẤM ====================

    public void handleKeyPressed(int code) {
        pressedKeys.add(code);

        if (code == KeyEvent.VK_R && isDead()) {
            revive();
            return;
        }

        if (isDead() || state == State.SPAWN || state == State.FALL || weaponDashing) return;

        if ((code == KeyEvent.VK_K || code == KeyEvent.VK_SPACE)
                && !dashKeyLocked && !isKnockedBack(System.currentTimeMillis())) {
            dashKeyLocked = true;
            tryDash();
        }

        if (code == KeyEvent.VK_J && !attackKeyLocked) {
            attackKeyLocked = true;
            tryBasicAttack();
        }

        if (code == KeyEvent.VK_U && !skillKeyLocked) {
            skillKeyLocked = true;
            tryWeaponSkill();
        }

        if (code == KeyEvent.VK_V && !upgradeKeyLocked) {
            upgradeKeyLocked = true;
            if (currentWeapon != null) {
                currentWeapon.cycleLevel();
                showNotice("Da doi sang: " + currentWeapon.getName());
            }
        }

        if (code == KeyEvent.VK_Q && !weaponSwitchKeyLocked) {
            weaponSwitchKeyLocked = true;
            switchToNextWeapon();
        }

        if (code == KeyEvent.VK_1) {
            equipWeaponByIndex(0);
        }

        if (code == KeyEvent.VK_2) {
            equipWeaponByIndex(1);
        }
    }

    public void handleKeyReleased(int code) {
        pressedKeys.remove(code);
        if (code == KeyEvent.VK_K || code == KeyEvent.VK_SPACE) dashKeyLocked = false;
        if (code == KeyEvent.VK_J) attackKeyLocked = false;
        if (code == KeyEvent.VK_U) skillKeyLocked = false;
        if (code == KeyEvent.VK_V) upgradeKeyLocked = false;
        if (code == KeyEvent.VK_Q) weaponSwitchKeyLocked = false;
    }

    public void clearPressedKeys() {
        pressedKeys.clear();
        weaponDashing = false;
        dashKeyLocked = false;
        attackKeyLocked = false;
        skillKeyLocked = false;
        upgradeKeyLocked = false;
        weaponSwitchKeyLocked = false;
    }

    // ==================== VÒNG LẶP CẬP NHẬT & VẼ ====================

    @Override
    public void update() {
        long nowNano = System.nanoTime();
        double dt = Math.min(0.05, (nowNano - lastUpdateNano) / 1_000_000_000.0);
        lastUpdateNano = nowNano;

        long nowMs = System.currentTimeMillis();

        if (isDead()) {
            dashing = false;
            weaponDashing = false;
            knockbackStartTimeMs = 0;
            if (currentWeapon != null) {
                currentWeapon.resetState();
            }
            if (state != State.DIE) {
                dieStartTimeMs = nowMs;
                setState(State.DIE);
            } else {
                syncAnimation(false);
            }
            return;
        }

        updateArmorRegen(nowMs);

        if (state == State.FALL) {
            syncAnimation(false);
            if (nowMs - fallStartTimeMs >= FALL_DURATION_MS) {
                finishPitFall();
            }
            return;
        }

        if (state == State.SPAWN) {
            if (nowMs - spawnStartTimeMs >= Math.max(100L, spawnDurationMs - 20L)) {
                setState(State.IDLE);
            }
            return;
        }

        boolean knockedBack = isKnockedBack(nowMs);

        if (!dashing && !weaponDashing && !knockedBack && isStandingSafelyOnGround()) {
            recordSafePosition();
        }

        boolean moving = false;
        if (weaponDashing) {
            moving = true;
        } else if (knockedBack) {
            double progress = Math.min(1.0, (nowMs - knockbackStartTimeMs) / (double) KNOCKBACK_DURATION_MS);
            double currentSpeed = KNOCKBACK_SPEED * (1.0 - progress);
            moveByPhysics(knockbackDirX * currentSpeed * dt, knockbackDirY * currentSpeed * dt);
        } else if (dashing) {
            if (nowMs - dashStartTimeMs >= dashDurationMs) {
                dashing = false;
            } else {
                moveByPhysics(dashDirX * dashSpeed * dt, dashDirY * dashSpeed * dt);
                moving = true;
            }
        } else {
            double dx = 0;
            double dy = 0;
            if (pressedKeys.contains(KeyEvent.VK_A)) dx -= 1;
            if (pressedKeys.contains(KeyEvent.VK_D)) dx += 1;
            if (pressedKeys.contains(KeyEvent.VK_W)) dy -= 1;
            if (pressedKeys.contains(KeyEvent.VK_S)) dy += 1;

            if (dx != 0 || dy != 0) {
                double len = Math.hypot(dx, dy);
                dx /= len;
                dy /= len;

                moveByPhysics(dx * moveSpeed * dt, dy * moveSpeed * dt);
                moving = true;

                if (dx > 0) facingRight = true;
                else if (dx < 0) facingRight = false;

                aimDirX = dx;
                aimDirY = dy;
            } else {
                aimDirX = facingRight ? 1.0 : -1.0;
                aimDirY = 0.0;
            }
        }

        updateAutoAim();

        if (!dashing && !weaponDashing && isFootCenterInPit()) {
            startPitFall();
            return;
        }

        boolean hurting = (nowMs - hurtStartTimeMs) < HURT_DURATION_MS;
        if (dashing || weaponDashing) {
            setState(State.DASH);
        } else if (hurting) {
            setState(State.HURT);
        } else if (moving) {
            setState(State.RUN);
        } else {
            setState(State.IDLE);
        }

        if (currentWeapon != null) {
            currentWeapon.update(this);
        }

        syncAnimation(false);
    }

    private boolean isStandingSafelyOnGround() {
        if (mapPits.isEmpty()) return true;
        Rectangle2D foot = hitbox.getFootBox();
        if (foot == null) return false;

        Rectangle2D padded = new Rectangle2D.Double(
                foot.getX() - 2.5,
                foot.getY() - 2.5,
                foot.getWidth() + 5.0,
                foot.getHeight() + 5.0
        );
        for (Rectangle2D pit : mapPits) {
            if (pit.intersects(padded)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFootCenterInPit() {
        if (mapPits.isEmpty()) return false;
        Rectangle2D foot = hitbox.getFootBox();
        if (foot == null) return false;
        double cx = foot.getCenterX();
        double cy = foot.getCenterY();
        for (Rectangle2D pit : mapPits) {
            if (pit.contains(cx, cy)) {
                return true;
            }
        }
        return false;
    }

    private void startPitFall() {
        this.dashing = false;
        this.weaponDashing = false;
        this.knockbackStartTimeMs = 0;
        if (currentWeapon != null) {
            currentWeapon.resetState();
        }
        this.fallStartX = getX();
        this.fallStartY = getY();
        this.fallStartTimeMs = System.currentTimeMillis();
        this.state = State.FALL;
        syncAnimation(false);
    }

    private void finishPitFall() {
        this.setLocation(lastSafeX, lastSafeY);

        long now = System.currentTimeMillis();
        trapInvulnUntilMs = now + 1000L;

        int[] lost = applyDamageArmorFirst(PIT_DAMAGE, now);

        if (isDead()) {
            dieStartTimeMs = now;
            setState(State.DIE);
        } else {
            if (lost[0] > 0 && lost[1] > 0) {
                showNotice("Roi xuong ho! -" + lost[0] + " Giap, -" + lost[1] + " HP");
            } else if (lost[0] > 0) {
                showNotice("Roi xuong ho! -" + lost[0] + " Giap");
            } else {
                showNotice("Roi xuong ho! -" + lost[1] + " HP");
            }
            triggerSpawn();
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (state == State.FALL) {
            renderFallingEffect(g);
            return;
        }
        if (!isDead() && state != State.SPAWN) {
            renderAutoAimReticle(g);
            if (currentWeapon != null) {
                currentWeapon.renderInHand(g, this);
            }
        }
    }

    private void renderAutoAimReticle(Graphics2D g) {
        if (!autoAimEnabled || autoAimTarget == null || autoAimTarget.isDead() || Game.world().camera() == null) {
            return;
        }
        Rectangle2D tb = autoAimTarget.getBoundingBox();
        if (tb == null || tb.getWidth() <= 0 || tb.getHeight() <= 0) return;

        double pad = 2.0;
        Point2D tl = Game.world().camera().getViewportLocation(new Point2D.Double(tb.getX() - pad, tb.getY() - pad));
        double w = tb.getWidth() + pad * 2.0;
        double h = tb.getHeight() + pad * 2.0;
        double corner = Math.max(2.5, Math.min(4.5, Math.min(w, h) * 0.26));

        int x1 = (int) Math.round(tl.getX());
        int y1 = (int) Math.round(tl.getY());
        int x2 = (int) Math.round(tl.getX() + w);
        int y2 = (int) Math.round(tl.getY() + h);
        int c = (int) Math.round(corner);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255, 75, 75, 225));
        g2.setStroke(new BasicStroke(1.2f));
        // 4 góc khung khóa mục tiêu
        g2.drawLine(x1, y1, x1 + c, y1);
        g2.drawLine(x1, y1, x1, y1 + c);
        g2.drawLine(x2, y1, x2 - c, y1);
        g2.drawLine(x2, y1, x2, y1 + c);
        g2.drawLine(x1, y2, x1 + c, y2);
        g2.drawLine(x1, y2, x1, y2 - c);
        g2.drawLine(x2, y2, x2 - c, y2);
        g2.drawLine(x2, y2, x2, y2 - c);
        g2.dispose();
    }

    private void renderFallingEffect(Graphics2D g) {
        if (Game.world().camera() == null) return;

        double t = Math.min(1.0, Math.max(0.0,
                (System.currentTimeMillis() - fallStartTimeMs) / (double) FALL_DURATION_MS));
        double easeIn = t * t;

        double cx = fallStartX + getWidth() / 2.0;
        double cy = fallStartY + getHeight() / 2.0 + easeIn * 10.0;
        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(cx, cy));

        double scale = Math.max(0.10, 1.0 - 0.88 * easeIn);
        double rotRad = Math.toRadians((facingRight ? 340.0 : -340.0) * t);
        float alpha = (float) Math.max(0.0, 1.0 - t * 0.95);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        Point2D rimVp = Game.world().camera().getViewportLocation(
                new Point2D.Double(fallStartX + getWidth() / 2.0, fallStartY + getHeight() * 0.85));
        float rimAlpha = (float) Math.max(0.0, 0.55 * (1.0 - t));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rimAlpha));
        g2.setColor(new Color(8, 6, 12));
        double rW = 12.0 * (1.0 + t * 0.5);
        double rH = 6.0 * (1.0 + t * 0.5);
        g2.fillOval((int) Math.round(rimVp.getX() - rW / 2.0), (int) Math.round(rimVp.getY() - rH / 2.0),
                (int) Math.round(rW), (int) Math.round(rH));

        BufferedImage sprite = (this.animations() instanceof PlayerAnimationController ctrl)
                ? ctrl.getFallSprite(skin, facingRight)
                : null;
        if (sprite != null && alpha > 0.01f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.translate(vp.getX(), vp.getY());
            g2.rotate(rotRad);
            g2.scale(scale, scale);
            int dw = (int) Math.round(getWidth());
            int dh = (int) Math.round(getHeight());
            g2.drawImage(sprite, -dw / 2, -dh / 2, dw, dh, null);
        }

        g2.dispose();
    }

    private void updateArmorRegen(long nowMs) {
        if (armor >= maxArmor) return;
        if (nowMs - lastDamageTimeMs < ARMOR_REGEN_DELAY_MS) return;

        if (nowMs - lastArmorRegenTimeMs >= ARMOR_REGEN_INTERVAL_MS) {
            armor = Math.min(maxArmor, armor + 1);
            lastArmorRegenTimeMs = nowMs;
        }
    }

    private void moveByPhysics(double dx, double dy) {
        if (dx != 0) {
            Game.physics().move(this, new Point2D.Double(getX() + dx, getY()));
        }
        if (dy != 0) {
            Game.physics().move(this, new Point2D.Double(getX(), getY() + dy));
        }
    }

    // ==================== HÀNH ĐỘNG & CHỈ SỐ ====================

    private void tryDash() {
        long now = System.currentTimeMillis();
        if (dashing || weaponDashing || (now - lastDashTimeMs < dashCooldownMs)) return;

        if (!consumeMana(DASH_MANA_COST)) {
            showNotice("Khong du Mana de Dash! (Can " + DASH_MANA_COST + ")");
            return;
        }

        double dx = 0;
        double dy = 0;
        if (pressedKeys.contains(KeyEvent.VK_A)) dx -= 1;
        if (pressedKeys.contains(KeyEvent.VK_D)) dx += 1;
        if (pressedKeys.contains(KeyEvent.VK_W)) dy -= 1;
        if (pressedKeys.contains(KeyEvent.VK_S)) dy += 1;

        if (dx != 0 || dy != 0) {
            double len = Math.hypot(dx, dy);
            dashDirX = dx / len;
            dashDirY = dy / len;
            if (dx > 0) facingRight = true;
            else if (dx < 0) facingRight = false;
        } else {
            dashDirX = facingRight ? 1.0 : -1.0;
            dashDirY = 0.0;
        }

        dashing = true;
        dashStartTimeMs = now;
        lastDashTimeMs = now;
        setState(State.DASH);
    }

    public void tryBasicAttack() {
        if (currentWeapon == null) return;
        updateAutoAim();
        currentWeapon.attack(this);
    }

    public void tryWeaponSkill() {
        if (currentWeapon == null) return;
        updateAutoAim();
        String err = currentWeapon.useSkill(this);
        if (!err.isEmpty()) {
            showNotice(err);
        }
    }

    private void updateAutoAim() {
        if (!autoAimEnabled || isDead() || state == State.SPAWN || state == State.FALL) {
            autoAimTarget = null;
            return;
        }
        ICombatEntity best = findAutoAimTarget();
        autoAimTarget = best;
        if (best == null) return;

        double px = getX() + getWidth() / 2.0;
        double py = getY() + getHeight() * 0.62;
        Point2D tc = best.getCenter();
        double dx = tc.getX() - px;
        double dy = tc.getY() - py;
        double len = Math.hypot(dx, dy);
        if (len > 1e-4) {
            aimDirX = dx / len;
            aimDirY = dy / len;
            if (Math.abs(aimDirX) > 0.05) {
                facingRight = aimDirX > 0;
            }
        }
    }

    private ICombatEntity findAutoAimTarget() {
        Environment env = Game.world().environment();
        if (env == null) return null;

        double px = getX() + getWidth() / 2.0;
        double py = getY() + getHeight() * 0.62;

        ICombatEntity bestVisible = null;
        double bestVisibleDist = AUTO_AIM_RANGE;
        ICombatEntity bestAny = null;
        double bestAnyDist = AUTO_AIM_RANGE;

        for (ICombatEntity entity : env.getCombatEntities()) {
            if (entity == null || entity == this || entity.isDead()) continue;
            if (entity.getTeam() == this.getTeam()) continue;

            Point2D tc = entity.getCenter();
            double dist = Math.hypot(tc.getX() - px, tc.getY() - py);
            if (dist > AUTO_AIM_RANGE) continue;

            if (WeaponCollision.hasLineOfSight(env, px, py, tc.getX(), tc.getY(), this)) {
                if (dist < bestVisibleDist) {
                    bestVisibleDist = dist;
                    bestVisible = entity;
                }
            } else if (dist < bestAnyDist) {
                bestAnyDist = dist;
                bestAny = entity;
            }
        }
        return (bestVisible != null) ? bestVisible : bestAny;
    }

    public boolean consumeMana(int amount) {
        if (amount <= 0) return true;
        if (mana < amount) return false;
        mana -= amount;
        return true;
    }

    public void restoreMana(int amount) {
        if (amount <= 0) return;
        mana = Math.min(maxMana, mana + amount);
    }

    public int heal(int amount) {
        if (amount <= 0 || isDead()) return 0;
        int before = getHp();
        int max = getMaxHp();
        if (before >= max) return 0;
        int next = Math.min(max, before + amount);
        getHitPoints().modify(new AttributeModifier<>(Modification.SET, next));
        return Math.max(0, getHp() - before);
    }

    public boolean isUntouchableByTrap() {
        return isDead() || dashing || weaponDashing || state == State.SPAWN || state == State.FALL
                || System.currentTimeMillis() < trapInvulnUntilMs;
    }

    public void hitByTrap(int damage) {
        hitByTrap(damage, getX() + getWidth() / 2.0, getY() + getHeight() * 0.85);
    }

    public void hitByTrap(int damage, double trapCenterX, double trapCenterY) {
        if (isUntouchableByTrap()) return;

        long now = System.currentTimeMillis();
        trapInvulnUntilMs = now + TRAP_INVULN_MS;
        hit(damage);

        if (!isDead()) {
            applyKnockbackFrom(trapCenterX, trapCenterY, now);
        }
    }

    private void applyKnockbackFrom(double sourceX, double sourceY, long nowMs) {
        Rectangle2D foot = hitbox.getFootBox();
        double px = (foot != null) ? foot.getCenterX() : (getX() + getWidth() / 2.0);
        double py = (foot != null) ? foot.getCenterY() : (getY() + getHeight() * 0.85);

        double dx = px - sourceX;
        double dy = py - sourceY;
        double len = Math.hypot(dx, dy);

        if (len < 2.5) {
            double aimLen = Math.hypot(aimDirX, aimDirY);
            if (aimLen > 0.01) {
                knockbackDirX = -aimDirX / aimLen;
                knockbackDirY = -aimDirY / aimLen;
            } else {
                knockbackDirX = facingRight ? -1.0 : 1.0;
                knockbackDirY = 0.0;
            }
        } else {
            knockbackDirX = dx / len;
            knockbackDirY = dy / len;
        }

        dashing = false;
        knockbackStartTimeMs = nowMs;
    }

    private int[] applyDamageArmorFirst(int damage, long now) {
        lastDamageTimeMs = now;
        lastArmorRegenTimeMs = now;

        int armorLost = 0;
        int hpLost = 0;
        int remain = damage;

        if (armor > 0) {
            armorLost = Math.min(armor, remain);
            armor -= armorLost;
            remain -= armorLost;
        }

        if (remain > 0) {
            hpLost = remain;
            super.hit(remain);
        }

        return new int[] { armorLost, hpLost };
    }

    @Override
    public void hit(int damage) {
        if (isDead() || dashing || weaponDashing || state == State.SPAWN || state == State.FALL || damage <= 0) return;

        long now = System.currentTimeMillis();
        if (now - hurtStartTimeMs < HURT_DURATION_MS) return;
        applyDamageArmorFirst(damage, now);

        if (isDead()) {
            dashing = false;
            weaponDashing = false;
            knockbackStartTimeMs = 0;
            if (currentWeapon != null) {
                currentWeapon.resetState();
            }
            dieStartTimeMs = now;
            setState(State.DIE);
        } else {
            hurtStartTimeMs = now;
            this.state = State.HURT;
            syncAnimation(true);
        }
    }

    public void triggerSpawn() {
        this.spawnStartTimeMs = System.currentTimeMillis();
        this.dashing = false;
        this.weaponDashing = false;
        this.knockbackStartTimeMs = 0;
        if (currentWeapon != null) {
            currentWeapon.resetState();
        }
        this.lastUpdateNano = System.nanoTime();
        this.state = State.SPAWN;
        syncAnimation(true);
    }

    public void revive() {
        this.resurrect();
        this.armor = maxArmor;
        this.mana = maxMana;
        this.hurtStartTimeMs = 0;
        this.dieStartTimeMs = 0;
        this.knockbackStartTimeMs = 0;
        this.weaponDashing = false;
        this.setLocation(lastSafeX, lastSafeY);
        triggerSpawn();
    }

    public void switchToNextWeapon() {
        if (weaponArsenal.size() <= 1) return;
        equipWeaponByIndex((currentWeaponIndex + 1) % weaponArsenal.size());
    }

    public void equipWeaponByIndex(int index) {
        if (index < 0 || index >= weaponArsenal.size() || index == currentWeaponIndex) return;
        if (currentWeapon != null) {
            currentWeapon.resetState();
        }
        weaponDashing = false;
        currentWeaponIndex = index;
        currentWeapon = weaponArsenal.get(index);
        showNotice("Da doi vu khi: " + currentWeapon.getName());
    }

    private void setState(State next) {
        boolean changed = (this.state != next);
        this.state = next;

        boolean shouldRestart = changed
                && (next == State.SPAWN || next == State.DIE || next == State.HURT || next == State.DASH);
        syncAnimation(shouldRestart);
    }

    private void syncAnimation(boolean forceRestart) {
        if (this.animations() == null) return;

        String target = getCurrentAnimationName();
        Animation current = this.animations().getCurrent();
        if (current == null || !target.equalsIgnoreCase(current.getName())) {
            this.animations().play(target);
            current = this.animations().get(target);
        }
        if (forceRestart && current != null) {
            current.restart();
        }
    }

    public void showNotice(String text) {
        this.lastActionNotice = text;
        this.actionNoticeUntilMs = System.currentTimeMillis() + 1400;
    }

    public String getCurrentAnimationName() {
        PlayerSkin activeSkin = (this.skin != null) ? this.skin : PlayerSkin.KNIGHT_01;
        State activeState = (this.state != null) ? this.state : State.IDLE;

        if (activeState == State.FALL) {
            return PlayerAnimationController.EMPTY_ANIM_KEY;
        }

        String stateKey = switch (activeState) {
            case SPAWN -> "spawn";
            case IDLE -> "stand";
            case RUN -> "run";
            case DASH -> "dash";
            case HURT -> "hurt";
            case DIE -> (System.currentTimeMillis() - dieStartTimeMs >= dieHoldDelayMs) ? "dead" : "die";
            case FALL -> "stand";
        };
        String dir = facingRight ? "right" : "left";
        return activeSkin.getId() + "-" + stateKey + "-" + dir;
    }

    // ==================== GETTERS & SETTERS ====================

    public Weapon getCurrentWeapon() { return currentWeapon; }

    public void setCurrentWeapon(Weapon weapon) {
        if (this.currentWeapon != null) {
            this.currentWeapon.resetState();
        }
        this.currentWeapon = weapon;
    }

    public List<Weapon> getWeaponArsenal() { return Collections.unmodifiableList(weaponArsenal); }
    public int getCurrentWeaponIndex() { return currentWeaponIndex; }
    public PlayerHitbox getHitbox() { return hitbox; }
    public PlayerSkin getSkin() { return skin; }
    public State getState() { return state; }
    public int getHp() { return getHitPoints().getModifiedValue(); }
    public int getMaxHp() { return getHitPoints().getMax(); }
    public int getArmor() { return armor; }
    public int getMaxArmor() { return maxArmor; }
    public int getMana() { return mana; }
    public int getMaxMana() { return maxMana; }
    public boolean isFacingRight() { return facingRight; }
    public double getAimDirX() { return aimDirX; }
    public double getAimDirY() { return aimDirY; }
    public ICombatEntity getAutoAimTarget() { return autoAimTarget; }
    public boolean isAutoAimEnabled() { return autoAimEnabled; }
    public void setAutoAimEnabled(boolean autoAimEnabled) { this.autoAimEnabled = autoAimEnabled; }

    public long getDashCooldownRemainingMs() {
        return Math.max(0, dashCooldownMs - (System.currentTimeMillis() - lastDashTimeMs));
    }

    public String getActiveNotice() {
        return (System.currentTimeMillis() <= actionNoticeUntilMs) ? lastActionNotice : "";
    }
}