package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.resources.Resources;
import player.Player;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class Sword extends Weapon {

    /** Hệ số phóng/thu chung cho hiệu ứng chém, trúng đòn và skill */
    private static final double EFFECT_SCALE = 1.0;

    private static final long COMBO_WINDOW_MS = 800;
    private static final long SKILL_THRUST_MS = 150;

    // Bảng góc vung kiếm theo từng mốc frame (0..4) khớp chặt với 3 nhịp của chem_sheet_1x_2.png
    private static final double[][] COMBO_SWING_KEYS = {
        { -10.0,  38.0, 128.0, 154.0, 164.0 }, // Nhịp 1: chém từ trên xuống
        { 172.0, 132.0,  34.0,  12.0,   4.0 }, // Nhịp 2: chém hất từ dưới lên
        { -20.0,  42.0, 152.0, 178.0, 188.0 }  // Nhịp 3: chém vòng cung rộng
    };

    private enum ActionMode {
        IDLE, ATTACK, SKILL_THRUST
    }

    // --- Dữ liệu ảnh dùng chung cho cả 3 cấp ---
    private static boolean assetsLoaded = false;

    // 1. Hình kiếm cầm tay (dựng thẳng mũi hướng lên): Lv1, Lv2, Lv3
    private static final BufferedImage[] SWORD_IMG = new BufferedImage[3];
    private static final double[] SWORD_HAND_H = { 13.0, 14.5, 16.5 };

    // 2. chem_sheet_1x_2.png (9 hàng x 4 cột -> [cấp 0..2][nhịp combo 0..2][frame 0..3])
    private static final BufferedImage[][][] SLASH_FRAMES = new BufferedImage[3][3][4];
    private static double slashDrawW = 34.0;
    private static double slashDrawH = 34.0;

    // 3. trung_don_sheet_1x.png (9 hàng x 3 cột -> [cấp 0..2][nhịp combo 0..2][frame 0..2])
    private static final BufferedImage[][][] HIT_FRAMES = new BufferedImage[3][3][3];
    private static double hitDrawW = 28.0;
    private static double hitDrawH = 28.0;

    // 4. skill_tong_hop_1x.png (3 hàng x N cột -> tách 4 pha: tạo chiêu, bay lặp, tan chiêu, nổ 5 frame)
    private static final BufferedImage[][] SKILL_LAUNCH = new BufferedImage[3][];
    private static final BufferedImage[][] SKILL_FLY = new BufferedImage[3][];
    private static final BufferedImage[][] SKILL_FADE = new BufferedImage[3][];
    private static final BufferedImage[][] SKILL_IMPACT = new BufferedImage[3][];
    private static double skillDrawW = 34.0;
    private static double skillDrawH = 34.0;

    // --- Trạng thái combo & ra chiêu ---
    private ActionMode mode = ActionMode.IDLE;
    private long actionStartMs = 0;
    private double actionAngle = 0.0;
    private int comboStep = 0;
    private long lastAttackEndMs = 0;
    private boolean wallSparkSpawned = false;

    private Rectangle2D activeDebugHitbox = null;
    private final Set<ICombatEntity> hitTargets = new HashSet<>();

    // Thông số theo cấp
    private int slashFrameMs = 80;
    private double slashOffset = 5.5;
    private double slashReach = 13.0;
    private double slashHitW = 15.0;
    private double slashHitH = 17.0;

    public Sword(int initialLevel) {
        super("Kiem Lv1", 1, 2, 5, 340, 1800, 20, null);
        ensureAssetsLoaded();
        applyLevel(initialLevel);
    }

    public void applyLevel(int targetLevel) {
        int lv = Math.max(1, Math.min(3, targetLevel));
        this.level = lv;
        resetState();

        switch (lv) {
            case 1 -> {
                this.name = "Kiem Lv1";
                this.damage = 2;
                this.skillDamage = 5;
                this.attackCooldownMs = 340;
                this.skillCooldownMs = 1800;
                this.skillManaCost = 20;
                this.slashFrameMs = 80; // 4 frame = 320ms
                this.slashOffset = 5.5;
                this.slashReach = 13.0;
                this.slashHitW = 15.0;
                this.slashHitH = 17.0;
            }
            case 2 -> {
                this.name = "Kiem Lv2";
                this.damage = 4;
                this.skillDamage = 9;
                this.attackCooldownMs = 320;
                this.skillCooldownMs = 2000;
                this.skillManaCost = 30;
                this.slashFrameMs = 75; // 4 frame = 300ms
                this.slashOffset = 6.5;
                this.slashReach = 15.5;
                this.slashHitW = 18.0;
                this.slashHitH = 20.0;
            }
            case 3 -> {
                this.name = "Kiem Lv3";
                this.damage = 6;
                this.skillDamage = 14;
                this.attackCooldownMs = 300;
                this.skillCooldownMs = 2200;
                this.skillManaCost = 40;
                this.slashFrameMs = 70; // 4 frame = 280ms
                this.slashOffset = 7.5;
                this.slashReach = 18.0;
                this.slashHitW = 22.0;
                this.slashHitH = 24.0;
            }
        }
    }

    @Override
    public void cycleLevel() {
        applyLevel((this.level % 3) + 1);
    }

    @Override
    public boolean isBusy() {
        return mode != ActionMode.IDLE;
    }

    @Override
    public void resetState() {
        this.mode = ActionMode.IDLE;
        this.activeDebugHitbox = null;
        this.hitTargets.clear();
        this.wallSparkSpawned = false;
    }

    @Override
    protected void performAttack(Player owner) {
        long now = System.currentTimeMillis();
        if (now - lastAttackEndMs <= COMBO_WINDOW_MS) {
            this.comboStep = (this.comboStep + 1) % 3;
        } else {
            this.comboStep = 0;
        }

        this.mode = ActionMode.ATTACK;
        this.actionStartMs = now;
        this.actionAngle = computeAimAngle(owner);
        this.hitTargets.clear();
        this.activeDebugHitbox = null;
        this.wallSparkSpawned = false;
    }

    @Override
    protected void performSkill(Player owner) {
        long now = System.currentTimeMillis();
        this.mode = ActionMode.SKILL_THRUST;
        this.actionStartMs = now;
        this.actionAngle = computeAimAngle(owner);
        this.hitTargets.clear();
        this.activeDebugHitbox = null;

        spawnSkillProjectile(owner);
    }

    private double computeAimAngle(Player owner) {
        double ax = owner.getAimDirX();
        double ay = owner.getAimDirY();
        if (Math.hypot(ax, ay) < 0.01) {
            return owner.isFacingRight() ? 0.0 : Math.PI;
        }
        return Math.atan2(ay, ax);
    }

    /** Vị trí bàn tay bám sát hông hiệp sĩ, tránh che mặt */
    private Point2D.Double getHandWorldPos(Player owner, boolean useActionAngle) {
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.71;

        if (useActionAngle) {
            return new Point2D.Double(
                    cx + Math.cos(actionAngle) * 4.5,
                    cy + Math.sin(actionAngle) * 2.5
            );
        }

        double dir = owner.isFacingRight() ? 1.0 : -1.0;
        long now = System.currentTimeMillis();
        double bobY = (owner.getState() == Player.State.RUN)
                ? Math.sin(now * 0.016) * 0.65
                : Math.sin(now * 0.005) * 0.30;
        return new Point2D.Double(cx + dir * 5.2, cy + bobY);
    }

    @Override
    public void update(Player owner) {
        if (owner == null || owner.isDead()) {
            resetState();
            return;
        }

        long now = System.currentTimeMillis();
        activeDebugHitbox = null;

        if (mode == ActionMode.ATTACK) {
            long elapsed = now - actionStartMs;
            int frameIdx = (int) (elapsed / slashFrameMs);

            if (frameIdx >= 4) {
                this.lastAttackEndMs = now;
                resetState();
                return;
            }

            if (frameIdx >= 1 && frameIdx <= 2) {
                Point2D.Double hand = getHandWorldPos(owner, true);
                double hx = hand.x + Math.cos(actionAngle) * slashReach;
                double hy = hand.y + Math.sin(actionAngle) * slashReach;
                double bonusScale = (comboStep == 2) ? 1.15 : 1.0;
                double w = slashHitW * bonusScale;
                double h = slashHitH * bonusScale;

                Rectangle2D box = new Rectangle2D.Double(hx - w / 2.0, hy - h / 2.0, w, h);
                activeDebugHitbox = box;

                int hitDmg = (comboStep == 2) ? (damage + 1) : damage;
                applyMeleeDamage(owner, box, hitDmg);

                if (!wallSparkSpawned) {
                    checkWallSpark(owner, hand);
                }
            }
        } else if (mode == ActionMode.SKILL_THRUST) {
            if (now - actionStartMs >= SKILL_THRUST_MS) {
                resetState();
            }
        }
    }

    private void checkWallSpark(Player owner, Point2D.Double hand) {
        Environment env = Game.world().environment();
        if (env == null) return;

        double maxDist = slashReach + slashHitW * 0.35;
        double clear = WeaponCollision.raycastObstacleDistance(env, hand.x, hand.y, actionAngle, maxDist, 2.0, owner);
        if (clear < maxDist - 1.5) {
            wallSparkSpawned = true;
            double sx = hand.x + Math.cos(actionAngle) * (clear + 1.5);
            double sy = hand.y + Math.sin(actionAngle) * (clear + 1.5);
            spawnHitEffect(env, sx, sy, level - 1, comboStep);
        }
    }

    private void applyMeleeDamage(Player owner, Rectangle2D box, int dmg) {
        Environment env = Game.world().environment();
        if (env == null) return;

        double ox = owner.getX() + owner.getWidth() / 2.0;
        double oy = owner.getY() + owner.getHeight() * 0.65;

        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;
            if (hitTargets.contains(target)) continue;

            Rectangle2D tb = target.getBoundingBox();
            if (tb.intersects(box)
                    && WeaponCollision.hasLineOfSight(env, ox, oy, tb.getCenterX(), tb.getCenterY(), owner)) {
                target.hit(dmg);
                hitTargets.add(target);
                spawnHitEffect(env, tb.getCenterX(), tb.getCenterY(), level - 1, comboStep);
            }
        }
    }

    private void spawnHitEffect(Environment env, double x, double y, int lvIdx, int step) {
        BufferedImage[] frames = HIT_FRAMES[lvIdx][step];
        if (frames == null || frames.length == 0 || frames[0] == null) return;

        env.add(new HitEffectEntity(
                frames,
                x, y,
                hitDrawW, hitDrawH,
                Math.cos(actionAngle) < 0,
                70
        ));
    }

    private void spawnSkillProjectile(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        int lvIdx = level - 1;
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.68;
        double desiredLaunchDist = 11.0;

        double clearDist = WeaponCollision.raycastObstacleDistance(env, cx, cy, actionAngle, desiredLaunchDist, 2.5, owner);
        boolean immediateImpact = (clearDist < desiredLaunchDist - 1.5);
        double spawnDist = immediateImpact ? Math.max(4.0, clearDist) : desiredLaunchDist;
        double sx = cx + Math.cos(actionAngle) * spawnDist;
        double sy = cy + Math.sin(actionAngle) * spawnDist;

        double projSpeed = (level == 1) ? 195.0 : (level == 2 ? 210.0 : 245.0);
        double projRange = (level == 1) ? 145.0 : (level == 2 ? 175.0 : 210.0);
        double projHitW = (level == 1) ? 14.0 : (level == 2 ? 18.0 : 22.0);
        double projHitH = (level == 1) ? 14.0 : (level == 2 ? 18.0 : 14.0);
        boolean piercing = (level >= 2);

        SwordSkillProjectile proj = new SwordSkillProjectile(
                owner,
                SKILL_LAUNCH[lvIdx],
                SKILL_FLY[lvIdx],
                SKILL_FADE[lvIdx],
                SKILL_IMPACT[lvIdx],
                HIT_FRAMES[lvIdx][2],
                skillDamage,
                sx, sy,
                actionAngle,
                projSpeed,
                projRange,
                skillDrawW,
                skillDrawH,
                hitDrawW,
                hitDrawH,
                projHitW,
                projHitH,
                34,
                38,
                48,
                52,
                piercing,
                immediateImpact
        );
        env.add(proj);
    }

    // ==================== VẼ KIẾM TRÊN TAY & VỆT CHÉM ====================

    @Override
    public void renderInHand(Graphics2D g, Player owner) {
        if (Game.world().camera() == null) return;
        int lvIdx = level - 1;

        if (mode == ActionMode.ATTACK) {
            long elapsed = System.currentTimeMillis() - actionStartMs;
            double framePos = Math.min(4.0, Math.max(0.0, (double) elapsed / slashFrameMs));
            int f = Math.min(3, (int) Math.floor(framePos));

            // 1. Vẽ vệt chém 4 frame từ chem_sheet_1x_2.png ôm sát tay nhân vật
            BufferedImage slashImg = SLASH_FRAMES[lvIdx][comboStep][f];
            if (slashImg != null) {
                drawSlashArc(g, owner, slashImg);
            }

            // 2. Vẽ thân kiếm nổi lên trên, bám sát mép dẫn của từng frame vệt chém
            double swingRad = computeSwingAngleRad(comboStep, framePos);
            drawSwordBlade(g, owner, lvIdx, true, swingRad);
        } else if (mode == ActionMode.SKILL_THRUST) {
            drawSwordBlade(g, owner, lvIdx, true, Math.PI / 2.0);
        } else {
            drawSwordBlade(g, owner, lvIdx, false, 0.0);
        }

        if (activeDebugHitbox != null && Game.config().debug().renderCollisionBoxes()) {
            Point2D bvp = Game.world().camera().getViewportLocation(
                    new Point2D.Double(activeDebugHitbox.getX(), activeDebugHitbox.getY()));
            Graphics2D dg = (Graphics2D) g.create();
            dg.setColor(new Color(255, 220, 40, 210));
            dg.drawRect(
                    (int) Math.round(bvp.getX()),
                    (int) Math.round(bvp.getY()),
                    (int) Math.round(activeDebugHitbox.getWidth()),
                    (int) Math.round(activeDebugHitbox.getHeight())
            );
            dg.dispose();
        }
    }

    private double computeSwingAngleRad(int step, double framePos) {
        double[] keys = COMBO_SWING_KEYS[Math.max(0, Math.min(2, step))];
        int seg = Math.min(3, Math.max(0, (int) Math.floor(framePos)));
        double localT = Math.min(1.0, Math.max(0.0, framePos - seg));
        double deg = keys[seg] + (keys[seg + 1] - keys[seg]) * localT;
        return Math.toRadians(deg);
    }

    private void drawSwordBlade(Graphics2D g, Player owner, int lvIdx, boolean inAction, double localSwingRad) {
        BufferedImage img = SWORD_IMG[lvIdx];
        if (img == null) return;

        double drawH = SWORD_HAND_H[lvIdx];
        double ratio = (double) img.getWidth() / Math.max(1, img.getHeight());
        double drawW = drawH * ratio;

        Point2D.Double hand = getHandWorldPos(owner, inAction);
        Point2D vp = Game.world().camera().getViewportLocation(hand);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());

        if (inAction) {
            g2.rotate(actionAngle);
            if (Math.cos(actionAngle) < 0) {
                g2.scale(1, -1);
            }
            if (mode == ActionMode.SKILL_THRUST) {
                g2.translate(2.5, 0);
            } else if (mode == ActionMode.ATTACK) {
                g2.translate(1.8, 0); // Đẩy nhẹ tay chém về trước để mũi kiếm chạm đúng viền ngoài vệt chém
            }
            g2.rotate(localSwingRad);
        } else {
            boolean facingRight = owner.isFacingRight();
            if (!facingRight) {
                g2.scale(-1, 1);
            }
            double idleDeg = 26.0;
            if (owner.getState() == Player.State.DASH) {
                idleDeg = 42.0;
            } else if (owner.getState() == Player.State.RUN) {
                idleDeg += Math.sin(System.currentTimeMillis() * 0.016) * 6.0;
            }
            g2.rotate(Math.toRadians(idleDeg));
        }

        int gx = (int) Math.round(-drawW / 2.0);
        int gy = (int) Math.round(-drawH * 0.78);
        g2.drawImage(img, gx, gy, (int) Math.round(drawW), (int) Math.round(drawH), null);
        g2.dispose();
    }

    private void drawSlashArc(Graphics2D g, Player owner, BufferedImage frame) {
        Point2D.Double hand = getHandWorldPos(owner, true);
        double sx = hand.x + Math.cos(actionAngle) * slashOffset;
        double sy = hand.y + Math.sin(actionAngle) * slashOffset;
        Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(sx, sy));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());
        g2.rotate(actionAngle);
        if (Math.cos(actionAngle) < 0) {
            g2.scale(1, -1);
        }

        g2.drawImage(
                frame,
                (int) Math.round(-slashDrawW / 2.0),
                (int) Math.round(-slashDrawH / 2.0),
                (int) Math.round(slashDrawW),
                (int) Math.round(slashDrawH),
                null
        );
        g2.dispose();
    }

    // ==================== NẠP & CẮT ĐỒ HỌA KIẾM AN TOÀN ====================

    private static void ensureAssetsLoaded() {
        if (assetsLoaded) return;
        assetsLoaded = true;

        // 1. Hình kiếm Lv1, Lv2, Lv3
        SWORD_IMG[0] = trimTransparent(loadWeaponImage("weapons/sword_lv1_2.png", "weapons/sword_lv1.png"));
        SWORD_IMG[1] = trimTransparent(loadWeaponImage("weapons/sword_lv2_2.png", "weapons/sword_lv2.png"));
        SWORD_IMG[2] = trimTransparent(loadWeaponImage("weapons/sword_lv3_2.png", "weapons/sword_lv3.png"));

        // 2. chem_sheet_1x_2.png (9 hàng x 4 cột: mỗi cấp 3 nhịp combo)
        BufferedImage slashSheet = loadWeaponImage("weapons/chem_sheet_1x_2.png", "weapons/chem_sheet_1x.png");
        if (slashSheet != null && slashSheet.getWidth() >= 4 && slashSheet.getHeight() >= 9) {
            int cellW = slashSheet.getWidth() / 4;
            int cellH = slashSheet.getHeight() / 9;
            double scale = computeAutoScale(cellH, 34.0) * EFFECT_SCALE;
            slashDrawW = cellW * scale;
            slashDrawH = cellH * scale;

            for (int lv = 0; lv < 3; lv++) {
                for (int step = 0; step < 3; step++) {
                    int row = lv * 3 + step;
                    for (int col = 0; col < 4; col++) {
                        SLASH_FRAMES[lv][step][col] = slashSheet.getSubimage(col * cellW, row * cellH, cellW, cellH);
                    }
                }
            }
        } else {
            System.err.println("[Sword] Khong tim thay hoac anh loi: weapons/chem_sheet_1x_2.png");
        }

        // 3. trung_don_sheet_1x.png (9 hàng x 3 cột)
        BufferedImage hitSheet = loadWeaponImage("weapons/trung_don_sheet_1x.png");
        if (hitSheet != null && hitSheet.getWidth() >= 3 && hitSheet.getHeight() >= 9) {
            int cellW = hitSheet.getWidth() / 3;
            int cellH = hitSheet.getHeight() / 9;
            double scale = computeAutoScale(cellH, 28.0) * EFFECT_SCALE;
            hitDrawW = cellW * scale;
            hitDrawH = cellH * scale;

            for (int lv = 0; lv < 3; lv++) {
                for (int step = 0; step < 3; step++) {
                    int row = lv * 3 + step;
                    for (int col = 0; col < 3; col++) {
                        HIT_FRAMES[lv][step][col] = hitSheet.getSubimage(col * cellW, row * cellH, cellW, cellH);
                    }
                }
            }
        } else {
            System.err.println("[Sword] Khong tim thay hoac anh loi: weapons/trung_don_sheet_1x.png");
        }

        // 4. skill_tong_hop_1x.png (3 hàng x N cột)
        BufferedImage skillSheet = loadWeaponImage("weapons/skill_tong_hop_1x.png");
        if (skillSheet != null && skillSheet.getHeight() >= 3) {
            int rowH = skillSheet.getHeight() / 3;
            int cols = (skillSheet.getWidth() >= rowH * 6 && skillSheet.getWidth() % rowH == 0)
                    ? (skillSheet.getWidth() / rowH)
                    : 22;
            cols = Math.max(1, Math.min(cols, skillSheet.getWidth()));
            int cellW = Math.max(1, skillSheet.getWidth() / cols);
            double scale = computeAutoScale(rowH, 34.0) * EFFECT_SCALE;
            skillDrawW = cellW * scale;
            skillDrawH = rowH * scale;

            int impactCount = Math.min(5, Math.max(1, cols / 4));
            int impactStart = Math.max(0, cols - impactCount);
            int launchCount = Math.min(3, Math.max(1, impactStart / 3));
            int fadeCount = Math.min(4, Math.max(1, (impactStart - launchCount) / 3));
            int flyStart = Math.min(launchCount, impactStart);
            int flyEnd = Math.max(flyStart, impactStart - fadeCount);
            int fadeStart = flyEnd;
            int actualFadeCount = Math.max(0, impactStart - fadeStart);

            for (int lv = 0; lv < 3; lv++) {
                int y = lv * rowH;
                SKILL_LAUNCH[lv] = sliceRowRange(skillSheet, y, cellW, rowH, 0, launchCount);
                SKILL_FLY[lv] = sliceRowRange(skillSheet, y, cellW, rowH, flyStart, Math.max(1, flyEnd - flyStart));
                SKILL_FADE[lv] = sliceRowRange(skillSheet, y, cellW, rowH, fadeStart, actualFadeCount);
                SKILL_IMPACT[lv] = sliceRowRange(skillSheet, y, cellW, rowH, impactStart, impactCount);
            }
        } else {
            System.err.println("[Sword] Khong tim thay hoac anh loi: weapons/skill_tong_hop_1x.png");
        }
    }

    private static BufferedImage[] sliceRowRange(BufferedImage sheet, int y, int cellW, int cellH, int startCol, int count) {
        if (count <= 0) return new BufferedImage[0];
        BufferedImage[] out = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            int sx = (startCol + i) * cellW;
            if (sx >= 0 && y >= 0 && sx + cellW <= sheet.getWidth() && y + cellH <= sheet.getHeight()) {
                out[i] = sheet.getSubimage(sx, y, cellW, cellH);
            }
        }
        return out;
    }

    private static double computeAutoScale(int cellH, double targetWorldPx) {
        if (cellH <= 0) return 1.0;
        if (cellH <= targetWorldPx) return 0.92;
        return targetWorldPx / cellH;
    }

    private static BufferedImage loadWeaponImage(String... candidatePaths) {
        for (String path : candidatePaths) {
            BufferedImage raw = readWeaponImageSafe(path);
            if (raw != null) {
                return cleanBackgroundIfNeeded(raw);
            }
        }
        System.err.println("[Sword] Khong tim thay anh: " + candidatePaths[0]);
        return null;
    }

    private static BufferedImage readWeaponImageSafe(String path) {
        if (path == null || path.isEmpty()) return null;

        try {
            BufferedImage img = Resources.images().get(path);
            if (img != null) return img;
        } catch (Throwable ignored) {
        }

        try (InputStream is = Sword.class.getResourceAsStream("/" + path)) {
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) return img;
            }
        } catch (Throwable ignored) {
        }

        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) return img;
            }
        } catch (Throwable ignored) {
        }

        for (String prefix : new String[] { "app/src/main/resources/", "src/main/resources/" }) {
            File f = new File(prefix + path);
            if (f.exists() && f.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) return img;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static BufferedImage cleanBackgroundIfNeeded(BufferedImage src) {
        if (src == null) return null;
        int cornerArgb = src.getRGB(0, 0);
        if (((cornerArgb >>> 24) & 0xFF) == 0) {
            return src;
        }

        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        int bgR = (cornerArgb >> 16) & 0xFF;
        int bgG = (cornerArgb >> 8) & 0xFF;
        int bgB = cornerArgb & 0xFF;

        boolean[] visited = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            enqueueIfBg(out, visited, queue, x, 0, w, bgR, bgG, bgB);
            enqueueIfBg(out, visited, queue, x, h - 1, w, bgR, bgG, bgB);
        }
        for (int y = 0; y < h; y++) {
            enqueueIfBg(out, visited, queue, 0, y, w, bgR, bgG, bgB);
            enqueueIfBg(out, visited, queue, w - 1, y, w, bgR, bgG, bgB);
        }

        while (!queue.isEmpty()) {
            int pos = queue.poll();
            int x = pos % w;
            int y = pos / w;
            out.setRGB(x, y, 0x00000000);

            if (x > 0) enqueueIfBg(out, visited, queue, x - 1, y, w, bgR, bgG, bgB);
            if (x + 1 < w) enqueueIfBg(out, visited, queue, x + 1, y, w, bgR, bgG, bgB);
            if (y > 0) enqueueIfBg(out, visited, queue, x, y - 1, w, bgR, bgG, bgB);
            if (y + 1 < h) enqueueIfBg(out, visited, queue, x, y + 1, w, bgR, bgG, bgB);
        }
        return out;
    }

    private static void enqueueIfBg(BufferedImage img, boolean[] visited, ArrayDeque<Integer> q,
                                    int x, int y, int w, int bgR, int bgG, int bgB) {
        int idx = y * w + x;
        if (visited[idx]) return;
        int argb = img.getRGB(x, y);
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if (a == 0 || (Math.abs(r - bgR) <= 10 && Math.abs(g - bgG) <= 10 && Math.abs(b - bgB) <= 10)) {
            visited[idx] = true;
            q.add(idx);
        }
    }

    private static BufferedImage trimTransparent(BufferedImage img) {
        if (img == null) return null;
        int w = img.getWidth();
        int h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return img;
        return img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}