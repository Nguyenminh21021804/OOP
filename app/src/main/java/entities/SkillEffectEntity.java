package entities;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SkillEffectEntity — Quản lý và vẽ hiệu ứng cho 3 kỹ năng nguyên tố của Zombie Boss:
 * - BOULDER_TOSS (Tảng đá ném to 4 ô vuông, bay nhanh và gây sát thương ngay khi chạm mục tiêu)
 * - EARTH_SPIKE (Hàng gai đất: 1 hàng duy nhất, trồi lên tại chỗ cực nhanh theo hướng trỏ chuột)
 * - WHIRLPOOL (Vòng xoáy nước to, xoay tít tốc độ cao trong 2 giây, hút mục tiêu cực mạnh)
 */
public class SkillEffectEntity extends Creature implements IUpdateable, IRenderable {

    public enum SkillType {
        BOULDER_TOSS,
        EARTH_SPIKE,
        WHIRLPOOL,
        ROCKFALL,
        ROCKFALL_ZONE
    }

    private static BufferedImage boulderRockSprite;
    private static BufferedImage[] earthSpikeFrames;
    private static BufferedImage[] whirlpoolFrames;
    private static BufferedImage[] rockfallFrames;
    private static boolean framesLoaded = false;

    private long lastZoneSpawnTime = 0;

    private final SkillType type;
    private final int casterTeam;
    private final boolean phase2;
    private final long startTime;
    private final int frameDurationMs;
    private final int totalFrames;
    private final boolean loop;

    private final int drawW;
    private final int drawH;

    private double dirX;
    private double dirY;
    private double speed;
    private final double rotationAngle;
    private final long maxLifeTimeMs;
    private boolean active = true;
    private boolean facingLeft = false;

    // Quản lý sát thương & va chạm
    private final Set<ICombatEntity> alreadyHitSet = new HashSet<>();
    private final Map<ICombatEntity, Long> lastDamageTick = new HashMap<>();

    public SkillEffectEntity(SkillType type, Point2D centerPos, double dirX, double dirY,
                             int casterTeam, boolean phase2) {
        super();
        ensureFramesLoaded();

        this.type = type;
        this.casterTeam = casterTeam;
        this.phase2 = phase2;
        this.dirX = dirX;
        this.dirY = dirY;
        this.facingLeft = (dirX < -0.05);
        this.startTime = Game.time().now();
        this.rotationAngle = Math.atan2(dirY, dirX);

        switch (type) {
            case BOULDER_TOSS:
                // Cục đá to đúng 4 ô vuông (2x2 = 64x64px), bay thẳng về phía trước, không vỡ
                this.drawW = 64;
                this.drawH = 64;
                this.setSize(drawW, drawH);
                this.frameDurationMs = 50;
                this.totalFrames = 1;
                this.loop = true;
                this.speed = phase2 ? 380.0 : 320.0;
                this.maxLifeTimeMs = 1600;
                break;

            case EARTH_SPIKE:
                // 1 hàng gai đất duy nhất, trồi lên tại chỗ cực nhanh theo hướng con trỏ chuột
                this.drawW = 175;
                this.drawH = 85;
                this.setSize(drawW, drawH);
                this.frameDurationMs = 20;
                this.totalFrames = 22;
                this.loop = false;
                this.speed = 0;
                this.maxLifeTimeMs = (long) totalFrames * frameDurationMs;
                break;

            case ROCKFALL:
                // Mưa đá rơi tại 1 điểm (128x134 -> 112x117), hiện vòng cảnh báo 350ms trước khi rơi
                this.drawW = 112;
                this.drawH = 117;
                this.setSize(drawW, drawH);
                this.frameDurationMs = 10;
                this.totalFrames = 31;
                this.loop = false;
                this.speed = 0;
                this.maxLifeTimeMs = 200L + (long) totalFrames * frameDurationMs;
                break;

            case ROCKFALL_ZONE:
                // Vùng tròn 6x6 ô (192x192px, bán kính 96px) lấy tâm là Boss
                this.drawW = 192;
                this.drawH = 192;
                this.setSize(drawW, drawH);
                this.frameDurationMs = 50;
                this.totalFrames = 1;
                this.loop = true;
                this.speed = 0;
                this.maxLifeTimeMs = phase2 ? 2800 : 2200;
                break;

            case WHIRLPOOL:
            default:
                // Vòng xoáy nước to hơn gần gấp đôi (134x89), xoay tít cực nhanh (34ms/frame), biến mất sau ~2 giây
                this.drawW = 134;
                this.drawH = 89;
                this.setSize(drawW, drawH);
                this.frameDurationMs = 34;
                this.totalFrames = 20;
                this.loop = true;
                this.speed = 0;
                this.maxLifeTimeMs = phase2 ? 2200 : 2000;
                break;
        }

        // Đặt tâm của effect trùng đúng centerPos
        this.setLocation(centerPos.getX() - drawW / 2.0, centerPos.getY() - drawH / 2.0);

        if (Game.world().environment() != null) {
            Game.world().environment().add(this, RenderType.OVERLAY);
        }
        Game.loop().attach(this);
    }

