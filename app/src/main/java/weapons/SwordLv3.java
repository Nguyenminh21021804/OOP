package weapons;

import player.Character;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class SwordLv3 extends MeleeWeapon {
    private BufferedImage swordSprite;      
    private BufferedImage[] slashFrames;    
    private BufferedImage[] skillFlyFrames; 
    private BufferedImage skillImpactFrame; 

    private static final double SWORD_HEIGHT = 38.0; 
    private static final double SLASH_EFFECT_HEIGHT = 50.0;
    private static final double SKILL_WAVE_SIZE = 80.0; 

    public SwordLv3() {
        super("Sword Lv.3", 15, 0, 240, 24.0); 
        this.skillDamage = 35;
        this.skillManaCost = 0; 
        this.skillCooldownMs = 1000; 

        loadSprites();
    }

    private void loadSprites() {
        swordSprite = loadRawImage("sword_lv3.png");
        
        BufferedImage rawSlash = loadRawImage("sword_lv3_slash.png");
        if (rawSlash != null) {
            slashFrames = splitEqualFrames(rawSlash, 4);
        }

        BufferedImage rawSkill = loadRawImage("sword_lv3_skill.png");
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

    private BufferedImage loadRawImage(String filename) {
        String[] paths = {
                "weapons/" + filename,
                "sprites/weapons/" + filename,
                "player/" + filename,
                "sprites/" + filename,
                filename
        };
        for (String p : paths) {
            try (InputStream is = SwordLv3.class.getClassLoader().getResourceAsStream(p)) {
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
            double spawnX = owner.getWeaponAnchorX() + norm[0] * 38.0;
            double spawnY = owner.getWeaponAnchorY() + norm[1] * 38.0 - 4.0;

            skillProjectiles.add(new Projectile(
                    spawnX, spawnY, norm[0], norm[1],
                    850.0, 600.0,
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

            // Góc chém
            double deg = isAttacking() ? (-45.0 + getSwingProgress() * 170.0) : 22.0;

            AffineTransform old = g.getTransform();
            g.translate(anchorX, anchorY);

            // BẢN FIX MỚI: Nếu quay trái thì lật ngang trục X, giữ nguyên góc chém deg
            if (!owner.isFacingRight()) {
                g.scale(-1.0, 1.0);
            }
            g.rotate(Math.toRadians(deg));

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