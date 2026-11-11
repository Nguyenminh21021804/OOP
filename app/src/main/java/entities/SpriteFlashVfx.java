package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Nháy trắng khi quái bị đánh: vẽ silhouette trắng của quái đè lên sprite thật
 * trong vài chục mili-giây.
 *
 * Cách làm: không đụng vào animation của engine, chỉ vẽ một lớp phủ ở đúng vị trí quái.
 * Silhouette lấy từ MỘT ảnh duy nhất: resources/sprites/<prefix>-flash.png, là phần hợp
 * (gộp) của mọi frame idle và walk, tô trắng. Nhờ vậy vùng trắng luôn phủ trọn sprite
 * ở bất kỳ frame nào, chỉ thừa vài pixel viền, không cần biết engine đang chiếu frame nào.
 *
 * Hiện KHÔNG lật ngang theo hướng nhìn, vì engine cũng đang vẽ sprite một hướng.
 * Khi quái có sprite quay trái thật, cần cho silhouette lật theo cùng quy tắc của engine.
 *
 * Cách dùng (trong hook onHurt của quái):
 *   SpriteFlashVfx.flash(this, "orc_warrior", 80);
 */
public final class SpriteFlashVfx {

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final List<SpriteFlashVfx> ACTIVE = new CopyOnWriteArrayList<>();
    private static final IRenderable RENDERER = SpriteFlashVfx::renderAll;
    private static Object registeredEnvironment;

    private final Enemy owner;
    private final String prefix;
    private final long durationMs;
    private final long startTime;
    private boolean finished;

    // [GĐ4] Chỉ dùng cho hiệu ứng chết (tan thành hạt); burstColor == null nghĩa là nháy trắng thường
    private Color burstColor;
    private BufferedImage snapshot;           // frame cuối của quái lúc chết
    private double boxX, boxY, boxW, boxH;    // hộp thực thể lúc chết (quái đã bị gỡ khỏi màn chơi)
    private static final long BURST_MS = 500;
    private static final double BURST_GRAVITY = 220;   // px/s^2

    private Color glowColor;                 // [GĐ7] khác null nghĩa là hiệu ứng phát sáng khi chốt đòn
    private BooleanSupplier glowActive;      // trả về false thì hiệu ứng tự tắt

    private SpriteFlashVfx(Enemy owner, String prefix, long durationMs) {
        this.owner = owner;
        this.prefix = prefix;
        this.durationMs = Math.max(1, durationMs);
        this.startTime = Game.time().now();
    }

    /** Nháy trắng quái trong durationMs. Gọi lại khi đang nháy sẽ làm mới thời gian. */
    public static void flash(Enemy owner, String spritePrefix, long durationMs) {
        if (!ensureRegistered()) {
            return;
        }
        ACTIVE.removeIf(v -> v.owner == owner && v.burstColor == null && v.glowColor == null);
        ACTIVE.add(new SpriteFlashVfx(owner, spritePrefix, durationMs));
    }

    /** Đăng ký bộ vẽ chung vào môi trường hiện tại (một lần cho mỗi môi trường). false nếu chưa có môi trường. */
    private static boolean ensureRegistered() {
        Object env = Game.world().environment();
        if (env == null) {
            return false;
        }
        if (registeredEnvironment != env) {
            Game.world().environment().add(RENDERER, RenderType.OVERLAY);
            registeredEnvironment = env;
        }
        return true;
    }

    /**
     * [GĐ4] Quái chết: sprite tan thành từng pixel bay lên rồi rơi (phong cách 8-bit).
     * Gọi TRƯỚC khi gỡ quái khỏi màn chơi: nó chụp frame hiện tại rồi tự vẽ độc lập với quái.
     * accent: màu phe, dùng cho một phần hạt.
     */
    public static void burst(Enemy owner, String spritePrefix, Color accent) {
        if (!ensureRegistered()) {
            return;
        }
        SpriteFlashVfx vfx = new SpriteFlashVfx(owner, spritePrefix, BURST_MS);
        vfx.burstColor = accent;
        vfx.snapshot = vfx.engineFrame();
        if (vfx.snapshot == null) {
            vfx.snapshot = load(spritePrefix);   // dự phòng: silhouette gộp (spritePrefix là đường dẫn ảnh flash)
        }
        vfx.boxX = owner.getX();
        vfx.boxY = owner.getY();
        vfx.boxW = owner.getWidth();
        vfx.boxH = owner.getHeight();
        if (vfx.snapshot != null) {
            ACTIVE.add(vfx);
        }
    }

