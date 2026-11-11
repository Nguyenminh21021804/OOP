package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;

/**
 * Hiệu ứng đòn cận chiến vẽ bằng code, chưa cần ảnh:
 *  - telegraph(): vùng báo hiệu hình quạt lớn dần trong pha WINDUP.
 *  - slash(): vệt chém hình cung, 3 bước (dày -> mỏng -> tan) trong pha STRIKE.
 *
 * Cách hoạt động: một bộ vẽ chung (Renderer) đăng ký vào môi trường đúng một lần
 * (đăng ký lại khi đổi map), rồi vẽ mọi hiệu ứng đang chạy. Hiệu ứng xong thì tự
 * bị loại khỏi danh sách, nên không cần gỡ renderable khỏi môi trường.
 *
 * Vẽ không khử răng cưa, đổi bước theo nấc cố định cho đúng cảm giác 8-bit.
 * Sau này có spritesheet vệt chém thì thay phần draw*, các lớp quái không phải sửa.
 *
 * Cách dùng (trong hook của quái):
 *   MeleeAttackVfx.telegraph(this, color, reach, 100);
 *   MeleeAttackVfx.slash(this, color, reach, 120, 180);
 */
public final class MeleeAttackVfx {

    private enum Kind { TELEGRAPH, SLASH, ALERT }

    // Bán kính vùng báo hiệu lớn dần theo nấc 4px thay vì mượt
    private static final double TELEGRAPH_STEP_PX = 4;

    // --- Bộ vẽ chung ---
    private static final List<MeleeAttackVfx> ACTIVE = new CopyOnWriteArrayList<>();
    private static final IRenderable RENDERER = MeleeAttackVfx::renderAll;
    private static Object registeredEnvironment;

    private final MeleeEnemy owner;
    private final Kind kind;
    private final Color color;
    private final double reach;
    private final double arcDegrees;
    private final long startTime;
    private final long durationMs;
    private boolean finished;

    private MonsterType.Shape shape = MonsterType.Shape.ARC;   // [GĐ8]
    private double lineWidth = 0;

    private MeleeAttackVfx(MeleeEnemy owner, Kind kind, Color color,
                           double reach, double arcDegrees, long durationMs) {
        this.owner = owner;
        this.kind = kind;
        this.color = color;
        this.reach = reach;
        this.arcDegrees = arcDegrees;
        this.durationMs = Math.max(1, durationMs);
        this.startTime = Game.time().now();
    }

    /** Vùng báo hiệu hình quạt, tự biến mất khi quái rời pha WINDUP. */
    public static void telegraph(MeleeEnemy owner, Color color, double reach, double arcDegrees) {
        start(new MeleeAttackVfx(owner, Kind.TELEGRAPH, color, reach, arcDegrees, 0));
    }

    /** Vệt chém hình cung, tự biến mất sau durationMs. */
    public static void slash(MeleeEnemy owner, Color color, double reach,
                             double arcDegrees, long durationMs) {
        start(new MeleeAttackVfx(owner, Kind.SLASH, color, reach, arcDegrees, durationMs));
    }

    /** [GĐ8] Vùng cảnh báo theo hình dạng: cung, dải thẳng hoặc vòng tròn. */
    public static void telegraph(MeleeEnemy owner, Color color, double reach,
                                 MonsterType.Shape shape, double arcDegrees, double lineWidth) {
        MeleeAttackVfx vfx = new MeleeAttackVfx(owner, Kind.TELEGRAPH, color, reach, arcDegrees, 0);
        vfx.shape = shape;
        vfx.lineWidth = lineWidth;
        start(vfx);
    }

    /** [GĐ8] Vệt chém theo hình dạng. */
    public static void slash(MeleeEnemy owner, Color color, double reach,
                             MonsterType.Shape shape, double arcDegrees, double lineWidth, long durationMs) {
        MeleeAttackVfx vfx = new MeleeAttackVfx(owner, Kind.SLASH, color, reach, arcDegrees, durationMs);
        vfx.shape = shape;
        vfx.lineWidth = lineWidth;
        start(vfx);
    }

    /** [GĐ6] Dấu "!" trên đầu quái, tự biến mất khi quái rời trạng thái ALERT. */
    public static void alert(MeleeEnemy owner) {
        start(new MeleeAttackVfx(owner, Kind.ALERT, Color.YELLOW, 0, 0, 1));
    }

    private static void start(MeleeAttackVfx vfx) {
        Object env = Game.world().environment();
        if (env == null) {
            return;
        }
        // Môi trường mới (đổi map) thì đăng ký lại bộ vẽ, môi trường cũ thì giữ nguyên
        if (registeredEnvironment != env) {
            Game.world().environment().add(RENDERER, RenderType.OVERLAY);
            registeredEnvironment = env;
        }
        ACTIVE.add(vfx);
    }

    /** Vẽ tất cả hiệu ứng đang chạy rồi dọn các hiệu ứng đã xong. */
    private static void renderAll(Graphics2D g) {
        for (MeleeAttackVfx vfx : ACTIVE) {
            vfx.draw(g);
        }
        ACTIVE.removeIf(v -> v.finished);
    }

