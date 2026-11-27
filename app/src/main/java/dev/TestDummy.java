package dev;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.CollisionInfo;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.entities.EntityInfo;
import de.gurkenlabs.litiengine.input.KeyboardEntityController;
import level.RoomData;

import java.awt.geom.Rectangle2D;

/**
 * Mục tiêu giả thay cho Player khi test quái.
 * Thuộc team 1 (quái là team 2), nhiều máu, điều khiển được bằng bàn phím.
 *
 * [GĐ2] Tự chặn không cho đi xuyên tường theo RoomData (trượt dọc tường), để test được
 * mà không phụ thuộc việc map đã khai báo hộp va chạm cho engine hay chưa.
 */
@EntityInfo(width = 16, height = 23)
@CollisionInfo(collisionBoxWidth = 10, collisionBoxHeight = 8, collision = true)
public class TestDummy extends Creature implements IUpdateable {

    private static final double FOOT_W = 10;
    private static final double FOOT_H = 8;

    private double lastX;
    private double lastY;
    private boolean hasLast;

    public TestDummy() {
        super("orc_warrior");   // mượn sprite của orc cho có hình
        setTeam(1);
        setVelocity(70);
        getHitPoints().setMax(1000);
        getHitPoints().setValue(1000);
        addController(new KeyboardEntityController<>(this));  // WASD / phím mũi tên
        Game.loop().attach(this);
    }

    @Override
    public void update() {
        RoomData room = RoomData.current();
        if (room == null) {
            return;
        }
        double x = getX();
        double y = getY();
        if (!hasLast) {
            lastX = x;
            lastY = y;
            hasLast = true;
        }
        if (room.blocks(foot(x, y))) {
            if (!room.blocks(foot(x, lastY))) {          // trượt ngang dọc theo tường
                y = lastY;
            } else if (!room.blocks(foot(lastX, y))) {   // trượt dọc
                x = lastX;
            } else {
                x = lastX;
                y = lastY;
            }
            setLocation(x, y);
        }
        lastX = x;
        lastY = y;
    }

    private Rectangle2D foot(double x, double y) {
        return new Rectangle2D.Double(x + (getWidth() - FOOT_W) / 2.0, y + getHeight() - FOOT_H, FOOT_W, FOOT_H);
    }
}