    private void drawBurst(Graphics2D g) {
        long elapsed = Game.time().now() - startTime;
        if (finished || elapsed >= durationMs) {
            finished = true;
            return;
        }

        Point2D origin = Game.world().camera().getViewportLocation(new Point2D.Double(boxX, boxY));
        double t = elapsed / 1000.0;
        double life = durationMs / 1000.0;
        int fade = (int) (255 * Math.min(1.0, (life - t) / (life * 0.4)));   // mờ dần ở 40% cuối
        int iw = snapshot.getWidth();
        int ih = snapshot.getHeight();
        double sx = boxW / iw;
        double sy = boxH / ih;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            for (int py = 0; py < ih; py++) {
                for (int px = 0; px < iw; px++) {
                    int argb = snapshot.getRGB(px, py);
                    if ((argb >>> 24) == 0) {
                        continue;
                    }
                    // Mỗi pixel có vận tốc riêng cố định theo toạ độ (không dùng random để khỏi nhấp nháy)
                    double vx = (px - iw / 2.0) * 6 + (hash01(px, py, 1) - 0.5) * 30;
                    double vy = -30 - hash01(px, py, 2) * 50;
                    double x = origin.getX() + (px + 0.5) * sx + vx * t;
                    double y = origin.getY() + (py + 0.5) * sy + vy * t + 0.5 * BURST_GRAVITY * t * t;

                    Color c;
                    if (t < 0.1) {
                        c = Color.WHITE;                       // lóe trắng lúc bị kết liễu
                    } else if ((px + py) % 4 == 0) {
                        c = burstColor;                        // vài hạt mang màu phe
                    } else {
                        c = new Color(argb, true);             // còn lại giữ màu gốc của sprite
                    }
                    g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(fade, c.getAlpha())));
                    g2.fill(new Rectangle2D.Double(Math.round(x), Math.round(y), sx, sy));
                }
            }
        } finally {
            g2.dispose();
        }
    }

    /** Số giả ngẫu nhiên ổn định trong [0, 1] theo toạ độ pixel. */
    private static double hash01(int x, int y, int k) {
        int h = x * 73856093 ^ y * 19349663 ^ k * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0;
    }

    private static void renderAll(Graphics2D g) {
        for (SpriteFlashVfx vfx : ACTIVE) {
            vfx.draw(g);
        }
        ACTIVE.removeIf(v -> v.finished);
    }

    private void draw(Graphics2D g) {
        if (glowColor != null) {   // [GĐ7] phát sáng khi chốt đòn
            drawGlow(g);
            return;
        }

        if (burstColor != null) {   // [GĐ4] hiệu ứng chết
            drawBurst(g);
            return;
        }
        long now = Game.time().now();
        if (finished || now - startTime >= durationMs || owner.isDead()) {
            finished = true;
            return;
        }

        // Ưu tiên silhouette của đúng frame engine đang vẽ (đúng hướng, đúng pose).
        // Không lấy được thì dùng ảnh gộp mọi frame (<prefix>-flash.png) làm dự phòng.
        BufferedImage img = whiten(engineFrame());
        if (img == null) {
            img = load(prefix);   // prefix chính là đường dẫn classpath của ảnh flash (MonsterType.getFlashImagePath)
        }
        if (img == null) {
            finished = true;
            return;
        }

        // Graphics của renderer đã được engine phóng sẵn theo render scale, nên toạ độ và kích
        // thước tính bằng đơn vị thế giới. Dùng toạ độ thực (không làm tròn) để khớp với cách
        // engine vẽ sprite ở vị trí thập phân.
        Point2D center = Game.world().camera().getViewportLocation(owner.getCenter());
        double w = owner.getWidth();
        double h = owner.getHeight();
        double x = center.getX() - w / 2.0;
        double y = center.getY() - h / 2.0;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.translate(x, y);
            g2.scale(w / img.getWidth(), h / img.getHeight());
            g2.drawImage(img, 0, 0, null);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Frame mà engine đang vẽ cho quái, hoặc null nếu không lấy được.
     * Đây là chỗ DUY NHẤT phụ thuộc API animation: nếu IDE báo đỏ ở getCurrentImage,
     * gõ "owner.animations()." rồi Ctrl+Space để xem hàm nào trả về ảnh frame hiện tại.
     */
    private BufferedImage engineFrame() {
        return owner.animations().getCurrentImage();
    }

    /** Tô trắng mọi pixel không trong suốt, giữ nguyên độ trong suốt. */
    private static BufferedImage whiten(BufferedImage src) {
        if (src == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int alpha = src.getRGB(x, y) >>> 24;
                if (alpha > 0) {
                    out.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
                }
            }
        }
        return out;
    }

    private static BufferedImage load(String name) {
        if (CACHE.containsKey(name)) {
            return CACHE.get(name);
        }
        BufferedImage img = null;
        try (InputStream in = SpriteFlashVfx.class.getResourceAsStream(name)) {
            if (in != null) {
                img = ImageIO.read(in);
            } else {
                System.err.println("[SpriteFlashVfx] Khong tim thay " + name);
            }
        } catch (IOException e) {
            System.err.println("[SpriteFlashVfx] Loi doc " + name + ": " + e.getMessage());
        }
        CACHE.put(name, img);   // cache cả khi null để không báo lỗi lặp lại
        return img;
    }

    /** [GĐ7] Quái phát sáng màu phe khi đã chốt đòn; tự tắt khi active trả về false. */
    public static void glow(Enemy owner, String flashImagePath, Color color, BooleanSupplier active) {
        if (!ensureRegistered()) {
            return;
        }
        SpriteFlashVfx vfx = new SpriteFlashVfx(owner, flashImagePath, 3000);
        vfx.glowColor = color;
        vfx.glowActive = active;
        ACTIVE.add(vfx);
    }

    private void drawGlow(Graphics2D g) {
        long elapsed = Game.time().now() - startTime;
        if (finished || elapsed >= durationMs || owner.isDead() || !glowActive.getAsBoolean()) {
            finished = true;
            return;
        }
        BufferedImage frame = engineFrame();
        if (frame == null) {
            frame = load(prefix);   // dự phòng: silhouette gộp
        }
        if (frame == null) {
            finished = true;
            return;
        }
        int alpha = (elapsed / 70) % 2 == 0 ? 150 : 70;   // nhấp nháy nhanh báo "đòn sắp tới"
        paint(g, tinted(frame, glowColor, alpha));
    }

    /** Vẽ ảnh phủ lên đúng hộp thực thể của quái (cùng cách đổi toạ độ với hiệu ứng nháy trắng). */
    private void paint(Graphics2D g, BufferedImage img) {
        Point2D center = Game.world().camera().getViewportLocation(owner.getCenter());
        double w = owner.getWidth();
        double h = owner.getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.translate(center.getX() - w / 2.0, center.getY() - h / 2.0);
            g2.scale(w / img.getWidth(), h / img.getHeight());
            g2.drawImage(img, 0, 0, null);
        } finally {
            g2.dispose();
        }
    }

    /** Tô mọi pixel không trong suốt bằng màu c với độ mờ alpha. */
    private static BufferedImage tinted(BufferedImage src, Color c, int alpha) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int rgb = c.getRGB() & 0xFFFFFF;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                if ((src.getRGB(x, y) >>> 24) > 0) {
                    out.setRGB(x, y, (alpha << 24) | rgb);
                }
            }
        }
        return out;
    }
}