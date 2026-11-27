package player;

import de.gurkenlabs.litiengine.Align;
import de.gurkenlabs.litiengine.Valign;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.environment.tilemap.IMapObject;
import de.gurkenlabs.litiengine.environment.tilemap.IMapObjectLayer;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public class PlayerHitbox {
    private final Player owner;

    public PlayerHitbox(Player owner) {
        this.owner = owner;
    }

    /** Căn kích thước CollisionBox nằm sát bàn chân nhân vật theo đúng kích thước 1 frame */
    public void configureForFrame(int frameW, int frameH) {
        double boxW = Math.max(8.0, Math.round(frameW * 0.62));
        double boxH = Math.max(5.0, Math.round(frameH * 0.30));

        owner.setWidth(frameW);
        owner.setHeight(frameH);
        owner.setCollision(true);
        owner.setCollisionBoxWidth(boxW);
        owner.setCollisionBoxHeight(boxH);
        owner.setCollisionBoxAlign(Align.CENTER);
        owner.setCollisionBoxValign(Valign.DOWN);
    }

    /** Hitbox dưới chân dùng để chặn tường, cột đá và dẫm bẫy sàn */
    public Rectangle2D getFootBox() {
        return owner.getCollisionBox();
    }

    /** Hurtbox toàn thân dùng để kiểm tra trúng đạn, nhận sát thương hoặc tương tác rương/vật phẩm */
    public Rectangle2D getBodyBox() {
        return owner.getBoundingBox();
    }

    /** Kiểm tra chân nhân vật có đang chạm vào 1 vùng chữ nhật cụ thể hay không */
    public boolean intersectsFoot(Rectangle2D area) {
        if (area == null) return false;
        return getFootBox().intersects(area);
    }

    /** Kiểm tra thân nhân vật có đang chạm vào 1 object trong Tiled Map hay không */
    public boolean intersectsObject(IMapObject obj) {
        if (obj == null) return false;
        Rectangle2D objRect = new Rectangle2D.Double(
                obj.getX(), obj.getY(),
                Math.max(1.0, obj.getWidth()),
                Math.max(1.0, obj.getHeight())
        );
        return getBodyBox().intersects(objRect);
    }

    /** Tìm tất cả các object trong bản đồ hiện tại mà nhân vật đang chạm vào (rương, bình thuốc, bẫy, cửa...) */
    public List<IMapObject> findTouchingObjects(Environment env) {
        List<IMapObject> result = new ArrayList<>();
        if (env == null || env.getMap() == null) return result;

        for (IMapObjectLayer layer : env.getMap().getMapObjectLayers()) {
            for (IMapObject obj : layer.getMapObjects()) {
                if (intersectsObject(obj)) {
                    result.add(obj);
                }
            }
        }
        return result;
    }
}