    private static synchronized void ensureFramesLoaded() {
        if (framesLoaded) return;
        boulderRockSprite = createRoundBoulderSprite();
        earthSpikeFrames = loadAndSlice("sprites/skills/earth_spike.png", 128, 64, 8, 22);
        whirlpoolFrames = loadAndSlice("sprites/skills/whirlpool.png", 72, 48, 8, 20);
        rockfallFrames = loadAndSlice("sprites/skills/rockfall.png", 128, 134, 8, 31);
        framesLoaded = true;
    }

    /**
     * Tạo sprite cục đá tròn phong cách pixel-art 64x64 (4 ô vuông).
     */
    private static BufferedImage createRoundBoulderSprite() {
        int grid = 16;
        int pixelSize = 4; // 16 * 4 = 64x64px
        BufferedImage img = new BufferedImage(grid * pixelSize, grid * pixelSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color outline = new Color(36, 30, 34);
        Color shadow = new Color(76, 68, 66);
        Color base = new Color(118, 108, 102);
        Color highlight = new Color(162, 152, 142);

        double cx = 7.5;
        double cy = 7.5;
        for (int y = 0; y < grid; y++) {
            for (int x = 0; x < grid; x++) {
                double dist = Math.hypot(x - cx, y - cy);
                if (dist > 7.6) continue;

                Color c;
                if (dist > 6.5) {
                    c = outline;
                } else if ((x == 5 && y >= 5 && y <= 8) || (x == 10 && y >= 7 && y <= 10) || (y == 9 && x >= 6 && x <= 9)) {
                    c = outline; // Đường nứt trên mặt đá
                } else if (x + y < 11) {
                    c = highlight; // Góc sáng trên-trái
                } else if (x + y > 18 || dist > 5.3) {
                    c = shadow; // Bóng tối viền dưới-phải
                } else {
                    c = base;
                }
                g.setColor(c);
                g.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
            }
        }
        g.dispose();
        return img;
    }

    private static BufferedImage[] loadAndSlice(String path, int cellW, int cellH, int cols, int count) {
        BufferedImage sheet = null;
        try {
            InputStream in = SkillEffectEntity.class.getClassLoader().getResourceAsStream(path);
            if (in != null) {
                sheet = ImageIO.read(in);
            }
            if (sheet == null) {
                File f = new File("app/src/main/resources/" + path);
                if (!f.exists()) f = new File("src/main/resources/" + path);
                if (f.exists()) sheet = ImageIO.read(f);
            }
        } catch (Exception e) {
            System.err.println("[SkillEffectEntity] Lỗi nạp ảnh: " + path + " - " + e.getMessage());
        }

        BufferedImage[] result = new BufferedImage[count];
        if (sheet == null) return result;

        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = col * cellW;
            int sy = row * cellH;
            if (sx + cellW <= sheet.getWidth() && sy + cellH <= sheet.getHeight()) {
                result[i] = sheet.getSubimage(sx, sy, cellW, cellH);
            }
        }
        return result;
    }

