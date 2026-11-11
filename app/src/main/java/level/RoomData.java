package level;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dữ liệu của một phòng, đọc thẳng từ file .tmx (chỉ dùng Java chuẩn, không phụ thuộc
 * phiên bản LITIengine). Lấy từ các object trong object layer, theo TÊN object:
 *
 *   collision     hình chữ nhật cản đường (tường, trụ)
 *   spawn_enemy   điểm sinh quái; thuộc tính tùy chọn "monster" = tên MonsterType (vd ORC_WARRIOR)
 *   spawn_player  điểm sinh người chơi
 *   door_*        vùng cửa (door_north, door_west, ...)
 *
 * RoomData.current() là phòng đang chơi; quái dùng nó để biết đâu là tường.
 */
public final class RoomData {

    /** Một điểm sinh quái. monster có thể null (không khai báo trong Tiled). */
    public static final class Spawn {
        public final double x;
        public final double y;
        public final String monster;

        public final String variant;   // "elite" hoặc null

        Spawn(double x, double y, String monster, String variant) {
            this.x = x;
            this.y = y;
            this.monster = monster;
            this.variant = variant;
        }
    }

    private static RoomData current;

    public static RoomData current() {
        return current;
    }

    public static void setCurrent(RoomData room) {
        current = room;
    }

    private final double widthPx;
    private final double heightPx;
    private final List<Rectangle2D> collisions = new ArrayList<>();
    private final List<Spawn> enemySpawns = new ArrayList<>();
    private final Map<String, Rectangle2D> doors = new LinkedHashMap<>();
    private Point2D playerSpawn;

    /** Đọc map từ classpath, ví dụ load("/maps/Map_01.tmx"). */
    public static RoomData load(String resourcePath) {
        try (InputStream in = RoomData.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Khong tim thay " + resourcePath);
            }
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            return new RoomData(doc.getDocumentElement());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Loi doc map " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    private RoomData(Element map) {
        widthPx = num(map, "width") * num(map, "tilewidth");
        heightPx = num(map, "height") * num(map, "tileheight");

        NodeList objects = map.getElementsByTagName("object");
        for (int i = 0; i < objects.getLength(); i++) {
            Element o = (Element) objects.item(i);
            String name = o.getAttribute("name");
            double x = num(o, "x");
            double y = num(o, "y");
            double w = num(o, "width");
            double h = num(o, "height");

            if (name.equals("collision")) {
                if (w > 0 && h > 0) {
                    collisions.add(new Rectangle2D.Double(x, y, w, h));
                }
            } else if (name.equals("spawn_enemy")) {
                Map<String, String> props = properties(o);
                enemySpawns.add(new Spawn(x, y, props.get("monster"), props.get("variant")));
            } else if (name.equals("spawn_player")) {
                playerSpawn = new Point2D.Double(x, y);
            } else if (name.startsWith("door")) {
                doors.put(name, new Rectangle2D.Double(x, y, w, h));
            }
        }
    }

    private static double num(Element e, String attr) {
        String v = e.getAttribute(attr);
        return v.isEmpty() ? 0 : Double.parseDouble(v);
    }

    private static Map<String, String> properties(Element object) {
        Map<String, String> props = new HashMap<>();
        NodeList list = object.getElementsByTagName("property");
        for (int i = 0; i < list.getLength(); i++) {
            Element p = (Element) list.item(i);
            props.put(p.getAttribute("name"), p.getAttribute("value"));
        }
        return props;
    }

    // ------------------------------------------------------------------

    /** true nếu hình chữ nhật r chạm tường hoặc nằm ngoài map. */
    public boolean blocks(Rectangle2D r) {
        if (r.getMinX() < 0 || r.getMinY() < 0 || r.getMaxX() > widthPx || r.getMaxY() > heightPx) {
            return true;
        }
        for (Rectangle2D c : collisions) {
            if (c.intersects(r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra một hộp w x h (tâm di chuyển từ (x1, y1) tới (x2, y2)) có đi thẳng được không,
     * tức là suốt đoạn đường không chạm tường. Lấy mẫu mỗi ~3px.
     * Dùng hộp lớn (kích thước chân quái) để kiểm tra đi lại, hộp nhỏ (2x2) để kiểm tra tầm nhìn/đánh.
     */
    public boolean isPathClear(double x1, double y1, double x2, double y2, double w, double h) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        int steps = (int) Math.ceil(Math.hypot(dx, dy) / 3.0);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            double cx = x1 + dx * t;
            double cy = y1 + dy * t;
            if (blocks(new Rectangle2D.Double(cx - w / 2, cy - h / 2, w, h))) {
                return false;
            }
        }
        return true;
    }

    public double getWidthPx() { return widthPx; }
    public double getHeightPx() { return heightPx; }
    public List<Rectangle2D> getCollisions() { return Collections.unmodifiableList(collisions); }
    public List<Spawn> getEnemySpawns() { return Collections.unmodifiableList(enemySpawns); }
    public Point2D getPlayerSpawn() { return playerSpawn; }
    public Rectangle2D getDoor(String name) { return doors.get(name); }
    public Map<String, Rectangle2D> getDoors() { return Collections.unmodifiableMap(doors); }
}