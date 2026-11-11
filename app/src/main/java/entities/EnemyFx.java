package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hiệu ứng nền cho quái, phong cách 8-bit (mọi thứ là ô vuông 1 pixel gốc):
 *  - bóng dưới chân mọi quái còn sống (vẽ DƯỚI sprite),
 *  - bụi bay lên khi quái chạy,
 *  - tia lửa màu phe khi quái trúng đòn.
 *
 * Bộ vẽ đăng ký vào môi trường đúng một lần (đăng ký lại khi đổi map) qua ensureRegistered(),
 * được Enemy.update() gọi mỗi tick. Hạt dùng toạ độ thế giới, tự biến mất khi hết tuổi thọ.
 */
public final class EnemyFx {

    private static final class Particle {
        final double x;
        final double y;
        final double vx;
        final double vy;
        final double gravity;
        final Color color;
        final long born;
        final long lifeMs;

        Particle(double x, double y, double vx, double vy, double gravity, Color color, long lifeMs) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.color = color;
            this.born = Game.time().now();
            this.lifeMs = lifeMs;
        }
    }

    private static final Color DUST = new Color(176, 160, 140, 190);
    private static final Color SHADOW = new Color(0, 0, 0, 80);

    private static final List<Particle> PARTICLES = new CopyOnWriteArrayList<>();
    private static final IRenderable SHADOW_RENDERER = EnemyFx::renderShadows;
    private static final IRenderable PARTICLE_RENDERER = EnemyFx::renderParticles;
    private static Object registeredEnvironment;

    private EnemyFx() {
    }

    /** Đăng ký bộ vẽ vào môi trường hiện tại (một lần cho mỗi môi trường). Gọi từ Enemy.update(). */
    public static void ensureRegistered() {
        Object env = Game.world().environment();
        if (env == null || registeredEnvironment == env) {
            return;
        }
        // Bóng ở lớp GROUND để nằm dưới sprite; hạt ở OVERLAY để nằm trên.
        Game.world().environment().add(SHADOW_RENDERER, RenderType.GROUND);
        Game.world().environment().add(PARTICLE_RENDERER, RenderType.OVERLAY);
        registeredEnvironment = env;
    }

    /** Bụi dưới chân khi chạy: hai hạt bay ngược hướng đi và hơi lên trên. */
    public static void dust(double footX, double footY, double dirX, double dirY) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 2; i++) {
            double vx = -dirX * (6 + r.nextDouble() * 8) + (r.nextDouble() - 0.5) * 8;
            double vy = -dirY * (6 + r.nextDouble() * 8) - 8 - r.nextDouble() * 8;
            PARTICLES.add(new Particle(footX + (r.nextDouble() - 0.5) * 4, footY, vx, vy, 20,
                    DUST, 260 + r.nextInt(120)));
        }
    }

    /**
     * Tia lửa văng ra theo hướng (dirX, dirY), tản ±40 độ. Cứ ba hạt có một hạt trắng.
     * Hướng (0, 0) thì văng ngẫu nhiên.
     */
    public static void sparks(double x, double y, double dirX, double dirY, Color color, int count) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double base = (dirX == 0 && dirY == 0) ? r.nextDouble(0, Math.PI * 2) : Math.atan2(dirY, dirX);
        for (int i = 0; i < count; i++) {
            double a = base + (r.nextDouble() - 0.5) * 1.4;
            double speed = 40 + r.nextDouble() * 50;
            Color c = (i % 3 == 0) ? Color.WHITE : color;
            PARTICLES.add(new Particle(x, y, Math.cos(a) * speed, Math.sin(a) * speed - 10, 120,
                    c, 200 + r.nextInt(120)));
        }
    }

    // ------------------------------------------------------------------

    private static void renderShadows(Graphics2D g) {
        if (Game.world().environment() == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(SHADOW);
            for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
                if (!(e instanceof Enemy) || e.isDead()) {
                    continue;
                }
                Enemy enemy = (Enemy) e;
                Point2D feet = enemy.getFeetCenter();
                double w = enemy.getFootWidth() + 2;
                Point2D s = Game.world().camera().getViewportLocation(
                        new Point2D.Double(feet.getX(), feet.getY() + enemy.getFootHeight() / 2.0 - 1));
                double cx = Math.round(s.getX());
                double cy = Math.round(s.getY());
                // Elip pixel ba hàng: hẹp, rộng, hẹp
                g2.fill(new Rectangle2D.Double(Math.round(cx - (w - 4) / 2), cy - 1, w - 4, 1));
                g2.fill(new Rectangle2D.Double(Math.round(cx - w / 2), cy, w, 1));
                g2.fill(new Rectangle2D.Double(Math.round(cx - (w - 4) / 2), cy + 1, w - 4, 1));
            }
        } catch (ConcurrentModificationException ignored) {
            // Danh sách thực thể đổi giữa lúc vẽ (quái vừa chết hoặc vừa sinh): bỏ qua khung hình này
        } finally {
            g2.dispose();
        }
    }

    private static void renderParticles(Graphics2D g) {
        long now = Game.time().now();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            for (Particle p : PARTICLES) {
                long age = now - p.born;
                if (age >= p.lifeMs) {
                    continue;
                }
                double t = age / 1000.0;
                double x = p.x + p.vx * t;
                double y = p.y + p.vy * t + 0.5 * p.gravity * t * t;
                Point2D s = Game.world().camera().getViewportLocation(new Point2D.Double(x, y));
                int alpha = (int) (p.color.getAlpha() * (1.0 - (double) age / p.lifeMs));
                g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
                g2.fill(new Rectangle2D.Double(Math.round(s.getX()), Math.round(s.getY()), 1, 1));
            }
        } finally {
            g2.dispose();
        }
        PARTICLES.removeIf(p -> now - p.born >= p.lifeMs);
    }
}