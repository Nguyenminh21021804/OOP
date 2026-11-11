package level;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bản đồ khoảng cách tới một mục tiêu, trên lưới ô 8px của phòng. Mọi quái cùng kích thước
 * dùng chung một bản (cache theo ô đích), nên 5 hay 50 quái cũng chỉ tính một lần mỗi khi
 * mục tiêu sang ô mới.
 *
 * Mỗi ô "tự do" nếu một quái có hộp chân (footW x footH) đứng tâm ô đó không chạm tường.
 * Tất cả toạ độ ở đây là TOẠ ĐỘ TÂM HỘP CHÂN (không phải góc trên trái của thực thể).
 *
 * Cách dùng: PathField.toward(room, goalFeet, w, h).nextWaypoint(meFeet)
 */
public final class PathField {

    private static final int CELL = 8;          // cạnh ô lưới (pixel)
    private static final int LOOKAHEAD = 8;     // nhìn trước tối đa bao nhiêu ô để cắt góc
    private static final float INF = Float.MAX_VALUE;
    private static final float DIAGONAL = 1.4142f;

    private static final Map<String, PathField> CACHE = new HashMap<>();

    private final RoomData room;
    private final double footW;
    private final double footH;
    private final int cols;
    private final int rows;
    private final boolean[] free;
    private final float[] dist;
    private final int requestedGx;
    private final int requestedGy;

    /** Lấy bản đồ khoảng cách tới goalFeet (dùng lại bản cũ nếu mục tiêu vẫn ở cùng ô). */
    public static PathField toward(RoomData room, Point2D goalFeet, double footW, double footH) {
        int gx = (int) Math.floor(goalFeet.getX() / CELL);
        int gy = (int) Math.floor(goalFeet.getY() / CELL);
        String key = footW + "x" + footH + "@" + gx + "," + gy;
        PathField cached = CACHE.get(key);
        if (cached != null && cached.room == room) {
            return cached;
        }
        if (CACHE.size() > 32) {
            CACHE.clear();   // giữ gọn bộ nhớ đệm khi có nhiều đích khác nhau
        }
        PathField field = new PathField(room, footW, footH, gx, gy);
        CACHE.put(key, field);
        return field;
    }

    private PathField(RoomData room, double footW, double footH, int gx, int gy) {
        this.room = room;
        this.footW = footW;
        this.footH = footH;
        this.requestedGx = gx;
        this.requestedGy = gy;
        this.cols = (int) Math.ceil(room.getWidthPx() / CELL);
        this.rows = (int) Math.ceil(room.getHeightPx() / CELL);

        free = new boolean[cols * rows];
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                double fx = (cx + 0.5) * CELL;
                double fy = (cy + 0.5) * CELL;
                free[cy * cols + cx] = !room.blocks(
                        new Rectangle2D.Double(fx - footW / 2, fy - footH / 2, footW, footH));
            }
        }

        dist = new float[cols * rows];
        Arrays.fill(dist, INF);
        int goal = nearestFree(clampX(gx), clampY(gy), 3);   // mục tiêu sát tường thì lấy ô tự do gần nhất
        if (goal >= 0) {
            dijkstra(goal);
        }
    }

    // ------------------------------------------------------------------
    //  Tính khoảng cách
    // ------------------------------------------------------------------

    private void dijkstra(int start) {
        PriorityQueue<float[]> queue = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        dist[start] = 0;
        queue.add(new float[] {0, start});
        while (!queue.isEmpty()) {
            float[] top = queue.poll();
            int i = (int) top[1];
            if (top[0] > dist[i]) {
                continue;
            }
            int cx = i % cols;
            int cy = i / cols;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx == 0 && dy == 0) || !canStep(cx, cy, dx, dy)) {
                        continue;
                    }
                    int j = (cy + dy) * cols + (cx + dx);
                    float nd = dist[i] + (dx != 0 && dy != 0 ? DIAGONAL : 1f);
                    if (nd < dist[j]) {
                        dist[j] = nd;
                        queue.add(new float[] {nd, j});
                    }
                }
            }
        }
    }

    /** Có đi được từ ô (cx, cy) sang ô kề (cx+dx, cy+dy) không (không cắt góc tường). */
    private boolean canStep(int cx, int cy, int dx, int dy) {
        int nx = cx + dx;
        int ny = cy + dy;
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || !free[ny * cols + nx]) {
            return false;
        }
        if (dx != 0 && dy != 0) {
            return free[cy * cols + nx] && free[ny * cols + cx];
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Truy vấn
    // ------------------------------------------------------------------

    /**
     * Điểm (toạ độ tâm chân) nên đi tới để tiến gần mục tiêu theo đường ngắn nhất quanh vật cản.
     * Đi theo dốc khoảng cách rồi chọn ô xa nhất mà đường thẳng tới đó vẫn thông (cắt góc cho mượt).
     *
     * @return null nếu đã ở ô đích hoặc không có đường
     */
    public Point2D nextWaypoint(Point2D me) {
        int cx = clampX((int) Math.floor(me.getX() / CELL));
        int cy = clampY((int) Math.floor(me.getY() / CELL));
        int cur = cy * cols + cx;
        if (!free[cur] || dist[cur] == INF) {
            cur = nearestReachable(cx, cy, 2);   // đứng sát tường làm ô hiện tại bị chặn: lấy ô gần nhất
            if (cur < 0) {
                return null;
            }
        }
        if (dist[cur] == 0) {
            return null;
        }

        int best = -1;
        int walker = cur;
        for (int step = 0; step < LOOKAHEAD; step++) {
            int next = bestNeighbor(walker);
            if (next < 0) {
                break;
            }
            walker = next;
            if (best < 0) {
                best = next;   // luôn có ít nhất ô kế tiếp
            } else if (room.isPathClear(me.getX(), me.getY(), centerX(walker), centerY(walker), footW, footH)) {
                best = walker;
            }
            if (dist[walker] == 0) {
                break;
            }
        }
        return best < 0 ? null : new Point2D.Double(centerX(best), centerY(best));
    }

    /** Ô kề có khoảng cách nhỏ nhất và nhỏ hơn ô hiện tại; -1 nếu không có. */
    private int bestNeighbor(int i) {
        int cx = i % cols;
        int cy = i / cols;
        int best = -1;
        float bestDist = dist[i];
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx == 0 && dy == 0) || !canStep(cx, cy, dx, dy)) {
                    continue;
                }
                int j = (cy + dy) * cols + (cx + dx);
                if (dist[j] < bestDist) {
                    bestDist = dist[j];
                    best = j;
                }
            }
        }
        return best;
    }

    private int nearestFree(int cx, int cy, int radius) {
        return nearest(cx, cy, radius, false);
    }

    private int nearestReachable(int cx, int cy, int radius) {
        return nearest(cx, cy, radius, true);
    }

    private int nearest(int cx, int cy, int radius, boolean needReachable) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = cx + dx;
                int ny = cy + dy;
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) {
                    continue;
                }
                int j = ny * cols + nx;
                if (!free[j] || (needReachable && dist[j] == INF)) {
                    continue;
                }
                double d = Math.hypot(dx, dy);
                if (d < bestD) {
                    bestD = d;
                    best = j;
                }
            }
        }
        return best;
    }

    private int clampX(int cx) {
        return Math.max(0, Math.min(cols - 1, cx));
    }

    private int clampY(int cy) {
        return Math.max(0, Math.min(rows - 1, cy));
    }

    private double centerX(int i) {
        return (i % cols + 0.5) * CELL;
    }

    private double centerY(int i) {
        return (i / cols + 0.5) * CELL;
    }
}