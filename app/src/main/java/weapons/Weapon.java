package weapons;

import player.Character;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;

public abstract class Weapon {
    protected final String name;
    protected final int damage;
    protected final int manaCost;
    protected final long cooldownMs;
    protected long lastAttackTime = 0;

    protected int skillDamage = 0;
    protected int skillManaCost = 0;
    protected long skillCooldownMs = 0;
    protected long lastSkillTime = -10000;

    protected boolean attacking = false;
    protected long attackStartTime = 0;
    protected long attackAnimDurationMs = 220;

    public Weapon(String name, int damage, int manaCost, long cooldownMs) {
        this.name = name;
        this.damage = damage;
        this.manaCost = manaCost;
        this.cooldownMs = cooldownMs;
    }

    public boolean canAttack(Character owner) {
        long now = System.currentTimeMillis();
        return !owner.isDead()
                && (now - lastAttackTime >= cooldownMs)
                && (owner.getMana() >= manaCost);
    }

    public boolean tryAttack(Character owner, double dirX, double dirY) {
        if (!canAttack(owner)) {
            return false;
        }
        if (manaCost > 0 && !consumeOwnerMana(owner, manaCost)) {
            return false;
        }

        lastAttackTime = System.currentTimeMillis();
        attacking = true;
        attackStartTime = lastAttackTime;
        onAttack(owner, dirX, dirY);
        return true;
    }

    public boolean hasSkill() {
        return skillCooldownMs > 0;
    }

    public boolean canUseSkill(Character owner) {
        if (!hasSkill() || owner.isDead()) return false;
        long now = System.currentTimeMillis();
        return (now - lastSkillTime >= skillCooldownMs) && (owner.getMana() >= skillManaCost);
    }

    public boolean trySkill(Character owner, double dirX, double dirY) {
        if (!canUseSkill(owner)) {
            return false;
        }
        if (skillManaCost > 0 && !consumeOwnerMana(owner, skillManaCost)) {
            return false;
        }

        lastSkillTime = System.currentTimeMillis();
        attacking = true;
        attackStartTime = lastSkillTime;
        onSkill(owner, dirX, dirY);
        return true;
    }

    protected void onSkill(Character owner, double dirX, double dirY) {
    }

    public long getSkillCooldownRemainingMs() {
        if (!hasSkill()) return 0;
        long elapsed = System.currentTimeMillis() - lastSkillTime;
        return Math.max(0, skillCooldownMs - elapsed);
    }

    protected boolean consumeOwnerMana(Character owner, int cost) {
        if (owner.getMana() < cost) return false;
        try {
            java.lang.reflect.Method m = owner.getClass().getMethod("useMana", int.class);
            return (boolean) m.invoke(owner, cost);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = owner.getClass().getDeclaredField("mana");
                f.setAccessible(true);
                int current = f.getInt(owner);
                if (current < cost) return false;
                f.setInt(owner, current - cost);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    protected abstract void onAttack(Character owner, double dirX, double dirY);

    public void update(double dt, Character owner) {
        if (attacking && System.currentTimeMillis() - attackStartTime >= attackAnimDurationMs) {
            attacking = false;
        }
    }

    public abstract void render(Graphics2D g, Character owner, double uiScale, double offsetY);

    public String getName() { return name; }
    public int getManaCost() { return manaCost; }
    public int getSkillManaCost() { return skillManaCost; }
    public boolean isAttacking() { return attacking; }

    protected static BufferedImage loadImageFromPaths(String... paths) {
        for (String p : paths) {
            try (InputStream is = Weapon.class.getClassLoader().getResourceAsStream(p)) {
                if (is != null) {
                    return ImageIO.read(is);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    protected static BufferedImage removeWhiteBackground(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        boolean[][] isBg = new boolean[w][h];
        Queue<int[]> q = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            if (isNearWhite(src.getRGB(x, 0))) { isBg[x][0] = true; q.add(new int[]{x, 0}); }
            if (isNearWhite(src.getRGB(x, h - 1))) { isBg[x][h - 1] = true; q.add(new int[]{x, h - 1}); }
        }
        for (int y = 0; y < h; y++) {
            if (isNearWhite(src.getRGB(0, y))) { isBg[0][y] = true; q.add(new int[]{0, y}); }
            if (isNearWhite(src.getRGB(w - 1, y))) { isBg[w - 1][y] = true; q.add(new int[]{w - 1, y}); }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int d = 0; d < 4; d++) {
                int nx = cur[0] + dx[d], ny = cur[1] + dy[d];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !isBg[nx][ny]) {
                    if (isNearWhite(src.getRGB(nx, ny))) {
                        isBg[nx][ny] = true;
                        q.add(new int[]{nx, ny});
                    }
                }
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, isBg[x][y] ? 0x00000000 : src.getRGB(x, y));
            }
        }
        return out;
    }

    private static boolean isNearWhite(int argb) {
        int a = (argb >> 24) & 0xFF;
        if (a < 25) return true;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return r >= 238 && g >= 238 && b >= 238;
    }

    protected static BufferedImage cropToContent(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >> 24) & 0xFF) > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return img;
        return img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}