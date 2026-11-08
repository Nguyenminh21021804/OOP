package weapons;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class Projectile {
    private final double startX, startY;
    private double x, y;
    private final double dirX, dirY;
    private final double speed;
    private final double maxRange;
    private final double angleRad;

    private final BufferedImage[] flyFrames;
    private final BufferedImage impactFrame;
    private final double drawSize;

    private boolean impacted = false;
    private boolean finished = false;

    private final long spawnTime;
    private long impactTime = 0;
    private static final long IMPACT_DURATION_MS = 150;
    private static final long ANIM_FRAME_MS = 80;

    public Projectile(double x, double y, double dirX, double dirY,
                      double speed, double maxRange,
                      BufferedImage[] flyFrames, BufferedImage impactFrame, double drawSize) {
        this.startX = x;
        this.startY = y;
        this.x = x;
        this.y = y;
        this.dirX = dirX;
        this.dirY = dirY;
        this.speed = speed;
        this.maxRange = maxRange;

        // Tính góc xoay của đường đạn (theo radian)
        this.angleRad = Math.atan2(dirY, dirX);

        this.flyFrames = flyFrames;
        this.impactFrame = impactFrame;
        this.drawSize = drawSize;
        this.spawnTime = System.currentTimeMillis();
    }

    public void update(double dt) {
        if (finished) return;

        if (impacted) {
            if (System.currentTimeMillis() - impactTime >= IMPACT_DURATION_MS) {
                finished = true;
            }
            return;
        }

        // Cập nhật tọa độ đạn bay
        x += dirX * speed * dt;
        y += dirY * speed * dt;

        double dist = Math.hypot(x - startX, y - startY);
        if (dist >= maxRange) {
            triggerImpact();
        }
    }

    public void triggerImpact() {
        if (!impacted) {
            impacted = true;
            impactTime = System.currentTimeMillis();
        }
    }

    private BufferedImage getCurrentImage() {
        if (impacted) {
            return impactFrame;
        }
        if (flyFrames != null && flyFrames.length > 0) {
            long elapsed = System.currentTimeMillis() - spawnTime;
            int idx = (int) ((elapsed / ANIM_FRAME_MS) % flyFrames.length);
            return flyFrames[idx];
        }
        return null;
    }

    public void render(Graphics2D g, double uiScale, double offsetY) {
        if (finished) return;
        BufferedImage img = getCurrentImage();
        if (img == null) return;

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        double screenX = x * uiScale;
        double screenY = offsetY + y * uiScale;

        // Chiều cao và chiều ngang luôn giữ đúng tỷ lệ (Aspect Ratio)
        int h = (int) Math.max(4, Math.round(drawSize * uiScale));
        double ratio = (double) img.getWidth() / Math.max(1, img.getHeight());
        int w = (int) Math.max(4, Math.round(h * ratio));

        AffineTransform old = g.getTransform();
        g.translate(screenX, screenY);
        
        // 1. Xoay đạn theo hướng bay
        g.rotate(angleRad);
        
        // 2. FIX LỖI UP-SIDE DOWN: Nếu đạn bay sang nửa trái màn hình, nó bị lật ngược. 
        // Ta lật lại trục Y để ép nó quay về hướng bình thường.
        if (dirX < 0) {
            g.scale(1.0, -1.0);
        }

        g.drawImage(img, -w / 2, -h / 2, w, h, null);

        g.setTransform(old);

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public double getX() { return x; }
    public double getY() { return y; }
}