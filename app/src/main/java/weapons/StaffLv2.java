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

public class StaffLv2 extends RangedWeapon {
    private BufferedImage staffSprite;          // truong_phep_lv2_transparent.png
    private BufferedImage[] orbFrames;          // staff_basic_orb_lv2.png
    private BufferedImage[] orbFlyFrames;       
    private BufferedImage orbImpactFrame;       
    private BufferedImage[] skillMagicFrames;   // kynang_truong_lv2_64px.png

    private static final double STAFF_SIZE = 38.0; 
    private static final double ORB_HEIGHT = 16.0;
    
    // Kích thước vòng ma thuật to hơn cấp 1
    private static final double SKILL_AOE_SIZE = 180.0; 

    private final List<SlashEffect> activeMagics = new ArrayList<>();

    public StaffLv2() {
        // Tên | Sát thương: 12 | Mana: 0 | Hồi chiêu: 300ms | Tốc độ đạn: 550.0 | Tầm bắn: 450.0
        super("Staff Lv.2", 12, 0, 300, 550.0, 450.0);
        this.attackAnimDurationMs = 220;
        
        // Kỹ năng: Cột Sét Ma Thuật cấp 2
        this.skillDamage = 28;
        this.skillManaCost = 0; 
        this.skillCooldownMs = 1300; 
        
        loadSprites();
    }

    private void loadSprites() {
        // 1. Nạp ảnh Trượng phép cấp 2
        BufferedImage rawStaff = loadRawImage("truong_phep_lv2_transparent.png");
        if (rawStaff != null) {
            staffSprite = cleanBackgroundOnly(rawStaff);
        }

        // 2. Nạp ảnh Đạn quả cầu (orb) cấp 2
        BufferedImage rawOrb = loadRawImage("staff_basic_orb_lv2.png");
        if (rawOrb != null) {
            orbFrames = splitEqualFrames(rawOrb, 4);
            orbFlyFrames = new BufferedImage[]{
                    cleanBackgroundOnly(orbFrames[0]),
                    cleanBackgroundOnly(orbFrames[1]),
                    cleanBackgroundOnly(orbFrames[2])
            };
            orbImpactFrame = cleanBackgroundOnly(orbFrames[3]);
        }

        // 3. Nạp ảnh Kỹ năng: Cột sét ma thuật cấp 2
        BufferedImage rawSkill = loadRawImage("kynang_truong_lv2_64px.png");
        if (rawSkill != null) {
            skillMagicFrames = splitEqualFrames(rawSkill, 4);
            for (int i = 0; i < skillMagicFrames.length; i++) {
                 skillMagicFrames[i] = cleanBackgroundOnly(skillMagicFrames[i]);
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

    // Xóa nền trắng/trong suốt mà KHÔNG làm thay đổi kích thước các frame
    private BufferedImage cleanBackgroundOnly(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

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
                }
            }
        }
        return out;
    }

    private BufferedImage loadRawImage(String filename) {
        String[] paths = { "weapons/" + filename, "sprites/weapons/" + filename, filename };
        for (String p : paths) {
            try (InputStream is = StaffLv2.class.getClassLoader().getResourceAsStream(p)) {
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
        activeMagics.removeIf(magic -> {
            magic.update(dt);
            return magic.isFinished();
        });
    }

    @Override
    protected void onAttack(Character owner, double dirX, double dirY) {
        if (orbFlyFrames == null || orbFlyFrames.length == 0) return;

        double[] norm = normalizeDir(owner, dirX, dirY);
        
        // Quả cầu bay ra từ ngọc trên đỉnh trượng
        double spawnX = owner.getWeaponAnchorX() + norm[0] * 18.0;
        double spawnY = owner.getWeaponAnchorY() + norm[1] * 18.0 - 10.0;

        projectiles.add(new Projectile(
                spawnX, spawnY, norm[0], norm[1],
                projectileSpeed, range,
                orbFlyFrames, orbImpactFrame, ORB_HEIGHT
        ));
    }

    @Override
    protected void onSkill(Character owner, double dirX, double dirY) {
        if (skillMagicFrames == null) return;

        double targetX = owner.getX();
        double targetY = owner.getY();

        activeMagics.add(new SlashEffect(
                targetX, targetY, 1.0, 0.0, 
                skillMagicFrames, SKILL_AOE_SIZE, 700 
        ));
    }

    private double[] normalizeDir(Character owner, double dirX, double dirY) {
        double len = Math.hypot(dirX, dirY);
        if (len == 0) return new double[]{owner.isFacingRight() ? 1.0 : -1.0, 0.0};
        return new double[]{dirX / len, dirY / len};
    }

    @Override
    public void render(Graphics2D g, Character owner, double uiScale, double offsetY) {
        if (!owner.isDead() && staffSprite != null) {
            Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            double anchorX = owner.getWeaponAnchorX() * uiScale;
            double anchorY = offsetY + owner.getWeaponAnchorY() * uiScale;

            double drawH = STAFF_SIZE * uiScale;
            double ratio = (double) staffSprite.getWidth() / Math.max(1, staffSprite.getHeight());
            int w = (int) Math.max(4, Math.round(drawH * ratio));
            int h = (int) Math.max(8, Math.round(drawH));

            AffineTransform old = g.getTransform();
            g.translate(anchorX, anchorY);

            // Cầm trượng thẳng đứng. Khi tấn công, nghiêng nhẹ 30 độ về phía trước.
            double angleDeg = 0.0;
            if (isAttacking()) {
                angleDeg = owner.isFacingRight() ? 30.0 : -30.0;
            }
            g.rotate(Math.toRadians(angleDeg));

            int drawX = -w / 2;
            int drawY = -(int) Math.round(h * 0.65);

            g.drawImage(staffSprite, drawX, drawY, w, h, null);

            g.setTransform(old);
            if (oldInterp != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }
        }

        renderProjectiles(g, uiScale, offsetY);
        
        for (SlashEffect magic : activeMagics) {
            magic.render(g, uiScale, offsetY);
        }
    }
}