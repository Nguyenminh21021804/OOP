package entities;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bảng thông số tập trung cho toàn bộ quái trong game.
 * Muốn cân bằng game chỉ cần sửa số ở đây, không đụng vào logic AI.
 *
 * Mỗi phe có 5 quái: 2 melee, 2 ranged, 1 boss.
 * Tất cả số liệu là GIÁ TRỊ KHỞI ĐIỂM, cần chỉnh lại sau khi chơi thử.
 *
 * Đơn vị: tầm (range) tính bằng pixel, thời gian bằng mili-giây,
 * tốc độ (speed) theo đơn vị velocity của LITIengine.
 */
public enum MonsterType {

    // ------------------------------------------------------------------
    //  ORCS: sức mạnh thô, đẩy lùi
    // ------------------------------------------------------------------
    //          prefix         phe              vai          hp  dmg spd  tầm  phátHiện hồiChiêu windup strike recover lunge
    ORC_WARRIOR("orc_warrior", Faction.ORCS,    Role.MELEE,   30,  5, 60f,  28, 160, 1200, 400, 120, 300, 10),
    MASKED_ORC ("masked_orc",  Faction.ORCS,    Role.MELEE,   45,  8, 32f,  34, 150, 1800, 600, 150, 500,  0),
    GOBLIN     ("goblin",      Faction.ORCS,    Role.RANGED,  14,  3, 50f, 140, 200, 1400, 300,   0,   0,  0),
    ORC_SHAMAN ("orc_shaman",  Faction.ORCS,    Role.RANGED,  20,  7, 28f, 180, 220, 2200, 700,   0,   0,  0),
    OGRE       ("ogre",        Faction.ORCS,    Role.BOSS,   300, 15, 28f,  44, 260, 2000, 700, 150, 700,  0),

    // ------------------------------------------------------------------
    //  UNDEADS: độc, lạnh, đông đảo
    // ------------------------------------------------------------------
    ZOMBIE     ("zombie",      Faction.UNDEADS, Role.MELEE,   50,  6, 22f,  26, 140, 1500, 550, 120, 450,  0),
    TINY_ZOMBIE("tiny_zombie", Faction.UNDEADS, Role.MELEE,   12,  3, 60f,  20, 180,  900, 250, 100, 200, 12),
    SKELET     ("skelet",      Faction.UNDEADS, Role.RANGED,  18,  4, 35f, 170, 220, 1500, 350,   0,   0,  0),
    ICE_ZOMBIE ("ice_zombie",  Faction.UNDEADS, Role.RANGED,  28,  4, 25f, 150, 200, 2000, 500,   0,   0,  0),
    BIG_ZOMBIE ("big_zombie",  Faction.UNDEADS, Role.BOSS,   280, 12, 24f,  44, 260, 2000, 700, 150, 700,  0),

    // ------------------------------------------------------------------
    //  DEMONS: lửa, bùng nổ
    // ------------------------------------------------------------------
    CHORT      ("chort",       Faction.DEMONS,  Role.MELEE,   35,  8, 40f,  30, 170, 1400, 300, 120, 400, 16),
    WOGOL      ("wogol",       Faction.DEMONS,  Role.MELEE,   22,  4, 55f,  24, 170,  700, 200, 100, 150,  6),
    IMP        ("imp",         Faction.DEMONS,  Role.RANGED,  10,  3, 60f, 130, 200,  900, 250,   0,   0,  0),
    NECROMANCER("necromancer", Faction.DEMONS,  Role.RANGED,  24,  6, 30f, 200, 240, 2500, 700,   0,   0,  0),
    BIG_DEMON  ("big_demon",   Faction.DEMONS,  Role.BOSS,   260, 14, 34f,  44, 260, 1800, 600, 150, 600,  0);

    // ==================================================================

    /** Phe của quái, kèm màu chủ đạo dùng cho hiệu ứng (nháy, vệt chém, hạt). */
    public enum Faction {
        ORCS(new Color(255, 230, 140)),     // trắng vàng, bụi
        UNDEADS(new Color(110, 200, 220)),  // xanh lơ
        DEMONS(new Color(255, 110, 50));    // đỏ cam, lửa

        private final Color effectColor;

        Faction(Color effectColor) {
            this.effectColor = effectColor;
        }

        public Color getEffectColor() {
            return effectColor;
        }
    }

