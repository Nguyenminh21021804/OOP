package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.resources.Resources;
import player.Player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Staff extends Weapon {

    /** Hệ số phóng/thu chung cho khiên Aegis và hiệu ứng nổ của Trượng */
    private static final double AEGIS_EFFECT_SCALE = 1.0;

    private static final long CAST_ANIM_MS = 190;
    private static final long SKILL_CAST_ANIM_MS = 230;

    private enum ActionMode {
        IDLE, CAST_BOLT, CAST_AEGIS
    }

    // --- Tài nguyên đánh thường từ staff_crystal_master_sheet.png (4 hàng x 4 cột) ---
    private static boolean assetsLoaded = false;

    private static final BufferedImage[] STAFF_IMG = new BufferedImage[3];
    private static final double[] STAFF_HAND_H = { 16.5, 18.0, 19.5 };

    private static final BufferedImage[][] BOLT_FRAMES = new BufferedImage[3][3];
    private static final double[] BOLT_DRAW_W = { 13.0, 16.0, 18.0 };
    private static final double[] BOLT_DRAW_H = { 6.0, 7.0, 12.0 };

    private static final BufferedImage[] BURST_IMG = new BufferedImage[3];
    private static final double[] BURST_SIZE = { 12.0, 16.0, 21.0 };

    // --- Tài nguyên skill Aegis từ staff_aegis_only_transparent_sheet.png (6 hàng) ---
    private static final BufferedImage[][] AEGIS_FRAMES = new BufferedImage[3][];
    private static final boolean[][] AEGIS_BEAM_FLAGS = new boolean[3][];
    private static final double[] AEGIS_DRAW_W = { 30.0, 58.0, 72.0 };
    private static final double[] AEGIS_DRAW_H = { 30.0, 36.0, 44.0 };
    private static final double[] AEGIS_PIVOT_X = { 0.50, 0.28, 0.30 };
    private static final int[] AEGIS_STARTUP_END = { 3, 1, 1 };
    private static final int[] AEGIS_FADE_START = { 10, 12, 14 };

    private static final BufferedImage[][] AEGIS_IMPACT_FRAMES = new BufferedImage[3][];
    private static double aegisImpactDrawW = 22.0;
    private static double aegisImpactDrawH = 22.0;

    // --- Trạng thái động tác tay & khiên đang bật ---
    private ActionMode mode = ActionMode.IDLE;
    private long actionStartMs = 0;
    private double actionAngle = 0.0;
    private StaffAegisEntity activeAegis = null;

    // Thông số đánh thường theo cấp
    private double boltSpeed = 205.0;
    private double boltRange = 170.0;
    private double boltHitW = 9.0;
    private double boltHitH = 9.0;
    private double boltSplashRadius = 0.0;
    private boolean boltPiercing = false;

    // Thông số skill khiên Aegis theo cấp
    private double aegisShieldRadius = 17.0;
    private double aegisAuraRadius = 23.0;
    private double aegisBeamLength = 0.0;
    private double aegisBeamThickness = 10.0;
    private int aegisBeamDamage = 4;
    private long aegisActiveDurationMs = 2400;
    private int aegisFrameMs = 68;

    public Staff(int initialLevel) {
        super("Truong Lv1", 1, 3, 2, 380, 4200, 30, null);
        ensureAssetsLoaded();
        applyLevel(initialLevel);
    }

    public void applyLevel(int targetLevel) {
        int lv = Math.max(1, Math.min(3, targetLevel));
        this.level = lv;
        resetState();

        switch (lv) {
            case 1 -> {
                this.name = "Truong Lv1";
                this.damage = 3;
                this.skillDamage = 2; // Sát thương mỗi nhịp xung khiên lên quái lại gần
                this.attackCooldownMs = 380;
                this.skillCooldownMs = 4200;
                this.skillManaCost = 30;
                this.boltSpeed = 205.0;
                this.boltRange = 170.0;
                this.boltHitW = 9.0;
                this.boltHitH = 9.0;
                this.boltSplashRadius = 0.0;
                this.boltPiercing = false;
                this.aegisShieldRadius = 17.0;
                this.aegisAuraRadius = 23.0;
                this.aegisBeamLength = 0.0;
                this.aegisBeamThickness = 8.0;
                this.aegisBeamDamage = 2;
                this.aegisActiveDurationMs = 2400;
                this.aegisFrameMs = 70;
            }
            case 2 -> {
                this.name = "Truong Lv2";
                this.damage = 5;
                this.skillDamage = 3; // Sát thương cận thân vòng khiên
                this.attackCooldownMs = 340;
                this.skillCooldownMs = 4800;
                this.skillManaCost = 40;
                this.boltSpeed = 235.0;
                this.boltRange = 200.0;
                this.boltHitW = 12.0;
                this.boltHitH = 9.0;
                this.boltSplashRadius = 15.0;
                this.boltPiercing = false;
                this.aegisShieldRadius = 20.0;
                this.aegisAuraRadius = 26.0;
                this.aegisBeamLength = 46.0;
                this.aegisBeamThickness = 11.0;
                this.aegisBeamDamage = 4; // Sát thương tia đôi pha lê
                this.aegisActiveDurationMs = 3000;
                this.aegisFrameMs = 64;
            }
            case 3 -> {
                this.name = "Truong Lv3";
                this.damage = 7;
                this.skillDamage = 4; // Sát thương cận thân pháp trận khiên kép
                this.attackCooldownMs = 310;
                this.skillCooldownMs = 5400;
                this.skillManaCost = 50;
                this.boltSpeed = 260.0;
                this.boltRange = 235.0;
                this.boltHitW = 15.0;
                this.boltHitH = 14.0;
                this.boltSplashRadius = 20.0;
                this.boltPiercing = true;
                this.aegisShieldRadius = 23.0;
                this.aegisAuraRadius = 30.0;
                this.aegisBeamLength = 58.0;
                this.aegisBeamThickness = 15.0;
                this.aegisBeamDamage = 6; // Sát thương chùm 3 tia pha lê
                this.aegisActiveDurationMs = 3600;
                this.aegisFrameMs = 60;
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
        if (activeAegis != null) {
            activeAegis.cancel();
            activeAegis = null;
        }
    }

    @Override
    protected void performAttack(Player owner) {
        this.mode = ActionMode.CAST_BOLT;
        this.actionStartMs = System.currentTimeMillis();
        this.actionAngle = computeAimAngle(owner);

        spawnMagicBolt(owner);
    }

    @Override
    protected void performSkill(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        this.mode = ActionMode.CAST_AEGIS;
        this.actionStartMs = System.currentTimeMillis();
        this.actionAngle = computeAimAngle(owner);

        if (activeAegis != null) {
            activeAegis.cancel();
            activeAegis = null;
        }

        int lvIdx = level - 1;
        double impMult = (level == 1) ? 0.90 : (level == 2 ? 1.08 : 1.25);

        StaffAegisEntity aegis = new StaffAegisEntity(
                env,
                owner,
                level,
                AEGIS_FRAMES[lvIdx],
                AEGIS_BEAM_FLAGS[lvIdx],
                AEGIS_IMPACT_FRAMES[lvIdx],
                AEGIS_STARTUP_END[lvIdx],
                AEGIS_FADE_START[lvIdx],
                AEGIS_DRAW_W[lvIdx],
                AEGIS_DRAW_H[lvIdx],
                AEGIS_PIVOT_X[lvIdx],
                aegisImpactDrawW * impMult,
                aegisImpactDrawH * impMult,
                aegisShieldRadius,
                aegisAuraRadius,
                aegisBeamLength,
                aegisBeamThickness,
                skillDamage,
                aegisBeamDamage,
                400L,
                250L,
                aegisActiveDurationMs,
                aegisFrameMs,
                36,
                actionAngle
        );
        this.activeAegis = aegis;
        env.add(aegis);
    }

    private void spawnMagicBolt(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        int lvIdx = level - 1;
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.67;
        double desiredLaunchDist = 11.5;

        double clearDist = WeaponCollision.raycastObstacleDistance(env, cx, cy, actionAngle, desiredLaunchDist, 2.0, owner);
        boolean immediateWall = (clearDist < desiredLaunchDist - 1.5);
        double spawnDist = immediateWall ? Math.max(3.5, clearDist) : desiredLaunchDist;

        double sx = cx + Math.cos(actionAngle) * spawnDist;
        double sy = cy + Math.sin(actionAngle) * spawnDist;

        StaffProjectileEntity bolt = new StaffProjectileEntity(
                owner,
                BOLT_FRAMES[lvIdx],
                BURST_IMG[lvIdx],
                damage,
                sx, sy,
                actionAngle,
                boltSpeed,
                boltRange,
                BOLT_DRAW_W[lvIdx],
                BOLT_DRAW_H[lvIdx],
                BURST_SIZE[lvIdx],
                boltHitW,
                boltHitH,
                boltSplashRadius,
                65,
                boltPiercing,
                immediateWall
        );
        env.add(bolt);
    }

    private double computeAimAngle(Player owner) {
        double ax = owner.getAimDirX();
        double ay = owner.getAimDirY();
        if (Math.hypot(ax, ay) < 0.01) {
            return owner.isFacingRight() ? 0.0 : Math.PI;
        }
        return Math.atan2(ay, ax);
    }

    private Point2D.Double getHandWorldPos(Player owner, boolean inAction) {
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.70;

        if (mode == ActionMode.CAST_AEGIS) {
            double dir = owner.isFacingRight() ? 1.0 : -1.0;
            return new Point2D.Double(cx + dir * 4.2, cy - 3.5);
        }

        if (inAction) {
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

        long elapsed = System.currentTimeMillis() - actionStartMs;
        if (mode == ActionMode.CAST_BOLT && elapsed >= CAST_ANIM_MS) {
            mode = ActionMode.IDLE;
        } else if (mode == ActionMode.CAST_AEGIS && elapsed >= SKILL_CAST_ANIM_MS) {
            mode = ActionMode.IDLE;
        }
    }

    @Override
    public void renderInHand(Graphics2D g, Player owner) {
        if (Game.world().camera() == null) return;

        int lvIdx = level - 1;
        BufferedImage img = STAFF_IMG[lvIdx];
        if (img == null) return;

        double drawH = STAFF_HAND_H[lvIdx];
        double ratio = (double) img.getWidth() / Math.max(1, img.getHeight());
        double drawW = drawH * ratio;

        Point2D.Double hand = getHandWorldPos(owner, mode != ActionMode.IDLE);
        Point2D vp = Game.world().camera().getViewportLocation(hand);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());

        if (mode == ActionMode.CAST_AEGIS) {
            // Giơ thẳng trượng lên cao triệu hồi lớp khiên pha lê
            double t = Math.min(1.0, Math.max(0.0, (System.currentTimeMillis() - actionStartMs) / (double) SKILL_CAST_ANIM_MS));
            double liftY = (t < 0.4) ? (-2.5 * (t / 0.4)) : (-2.5 * (1.0 - (t - 0.4) / 0.6));
            if (!owner.isFacingRight()) {
                g2.scale(-1, 1);
            }
            g2.translate(0, liftY);
            g2.rotate(Math.toRadians(6.0));
        } else if (mode == ActionMode.CAST_BOLT) {
            double t = Math.min(1.0, Math.max(0.0, (System.currentTimeMillis() - actionStartMs) / (double) CAST_ANIM_MS));
            double thrust = (t < 0.30) ? (3.2 * (t / 0.30)) : (3.2 * (1.0 - (t - 0.30) / 0.70));

            g2.rotate(actionAngle);
            if (Math.cos(actionAngle) < 0) {
                g2.scale(1, -1);
            }
            g2.translate(thrust, 0);
            g2.rotate(Math.toRadians(82.0));
        } else {
            if (!owner.isFacingRight()) {
                g2.scale(-1, 1);
            }
            double idleDeg = 16.0;
            if (owner.getState() == Player.State.DASH) {
                idleDeg = 38.0;
            } else if (owner.getState() == Player.State.RUN) {
                idleDeg += Math.sin(System.currentTimeMillis() * 0.016) * 5.0;
            }
            g2.rotate(Math.toRadians(idleDeg));
        }

        int gx = (int) Math.round(-drawW / 2.0);
        int gy = (int) Math.round(-drawH * 0.64);
        g2.drawImage(img, gx, gy, (int) Math.round(drawW), (int) Math.round(drawH), null);
        g2.dispose();
    }

    // ==================== NẠP & CẮT ĐỒ HỌA ĐÁNH THƯỜNG + SKILL KHIÊN AEGIS ====================

    private static void ensureAssetsLoaded() {
        if (assetsLoaded) return;
        assetsLoaded = true;

        loadBasicStaffSheet();
        loadAegisSkillSheet();
    }

    private static void loadBasicStaffSheet() {
        BufferedImage sheet = cleanBackgroundIfNeeded(readWeaponImageSafe("weapons/staff_crystal_master_sheet.png"));
        if (sheet == null || sheet.getWidth() < 4 || sheet.getHeight() < 4) {
            System.err.println("[Staff] Khong tim thay hoac anh loi: weapons/staff_crystal_master_sheet.png");
            return;
        }

        int sw = sheet.getWidth();
        int cellW = sw / 4;
        int[][] bands = findRowBands(sheet, 4);

        int r0Y = bands[0][0];
        int r0H = Math.max(1, bands[0][1] - r0Y + 1);
        for (int lv = 0; lv < 3; lv++) {
            STAFF_IMG[lv] = trimTransparent(sheet.getSubimage(lv * cellW, r0Y, cellW, r0H));
        }

        for (int lv = 0; lv < 3; lv++) {
            int rowY = bands[lv + 1][0];
            int rowH = Math.max(1, bands[lv + 1][1] - rowY + 1);

            int minX = cellW, minY = rowH, maxX = -1, maxY = -1;
            for (int col = 0; col < 3; col++) {
                BufferedImage raw = sheet.getSubimage(col * cellW, rowY, cellW, rowH);
                int[] b = findOpaqueBounds(raw);
                if (b != null) {
                    if (b[0] < minX) minX = b[0];
                    if (b[1] < minY) minY = b[1];
                    if (b[2] > maxX) maxX = b[2];
                    if (b[3] > maxY) maxY = b[3];
                }
            }

            if (maxX >= minX && maxY >= minY) {
                int cropW = maxX - minX + 1;
                int cropH = maxY - minY + 1;
                for (int col = 0; col < 3; col++) {
                    BufferedImage raw = sheet.getSubimage(col * cellW, rowY, cellW, rowH);
                    BOLT_FRAMES[lv][col] = raw.getSubimage(minX, minY, cropW, cropH);
                }
                BOLT_DRAW_H[lv] = BOLT_DRAW_W[lv] * ((double) cropH / Math.max(1, cropW));
            } else {
                for (int col = 0; col < 3; col++) {
                    BOLT_FRAMES[lv][col] = sheet.getSubimage(col * cellW, rowY, cellW, rowH);
                }
            }

            int lastColW = sw - 3 * cellW;
            BURST_IMG[lv] = trimTransparent(sheet.getSubimage(3 * cellW, rowY, lastColW, rowH));
        }
    }

    /** Nạp & cắt chuẩn 6 hàng từ staff_aegis_only_transparent_sheet.png */
    private static void loadAegisSkillSheet() {
        BufferedImage sheet = readWeaponImageSafe("weapons/staff_aegis_only_transparent_sheet.png");
        if (sheet == null) {
            System.err.println("[Staff] Khong tim thay weapons/staff_aegis_only_transparent_sheet.png");
            return;
        }

        int sw = sheet.getWidth();
        int[][] bands = findRowBands(sheet, 6);

        double[] targetHeights = { 30.0, 36.0, 44.0 };
        int[] defaultStartup = { 3, 1, 1 };
        int[] defaultFadeCount = { 2, 2, 3 };

        for (int lv = 0; lv < 3; lv++) {
            int y0 = bands[lv][0];
            int y1 = bands[lv][1];
            int bh = Math.max(1, y1 - y0 + 1);

            double[] grid = detectAegisRowGrid(sheet, y0, y1, lv);
            double startX = grid[0];
            double pitch = grid[1];
            int cellW = Math.max(1, (int) Math.round(pitch));
            int maxCols = Math.max(1, (int) Math.ceil((sw - startX) / pitch));

            List<BufferedImage> rawCells = new ArrayList<>();
            int firstNonEmpty = -1;
            int lastNonEmpty = -1;

            for (int col = 0; col < maxCols; col++) {
                int sx = (int) Math.round(startX + col * pitch);
                int ex = (int) Math.round(startX + (col + 1) * pitch);
                if (sx >= sw) break;
                int clampedSx = clamp(sx, 0, sw - 1);
                int clampedEx = clamp(ex, clampedSx + 1, sw);
                BufferedImage cell = slicePaddedCell(sheet, clampedSx, y0, clampedEx - clampedSx, bh, cellW, bh);
                rawCells.add(cell);
                if (!isEmpty(cell)) {
                    if (firstNonEmpty == -1) firstNonEmpty = col;
                    lastNonEmpty = col;
                }
            }

            BufferedImage[] validFrames = (firstNonEmpty >= 0 && lastNonEmpty >= firstNonEmpty)
                    ? rawCells.subList(firstNonEmpty, lastNonEmpty + 1).toArray(new BufferedImage[0])
                    : new BufferedImage[0];
            AEGIS_FRAMES[lv] = validFrames;

            int n = validFrames.length;
            AEGIS_STARTUP_END[lv] = Math.min(defaultStartup[lv], Math.max(0, n - 2));
            AEGIS_FADE_START[lv] = Math.max(AEGIS_STARTUP_END[lv] + 1, n - defaultFadeCount[lv]);

            // Tự tính tâm vòng khiên (pivotRatioX) và đánh dấu các frame bắn tia pha lê từ dữ liệu pixel thật
            boolean[] beamFlags = new boolean[n];
            double pivotX = 0.5;
            if (n >= 4) {
                int refStart = (lv == 0) ? Math.min(3, n - 1) : 1;
                int refEnd = Math.min(n - 1, refStart + 2);
                double sumCenter = 0.0;
                int refCount = 0;
                int baseRightMax = 0;

                for (int i = refStart; i <= refEnd; i++) {
                    int[] b = findOpaqueBounds(validFrames[i]);
                    if (b != null) {
                        sumCenter += (b[0] + b[2]) / 2.0;
                        if (b[2] > baseRightMax) baseRightMax = b[2];
                        refCount++;
                    }
                }
                if (refCount > 0) {
                    pivotX = (sumCenter / refCount) / Math.max(1, cellW);
                }

                if (lv >= 1) {
                    int beamThreshold = baseRightMax + Math.max(4, (int) Math.round(cellW * 0.12));
                    for (int i = 0; i < n; i++) {
                        int[] b = findOpaqueBounds(validFrames[i]);
                        if (b != null && b[2] > beamThreshold) {
                            beamFlags[i] = true;
                        }
                    }
                }
            }
            AEGIS_PIVOT_X[lv] = Math.max(0.15, Math.min(0.85, pivotX));
            AEGIS_BEAM_FLAGS[lv] = beamFlags;

            double scale = (targetHeights[lv] / bh) * AEGIS_EFFECT_SCALE;
            AEGIS_DRAW_W[lv] = pitch * scale;
            AEGIS_DRAW_H[lv] = bh * scale;

            System.out.println(String.format(
                    "[Staff] Aegis Lv%d -> frames=%d, cell=%.2fx%d, pivotX=%.2f, draw=%.1fx%.1f",
                    lv + 1, n, pitch, bh, AEGIS_PIVOT_X[lv], AEGIS_DRAW_W[lv], AEGIS_DRAW_H[lv]
            ));
        }

        // Hàng 3, 4, 5: hiệu ứng tia nổ khi chặn đạn / đánh trúng quái
        int uniformImpH = 1;
        for (int lv = 0; lv < 3; lv++) {
            int bh = Math.max(1, bands[3 + lv][1] - bands[3 + lv][0] + 1);
            if (bh > uniformImpH) uniformImpH = bh;
        }
        double[] impGrid = detectPreciseHitGrid(sheet, bands[3], bands[4], bands[5], uniformImpH);
        sliceBurstRows(sheet, bands[3], bands[4], bands[5], impGrid[0], impGrid[1], uniformImpH, AEGIS_IMPACT_FRAMES);

        double impScale = (22.0 / Math.max(1.0, Math.max(impGrid[1], uniformImpH))) * AEGIS_EFFECT_SCALE;
        aegisImpactDrawW = impGrid[1] * impScale;
        aegisImpactDrawH = uniformImpH * impScale;
    }

    /** Đo chuẩn (startX, pitch) cho từng hàng khiên Aegis (ổn định ngay cả khi Lv2/Lv3 bắn tia dài sang phải) */
    private static double[] detectAegisRowGrid(BufferedImage sheet, int y0, int y1, int lvIdx) {
        int sw = sheet.getWidth();
        int bh = Math.max(1, y1 - y0 + 1);
        int[] colWeight = new int[sw];
        boolean[] hasPixel = new boolean[sw];
        int activeMinX = sw;
        int activeMaxX = -1;

        for (int x = 0; x < sw; x++) {
            for (int y = y0; y <= y1; y++) {
                if (((sheet.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                    colWeight[x]++;
                    hasPixel[x] = true;
                    if (x < activeMinX) activeMinX = x;
                    if (x > activeMaxX) activeMaxX = x;
                }
            }
        }

        if (activeMaxX < activeMinX) {
            return new double[] { 0.0, Math.max(16.0, bh) };
        }

        // Hàng 2 (Lv3) chạy hết bề ngang tấm ảnh (thường chia đúng 18 cột đều từ x=0)
        if (lvIdx == 2) {
            for (int cols : new int[] { 18, 17, 19, 16 }) {
                double p = sw / (double) cols;
                if (computeCutPenalty(colWeight, 0.0, p, activeMinX, activeMaxX) == 0.0) {
                    return new double[] { 0.0, p };
                }
            }
        }

        int mergeGap = Math.max(3, bh / 5);
        List<int[]> blobs = groupTrueRuns(hasPixel, mergeGap);
        List<int[]> mainBlobs = new ArrayList<>();
        int minBlobW = Math.max(6, (int) Math.round(bh * 0.45));
        for (int[] b : blobs) {
            if (b[1] - b[0] + 1 >= minBlobW) {
                mainBlobs.add(b);
            }
        }
        if (mainBlobs.size() < 4) mainBlobs = blobs;

        double estPitch = Math.max(16.0, bh * 1.1);
        if (mainBlobs.size() >= 4) {
            // Dùng khoảng cách giữa các mép trái (b[0]) để không bị lệch khi Lv2/Lv3 bắn tia sang phải
            int sampleCount = Math.min(10, mainBlobs.size() - 1);
            double[] diffs = new double[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                diffs[i] = mainBlobs.get(i + 1)[0] - mainBlobs.get(i)[0];
            }
            Arrays.sort(diffs);
            estPitch = diffs[sampleCount / 2];
        } else if (mainBlobs.size() >= 2) {
            estPitch = mainBlobs.get(1)[0] - mainBlobs.get(0)[0];
        }

        double minP = Math.max(12.0, estPitch - 6.0);
        double maxP = Math.min(sw / 5.0, estPitch + 6.0);
        double bestPitch = estPitch;
        double bestStartX = 0.0;
        double bestScore = Double.MAX_VALUE;

        for (double p = minP; p <= maxP; p += 0.25) {
            for (double sx = 0.0; sx <= Math.min(6.0, p * 0.15); sx += 1.0) {
                double cutPenalty = computeCutPenalty(colWeight, sx, p, activeMinX, activeMaxX);
                double alignErr = computeLeftAlignError(mainBlobs, sx, p);
                double zeroBias = (sx == 0.0) ? -0.2 : 0.0;
                double score = cutPenalty + alignErr + zeroBias;
                if (score < bestScore) {
                    bestScore = score;
                    bestPitch = p;
                    bestStartX = sx;
                }
            }
        }

        return new double[] { bestStartX, bestPitch };
    }

    private static double computeCutPenalty(int[] colWeight, double sx, double pitch, int activeMinX, int activeMaxX) {
        int sw = colWeight.length;
        int maxC = (int) Math.ceil((activeMaxX - sx) / pitch) + 1;
        double penalty = 0.0;
        for (int k = 1; k < maxC; k++) {
            int bx = (int) Math.round(sx + k * pitch);
            if (bx >= activeMinX && bx <= activeMaxX && bx < sw) {
                penalty += colWeight[bx] * 120.0;
            }
        }
        return penalty;
    }

    private static double computeLeftAlignError(List<int[]> mainBlobs, double sx, double pitch) {
        int count = Math.min(10, mainBlobs.size());
        if (count < 2) return 0.0;

        double[] offsets = new double[count];
        for (int i = 0; i < count; i++) {
            double left = mainBlobs.get(i)[0];
            double rel = (left - sx) / pitch;
            int col = (int) Math.floor(rel);
            offsets[i] = left - (sx + col * pitch);
        }
        Arrays.sort(offsets);
        double median = offsets[count / 2];

        double err = 0.0;
        for (double off : offsets) {
            err += Math.abs(off - median);
        }
        return err / count;
    }

    private static void sliceBurstRows(
            BufferedImage sheet,
            int[] bandLv1,
            int[] bandLv2,
            int[] bandLv3,
            double startX,
            double pitch,
            int uniformH,
            BufferedImage[][] dest
    ) {
        int sw = sheet.getWidth();
        int sh = sheet.getHeight();
        int targetCellW = Math.max(1, (int) Math.round(pitch));
        int[][] bands = { bandLv1, bandLv2, bandLv3 };

        for (int lv = 0; lv < 3; lv++) {
            int yMid = (bands[lv][0] + bands[lv][1]) / 2;
            int y0 = clamp(yMid - uniformH / 2, 0, Math.max(0, sh - uniformH));
            int actualH = Math.min(uniformH, sh - y0);

            List<BufferedImage> valid = new ArrayList<>();
            for (int col = 0; col < 12; col++) {
                int sx = clamp((int) Math.round(startX + col * pitch), 0, sw - 1);
                int ex = clamp((int) Math.round(startX + (col + 1) * pitch), sx + 1, sw);
                BufferedImage cell = slicePaddedCell(sheet, sx, y0, ex - sx, actualH, targetCellW, uniformH);
                if (!isEmpty(cell)) {
                    valid.add(cell);
                }
            }
            dest[lv] = valid.toArray(new BufferedImage[0]);
        }
    }

    private static double[] detectPreciseHitGrid(
            BufferedImage sheet,
            int[] bandLv1,
            int[] bandLv2,
            int[] bandLv3,
            int uniformHitH
    ) {
        int sw = sheet.getWidth();
        int[] colWeight = new int[sw];
        boolean[] hasPixel = new boolean[sw];
        int[][] hitBands = { bandLv1, bandLv2, bandLv3 };

        for (int[] b : hitBands) {
            int y0 = clamp(b[0], 0, sheet.getHeight() - 1);
            int y1 = clamp(b[1], y0, sheet.getHeight() - 1);
            for (int x = 0; x < sw; x++) {
                for (int y = y0; y <= y1; y++) {
                    if (((sheet.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                        colWeight[x]++;
                        hasPixel[x] = true;
                    }
                }
            }
        }

        int mergeGap = Math.max(2, uniformHitH / 6);
        List<int[]> blobs = groupTrueRuns(hasPixel, mergeGap);

        double estPitch = Math.max(12.0, Math.min(sw / 12.0, uniformHitH * 1.15));
        double c0 = estPitch / 2.0;

        if (blobs.size() >= 4) {
            double[] centers = new double[4];
            for (int i = 0; i < 4; i++) {
                centers[i] = (blobs.get(i)[0] + blobs.get(i)[1]) / 2.0;
            }
            c0 = centers[0];
            double[] diffs = { centers[1] - centers[0], centers[2] - centers[1], centers[3] - centers[2] };
            Arrays.sort(diffs);
            estPitch = diffs[1];
        } else if (blobs.size() >= 2) {
            c0 = (blobs.get(0)[0] + blobs.get(0)[1]) / 2.0;
            estPitch = ((blobs.get(1)[0] + blobs.get(1)[1]) / 2.0) - c0;
        }

        double minP = Math.max(8.0, estPitch - 6.0);
        double maxP = Math.min(sw / 12.0 + 1.0, estPitch + 6.0);
        double bestPitch = estPitch;
        double bestStartX = Math.max(0.0, c0 - estPitch / 2.0);
        double bestScore = Double.MAX_VALUE;

        for (double p = minP; p <= maxP; p += 0.25) {
            double baseStart = c0 - p / 2.0;
            double[] startCandidates = new double[14];
            startCandidates[0] = 0.0;
            for (int s = 0; s < 13; s++) {
                startCandidates[s + 1] = Math.max(0.0, baseStart + (s - 6) * 0.5);
            }

            for (double sx : startCandidates) {
                if (sx + p * 11.5 > sw + 2.0) continue;

                double cutPenalty = 0.0;
                for (int k = 1; k < 12; k++) {
                    int bx = (int) Math.round(sx + k * p);
                    if (bx >= 0 && bx < sw) {
                        cutPenalty += colWeight[bx] * 100.0;
                    }
                }

                double centerErr = 0.0;
                int counted = 0;
                for (int k = 0; k < 8; k++) {
                    int x0 = clamp((int) Math.round(sx + k * p), 0, sw - 1);
                    int x1 = clamp((int) Math.round(sx + (k + 1) * p) - 1, x0, sw - 1);
                    int minX = sw, maxX = -1;
                    for (int x = x0; x <= x1; x++) {
                        if (hasPixel[x]) {
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                        }
                    }
                    if (maxX >= minX) {
                        centerErr += Math.abs(((minX + maxX) / 2.0) - ((x0 + x1) / 2.0));
                        counted++;
                    }
                }

                double score = cutPenalty + (counted > 0 ? (centerErr / counted) : 999.0);
                if (score < bestScore) {
                    bestScore = score;
                    bestPitch = p;
                    bestStartX = sx;
                }
            }
        }

        return new double[] { bestStartX, bestPitch };
    }

    private static int[][] findRowBands(BufferedImage sheet, int targetRows) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        boolean[] hasPixel = new boolean[h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((sheet.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                    hasPixel[y] = true;
                    break;
                }
            }
        }

        List<int[]> bands = groupTrueRuns(hasPixel, 2);
        while (bands.size() > targetRows) {
            int bestIdx = 0;
            int minGap = Integer.MAX_VALUE;
            for (int i = 0; i < bands.size() - 1; i++) {
                int gap = bands.get(i + 1)[0] - bands.get(i)[1];
                if (gap < minGap) {
                    minGap = gap;
                    bestIdx = i;
                }
            }
            bands.get(bestIdx)[1] = bands.get(bestIdx + 1)[1];
            bands.remove(bestIdx + 1);
        }

        int[][] out = new int[targetRows][2];
        if (bands.size() == targetRows) {
            for (int i = 0; i < targetRows; i++) out[i] = bands.get(i);
        } else {
            int rowH = Math.max(1, h / targetRows);
            for (int i = 0; i < targetRows; i++) {
                out[i][0] = i * rowH;
                out[i][1] = Math.min(h - 1, (i + 1) * rowH - 1);
            }
        }
        return out;
    }

    private static List<int[]> groupTrueRuns(boolean[] flags, int maxGapPx) {
        List<int[]> raw = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < flags.length; i++) {
            if (flags[i]) {
                if (start == -1) start = i;
            } else if (start != -1) {
                raw.add(new int[] { start, i - 1 });
                start = -1;
            }
        }
        if (start != -1) raw.add(new int[] { start, flags.length - 1 });

        List<int[]> merged = new ArrayList<>();
        for (int[] seg : raw) {
            if (!merged.isEmpty() && seg[0] - merged.get(merged.size() - 1)[1] - 1 <= maxGapPx) {
                merged.get(merged.size() - 1)[1] = seg[1];
            } else {
                merged.add(new int[] { seg[0], seg[1] });
            }
        }
        return merged;
    }

    private static BufferedImage slicePaddedCell(
            BufferedImage sheet,
            int sx,
            int sy,
            int cw,
            int ch,
            int targetW,
            int targetH
    ) {
        BufferedImage sub = sheet.getSubimage(sx, sy, cw, ch);
        if (cw == targetW && ch == targetH) return sub;
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(sub, (targetW - cw) / 2, (targetH - ch) / 2, null);
        g.dispose();
        return out;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static boolean isEmpty(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 10) return false;
            }
        }
        return true;
    }

    private static int[] findOpaqueBounds(BufferedImage img) {
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
        return (maxX >= minX && maxY >= minY) ? new int[] { minX, minY, maxX, maxY } : null;
    }

    private static BufferedImage trimTransparent(BufferedImage img) {
        if (img == null) return null;
        int[] b = findOpaqueBounds(img);
        if (b == null) return img;
        return img.getSubimage(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1);
    }

    private static BufferedImage readWeaponImageSafe(String path) {
        if (path == null || path.isEmpty()) return null;

        try {
            BufferedImage img = Resources.images().get(path);
            if (img != null) return img;
        } catch (Throwable ignored) {
        }

        try (InputStream is = Staff.class.getResourceAsStream("/" + path)) {
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
}