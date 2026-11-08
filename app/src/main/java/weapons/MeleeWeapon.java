package weapons;

import player.Character;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class MeleeWeapon extends Weapon {
    protected final double slashRange;
    protected final List<SlashEffect> slashEffects = new ArrayList<>();
    protected final List<Projectile> skillProjectiles = new ArrayList<>();

    public MeleeWeapon(String name, int damage, int manaCost, long cooldownMs, double slashRange) {
        super(name, damage, manaCost, cooldownMs);
        this.slashRange = slashRange;
        this.attackAnimDurationMs = 200;
    }

    @Override
    public void update(double dt, Character owner) {
        super.update(dt, owner);

        Iterator<SlashEffect> it = slashEffects.iterator();
        while (it.hasNext()) {
            SlashEffect fx = it.next();
            fx.update(dt);
            if (fx.isFinished()) {
                it.remove();
            }
        }

        Iterator<Projectile> pit = skillProjectiles.iterator();
        while (pit.hasNext()) {
            Projectile p = pit.next();
            p.update(dt);
            if (p.isFinished()) {
                pit.remove();
            }
        }
    }

    protected void renderSlashEffects(Graphics2D g, double uiScale, double offsetY) {
        for (SlashEffect fx : slashEffects) {
            fx.render(g, uiScale, offsetY);
        }
        for (Projectile p : skillProjectiles) {
            p.render(g, uiScale, offsetY);
        }
    }

    protected double getSwingProgress() {
        if (!attacking) return 0.0;
        long elapsed = System.currentTimeMillis() - attackStartTime;
        return Math.min(1.0, Math.max(0.0, (double) elapsed / attackAnimDurationMs));
    }

    protected static BufferedImage[] splitEqualFrames(BufferedImage sheet, int frameCount) {
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
}