    public enum Role {
        MELEE, RANGED, BOSS
    }

    /** [GĐ5] Cỡ sprite (cũng là hộp thực thể) và hộp chân dùng kiểm tra tường, đơn vị pixel. */
    public enum Size {
        SMALL(16, 16, 10, 6),    // imp, goblin, skelet, các loại zombie nhỏ
        TALL(16, 23, 10, 8),     // orc, chort, wogol, necromancer
        BIG(32, 36, 22, 12);     // ogre, big_zombie, big_demon

        public final int spriteW;
        public final int spriteH;
        public final int footW;
        public final int footH;

        Size(int spriteW, int spriteH, int footW, int footH) {
            this.spriteW = spriteW;
            this.spriteH = spriteH;
            this.footW = footW;
            this.footH = footH;
        }
    }

    /** [GĐ8] Hình dạng vùng đòn. */
    public enum Shape { ARC, LINE, RING }

    /** [GĐ8] Cá tính đòn đánh và độ chịu đòn của một loại quái cận chiến. */
    public static final class Profile {
        public final Shape shape;
        public final double arcDeg;
        public final double lineWidth;
        public final double reachScale;
        public final boolean interruptible;
        public final double commitRatio;
        public final long hitStunMs;
        public final double knockbackScale;

        // [GĐ9]
        public int comboHits = 1;                 // số nhát liên tiếp trong một lượt đánh (1 = đánh đơn)
        public double comboWindupScale = 1.0;     // các nhát nối lấy đà ngắn hơn: windup nhân hệ số này
        public boolean flank = false;             // vây mục tiêu từ nhiều hướng khi còn xa

        public Profile(Shape shape, double arcDeg, double lineWidth, double reachScale,
                       boolean interruptible, double commitRatio, long hitStunMs, double knockbackScale) {
            this.shape = shape;
            this.arcDeg = arcDeg;
            this.lineWidth = lineWidth;
            this.reachScale = reachScale;
            this.interruptible = interruptible;
            this.commitRatio = commitRatio;
            this.hitStunMs = hitStunMs;
            this.knockbackScale = knockbackScale;
        }

        public Profile combo(int hits, double windupScale) {
            this.comboHits = hits;
            this.comboWindupScale = windupScale;
            return this;
        }

        public Profile flank() {
            this.flank = true;
            return this;
        }
    }

    private final String spritePrefix;
    private final Faction faction;
    private final Role role;
    private final int maxHealth;
    private final int damage;
    private final float speed;
    private final double attackRange;
    private final double detectionRange;
    private final long cooldownMs;
    private final long windupMs;
    private final long strikeMs;
    private final long recoverMs;
    private final double lungeDistance;

    MonsterType(String spritePrefix, Faction faction, Role role,
                int maxHealth, int damage, float speed,
                double attackRange, double detectionRange, long cooldownMs,
                long windupMs, long strikeMs, long recoverMs, double lungeDistance) {
        this.spritePrefix = spritePrefix;
        this.faction = faction;
        this.role = role;
        this.maxHealth = maxHealth;
        this.damage = damage;
        this.speed = speed;
        this.attackRange = attackRange;
        this.detectionRange = detectionRange;
        this.cooldownMs = cooldownMs;
        this.windupMs = windupMs;
        this.strikeMs = strikeMs;
        this.recoverMs = recoverMs;
        this.lungeDistance = lungeDistance;
    }

    // ------------------------------------------------------------------
    //  Tạo quái
    // ------------------------------------------------------------------

    /**
     * Tạo quái cận chiến theo thông số của loại này.
     * Chỉ dùng cho Role.MELEE. Ranged và Boss do lớp riêng của chúng tạo
     * (RangedEnemy, các lớp boss) và đọc thông số từ các getter bên dưới.
     */
    public MeleeEnemy createMelee() {
        return createMelee(false);
    }

