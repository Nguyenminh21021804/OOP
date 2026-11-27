package weapons;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.ICombatEntity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.resources.Resources;
import player.Player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Bow extends Weapon {

    /** Hệ số phóng/thu chung cho hiệu ứng skill và vòng nổ của Cung */
    private static final double SKILL_EFFECT_SCALE = 1.0;

    private static final long SHOOT_ANIM_MS = 165;
    private static final long SKILL_CAST_ANIM_MS = 240;

    private enum ActionMode {
        IDLE, SHOOT, SKILL_CAST
    }

    // --- Tài nguyên đánh thường từ bow_sigil_master_sheet.png (4 hàng x 4 cột) ---
    private static boolean assetsLoaded = false;

    private static final BufferedImage[] BOW_IMG = new BufferedImage[3];
    private static final double[] BOW_HAND_H = { 12.5, 14.0, 15.5 };

    private static final BufferedImage[][] ARROW_FRAMES = new BufferedImage[3][3];
    private static final double[] ARROW_DRAW_W = { 12.0, 13.5, 16.0 };
    private static final double[] ARROW_DRAW_H = { 5.0, 6.0, 7.5 };

    private static final BufferedImage[] SIGIL_IMG = new BufferedImage[3];
    private static final double[] SIGIL_SIZE = { 11.0, 14.0, 17.5 };

    // --- Tài nguyên skill từ bow_skill2_only_transparent_sheet.png (6 hàng) ---
    private static final BufferedImage[][] SKILL_FRAMES = new BufferedImage[3][];
    private static final double[] SKILL_DRAW_W = { 48.0, 58.0, 74.0 };
    private static final double[] SKILL_DRAW_H = { 46.0, 56.0, 70.0 };

    private static final BufferedImage[][] SKILL_IMPACT_FRAMES = new BufferedImage[3][];
    private static double skillImpactDrawW = 24.0;
    private static double skillImpactDrawH = 24.0;

    // --- Trạng thái động tác tay ---
    private ActionMode mode = ActionMode.IDLE;
    private long actionStartMs = 0;
    private double actionAngle = 0.0;

    // Thông số theo cấp
    private double arrowSpeed = 230.0;
    private double arrowRange = 165.0;
    private double arrowHitW = 10.0;
    private double arrowHitH = 6.0;
    private boolean arrowPiercing = false;

    private double skillCastDistance = 58.0;
    private double skillAoeRadiusX = 20.0;
    private double skillAoeRadiusY = 12.0;
    private int skillFrameMs = 52;
    private int skillBeamTickDamage = 5;

    public Bow(int initialLevel) {
        super("Cung Lv1", 1, 2, 2, 360, 2200, 25, null);
        ensureAssetsLoaded();
        applyLevel(initialLevel);
    }

    public void applyLevel(int targetLevel) {
        int lv = Math.max(1, Math.min(3, targetLevel));
        this.level = lv;
        resetState();

        switch (lv) {
            case 1 -> {
                this.name = "Cung Lv1";
                this.damage = 2;
                this.skillDamage = 2; // 3 đợt mưa tên = tổng tối đa 6 sát thương
                this.skillBeamTickDamage = 2;
                this.attackCooldownMs = 360;
                this.skillCooldownMs = 2200;
                this.skillManaCost = 25;
                this.arrowSpeed = 230.0;
                this.arrowRange = 165.0;
                this.arrowHitW = 10.0;
                this.arrowHitH = 6.0;
                this.arrowPiercing = false;
                this.skillCastDistance = 58.0;
                this.skillAoeRadiusX = 20.0;
                this.skillAoeRadiusY = 12.0;
                this.skillFrameMs = 52;
            }
            case 2 -> {
                this.name = "Cung Lv2";
                this.damage = 4;
                this.skillDamage = 3; // 4 đợt mưa tên = tổng tối đa 12 sát thương
                this.skillBeamTickDamage = 3;
                this.attackCooldownMs = 320;
                this.skillCooldownMs = 2500;
                this.skillManaCost = 35;
                this.arrowSpeed = 265.0;
                this.arrowRange = 195.0;
                this.arrowHitW = 12.0;
                this.arrowHitH = 7.0;
                this.arrowPiercing = false;
                this.skillCastDistance = 70.0;
                this.skillAoeRadiusX = 26.0;
                this.skillAoeRadiusY = 15.0;
                this.skillFrameMs = 50;
            }
            case 3 -> {
                this.name = "Cung Lv3";
                this.damage = 6;
                this.skillDamage = 4;       // 2 đợt mưa tên vòng ngoài = 8
                this.skillBeamTickDamage = 5; // + 3 đợt cột năng lượng = 15 (tổng 23)
                this.attackCooldownMs = 280;
                this.skillCooldownMs = 2800;
                this.skillManaCost = 45;
                this.arrowSpeed = 305.0;
                this.arrowRange = 235.0;
                this.arrowHitW = 14.0;
                this.arrowHitH = 8.0;
                this.arrowPiercing = true;
                this.skillCastDistance = 82.0;
                this.skillAoeRadiusX = 32.0;
                this.skillAoeRadiusY = 18.0;
                this.skillFrameMs = 54;
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
    }

    @Override
    protected void performAttack(Player owner) {
        this.mode = ActionMode.SHOOT;
        this.actionStartMs = System.currentTimeMillis();
        this.actionAngle = computeAimAngle(owner);

        spawnArrow(owner);
    }

    @Override
    protected void performSkill(Player owner) {
        this.mode = ActionMode.SKILL_CAST;
        this.actionStartMs = System.currentTimeMillis();
        this.actionAngle = computeAimAngle(owner);

        spawnArrowRainSkill(owner);
    }

    private void spawnArrow(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        int lvIdx = level - 1;
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.68;
        double desiredLaunchDist = 9.0;

        double clearDist = WeaponCollision.raycastObstacleDistance(env, cx, cy, actionAngle, desiredLaunchDist, 2.0, owner);
        boolean immediateWall = (clearDist < desiredLaunchDist - 1.5);
        double spawnDist = immediateWall ? Math.max(3.5, clearDist) : desiredLaunchDist;

        double sx = cx + Math.cos(actionAngle) * spawnDist;
        double sy = cy + Math.sin(actionAngle) * spawnDist;

        BowArrowEntity arrow = new BowArrowEntity(
                owner,
                ARROW_FRAMES[lvIdx],
                SIGIL_IMG[lvIdx],
                damage,
                sx, sy,
                actionAngle,
                arrowSpeed,
                arrowRange,
                ARROW_DRAW_W[lvIdx],
                ARROW_DRAW_H[lvIdx],
                SIGIL_SIZE[lvIdx],
                arrowHitW,
                arrowHitH,
                65,
                arrowPiercing,
                immediateWall
        );
        env.add(arrow);
    }

    private void spawnArrowRainSkill(Player owner) {
        Environment env = Game.world().environment();
        if (env == null) return;

        int lvIdx = level - 1;
        Point2D.Double target = resolveSkillTargetPos(env, owner);
        boolean flipX = Math.cos(actionAngle) < 0;
        double impMult = (level == 1) ? 0.90 : (level == 2 ? 1.08 : 1.25);

        BowSkillEntity skill = new BowSkillEntity(
                owner,
                level,
                SKILL_FRAMES[lvIdx],
                SKILL_IMPACT_FRAMES[lvIdx],
                target.x,
                target.y,
                SKILL_DRAW_W[lvIdx],
                SKILL_DRAW_H[lvIdx],
                skillImpactDrawW * impMult,
                skillImpactDrawH * impMult,
                skillAoeRadiusX,
                skillAoeRadiusY,
                flipX,
                skillFrameMs,
                38,
                skillDamage,
                skillBeamTickDamage
        );
        env.add(skill);
    }

    /** Ưu tiên khóa vào quái gần nhất trong hướng ngắm; nếu phòng trống thì thả trước mặt và chặn tại tường */
    private Point2D.Double resolveSkillTargetPos(Environment env, Player owner) {
        double cx = owner.getX() + owner.getWidth() / 2.0;
        double cy = owner.getY() + owner.getHeight() * 0.78;
        double dirX = Math.cos(actionAngle);
        double dirY = Math.sin(actionAngle);
        double lockRange = skillCastDistance + 55.0;

        ICombatEntity bestTarget = null;
        double bestDist = Double.MAX_VALUE;

        for (ICombatEntity target : env.getCombatEntities()) {
            if (target == owner || target.isDead()) continue;
            if (target.getTeam() == owner.getTeam()) continue;

            Rectangle2D tb = target.getBoundingBox();
            double tx = tb.getCenterX();
            double ty = tb.getY() + tb.getHeight() * 0.85;
            double dx = tx - cx;
            double dy = ty - cy;
            double dist = Math.hypot(dx, dy);
            if (dist < 1.0 || dist > lockRange) continue;

            // Nằm trong góc ngắm phía trước (~120 độ)
            double dot = (dx / dist) * dirX + (dy / dist) * dirY;
            if (dot >= 0.45 && dist < bestDist) {
                bestDist = dist;
                bestTarget = target;
            }
        }

        if (bestTarget != null) {
            Rectangle2D tb = bestTarget.getBoundingBox();
            return new Point2D.Double(tb.getCenterX(), tb.getY() + tb.getHeight() * 0.85);
        }

        double clearDist = WeaponCollision.raycastObstacleDistance(
                env, cx, cy, actionAngle, skillCastDistance, 3.0, owner
        );
        double finalDist = Math.max(12.0, Math.min(skillCastDistance, clearDist - 4.0));
        return new Point2D.Double(cx + dirX * finalDist, cy + dirY * finalDist);
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
        double cy = owner.getY() + owner.getHeight() * 0.69;

        if (mode == ActionMode.SKILL_CAST) {
            double dir = owner.isFacingRight() ? 1.0 : -1.0;
            return new Point2D.Double(cx + dir * 4.5, cy - 3.0);
        }

        if (inAction) {
            return new Point2D.Double(
                    cx + Math.cos(actionAngle) * 5.2,
                    cy + Math.sin(actionAngle) * 3.0
            );
        }

        double dir = owner.isFacingRight() ? 1.0 : -1.0;
        long now = System.currentTimeMillis();
        double bobY = (owner.getState() == Player.State.RUN)
                ? Math.sin(now * 0.016) * 0.65
                : Math.sin(now * 0.005) * 0.30;
        return new Point2D.Double(cx + dir * 5.5, cy + bobY);
    }

    @Override
    public void update(Player owner) {
        if (owner == null || owner.isDead()) {
            resetState();
            return;
        }

        long elapsed = System.currentTimeMillis() - actionStartMs;
        if (mode == ActionMode.SHOOT && elapsed >= SHOOT_ANIM_MS) {
            mode = ActionMode.IDLE;
        } else if (mode == ActionMode.SKILL_CAST && elapsed >= SKILL_CAST_ANIM_MS) {
            mode = ActionMode.IDLE;
        }
    }

    @Override
    public void renderInHand(Graphics2D g, Player owner) {
        if (Game.world().camera() == null) return;

        int lvIdx = level - 1;
        BufferedImage img = BOW_IMG[lvIdx];
        if (img == null) return;

        double drawH = BOW_HAND_H[lvIdx];
        double ratio = (double) img.getWidth() / Math.max(1, img.getHeight());
        double drawW = drawH * ratio;

        Point2D.Double hand = getHandWorldPos(owner, mode != ActionMode.IDLE);
        Point2D vp = Game.world().camera().getViewportLocation(hand);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.translate(vp.getX(), vp.getY());

        if (mode == ActionMode.SKILL_CAST) {
            // Giương cung lên trời bắn mũi tên tín hiệu mở cổng mưa tên
            double t = Math.min(1.0, Math.max(0.0, (System.currentTimeMillis() - actionStartMs) / (double) SKILL_CAST_ANIM_MS));
            double recoil = (t < 0.35) ? (-2.0 * (t / 0.35)) : (-2.0 * (1.0 - (t - 0.35) / 0.65));

            if (!owner.isFacingRight()) {
                g2.scale(-1, 1);
            }
            g2.rotate(Math.toRadians(-66.0));
            g2.translate(recoil, 0);
        } else if (mode == ActionMode.SHOOT) {
            double t = Math.min(1.0, Math.max(0.0, (System.currentTimeMillis() - actionStartMs) / (double) SHOOT_ANIM_MS));
            double recoil = (t < 0.35) ? (-2.2 * (t / 0.35)) : (-2.2 * (1.0 - (t - 0.35) / 0.65));

            g2.rotate(actionAngle);
            if (Math.cos(actionAngle) < 0) {
                g2.scale(1, -1);
            }
            g2.translate(recoil, 0);
        } else {
            if (!owner.isFacingRight()) {
                g2.scale(-1, 1);
            }
            double idleDeg = 0.0;
            if (owner.getState() == Player.State.DASH) {
                idleDeg = -14.0;
            } else if (owner.getState() == Player.State.RUN) {
                idleDeg = Math.sin(System.currentTimeMillis() * 0.016) * 5.0;
            }
            g2.rotate(Math.toRadians(idleDeg));
        }

        g2.drawImage(
                img,
                (int) Math.round(-drawW * 0.42),
                (int) Math.round(-drawH / 2.0),
                (int) Math.round(drawW),
                (int) Math.round(drawH),
                null
        );
        g2.dispose();
    }

    // ==================== NẠP & CẮT ĐỒ HỌA ĐÁNH THƯỜNG + SKILL CỦA CUNG ====================

    private static void ensureAssetsLoaded() {
        if (assetsLoaded) return;
        assetsLoaded = true;

        loadBasicBowSheet();
        loadSkillSheet();
    }

    private static void loadBasicBowSheet() {
        BufferedImage sheet = cleanBackgroundIfNeeded(readWeaponImageSafe("weapons/bow_sigil_master_sheet.png"));
        if (sheet == null || sheet.getWidth() < 4 || sheet.getHeight() < 4) {
            System.err.println("[Bow] Khong tim thay hoac anh loi: weapons/bow_sigil_master_sheet.png");
            return;
        }

        int cellW = sheet.getWidth() / 4;
        int cellH = sheet.getHeight() / 4;

        for (int lv = 0; lv < 3; lv++) {
            BOW_IMG[lv] = trimTransparent(sheet.getSubimage(lv * cellW, 0, cellW, cellH));
        }

        for (int lv = 0; lv < 3; lv++) {
            int rowY = (lv + 1) * cellH;
            int minX = cellW, minY = cellH, maxX = -1, maxY = -1;

            for (int col = 0; col < 3; col++) {
                BufferedImage raw = sheet.getSubimage(col * cellW, rowY, cellW, cellH);
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
                    BufferedImage raw = sheet.getSubimage(col * cellW, rowY, cellW, cellH);
                    ARROW_FRAMES[lv][col] = raw.getSubimage(minX, minY, cropW, cropH);
                }
                ARROW_DRAW_H[lv] = ARROW_DRAW_W[lv] * ((double) cropH / Math.max(1, cropW));
            } else {
                for (int col = 0; col < 3; col++) {
                    ARROW_FRAMES[lv][col] = sheet.getSubimage(col * cellW, rowY, cellW, cellH);
                }
            }

            SIGIL_IMG[lv] = trimTransparent(sheet.getSubimage(3 * cellW, rowY, cellW, cellH));
        }
    }

    /** Nạp & cắt chuẩn 6 hàng từ bow_skill2_only_transparent_sheet.png */
    private static void loadSkillSheet() {
        BufferedImage sheet = readWeaponImageSafe("weapons/bow_skill2_only_transparent_sheet.png");
        if (sheet == null) {
            System.err.println("[Bow] Khong tim thay weapons/bow_skill2_only_transparent_sheet.png");
            return;
        }

        int sw = sheet.getWidth();
        int sh = sheet.getHeight();
        int[][] bands = findSixRowBands(sheet);

        // Hàng 0, 1, 2: hoạt ảnh mưa tên / cột năng lượng cho Lv1, Lv2, Lv3
        double[] targetHeights = { 46.0, 56.0, 70.0 };
        for (int lv = 0; lv < 3; lv++) {
            int y0 = bands[lv][0];
            int y1 = bands[lv][1];
            int bh = Math.max(1, y1 - y0 + 1);

            double[] grid = detectPortalSkillGrid(sheet, y0, y1);
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

            if (firstNonEmpty >= 0 && lastNonEmpty >= firstNonEmpty) {
                SKILL_FRAMES[lv] = rawCells.subList(firstNonEmpty, lastNonEmpty + 1).toArray(new BufferedImage[0]);
            } else {
                SKILL_FRAMES[lv] = new BufferedImage[0];
            }

            double scale = (targetHeights[lv] / bh) * SKILL_EFFECT_SCALE;
            SKILL_DRAW_W[lv] = pitch * scale;
            SKILL_DRAW_H[lv] = bh * scale;

            System.out.println(String.format(
                    "[Bow] Skill Lv%d -> frames=%d, startX=%.2f, cell=%.2fx%d, draw=%.1fx%.1f",
                    lv + 1, SKILL_FRAMES[lv].length, startX, pitch, bh, SKILL_DRAW_W[lv], SKILL_DRAW_H[lv]
            ));
        }

        // Hàng 3, 4, 5: hiệu ứng vòng nổ trúng đích (10..12 frame mỗi cấp)
        int uniformImpH = 1;
        for (int lv = 0; lv < 3; lv++) {
            int bh = Math.max(1, bands[3 + lv][1] - bands[3 + lv][0] + 1);
            if (bh > uniformImpH) uniformImpH = bh;
        }
        double[] impGrid = detectPreciseHitGrid(sheet, bands[3], bands[4], bands[5], uniformImpH);
        sliceBurstRows(sheet, bands[3], bands[4], bands[5], impGrid[0], impGrid[1], uniformImpH, SKILL_IMPACT_FRAMES);

        double impScale = (24.0 / Math.max(1.0, Math.max(impGrid[1], uniformImpH))) * SKILL_EFFECT_SCALE;
        skillImpactDrawW = impGrid[1] * impScale;
        skillImpactDrawH = uniformImpH * impScale;
    }

    /** Tìm đúng 6 dải hàng theo chiều dọc (tự gộp 2 dải gần nhất nếu bên trong 1 hàng có khe hở nhỏ) */
    private static int[][] findSixRowBands(BufferedImage sheet) {
        int sh = sheet.getHeight();
        List<int[]> yBands = findNonEmptyYBands(sheet, 2);

        while (yBands.size() > 6) {
            int bestIdx = 0;
            int minGap = Integer.MAX_VALUE;
            for (int i = 0; i < yBands.size() - 1; i++) {
                int gap = yBands.get(i + 1)[0] - yBands.get(i)[1];
                if (gap < minGap) {
                    minGap = gap;
                    bestIdx = i;
                }
            }
            yBands.get(bestIdx)[1] = yBands.get(bestIdx + 1)[1];
            yBands.remove(bestIdx + 1);
        }

        int[][] bands = new int[6][2];
        if (yBands.size() == 6) {
            for (int i = 0; i < 6; i++) {
                bands[i] = yBands.get(i);
            }
        } else {
            int rowH = Math.max(1, sh / 6);
            for (int i = 0; i < 6; i++) {
                bands[i][0] = i * rowH;
                bands[i][1] = Math.min(sh - 1, (i + 1) * rowH - 1);
            }
        }
        return bands;
    }

    /**
     * Đo chuẩn bước cột (pitch) của hàng mưa tên từ dải cổng trời ở 32% phía trên (vốn liền khối từng khung),
     * sau đó tinh chỉnh theo bước 0.25px để mọi đường cắt rơi vào cột rỗng và cổng trời nằm chính giữa mỗi ô.
     */
    private static double[] detectPortalSkillGrid(BufferedImage sheet, int y0, int y1) {
        int sw = sheet.getWidth();
        int bh = Math.max(1, y1 - y0 + 1);

        int[] fullColWeight = new int[sw];
        boolean[] topHasPixel = new boolean[sw];
        int topY1 = Math.min(y1, y0 + Math.max(4, (int) Math.round(bh * 0.32)));

        int activeMinX = sw;
        int activeMaxX = -1;

        for (int x = 0; x < sw; x++) {
            for (int y = y0; y <= y1; y++) {
                if (((sheet.getRGB(x, y) >>> 24) & 0xFF) > 10) {
                    fullColWeight[x]++;
                    if (x < activeMinX) activeMinX = x;
                    if (x > activeMaxX) activeMaxX = x;
                    if (y <= topY1) {
                        topHasPixel[x] = true;
                    }
                }
            }
        }

        if (activeMaxX < activeMinX) {
            return new double[] { 0.0, Math.max(16.0, bh) };
        }

        List<int[]> portalBlobs = groupTrueRuns(topHasPixel, Math.max(2, bh / 12));
        double estPitch = Math.max(16.0, bh * 1.05);
        double firstCenter = (activeMinX + activeMaxX) / 2.0;

        if (portalBlobs.size() >= 4) {
            int sampleCount = Math.min(10, portalBlobs.size() - 1);
            double[] diffs = new double[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                double cA = (portalBlobs.get(i)[0] + portalBlobs.get(i)[1]) / 2.0;
                double cB = (portalBlobs.get(i + 1)[0] + portalBlobs.get(i + 1)[1]) / 2.0;
                diffs[i] = cB - cA;
            }
            Arrays.sort(diffs);
            estPitch = diffs[sampleCount / 2];
            firstCenter = (portalBlobs.get(0)[0] + portalBlobs.get(0)[1]) / 2.0;
        } else if (portalBlobs.size() >= 2) {
            firstCenter = (portalBlobs.get(0)[0] + portalBlobs.get(0)[1]) / 2.0;
            estPitch = ((portalBlobs.get(1)[0] + portalBlobs.get(1)[1]) / 2.0) - firstCenter;
        }

        double minP = Math.max(16.0, estPitch - 8.0);
        double maxP = Math.min(sw / 5.0, estPitch + 8.0);
        double bestPitch = estPitch;
        double bestStartX = 0.0;
        double bestScore = Double.MAX_VALUE;

        for (double p = minP; p <= maxP; p += 0.25) {
            // Thử gốc chuẩn x=0 cùng các gốc canh theo tâm cổng trời đầu tiên (kể cả khi cột 0 bỏ trống)
            double[] sxCandidates = new double[11];
            sxCandidates[0] = 0.0;
            double modCenter = ((firstCenter - p * 0.5) % p + p) % p;
            for (int s = 0; s < 10; s++) {
                sxCandidates[s + 1] = Math.max(0.0, modCenter + (s - 4.5) * 0.5);
            }

            for (double sx : sxCandidates) {
                int maxC = (int) Math.ceil((activeMaxX - sx) / p) + 1;
                double cutPenalty = 0.0;
                for (int k = 1; k < maxC; k++) {
                    int bx = (int) Math.round(sx + k * p);
                    if (bx >= activeMinX && bx <= activeMaxX && bx < sw) {
                        cutPenalty += fullColWeight[bx] * 120.0;
                    }
                }

                // Đo độ lệch tâm của các cổng trời trong dải trên so với tâm ô lưới
                double centerErr = 0.0;
                int counted = 0;
                for (int i = 0; i < Math.min(12, portalBlobs.size()); i++) {
                    double pc = (portalBlobs.get(i)[0] + portalBlobs.get(i)[1]) / 2.0;
                    double rel = (pc - sx) / p;
                    int cellIdx = (int) Math.floor(rel);
                    double cellCenter = sx + (cellIdx + 0.5) * p;
                    centerErr += Math.abs(pc - cellCenter);
                    counted++;
                }

                double zeroBias = (sx == 0.0) ? -0.15 : 0.0;
                double score = cutPenalty + (counted > 0 ? (centerErr / counted) : 999.0) + zeroBias;
                if (score < bestScore) {
                    bestScore = score;
                    bestPitch = p;
                    bestStartX = sx;
                }
            }
        }

        return new double[] { bestStartX, bestPitch };
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

        try (InputStream is = Bow.class.getResourceAsStream("/" + path)) {
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