    private void draw(Graphics2D g) {
        if (finished) {
            return;
        }

        long elapsed = Game.time().now() - startTime;

        // Điều kiện kết thúc
        if (kind == Kind.TELEGRAPH
                && (owner.isDead() || owner.getState() != MeleeEnemy.State.WINDUP)) {
            finished = true;
            return;
        }
        if (kind == Kind.SLASH && elapsed >= durationMs) {
            finished = true;
            return;
        }
        if (kind == Kind.ALERT && (owner.isDead() || owner.getState() != MeleeEnemy.State.ALERT || elapsed > 2000)) {
            finished = true;
            return;
        }

        // Đổi tọa độ thế giới sang tọa độ màn hình.
        // Nếu hiệu ứng bị lệch vị trí hoặc sai kích thước khi zoom camera,
        // đây là chỗ cần chỉnh (xem scale bên dưới).
        Point2D center = Game.world().camera().getViewportLocation(owner.getCenter());
        double scale = 1.0;   // Graphics của renderer đã được engine phóng sẵn theo render scale,
        // nên không nhân thêm (nhân thêm sẽ làm hiệu ứng to gấp nhiều lần)
        double cx = Math.round(center.getX());
        double cy = Math.round(center.getY());

        // Java2D: góc dương đi ngược chiều kim đồng hồ, còn góc khóa của quái
        // đi theo chiều kim đồng hồ (y hướng xuống), nên phải đổi dấu.
        double centerAngleDeg = -Math.toDegrees(owner.getLockedAngleRad());
        double startDeg = centerAngleDeg - arcDegrees / 2.0;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            if (kind == Kind.TELEGRAPH) {
                drawTelegraph(g2, cx, cy, scale, startDeg);
            } else if (kind == Kind.SLASH) {
                drawSlash(g2, cx, cy, scale, startDeg, elapsed);
            } else {
                drawAlert(g2, cx, cy, elapsed);
            }
        } finally {
            g2.dispose();
        }
    }

    private void drawTelegraph(Graphics2D g2, double cx, double cy, double scale, double startDeg) {
        double progress = owner.getWindupProgress();
        boolean committed = progress > 0.75;   // sắp đánh: đổi sang trắng
        Color fill = committed ? withAlpha(Color.WHITE, 110) : withAlpha(color, 80);
        Color edge = withAlpha(color, 170);
        double grown = Math.floor(reach * progress / TELEGRAPH_STEP_PX) * TELEGRAPH_STEP_PX;

        switch (shape) {
            case RING:
                g2.setColor(fill);
                g2.fill(new Ellipse2D.Double(cx - grown, cy - grown, 2 * grown, 2 * grown));
                g2.setColor(edge);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new Ellipse2D.Double(cx - reach, cy - reach, 2 * reach, 2 * reach));
                break;
            case LINE: {
                AffineTransform saved = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(owner.getLockedAngleRad());
                g2.setColor(fill);
                g2.fill(new Rectangle2D.Double(0, -lineWidth / 2, grown, lineWidth));
                g2.setColor(edge);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new Rectangle2D.Double(0, -lineWidth / 2, reach, lineWidth));
                g2.setTransform(saved);
                break;
            }
            default: {   // ARC
                double r = grown * scale;
                g2.setColor(fill);
                g2.fill(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, startDeg, arcDegrees, Arc2D.PIE));
                double outer = reach * scale;
                g2.setColor(edge);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new Arc2D.Double(cx - outer, cy - outer, 2 * outer, 2 * outer,
                        startDeg, arcDegrees, Arc2D.PIE));
            }
        }
    }

    private void drawSlash(Graphics2D g2, double cx, double cy, double scale, double startDeg, long elapsed) {
        // 3 bước: dày trắng -> vừa -> mỏng
        int stage = (int) Math.min(2, elapsed * 3 / durationMs);
        float[] widths = {5f, 3f, 1f};
        double[] radiusFactor = {0.80, 0.90, 1.00};
        g2.setColor(stage == 0 ? Color.WHITE : color);

        switch (shape) {
            case RING: {   // sóng xung kích lan ra
                double r = reach * radiusFactor[stage];
                g2.setStroke(new BasicStroke(widths[stage], BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                break;
            }
            case LINE: {   // vệt lao: đoạn sáng trượt dần ra xa
                AffineTransform saved = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(owner.getLockedAngleRad());
                double from = reach * stage * 0.15;
                double to = reach * radiusFactor[stage];
                g2.fill(new Rectangle2D.Double(from, -widths[stage] / 2, to - from, widths[stage]));
                g2.setTransform(saved);
                break;
            }
            default: {     // ARC
                double r = reach * radiusFactor[stage] * scale;
                g2.setStroke(new BasicStroke((float) (widths[stage] * scale),
                        BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                g2.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, startDeg, arcDegrees, Arc2D.OPEN));
            }
        }
    }

    private void drawAlert(Graphics2D g2, double cx, double cy, long elapsed) {
        // Dấu "!" pixel phía trên đầu: nảy lên một nhịp rồi đứng yên
        double bounce = elapsed < 140 ? -3 * Math.sin(Math.PI * elapsed / 140.0) : 0;
        double x = Math.round(cx) - 1;
        double y = Math.round(cy - owner.getHeight() / 2.0 - 9 + bounce);
        g2.setColor(new Color(24, 18, 28));                           // viền tối cho dễ đọc trên nền hang
        g2.fill(new Rectangle2D.Double(x - 1, y - 1, 4, 8));
        g2.setColor(new Color(255, 220, 70));                         // thân dấu chấm than
        g2.fill(new Rectangle2D.Double(x, y, 2, 4));
        g2.fill(new Rectangle2D.Double(x, y + 5, 2, 1));
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}