    /** [GĐ10] elite: prefix có hậu tố _elite (sprite tím), máu x2, sát thương x1,5, nhanh hơn 10%, lấy đà ngắn hơn 15%. */
    public MeleeEnemy createMelee(boolean elite) {
        if (role != Role.MELEE) {
            throw new UnsupportedOperationException(
                    name() + " là " + role + ", không tạo được bằng createMelee()");
        }
        String prefix = elite ? spritePrefix + "_elite" : spritePrefix;
        int hp = elite ? maxHealth * 2 : maxHealth;
        int dmg = elite ? (int) Math.round(damage * 1.5) : damage;
        float spd = elite ? speed * 1.1f : speed;
        long windup = elite ? Math.round(windupMs * 0.85) : windupMs;

        MeleeEnemy enemy = new MeleeEnemy(prefix, hp, dmg, spd, attackRange, detectionRange, cooldownMs);
        enemy.setWindupMs(windup);
        enemy.setStrikeMs(strikeMs);
        enemy.setRecoverMs(recoverMs);
        enemy.setLungeDistance(lungeDistance);
        enemy.setType(this);
        Size size = getSize();
        enemy.setSize(size.spriteW, size.spriteH);
        enemy.setFootprint(size.footW, size.footH);
        return enemy;
    }

    // ------------------------------------------------------------------
    //  Tra cứu
    // ------------------------------------------------------------------

    /** Danh sách loại quái theo phe và vai trò, ví dụ để chọn quái spawn cho một phòng. */
    public static List<MonsterType> of(Faction faction, Role role) {
        return Arrays.stream(values())
                .filter(t -> t.faction == faction && t.role == role)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    //  Getter
    // ------------------------------------------------------------------

    public String getSpritePrefix() { return spritePrefix; }

    /** [GĐ5] Thư mục sprite của loại quái này, ví dụ "sprites/OrcWarriorFrame/" (PascalCase của tên + "Frame"). */
    public String getSpriteDir() {
        StringBuilder sb = new StringBuilder("sprites/");
        for (String part : spritePrefix.split("[_-]")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.append("Frame/").toString();
    }

    /** [GĐ10] Đường dẫn sheet; elite dùng prefix có hậu tố _elite nhưng cùng thư mục. */
    public String getSpritePath(String state, String direction, boolean elite) {
        return getSpriteDir() + (elite ? spritePrefix + "_elite" : spritePrefix) + "-" + state + "-" + direction + ".png";
    }

    /** [GĐ5] Đường dẫn classpath (bắt đầu bằng /) của ảnh silhouette dùng cho nháy trắng và tan hạt. */
    public String getFlashImagePath() {
        return "/" + getSpriteDir() + spritePrefix + "-flash.png";
    }

    /** [GĐ5] Cỡ sprite theo kích thước frame trong tileset 0x72. */
    public Size getSize() {
        switch (this) {
            case IMP: case GOBLIN: case SKELET: case ZOMBIE: case TINY_ZOMBIE: case ICE_ZOMBIE:
                return Size.SMALL;
            case OGRE: case BIG_ZOMBIE: case BIG_DEMON:
                return Size.BIG;
            default:
                return Size.TALL;
        }
    }

    /** [GĐ8] Cá tính đòn đánh theo loại quái; loại chưa liệt kê dùng bộ thông số chuẩn của orc. */
    public Profile getProfile() {
        switch (this) {
            case MASKED_ORC:  return new Profile(Shape.ARC, 200, 0, 1.15, true, 0.5, 180, 0.7);
            case ZOMBIE:      return new Profile(Shape.ARC, 90, 0, 1.0, false, 0, 100, 0.4);
            case TINY_ZOMBIE: return new Profile(Shape.LINE, 0, 10, 1.0, true, 0.75, 250, 1.2).flank();
            case CHORT:       return new Profile(Shape.LINE, 0, 12, 1.0, true, 0.6, 220, 1.0);
            case WOGOL:       return new Profile(Shape.ARC, 80, 0, 1.0, true, 0.8, 200, 1.0).combo(3, 0.7);
            default:          return new Profile(Shape.ARC, 110, 0, 1.0, true, 0.75, 250, 1.0);
        }
    }

    public Faction getFaction() { return faction; }
    public Role getRole() { return role; }
    public int getMaxHealth() { return maxHealth; }
    public int getDamage() { return damage; }
    public float getSpeed() { return speed; }
    public double getAttackRange() { return attackRange; }
    public double getDetectionRange() { return detectionRange; }
    public long getCooldownMs() { return cooldownMs; }
    public long getWindupMs() { return windupMs; }
    public long getStrikeMs() { return strikeMs; }
    public long getRecoverMs() { return recoverMs; }
    public double getLungeDistance() { return lungeDistance; }
}