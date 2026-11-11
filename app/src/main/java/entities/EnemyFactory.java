package entities;

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