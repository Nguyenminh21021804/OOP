package entities;

import entities.melee.MeleeEnemy;
import entities.ranged.*;
import entities.ranged.SkeletonEnemy;

/**
 * Tạo quái từ MonsterType. Chỗ duy nhất cần sửa khi thêm một loại quái mới.
 */
public final class EnemyFactory {

    private EnemyFactory() {
    }

    /** Đổi tên (từ thuộc tính "monster" trong Tiled) sang MonsterType; sai hoặc trống thì dùng fallback. */
    public static MonsterType typeOrDefault(String name, MonsterType fallback) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        try {
            return MonsterType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("[EnemyFactory] Ten quai khong hop le: '" + name + "', dung " + fallback);
            return fallback;
        }
    }

    /** Tạo quái theo loại: cận chiến hoặc đánh xa (boss chưa có). */
    public static Enemy create(MonsterType type, boolean elite) {
        switch (type.getRole()) {
            case MELEE:
                return createMelee(type, elite);
            case RANGED:
                return createRanged(type);
            default:
                throw new UnsupportedOperationException(type + " (" + type.getRole() + ") chưa được cài đặt");
        }
    }

    public static RangedEnemy createRanged(MonsterType type) {
        switch (type) {
            case DOC:         return new DocEnemy();
            case ICE_ZOMBIE:  return new IceZombieEnemy();
            case IMP:         return new ImpEnemy();
            case LIZARD:      return new LizardEnemy();
            case NECROMANCER: return new NecromancerEnemy();
            case ORC_SHAMAN:  return new ShamanEnemy();
            case SKELET:      return new SkeletonEnemy();
            default:
                throw new UnsupportedOperationException(type + " chưa có lớp quái đánh xa");
        }
    }

    /**
     * Tạo quái cận chiến. Loại có lớp riêng (có hiệu ứng riêng) thì dùng lớp đó,
     * còn lại dùng MeleeEnemy chung theo thông số trong MonsterType.
     *
     * @throws UnsupportedOperationException nếu loại này không phải quái cận chiến
     */
    public static MeleeEnemy createMelee(MonsterType type) {

        return type.createMelee();
    }

    public static MeleeEnemy createMelee(MonsterType type, boolean elite) {
        return type.createMelee(elite);
    }
}