    @Override
    public void update() {
        if (!active) return;

        long now = Game.time().now();
        long elapsed = now - startTime;

        if (elapsed >= maxLifeTimeMs) {
            destroy();
            return;
        }

        double dt = Math.min(0.05, Math.max(0.001, Game.loop().getDeltaTime() / 1000.0));

        switch (type) {
            case BOULDER_TOSS:
                updateBoulder(now, elapsed, dt);
                break;
            case EARTH_SPIKE:
                updateEarthSpike(now, elapsed, dt);
                break;
            case WHIRLPOOL:
                updateWhirlpool(now, dt);
                break;
            case ROCKFALL:
                updateRockfall(elapsed);
                break;
            case ROCKFALL_ZONE:
                updateRockfallZone(now);
                break;
        }
    }

    private void updateBoulder(long now, long elapsed, double dt) {
        // Cục đá bay thẳng liên tục theo hướng bắn
        setLocation(getX() + dirX * speed * dt, getY() + dirY * speed * dt);

        // Hitbox 64x64 khớp 100% với kích thước cục đá đang vẽ
        int targetTeam = (casterTeam == 1) ? 2 : 1;
        int dmg = phase2 ? 95 : 70;

        if (Game.world().environment() != null) {
            for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
                if (e != this && !(e instanceof SkillEffectEntity) && !(e instanceof Projectile) && !e.isDead()
                        && (e.getTeam() == targetTeam || e.getTeam() != casterTeam)) {
                    if (!alreadyHitSet.contains(e) && getBoundingBox().intersects(e.getBoundingBox())) {
                        e.hit(dmg);
                        alreadyHitSet.add(e);
                    }
                }
            }
        }
    }

    private void updateEarthSpike(long now, long elapsed, double dt) {
        int currentFrame = (int) (elapsed / frameDurationMs);
        // Gai đất nhô lên gây sát thương từ frame 2 đến 16
        if (currentFrame >= 2 && currentFrame <= 16) {
            int targetTeam = (casterTeam == 1) ? 2 : 1;
            Point2D center = getCenter();
            double hitRadius = phase2 ? 95.0 : 80.0;
            int dmg = phase2 ? 85 : 65;

            if (Game.world().environment() != null) {
                for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
                    if (e != this && !(e instanceof SkillEffectEntity) && !(e instanceof Projectile) && !e.isDead()
                            && (e.getTeam() == targetTeam || e.getTeam() != casterTeam)) {
                        if (!alreadyHitSet.contains(e) && center.distance(e.getCenter()) <= hitRadius) {
                            e.hit(dmg);
                            alreadyHitSet.add(e);
                        }
                    }
                }
            }
        }
    }

    private void updateWhirlpool(long now, double dt) {
        int targetTeam = (casterTeam == 1) ? 2 : 1;
        Point2D center = getCenter();
        // Tầm hút khớp đúng vùng hình ảnh vòng xoáy nước (134x89 -> bán trục rx=67, ry=45)
        double rx = drawW / 2.0;
        double ry = drawH / 2.0;
        int tickDmg = phase2 ? 18 : 14;
        double pullSpeed = phase2 ? 260.0 : 210.0;

        if (Game.world().environment() != null) {
            for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
                if (e != this && !(e instanceof SkillEffectEntity) && !(e instanceof Projectile) && !e.isDead()
                        && (e.getTeam() == targetTeam || e.getTeam() != casterTeam)) {
                    double dx = center.getX() - e.getCenter().getX();
                    double dy = center.getY() - e.getCenter().getY();
                    double normDist = Math.hypot(dx / rx, dy / ry);

                    // Chỉ hút và gây sát thương khi mục tiêu thực sự đứng trong vòng xoáy
                    if (normDist <= 1.0) {
                        double len = Math.hypot(dx, dy);
                        if (len > 6.0) {
                            double step = pullSpeed * dt;
                            e.setLocation(e.getX() + (dx / len) * step, e.getY() + (dy / len) * step);
                        }

                        long lastHit = lastDamageTick.getOrDefault(e, 0L);
                        if (now - lastHit >= 220) {
                            e.hit(tickDmg);
                            lastDamageTick.put(e, now);
                        }
                    }
                }
            }
        }
    }

    private void updateRockfallZone(long now) {
        long interval = phase2 ? 150 : 220;
        if (lastZoneSpawnTime == 0 || now - lastZoneSpawnTime >= interval) {
            int spawnCount = 1;
            lastZoneSpawnTime = now;
            Point2D zoneCenter = getCenter();
            for (int i = 0; i < spawnCount; i++) {
                double angle = Math.random() * 2.0 * Math.PI;
                double r = Math.sqrt(Math.random()) * 78.0; // Nằm gọn trong vùng tròn 6x6 (bán kính 96px)
                // Đặt tâm sprite sao cho điểm đá rơi (72% chiều cao) nằm đúng tọa độ random trong vòng tròn
                Point2D impactPos = new Point2D.Double(
                    zoneCenter.getX() + Math.cos(angle) * r,
                    zoneCenter.getY() + Math.sin(angle) * r - 24.0
                );
                new SkillEffectEntity(SkillType.ROCKFALL, impactPos, 0, 0, casterTeam, phase2);
            }
        }
    }

    private static final long ROCKFALL_WARNING_MS = 200; // Hiện vòng cảnh báo trước khi đá rơi

    private void updateRockfall(long elapsed) {
        if (elapsed < ROCKFALL_WARNING_MS) return; // Đang cảnh báo, chưa gây sát thương
        int rockFrame = (int) ((elapsed - ROCKFALL_WARNING_MS) / frameDurationMs);
        // Gây sát thương khi đá chạm đất và vỡ ra (frame 8-18)
        if (rockFrame >= 8 && rockFrame <= 18) {
            int targetTeam = (casterTeam == 1) ? 2 : 1;
            Point2D impactCenter = new Point2D.Double(getX() + drawW / 2.0, getY() + drawH * 0.88);
            double hitRadius = 16.0; // Đúng bằng vòng cảnh báo (bán kính 16px = 1 ô vuông)
            int dmg = phase2 ? 60 : 45;

            if (Game.world().environment() != null) {
                for (ICombatEntity e : Game.world().environment().getCombatEntities()) {
                    if (e != this && !(e instanceof SkillEffectEntity) && !(e instanceof Projectile) && !e.isDead()
                            && (e.getTeam() == targetTeam || e.getTeam() != casterTeam)) {
                        if (!alreadyHitSet.contains(e) && impactCenter.distance(e.getCenter()) <= hitRadius) {
                            e.hit(dmg);
                            alreadyHitSet.add(e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        if (!active) return;

        long now = Game.time().now();
        long elapsed = now - startTime;

        Point2D worldPos = getLocation();
        Point2D viewPos = (Game.world().camera() != null)
                ? Game.world().camera().getViewportLocation(worldPos)
                : worldPos;
        int vx = (int) Math.round(viewPos.getX());
        int vy = (int) Math.round(viewPos.getY());

        Graphics2D g2 = (Graphics2D) g.create();

        // --- ROCKFALL: xử lý riêng hoàn toàn ---
        if (type == SkillType.ROCKFALL) {
            int r = 16;
            int ix = (int) Math.round(viewPos.getX() + drawW / 2.0);
            int iy = (int) Math.round(viewPos.getY() + drawH * 0.88);

            if (elapsed < ROCKFALL_WARNING_MS) {
                // Giai đoạn cảnh báo: vòng tròn vàng nhấp nháy, chưa có đá
                float pulse = (float) (0.5 + 0.5 * Math.sin(elapsed * 0.03));
                int alpha = (int) (120 + 80 * pulse);
                g2.setColor(new Color(255, 220, 50, alpha / 4));
                g2.fillOval(ix - r, iy - r, r * 2, r * 2);
                g2.setColor(new Color(255, 200, 40, alpha));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawOval(ix - r, iy - r, r * 2, r * 2);
            } else {
                // Giai đoạn rơi: chỉ vẽ sprite đá, không có vòng hitbox
                int rockFrame = (int) ((elapsed - ROCKFALL_WARNING_MS) / frameDurationMs);
                if (rockFrame >= 0 && rockFrame < totalFrames && rockfallFrames != null
                        && rockFrame < rockfallFrames.length && rockfallFrames[rockFrame] != null) {
                    g2.drawImage(rockfallFrames[rockFrame], vx, vy, drawW, drawH, null);
                }
            }
            g2.dispose();
            return;
        }

        // --- Các skill khác ---
        int frameIndex = (int) (elapsed / frameDurationMs);
        if (loop) {
            frameIndex = frameIndex % totalFrames;
        } else if (frameIndex >= totalFrames) {
            g2.dispose();
            return;
        }

        BufferedImage frame = getFrame(frameIndex);
        if (frame == null) { g2.dispose(); return; }
        if (type == SkillType.ROCKFALL_ZONE) {
            // Chỉ vẽ viền mỏng để không che khuất hitbox của từng viên đá
            g2.setColor(new Color(220, 165, 95, 80));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(vx, vy, drawW, drawH);
        } else if (type == SkillType.BOULDER_TOSS) {
            // Vẽ cục đá tròn 64x64 xoay tròn bay thẳng
            double spin = elapsed * (facingLeft ? -0.008 : 0.008);
            g2.translate(vx + drawW / 2.0, vy + drawH / 2.0);
            g2.rotate(spin);
            g2.drawImage(boulderRockSprite, -drawW / 2, -drawH / 2, drawW, drawH, null);
        } else if (type == SkillType.EARTH_SPIKE) {
            // Xoay hàng gai chính xác theo hướng từ Boss đến con trỏ chuột
            double angle = rotationAngle;
            g2.translate(vx + drawW / 2.0, vy + drawH / 2.0);
            g2.rotate(angle);
            if (Math.abs(angle) > Math.PI / 2.0) {
                // Khi ngắm sang nửa màn hình bên trái: lật Y để gai luôn hướng lên tự nhiên
                g2.scale(1, -1);
            }
            g2.drawImage(frame, -drawW / 2, -drawH / 2, drawW, drawH, null);
        } else if (facingLeft) {
            // Lật ngang chuẩn xác bằng scale(-1, 1)
            g2.translate(vx + drawW, vy);
            g2.scale(-1, 1);
            g2.drawImage(frame, 0, 0, drawW, drawH, null);
        } else {
            g2.drawImage(frame, vx, vy, drawW, drawH, null);
        }
        g2.dispose();
    }

    private BufferedImage getFrame(int index) {
        if (type == SkillType.BOULDER_TOSS) {
            return boulderRockSprite;
        }
        if (type == SkillType.ROCKFALL_ZONE) {
            return null;
        }
        BufferedImage[] arr;
        switch (type) {
            case EARTH_SPIKE: arr = earthSpikeFrames; break;
            case ROCKFALL: arr = rockfallFrames; break;
            case WHIRLPOOL: default: arr = whirlpoolFrames; break;
        }
        if (arr != null && index >= 0 && index < arr.length) {
            return arr[index];
        }
        return null;
    }

    public void destroy() {
        if (!active) return;
        active = false;
        Game.loop().detach(this);
        if (Game.world().environment() != null) {
            Game.world().environment().remove(this);
        }
    }
}
