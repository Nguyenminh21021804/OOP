package weapons;

import player.Character;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class BowLv1 extends RangedWeapon {
    private BufferedImage bowSprite;            
    private BufferedImage[] arrowFrames;        
    private BufferedImage[] arrowFlyFrames;     
    private BufferedImage arrowImpactFrame;     
    private BufferedImage[] skillRainFrames;    

    private static final double BOW_SIZE = 26.0;
    private static final double ARROW_HEIGHT = 8.0;
    private static final double SKILL_AOE_SIZE = 150.0; 

    private final List<SlashEffect> activeRains = new ArrayList<>();

    public BowLv1() {
        super("Bow Lv.1", 5, 0, 300, 550.0, 450.0);
        this.attackAnimDurationMs = 200;
        
        this.skillDamage = 15;
        this.skillManaCost = 0; 
        this.skillCooldownMs = 1500; 
        
        loadSprites();
    }

    private void loadSprites() {
        BufferedImage rawBow = loadRawImage("bow_jade_gold_lv1.png");
        if (rawBow != null) {
            bowSprite = cleanAndCrop(rawBow);
            System.out.println("[BowLv1] LOAD OK: bow_jade_gold_lv1.png");
        }

        BufferedImage rawArrow = loadRawImage("bow_basic_arrow_lv1.png");
        if (rawArrow != null) {
            arrowFrames = splitEqualFrames(rawArrow, 4);
            arrowFlyFrames = new BufferedImage[]{
                    cleanAndCrop(arrowFrames[0]),
                    cleanAndCrop(arrowFrames[1]),
                    cleanAndCrop(arrowFrames[2])
            };
            arrowImpactFrame = cleanAndCrop(arrowFrames[3]);
        }

        BufferedImage rawSkill = loadRawImage("bow_aoe_rain_lv1_clean.png");
        if (rawSkill != null) {
            skillRainFrames = splitEqualFrames(rawSkill, 4);
            for (int i = 0; i < skillRainFrames.length; i++) {
                 skillRainFrames[i] = cleanAndCrop(skillRainFrames[i]);
            }
        }
    }

    protected BufferedImage[] splitEqualFrames(BufferedImage sheet, int frameCount) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        int fw = w / frameCount;
        BufferedImage[] res = new BufferedImage[frameCount];
        for (int i = 0; i < frameCount; i++) {
            int subW = (i == frameCount - 1) ? (w - i * fw) : fw;
            res[i] = sheet.getSubimage(i * fw, 0, subW, h);
        }
        return res;
    }

    private BufferedImage cleanAndCrop(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                if (a < 25 || (r > 240 && g > 240 && b > 240)) {
                    out.setRGB(x, y, 0x00000000);
                } else {
                    out.setRGB(x, y, argb);
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return out;
        return out.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private BufferedImage loadRawImage(String filename) {
        String[] paths = { "weapons/" + filename, "sprites/weapons/" + filename, filename };
        for (String p : paths) {
            try (InputStream is = BowLv1.class.getClassLoader().getResourceAsStream(p)) {
                if (is != null) return ImageIO.read(is);
            } catch (Exception ignored) {}
        }
        String[] baseDirs = { "src/main/resources/weapons/", "src/main/resources/sprites/weapons/", "src/main/resources/" };
        for (String dir : baseDirs) {
            File f = new File(dir + filename);
            if (f.exists() && f.isFile()) {
                try { return ImageIO.read(f); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    @Override
    public void update(double dt, Character owner) {
        super.update(dt, owner);
        activeRains.removeIf(rain -> {
            rain.update(dt);
            return rain.isFinished();
        });
    }

    @Override
    protected void onAttack(Character owner, double dirX, double dirY) {
        if (arrowFlyFrames == null || arrowFlyFrames.length == 0) return;

        double[] norm = normalizeDir(owner, dirX, dirY);
        double spawnX = owner.getWeaponAnchorX() + norm[0] * 12.0;
        double spawnY = owner.getWeaponAnchorY() + norm[1] * 12.0;

        projectiles.add(new Projectile(
                spawnX, spawnY, norm[0], norm[1],
                projectileSpeed, range,
                arrowFlyFrames, arrowImpactFrame, ARROW_HEIGHT
        ));
    }

    @Override
    protected void onSkill(Character owner, double dirX, double dirY) {
        if (skillRainFrames == null) return;

        double targetX = owner.getX();
        double targetY = owner.getY();

        activeRains.add(new SlashEffect(
                targetX, targetY, 1.0, 0.0, 
                skillRainFrames, SKILL_AOE_SIZE, 600 
        ));
    }

    private double[] normalizeDir(Character owner, double dirX, double dirY) {
        double len = Math.hypot(dirX, dirY);
        if (len == 0) return new double[]{owner.isFacingRight() ? 1.0 : -1.0, 0.0};
        return new double[]{dirX / len, dirY / len};
    }

    @Override
    public void render(Graphics2D g, Character owner, double uiScale, double offsetY) {
        if (!owner.isDead() && bowSprite != null) {
            Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            double anchorX = owner.getWeaponAnchorX() * uiScale;
            double anchorY = offsetY + owner.getWeaponAnchorY() * uiScale;

            double drawH = BOW_SIZE * uiScale;
            double ratio = (double) bowSprite.getWidth() / Math.max(1, bowSprite.getHeight());
            int w = (int) Math.max(4, Math.round(drawH * ratio));
            int h = (int) Math.max(4, Math.round(drawH));

            AffineTransform old = g.getTransform();
            g.translate(anchorX, anchorY);

            if (!owner.isFacingRight()) {
                g.scale(-1.0, 1.0);
            }

            // Xóa góc nghiêng, giữ nguyên 0 độ để cung luôn thẳng đứng
            g.rotate(0.0);

            // Xử lý vị trí thân cung
            int drawX = -w / 2;
            int drawY = -h / 2;

            // Kéo giật nhẹ thân cung về phía sau (ngược hướng bắn) khi ấn tấn công
            if (isAttacking()) {
                drawX -= (int) (4 * uiScale); 
            }

            g.drawImage(bowSprite, drawX, drawY, w, h, null);

            g.setTransform(old);
            if (oldInterp != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }
        }

        renderProjectiles(g, uiScale, offsetY);
        
        for (SlashEffect rain : activeRains) {
            rain.render(g, uiScale, offsetY);
        }
    }
}