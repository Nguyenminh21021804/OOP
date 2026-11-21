package entities;

import entities.ranged.DocEnemy;

/**
 * GoblinEnemy — Đã được chuyển đổi sang DocEnemy (Doc phóng dao găm).
 * Kế thừa DocEnemy để đảm bảo tính tương thích ngược với mã nguồn cũ.
 */
public class GoblinEnemy extends DocEnemy {
    public GoblinEnemy() {
        super();
    }
}
