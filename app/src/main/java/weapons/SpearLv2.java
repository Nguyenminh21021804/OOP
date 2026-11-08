package weapons;

import player.Character;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class SpearLv2 extends MeleeWeapon {
    private BufferedImage spearSprite;      
    private BufferedImage[] thrustFrames;   
    private BufferedImage[] skillFlyFrames; 
    private BufferedImage skillImpactFrame; 

    // Thương Lv.2 dài và ngầu hơn Lv.1
    private static final double SPEAR_HEIGHT = 48.0; 
    private static final double THRUST_EFFECT_HEIGHT = 32.0; 
    private static final double SKILL_WAVE_SIZE = 65.0; // Vòng xoáy to hơn

    public SpearLv2() {
        // Tên: Thương Lv.2 | ST: 10 | Mana: 0 | Hồi chiêu: 280ms (nhanh hơn Lv1 một chút) | Tầm đâm: 40.0
        super("Spear Lv.2", 10, 0, 280, 40.0); 
        
        // Skill: Vòng xoáy điện vàng
        this.skillDamage = 22;
        this.skillManaCost = 0; 
        this.skillCooldownMs = 1300; // Hồi chiêu 1.3s

        loadSprites();
    }

    private void loadSprites() {
        // 1. Nạp ảnh thương (thuong_lv2.png)[cite: 31]
        BufferedImage rawSpear = loadRawImage("thuong_lv2.png");
        if (rawSpear != null) spearSprite = cleanAndCrop(rawSpear);

        // 2. Nạp ảnh hiệu ứng đâm (thuong_danh_thuong_lv2.png)[cite: 29]
        BufferedImage rawThrust = loadRawImage("thuong_danh_thuong_lv2.png");
        if (rawThrust != null) thrustFrames = splitEqualFrames(rawThrust, 4);

        // 3. Nạp ảnh kỹ năng xoáy (spear_aoe_lv2_clean.png)[cite: 30]
        BufferedImage rawSkill = loadRawImage("spear_aoe_lv2_clean.png");
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
            try (InputStream is = SpearLv2.class.getClassLoader().getResourceAsStream(p)) {
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
            double spawnX = owner.getWeaponAnchorX() + norm[0] * 42.0;
            double spawnY = owner.getWeaponAnchorY() + norm[1] * 42.0 - 4.0;

            skillProjectiles.add(new Projectile(
                    spawnX, spawnY, norm[0], norm[1],
                    650.0, 450.0,
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

            // Lật trục X nếu quay mặt sang trái
            if (!owner.isFacingRight()) {
                g.scale(-1.0, 1.0);
            }

            // Hoạt ảnh đâm: Cầm nghiêng 45 độ, đâm thì hạ xuống ngang hơn (75 độ) và tiến tới
            double progress = getSwingProgress();
            double thrustOffset = 0.0;
            double angleDeg = 45.0; 

            if (isAttacking()) {
                angleDeg = 75.0; 
                if (progress < 0.5) {
                    thrustOffset = (progress * 2.0) * 16.0 * uiScale; 
                } else {
                    thrustOffset = (1.0 - (progress - 0.5) * 2.0) * 16.0 * uiScale;
                }
            }

            g.rotate(Math.toRadians(angleDeg));
            g.translate(0, -thrustOffset); 

            // Cầm ở khoảng 70% chiều dài thân dưới
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