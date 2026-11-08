package weapons;

import player.Character;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class SwordLv2 extends MeleeWeapon {
    private BufferedImage swordSprite;      
    private BufferedImage[] slashFrames;    
    private BufferedImage[] skillFlyFrames; 
    private BufferedImage skillImpactFrame; 

    private static final double SWORD_HEIGHT = 32.0; 
    private static final double SLASH_EFFECT_HEIGHT = 42.0;
    private static final double SKILL_WAVE_SIZE = 65.0; 

    public SwordLv2() {
        super("Sword Lv.2", 8, 0, 260, 22.0); 
        
        this.skillDamage = 18;
        this.skillManaCost = 0; 
        this.skillCooldownMs = 1200;

        loadSprites();
    }

    private void loadSprites() {
        // 1. Nạp ảnh kiếm thường
        swordSprite = loadRawImage("sword_lv2.png");
        
        // 2. Nạp ảnh chém thường
        BufferedImage rawSlash = loadRawImage("sword_lv2_slash.png");
        if (rawSlash != null) {
            slashFrames = splitEqualFrames(rawSlash, 4);
        }

        // 3. Nạp ảnh skill (Kiếm khí lửa)
        BufferedImage rawSkill = loadRawImage("sword_lv2_skill_2.png");
        if (rawSkill != null) {
            BufferedImage[] allSkillFrames = splitEqualFrames(rawSkill, 4);
            
            // Xóa bỏ hàm lật ngang, nạp thẳng khung ảnh vào mảng và giữ nguyên thứ tự
            skillFlyFrames = new BufferedImage[]{
                    cropToContent(allSkillFrames[0]), // Frame tụ năng lượng
                    cropToContent(allSkillFrames[1]), // Frame đang bay 1
                    cropToContent(allSkillFrames[2])  // Frame đang bay 2
            };
            
            skillImpactFrame = cropToContent(allSkillFrames[3]); // Frame nổ
            System.out.println("[SwordLv2] LOAD OK: sword_lv2_skill_2.png");
        } else {
            System.err.println("[SwordLv2] LOI: Khong tim thay sword_lv2_skill_2.png");
        }
    }

    private BufferedImage loadRawImage(String filename) {
        String[] paths = {
                "weapons/" + filename,
                "sprites/weapons/" + filename,
                "player/" + filename,
                "sprites/" + filename,
                filename
        };
        for (String p : paths) {
            try (InputStream is = SwordLv2.class.getClassLoader().getResourceAsStream(p)) {
                if (is != null) return ImageIO.read(is);
            } catch (Exception ignored) {}
        }
        String[] baseDirs = {
                "src/main/resources/weapons/",
                "src/main/resources/sprites/weapons/",
                "src/main/resources/player/",
                "src/main/resources/",
                "build/resources/main/weapons/",
                "build/resources/main/"
        };
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
        double slashX = owner.getWeaponAnchorX() + norm[0] * slashRange;
        double slashY = owner.getWeaponAnchorY() + norm[1] * slashRange - 4.0;

        if (slashFrames != null) {
            slashEffects.add(new SlashEffect(
                    slashX, slashY, norm[0], norm[1],
                    slashFrames, SLASH_EFFECT_HEIGHT, attackAnimDurationMs
            ));
        }
    }

    @Override
    protected void onSkill(Character owner, double dirX, double dirY) {
        double[] norm = normalizeDir(owner, dirX, dirY);

        if (slashFrames != null) {
            double slashX = owner.getWeaponAnchorX() + norm[0] * slashRange;
            double slashY = owner.getWeaponAnchorY() + norm[1] * slashRange - 4.0;
            slashEffects.add(new SlashEffect(
                    slashX, slashY, norm[0], norm[1],
                    slashFrames, SLASH_EFFECT_HEIGHT, attackAnimDurationMs
            ));
        }

        if (skillFlyFrames != null) {
            double spawnX = owner.getWeaponAnchorX() + norm[0] * 32.0;
            double spawnY = owner.getWeaponAnchorY() + norm[1] * 32.0 - 4.0;

            skillProjectiles.add(new Projectile(
                    spawnX, spawnY, norm[0], norm[1],
                    700.0, 500.0,
                    skillFlyFrames, skillImpactFrame, SKILL_WAVE_SIZE
            ));
        }
    }

    private double[] normalizeDir(Character owner, double dirX, double dirY) {
        double len = Math.hypot(dirX, dirY);
        if (len == 0) {
            return new double[]{owner.isFacingRight() ? 1.0 : -1.0, 0.0};
        }
        return new double[]{dirX / len, dirY / len};
    }

    @Override
    public void render(Graphics2D g, Character owner, double uiScale, double offsetY) {
        if (!owner.isDead() && swordSprite != null) {
            Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            double anchorX = owner.getWeaponAnchorX() * uiScale;
            double anchorY = offsetY + owner.getWeaponAnchorY() * uiScale;

            double drawH = SWORD_HEIGHT * uiScale;
            double ratio = (double) swordSprite.getWidth() / Math.max(1, swordSprite.getHeight());
            int w = (int) Math.max(6, Math.round(drawH * ratio));
            int h = (int) Math.max(12, Math.round(drawH));

            double deg = isAttacking() ? (-40.0 + getSwingProgress() * 160.0) : 22.0;
            double rad = Math.toRadians(owner.isFacingRight() ? deg : -deg);

            AffineTransform old = g.getTransform();
            g.translate(anchorX, anchorY);
            g.rotate(rad);

            int gripX = -w / 2;
            int gripY = -(int) Math.round(h * 0.82);
            g.drawImage(swordSprite, gripX, gripY, w, h, null);

            g.setTransform(old);
            if (oldInterp != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }
        }

        renderSlashEffects(g, uiScale, offsetY);
    }
}