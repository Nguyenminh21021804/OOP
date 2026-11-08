package weapons;

import player.Character;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class RangedWeapon extends Weapon {
    protected final double projectileSpeed;
    protected final double range;
    protected final List<Projectile> projectiles = new ArrayList<>();

    public RangedWeapon(String name, int damage, int manaCost, long cooldownMs,
                        double projectileSpeed, double range) {
        super(name, damage, manaCost, cooldownMs);
        this.projectileSpeed = projectileSpeed;
        this.range = range;
    }

    @Override
    public void update(double dt, Character owner) {
        super.update(dt, owner);
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.update(dt);
            if (p.isFinished()) {
                it.remove();
            }
        }
    }

    protected void renderProjectiles(Graphics2D g, double uiScale, double offsetY) {
        for (Projectile p : projectiles) {
            p.render(g, uiScale, offsetY);
        }
    }

    public void clearProjectiles() {
        projectiles.clear();
    }
}