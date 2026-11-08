package weapons;

import player.Character;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class SpearLv3 extends MeleeWeapon {
    private BufferedImage spearSprite;      
    private BufferedImage[] thrustFrames;   
    private BufferedImage[] skillFlyFrames; 
    private BufferedImage skillImpactFrame; 

    // Kích thước của Thương Lv.3 to và dài nhất
    private static final double SPEAR_HEIGHT = 52.0; 
    private static final double THRUST_EFFECT_HEIGHT = 38.0; 
    // Lốc xoáy lửa khổng lồ
    private static final double SKILL_WAVE_SIZE = 85.0; 

    public SpearLv3() {
        // Tên: Thương Lv.3 | ST: 16 | Mana: 0 | Hồi chiêu: 250ms | Tầm đâm: 45.0
        super("Spear Lv.3", 16, 0, 250, 45.0); 
        
        // Skill: Lốc xoáy lửa/Thánh quang
        this.skillDamage = 35;
        this.skillManaCost = 0; 
        this.skillCooldownMs = 1100; // Hồi chiêu 1.1s

        loadSprites();
    }

    private void loadSprites() {
        // 1. Nạp ảnh thương gốc[cite: 33]
        BufferedImage rawSpear = loadRawImage("thuong_lv3.png");
        if (rawSpear != null) spearSprite = cleanAndCrop(rawSpear);

        // 2. Nạp ảnh hiệu ứng đâm[cite: 32]
        BufferedImage rawThrust = loadRawImage("thuong_danh_thuong_lv3.png");
        if (rawThrust != null) thrustFrames = splitEqualFrames(rawThrust, 4);

        // 3. Nạp ảnh kỹ năng lốc xoáy[cite: 31]
        BufferedImage rawSkill = loadRawImage("spear_aoe_lv3_clean.png");
        if (rawSkill != null) {
            BufferedImage[] allSkillFrames = splitEqualFrames(rawSkill, 4);
            skillFlyFrames = new BufferedImage[]{
                    cropToContent(allSkillFrames[0]),
                    cropToContent(allSkillFrames[1]),
                    cropToContent(allSkillFrames[2])
            };
            skillImpactFrame = cropToContent(allSkillFrames[3]);
        }
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

                // Lọc nền trắng/trong suốt
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
            try (InputStream is = SpearLv3.class.getClassLoader().getResourceAsStream(p)) {
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
    protected void onAttack(Character owner, double dirX, double dirY) {
        double[] norm = normalizeDir(owner, dirX, dirY);
        
        // Hiệu ứng đâm xuất hiện ở mũi thương
        double thrustX = owner.getWeaponAnchorX() + norm[0] * slashRange;
        double thrustY = owner.getWeaponAnchorY() + norm[1] * slashRange - 4.0;

        if (thrustFrames != null) {
            slashEffects.add(new SlashEffect(
                    thrustX, thrustY, norm[0], norm[1],
                    thrustFrames, THRUST_EFFECT_HEIGHT, attackAnimDurationMs
            ));
        }
    }

    @Override
    protected void onSkill(Character owner, double dirX, double dirY) {
        double[] norm = normalizeDir(owner, dirX, dirY);

        if (thrustFrames != null) {
            double thrustX = owner.getWeaponAnchorX() + norm[0] * slashRange;
            double thrustY = owner.getWeaponAnchorY() + norm[1] * slashRange - 4.0;
            slashEffects.add(new SlashEffect(
                    thrustX, thrustY, norm[0], norm[1],
                    thrustFrames, THRUST_EFFECT_HEIGHT, attackAnimDurationMs
            ));
        }

        if (skillFlyFrames != null) {
            // Tầm xuất hiện của lốc xoáy được đẩy xa ra phía trước một chút
            double spawnX = owner.getWeaponAnchorX() + norm[0] * 45.0;
            double spawnY = owner.getWeaponAnchorY() + norm[1] * 45.0 - 4.0;

            skillProjectiles.add(new Projectile(
                    spawnX, spawnY, norm[0], norm[1],
                    750.0, 550.0,
                    skillFlyFrames, skillImpactFrame, SKILL_WAVE_SIZE
            ));
        }
    }

    private double[] normalizeDir(Character owner, double dirX, double dirY) {
        double len = Math.hypot(dirX, dirY);
        if (len == 0) return new double[]{owner.isFacingRight() ? 1.0 : -1.0, 0.0};
        return new double[]{dirX / len, dirY / len};
    }

    @Override
    public void render(Graphics2D g, Character owner, double uiScale, double offsetY) {
        if (!owner.isDead() && spearSprite != null) {
            Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            double anchorX = owner.getWeaponAnchorX() * uiScale;
            double anchorY = offsetY + owner.getWeaponAnchorY() * uiScale;

            double drawH = SPEAR_HEIGHT * uiScale;
            double ratio = (double) spearSprite.getWidth() / Math.max(1, spearSprite.getHeight());
            int w = (int) Math.max(6, Math.round(drawH * ratio));
            int h = (int) Math.max(12, Math.round(drawH));

            AffineTransform old = g.getTransform();
            g.translate(anchorX, anchorY);

            // Lật trục X nếu nhân vật quay mặt sang trái
            if (!owner.isFacingRight()) {
                g.scale(-1.0, 1.0);
            }

            // Hoạt ảnh đâm: Cầm nghiêng 45 độ, khi đâm hạ xuống 75 độ và đẩy cán tới trước
            double progress = getSwingProgress();
            double thrustOffset = 0.0;
            double angleDeg = 45.0; 

            if (isAttacking()) {
                angleDeg = 75.0; 
                // Cú đâm uy lực hơn nên độ giật xa hơn (18.0)
                if (progress < 0.5) {
                    thrustOffset = (progress * 2.0) * 18.0 * uiScale; 
                } else {
                    thrustOffset = (1.0 - (progress - 0.5) * 2.0) * 18.0 * uiScale;
                }
            }

            g.rotate(Math.toRadians(angleDeg));
            g.translate(0, -thrustOffset); 

            // Cầm thương ở khoảng 70% chiều dài cán
            int gripX = -w / 2;
            int gripY = -(int) Math.round(h * 0.70); 
            g.drawImage(spearSprite, gripX, gripY, w, h, null);

            g.setTransform(old);
            if (oldInterp != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }
        }

        renderSlashEffects(g, uiScale, offsetY);
    }
}