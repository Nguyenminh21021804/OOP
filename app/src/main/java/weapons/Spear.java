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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Spear extends Weapon {

    /** Hệ số phóng/thu chung cho hiệu ứng đâm và skill của Thương */
    private static final double EFFECT_SCALE = 1.0;

    private static final long THRUST_HAND_DURATION_MS = 230;
    private static final long SKILL_FOLLOW_THROUGH_MS = 100;

    private enum ActionMode {
        IDLE, THRUST, SKILL_DASH
    }

    // --- Tài nguyên đánh thường từ spear_master_transparent_sheet.png ---
    private static boolean assetsLoaded = false;

    private static final BufferedImage[] SPEAR_IMG = new BufferedImage[3];
    private static final double[] SPEAR_HAND_H = { 17.5, 19.0, 21.0 };

    private static final BufferedImage[][] THRUST_FRAMES = new BufferedImage[3][];
    private static double thrustDrawW = 40.0;
    private static double thrustDrawH = 22.0;

    private static final BufferedImage[][] HIT_FRAMES = new BufferedImage[3][];
    private static double hitDrawW = 24.0;
    private static double hitDrawH = 24.0;

    // --- Tài nguyên skill từ spear_skill_only_transparent_sheet.png (6 hàng) ---
    private static final BufferedImage[][] SKILL_FRAMES = new BufferedImage[3][];
    private static final double[] SKILL_DRAW_W = { 74.0, 92.0, 92.0 };
    private static final double[] SKILL_DRAW_H = { 22.0, 28.0, 58.0 };

    private static final BufferedImage[][] SKILL_IMPACT_FRAMES = new BufferedImage[3][];
    private static double skillImpactDrawW = 24.0;
    private static double skillImpactDrawH = 24.0;

    // --- Trạng thái động tác tay ---
    private ActionMode mode = ActionMode.IDLE;
    private long actionStartMs = 0;
    private double actionAngle = 0.0;
    private SpearSkillEntity activeSkill = null;
    private long skillDashEndMs = 0;

    // Thông số theo cấp
    private int thrustFrameMs = 34;
    private int hitFrameMs = 34;
    private double thrustHitLength = 30.0;
    private double thrustHitThickness = 10.0;

    public Spear(int initialLevel) {
        super("Thuong Lv1", 1, 3, 7, 340, 2000, 25, null);
        ensureAssetsLoaded();
        applyLevel(initialLevel);
    }

    public void applyLevel(int targetLevel) {
        int lv = Math.max(1, Math.min(3, targetLevel));
        this.level = lv;
        resetState();

        switch (lv) {
            case 1 -> {
                this.name = "Thuong Lv1";
                this.damage = 3;
                this.skillDamage = 7;
                this.attackCooldownMs = 340;
                this.skillCooldownMs = 1900;
                this.skillManaCost = 25;
                this.thrustFrameMs = 34;
                this.hitFrameMs = 36;
                this.thrustHitLength = 28.0;
                this.thrustHitThickness = 9.0;
            }
            case 2 -> {
                this.name = "Thuong Lv2";
                this.damage = 5;
                this.skillDamage = 11;
                this.attackCooldownMs = 315;
                this.skillCooldownMs = 2200;
                this.skillManaCost = 35;
                this.thrustFrameMs = 32;
                this.hitFrameMs = 34;
                this.thrustHitLength = 34.0;
                this.thrustHitThickness = 11.0;
            }
            case 3 -> {
                this.name = "Thuong Lv3";
                this.damage = 7;
                this.skillDamage = 16;
                this.attackCooldownMs = 290;
                this.skillCooldownMs = 2600;
                this.skillManaCost = 45;
                this.thrustFrameMs = 30;
                this.hitFrameMs = 32;
                this.thrustHitLength = 40.0;
                this.thrustHitThickness = 13.0;
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
        if (activeSkill != null) {
            activeSkill.finishDashEarly();
            activeSkill = null;
        }
        this.mode = ActionMode.IDLE;
        this.skillDashEndMs = 0;
    }

    @Override
    protected void performAttack(Player owner) {
        long now = System.currentTimeMillis();
        this.mode = ActionMode.THRUST;
        this.actionStartMs = now;
        this.actionAngle = computeAimAngle(owner);

        spawnThrustEffect(owner, this.actionAngle, damage, 1.0);
    }

    @Override
    protected void performSkill(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        long now = System.currentTimeMillis();
        this.mode = ActionMode.SKILL_DASH;
        this.actionStartMs = now;
        this.actionAngle = computeAimAngle(owner);
        this.skillDashEndMs = 0;

        int lvIdx = level - 1;
        BufferedImage[] frames = SKILL_FRAMES[lvIdx];
        BufferedImage[] impFrames = (SKILL_IMPACT_FRAMES[lvIdx] != null && SKILL_IMPACT_FRAMES[lvIdx].length > 0)
                ? SKILL_IMPACT_FRAMES[lvIdx]
                : HIT_FRAMES[lvIdx];

        double targetDashDist = (level == 1) ? 62.0 : (level == 2 ? 78.0 : 90.0);
        int pierceDmg = (level == 1) ? skillDamage : Math.max(3, skillDamage / 2);
        int burstDmg = (level == 1) ? 0 : skillDamage;
        int skillFrameMs = (level == 1) ? 38 : (level == 2 ? 42 : 44);
        double impMult = (level == 1) ? 0.90 : (level == 2 ? 1.08 : 1.25);

        SpearSkillEntity skill = new SpearSkillEntity(
                owner,
                level,
                frames,
                impFrames,
                pierceDmg,
                burstDmg,
                this.actionAngle,
                targetDashDist,
                SKILL_DRAW_W[lvIdx],
                SKILL_DRAW_H[lvIdx],
                skillImpactDrawW * impMult,
                skillImpactDrawH * impMult,
                skillFrameMs,
                34
        );
        this.activeSkill = skill;
        env.add(skill);
    }

    private void spawnThrustEffect(Player owner, double angle, int dmg, double scaleBoost) {
        Environment env = Game.world().environment();
        if (env == null) return;

        int lvIdx = level - 1;
        double levelSizeMult = (level == 1) ? 0.90 : (level == 2 ? 1.05 : 1.20);
        double finalMult = levelSizeMult * scaleBoost;

        SpearThrustEntity fx = new SpearThrustEntity(
                owner,
                THRUST_FRAMES[lvIdx],
                HIT_FRAMES[lvIdx],
                dmg,
                angle,
                7.0,
                thrustDrawW * finalMult,
                thrustDrawH * finalMult,
                hitDrawW * levelSizeMult,
                hitDrawH * levelSizeMult,
                thrustHitLength * scaleBoost,
                thrustHitThickness * scaleBoost,
                thrustFrameMs,
                hitFrameMs
        );
        env.add(fx);
    }

    private double computeAimAngle(Player owner) {
        double ax = owner.getAimDirX();
        double ay = owner.getAimDirY();
        if (Math.hypot(ax, ay) < 0.01) {
            return owner.isFacingRight() ? 0.0 : Math.PI;
        }
        return Math.atan2(ay, ax);
    }

    private Point2D.Double getHandWorldPos(Player owner, boolean inAction, double angle) {
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.70;

        if (inAction) {
            return new Point2D.Double(
                    cx + Math.cos(angle) * 4.5,
                    cy + Math.sin(angle) * 2.5
            );
        }

        double dir = owner.isFacingRight() ? 1.0 : -1.0;
        long now = System.currentTimeMillis();
        double bobY = (owner.getState() == Player.State.RUN)
                ? Math.sin(now * 0.016) * 0.65
                : Math.sin(now * 0.005) * 0.30;
        return new Point2D.Double(cx + dir * 4.8, cy + bobY);
    }

    @Override
    public void update(Player owner) {
        if (owner == null || owner.isDead()) {
            resetState();
            return;
        }

        long now = System.currentTimeMillis();
        if (mode == ActionMode.THRUST) {
            if (now - actionStartMs >= THRUST_HAND_DURATION_MS) {
                resetState();
            }
        } else if (mode == ActionMode.SKILL_DASH) {
            if (activeSkill == null || !activeSkill.isDashingPhase()) {
                if (skillDashEndMs == 0) {
                    skillDashEndMs = now;
                }
                if (now - skillDashEndMs >= SKILL_FOLLOW_THROUGH_MS) {
                    activeSkill = null;
                    mode = ActionMode.IDLE;
                    skillDashEndMs = 0;
                }
            }
        }
    }

    // ==================== VẼ THƯƠNG TRÊN TAY ====================

    @Override
    public void renderInHand(Graphics2D g, Player owner) {
        if (Game.world().camera() == null) return;

        int lvIdx = level - 1;
        BufferedImage img = SPEAR_IMG[lvIdx];
        if (img == null) return;

        double drawH = SPEAR_HAND_H[lvIdx];
        double ratio = (double) img.getWidth() / Math.max(1, img.getHeight());
        double drawW = drawH * ratio;

        boolean inAction = (mode != ActionMode.IDLE);
        double activeAngle = (mode == ActionMode.SKILL_DASH && activeSkill != null)
                ? activeSkill.getCurrentAimAngle()
                : actionAngle;
        Point2D.Double hand = getHandWorldPos(owner, inAction, activeAngle);
        Point2D vp = Game.world().camera().getViewportLocation(hand);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());

        if (mode == ActionMode.SKILL_DASH) {
            g2.rotate(activeAngle);
            if (Math.cos(activeAngle) < 0) {
                g2.scale(1, -1);
            }
            g2.translate(5.5, 0);
            g2.rotate(Math.PI / 2.0);
        } else if (mode == ActionMode.THRUST) {
            long elapsed = System.currentTimeMillis() - actionStartMs;
            double t = Math.min(1.0, Math.max(0.0, (double) elapsed / THRUST_HAND_DURATION_MS));

            double lunge;
            if (t < 0.18) {
                lunge = -2.5 * (t / 0.18);
            } else if (t < 0.45) {
                double p = (t - 0.18) / 0.27;
                lunge = -2.5 + 10.0 * easeOutQuad(p);
            } else {
                double p = (t - 0.45) / 0.55;
                lunge = 7.5 * (1.0 - p);
            }

            g2.rotate(activeAngle);
            if (Math.cos(activeAngle) < 0) {
                g2.scale(1, -1);
            }
            g2.translate(lunge, 0);
            g2.rotate(Math.PI / 2.0);
        } else {
            boolean facingRight = owner.isFacingRight();
            if (!facingRight) {
                g2.scale(-1, 1);
            }
            double idleDeg = 38.0;
            if (owner.getState() == Player.State.DASH) {
                idleDeg = 68.0;
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

    private static double easeOutQuad(double t) {
        return 1.0 - (1.0 - t) * (1.0 - t);
    }

    // ==================== NẠP & CẮT 2 TẤM SPRITE SHEET CỦA THƯƠNG ====================

    private static void ensureAssetsLoaded() {
        if (assetsLoaded) return;
        assetsLoaded = true;

        loadMasterAttackSheet();
        loadSkillSheet();
    }

    private static void loadMasterAttackSheet() {
        BufferedImage sheet = readWeaponImageSafe("weapons/spear_master_transparent_sheet.png");
        if (sheet == null) {
            System.err.println("[Spear] Khong tim thay weapons/spear_master_transparent_sheet.png");
            return;
        }

        int sw = sheet.getWidth();
        int sh = sheet.getHeight();
        List<int[]> yBands = findNonEmptyYBands(sheet, 2);

        int[][] bands = new int[7][2];
        if (yBands.size() == 7) {
            for (int i = 0; i < 7; i++) bands[i] = yBands.get(i);
        } else {
            int rowH = Math.max(1, sh / 7);
            for (int i = 0; i < 7; i++) {
                bands[i][0] = i * rowH;
                bands[i][1] = Math.min(sh - 1, (i + 1) * rowH - 1);
            }
        }

        // 1. Hàng 0: 3 cây thương Lv1, Lv2, Lv3
        int r0Y0 = bands[0][0];
        int r0H = Math.max(1, bands[0][1] - r0Y0 + 1);
        List<int[]> spearXBands = findNonEmptyXBands(sheet, r0Y0, bands[0][1], 1);
        for (int i = 0; i < 3 && i < spearXBands.size(); i++) {
            int x0 = spearXBands.get(i)[0];
            int x1 = spearXBands.get(i)[1];
            SPEAR_IMG[i] = trimTransparent(sheet.getSubimage(x0, r0Y0, Math.max(1, x1 - x0 + 1), r0H));
        }

        // 2. Hàng 1..3: 12 frame hiệu ứng đâm thường
        int thrustCols = 12;
        double thrustStepX = sw / (double) thrustCols;
        int uniformThrustH = 1;
        for (int lv = 0; lv < 3; lv++) {
            int bh = Math.max(1, bands[1 + lv][1] - bands[1 + lv][0] + 1);
            if (bh > uniformThrustH) uniformThrustH = bh;
        }

        for (int lv = 0; lv < 3; lv++) {
            int yMid = (bands[1 + lv][0] + bands[1 + lv][1]) / 2;
            int y0 = clamp(yMid - uniformThrustH / 2, 0, Math.max(0, sh - uniformThrustH));
            int actualH = Math.min(uniformThrustH, sh - y0);

            BufferedImage[] frames = new BufferedImage[thrustCols];
            for (int col = 0; col < thrustCols; col++) {
                int sx = (int) Math.round(col * thrustStepX);
                int ex = (int) Math.round((col + 1) * thrustStepX);
                int cw = Math.max(1, Math.min(sw - sx, ex - sx));
                frames[col] = slicePaddedCell(sheet, sx, y0, cw, actualH, (int) Math.round(thrustStepX), uniformThrustH);
            }
            THRUST_FRAMES[lv] = frames;
        }
        double thrustScale = (20.0 / Math.max(1, uniformThrustH)) * EFFECT_SCALE;
        thrustDrawW = thrustStepX * thrustScale;
        thrustDrawH = uniformThrustH * thrustScale;

        // 3. Hàng 4..6: 12 frame hiệu ứng trúng đích đánh thường
        int uniformHitH = 1;
        for (int lv = 0; lv < 3; lv++) {
            int bh = Math.max(1, bands[4 + lv][1] - bands[4 + lv][0] + 1);
            if (bh > uniformHitH) uniformHitH = bh;
        }
        double[] hitGrid = detectPreciseHitGrid(sheet, bands[4], bands[5], bands[6], uniformHitH);
        sliceBurstRows(sheet, bands[4], bands[5], bands[6], hitGrid[0], hitGrid[1], uniformHitH, HIT_FRAMES);

        double hitScale = (26.0 / Math.max(1.0, Math.max(hitGrid[1], uniformHitH))) * EFFECT_SCALE;
        hitDrawW = hitGrid[1] * hitScale;
        hitDrawH = uniformHitH * hitScale;
    }

    /** Nạp & cắt chuẩn 6 hàng từ spear_skill_only_transparent_sheet.png */
    private static void loadSkillSheet() {
        BufferedImage sheet = readWeaponImageSafe("weapons/spear_skill_only_transparent_sheet.png");
        if (sheet == null) {
            System.err.println("[Spear] Khong tim thay weapons/spear_skill_only_transparent_sheet.png");
            return;
        }

        int sw = sheet.getWidth();
        int sh = sheet.getHeight();
        List<int[]> yBands = findNonEmptyYBands(sheet, 2);

        int[][] bands = new int[6][2];
        if (yBands.size() == 6) {
            for (int i = 0; i < 6; i++) bands[i] = yBands.get(i);
        } else {
            int rowH = Math.max(1, sh / 6);
            for (int i = 0; i < 6; i++) {
                bands[i][0] = i * rowH;
                bands[i][1] = Math.min(sh - 1, (i + 1) * rowH - 1);
            }
        }

        // Hàng 0, 1, 2: hoạt ảnh skill Lv1 (14 frame), Lv2 (14 frame), Lv3 (17 frame)
        double[] targetHeights = { 22.0, 28.0, 58.0 };
        int[] expectedCols = { 14, 15, 18 };

        for (int lv = 0; lv < 3; lv++) {
            int y0 = bands[lv][0];
            int y1 = bands[lv][1];
            int bh = Math.max(1, y1 - y0 + 1);

            double[] grid = detectSkillRowGrid(sheet, y0, y1, lv, expectedCols[lv]);
            double startX = grid[0];
            double pitch = grid[1];
            int cellW = Math.max(1, (int) Math.round(pitch));
            int maxCols = Math.max(1, (int) Math.floor((sw - startX + 1.0) / pitch));

            List<BufferedImage> frames = new ArrayList<>();
            boolean seenNonEmpty = false;
            for (int col = 0; col < maxCols; col++) {
                int sx = clamp((int) Math.round(startX + col * pitch), 0, sw - 1);
                int ex = clamp((int) Math.round(startX + (col + 1) * pitch), sx + 1, sw);
                BufferedImage cell = slicePaddedCell(sheet, sx, y0, ex - sx, bh, cellW, bh);
                if (!isEmpty(cell)) {
                    seenNonEmpty = true;
                    frames.add(cell);
                } else if (seenNonEmpty) {
                    break;
                }
            }

            SKILL_FRAMES[lv] = frames.toArray(new BufferedImage[0]);

            double scale = (targetHeights[lv] / bh) * EFFECT_SCALE;
            SKILL_DRAW_W[lv] = pitch * scale;
            SKILL_DRAW_H[lv] = bh * scale;

            System.out.println(String.format(
                    "[Spear] Skill Lv%d -> frames=%d, cell=%.1fx%d, draw=%.1fx%.1f",
                    lv + 1, SKILL_FRAMES[lv].length, pitch, bh, SKILL_DRAW_W[lv], SKILL_DRAW_H[lv]
            ));
        }

        // Hàng 3, 4, 5: hiệu ứng nổ trúng đích của skill (12 frame mỗi cấp)
        int uniformImpH = 1;
        for (int lv = 0; lv < 3; lv++) {
            int bh = Math.max(1, bands[3 + lv][1] - bands[3 + lv][0] + 1);
            if (bh > uniformImpH) uniformImpH = bh;
        }
        double[] impGrid = detectPreciseHitGrid(sheet, bands[3], bands[4], bands[5], uniformImpH);
        sliceBurstRows(sheet, bands[3], bands[4], bands[5], impGrid[0], impGrid[1], uniformImpH, SKILL_IMPACT_FRAMES);

        double impScale = (26.0 / Math.max(1.0, Math.max(impGrid[1], uniformImpH))) * EFFECT_SCALE;
        skillImpactDrawW = impGrid[1] * impScale;
        skillImpactDrawH = uniformImpH * impScale;
    }

    /** Đo chính xác (startX, pitch) cho từng hàng skill chính (Hàng 0, 1, 2) không bị cắt phạm pixel */
    private static double[] detectSkillRowGrid(BufferedImage sheet, int y0, int y1, int lvIdx, int expectedCols) {
        int sw = sheet.getWidth();
        int bh = Math.max(1, y1 - y0 + 1);
        int[] colWeight = new int[sw];
        boolean[] hasPixel = new boolean[sw];

        for (int x = 0; x < sw; x++) {
            for (int y = y0; y <= y1; y++) {
                if (((sheet.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                    colWeight[x]++;
                    hasPixel[x] = true;
                }
            }
        }

        // Hàng 2 (Lv3 zíc-zắc) chạy hết chiều ngang ảnh -> kiểm tra chia đều sw / expectedCols trước
        if (lvIdx == 2) {
            for (int cols : new int[] { 18, 17, 19, 16 }) {
                double p = sw / (double) cols;
                if (computeCutPenalty(colWeight, 0.0, p, cols) == 0.0) {
                    return new double[] { 0.0, p };
                }
            }
        }

        // Dùng các khối liền nét chính giữa hàng để ước lượng pitch ban đầu
        int mergeGap = Math.max(3, bh / 4);
        List<int[]> blobs = groupTrueRuns(hasPixel, mergeGap);
        List<int[]> dense = new ArrayList<>();
        int minBlobW = Math.max(6, bh / 2);
        for (int[] b : blobs) {
            if (b[1] - b[0] + 1 >= minBlobW) {
                dense.add(b);
            }
        }
        if (dense.size() < 4) dense = blobs;

        double estPitch = sw / (double) Math.max(1, expectedCols);
        if (dense.size() >= 4) {
            int sampleCount = Math.min(7, dense.size() - 1);
            double[] diffs = new double[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                double cA = (dense.get(i)[0] + dense.get(i)[1]) / 2.0;
                double cB = (dense.get(i + 1)[0] + dense.get(i + 1)[1]) / 2.0;
                diffs[i] = cB - cA;
            }
            Arrays.sort(diffs);
            estPitch = diffs[sampleCount / 2];
        }

        double minP = Math.max(16.0, estPitch - 10.0);
        double maxP = Math.min(sw / 6.0, estPitch + 10.0);
        double bestPitch = estPitch;
        double bestStartX = 0.0;
        double bestScore = Double.MAX_VALUE;

        for (double p = minP; p <= maxP; p += 0.5) {
            int maxC = Math.min(expectedCols + 2, (int) Math.floor(sw / p));
            for (double sx = 0.0; sx <= Math.min(6.0, p * 0.15); sx += 1.0) {
                double cutPenalty = computeCutPenalty(colWeight, sx, p, maxC);
                double centerErr = computeCenterError(hasPixel, sx, p, Math.min(maxC, 12));
                double score = cutPenalty + centerErr;
                if (score < bestScore) {
                    bestScore = score;
                    bestPitch = p;
                    bestStartX = sx;
                }
            }
        }

        return new double[] { bestStartX, bestPitch };
    }

    private static double computeCutPenalty(int[] colWeight, double sx, double pitch, int cols) {
        double penalty = 0.0;
        int sw = colWeight.length;
        for (int k = 1; k < cols; k++) {
            int bx = (int) Math.round(sx + k * pitch);
            if (bx >= 0 && bx < sw) {
                penalty += colWeight[bx] * 100.0;
            }
        }
        return penalty;
    }

    private static double computeCenterError(boolean[] hasPixel, double sx, double pitch, int sampleCols) {
        int sw = hasPixel.length;
        double err = 0.0;
        int counted = 0;
        for (int k = 0; k < sampleCols; k++) {
            int x0 = clamp((int) Math.round(sx + k * pitch), 0, sw - 1);
            int x1 = clamp((int) Math.round(sx + (k + 1) * pitch) - 1, x0, sw - 1);
            int minX = sw, maxX = -1;
            for (int x = x0; x <= x1; x++) {
                if (hasPixel[x]) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                }
            }
            if (maxX >= minX) {
                err += Math.abs(((minX + maxX) / 2.0) - ((x0 + x1) / 2.0));
                counted++;
            }
        }
        return (counted > 0) ? (err / counted) : 999.0;
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

                double cutPenalty = computeCutPenalty(colWeight, sx, p, 12);
                double centerErr = computeCenterError(hasPixel, sx, p, 9);
                double score = cutPenalty + centerErr;
                if (score < bestScore) {
                    bestScore = score;
                    bestPitch = p;
                    bestStartX = sx;
                }
            }
        }

        return new double[] { bestStartX, bestPitch };
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

    private static List<int[]> findNonEmptyYBands(BufferedImage img, int maxGapPx) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[] hasPixel = new boolean[h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                    hasPixel[y] = true;
                    break;
                }
            }
        }
        return groupTrueRuns(hasPixel, maxGapPx);
    }

    private static List<int[]> findNonEmptyXBands(BufferedImage img, int y0, int y1, int maxGapPx) {
        int w = img.getWidth();
        int safeY0 = Math.max(0, y0);
        int safeY1 = Math.min(img.getHeight() - 1, y1);
        boolean[] hasPixel = new boolean[w];
        for (int x = 0; x < w; x++) {
            for (int y = safeY0; y <= safeY1; y++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                    hasPixel[x] = true;
                    break;
                }
            }
        }
        return groupTrueRuns(hasPixel, maxGapPx);
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

    private static boolean isEmpty(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 10) return false;
            }
        }
        return true;
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

    private static BufferedImage readWeaponImageSafe(String path) {
        if (path == null || path.isEmpty()) return null;

        try {
            BufferedImage img = Resources.images().get(path);
            if (img != null) return img;
        } catch (Throwable ignored) {
        }

        try (InputStream is = Spear.class.getResourceAsStream("/" + path)) {
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
}