package weapons;

import de.gurkenlabs.litiengine.entities.CollisionBox;
import de.gurkenlabs.litiengine.entities.ICollisionEntity;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.entities.IEntity;
import de.gurkenlabs.litiengine.environment.Environment;
import player.Player;

import java.awt.geom.Rectangle2D;

public final class WeaponCollision {

    private WeaponCollision() {
    }

    /** Kiểm tra vùng chữ nhật có chạm vào bất kỳ vật cản rắn nào trong map hay không */
    public static boolean isBlocked(Environment env, Rectangle2D area, Player owner) {
        if (env == null || area == null) return false;

        // 1. Toàn bộ CollisionBox trên map (tường, cột đá, cửa đang đóng...)
        for (CollisionBox box : env.getCollisionBoxes()) {
            if (box == null || !box.hasCollision()) continue;
            Rectangle2D r = box.getCollisionBox();
            if (r == null || r.getWidth() <= 0 || r.getHeight() <= 0) {
                r = box.getBoundingBox();
            }
            if (r != null && r.intersects(area)) {
                return true;
            }
        }

        // 2. Các thực thể có va chạm khác (rương, thùng gỗ, vật thể trang trí rắn...; bỏ qua quái & người chơi)
        for (IEntity raw : env.getEntities()) {
            if (!(raw instanceof ICollisionEntity entity)) continue;
            if (entity == owner || !entity.hasCollision()) continue;
            if (entity instanceof CollisionBox) continue;
            if (entity instanceof ICombatEntity) continue;

            Rectangle2D r = entity.getCollisionBox();
            if (r == null || r.getWidth() <= 0 || r.getHeight() <= 0) {
                r = entity.getBoundingBox();
            }
            if (r != null && r.intersects(area)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Dò tia từ (startX, startY) theo góc angleRad tối đa maxDist.
     * Trả về khoảng cách tới mép vật cản đầu tiên (hoặc trả về maxDist nếu thông thoáng).
     */
    public static double raycastObstacleDistance(
            Environment env,
            double startX,
            double startY,
            double angleRad,
            double maxDist,
            double halfProbe,
            Player owner
    ) {
        if (env == null || maxDist <= 0) return 0.0;

        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double step = 2.0;
        double probeSize = Math.max(1.5, halfProbe * 2.0);

        for (double d = step; d <= maxDist; d += step) {
            double px = startX + cos * d;
            double py = startY + sin * d;
            Rectangle2D probe = new Rectangle2D.Double(px - halfProbe, py - halfProbe, probeSize, probeSize);
            if (isBlocked(env, probe, owner)) {
                return Math.max(0.0, d - step);
            }
        }

        // Kiểm tra điểm cuối chính xác tại maxDist
        double endX = startX + cos * maxDist;
        double endY = startY + sin * maxDist;
        Rectangle2D endProbe = new Rectangle2D.Double(endX - halfProbe, endY - halfProbe, probeSize, probeSize);
        if (isBlocked(env, endProbe, owner)) {
            return Math.max(0.0, maxDist - step);
        }

        return maxDist;
    }

    /** Kiểm tra đường thẳng từ (x1, y1) đến (x2, y2) có bị vật cản chặn ngang hay không */
    public static boolean hasLineOfSight(
            Environment env,
            double x1,
            double y1,
            double x2,
            double y2,
            Player owner
    ) {
        double dist = Math.hypot(x2 - x1, y2 - y1);
        if (dist <= 2.0) return true;
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double clear = raycastObstacleDistance(env, x1, y1, angle, dist, 1.5, owner);
        return clear >= dist - 2.0;
    }
}