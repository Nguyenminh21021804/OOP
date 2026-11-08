package player;

import de.gurkenlabs.litiengine.input.Input;
import de.gurkenlabs.litiengine.resources.Resources;
import screens.Viewport;
import weapons.BowLv1;
import weapons.BowLv2;
import weapons.BowLv3;
import weapons.SpearLv1;
import weapons.SpearLv2;
import weapons.SpearLv3;
import weapons.Staff;
import weapons.StaffLv2;
import weapons.StaffLv3;
import weapons.SwordLv1;
import weapons.SwordLv2;
import weapons.SwordLv3;

import weapons.Weapon;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerController {
    private final Character character;

    private final double moveSpeed = 220.0;
    private double aimDirX = 1.0;
    private double aimDirY = 0.0;

    private boolean dashing = false;
    private final double dashSpeed = 620.0;
    private final long dashDurationMs = 200;
    private final long dashCooldownMs = 1500;
    private long dashStartTime = 0;
    private long lastDashTime = -dashCooldownMs;
    private double dashDirX = 0;
    private double dashDirY = 0;

    private final List<Weapon> weapons = new ArrayList<>();
    private int currentWeaponIndex = 0;
    private Weapon currentWeapon = null;

    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean jKeyLocked = false;
    private boolean uKeyLocked = false;
    private boolean kKeyLocked = false;
    private boolean cKeyLocked = false;
    private boolean eKeyLocked = false;
    private boolean qKeyLocked = false;

    public PlayerController(double startX, double startY) {
        this.character = new Character(startX, startY, 6, 5, 200);

        weapons.add(new BowLv1());
        weapons.add(new BowLv2());
        weapons.add(new BowLv3());
        weapons.add(new SpearLv1());
        weapons.add(new SpearLv2());
        weapons.add(new SpearLv3());
        weapons.add(new SwordLv1());
        weapons.add(new SwordLv2());
        weapons.add(new SwordLv3());
        weapons.add(new Staff());
        weapons.add(new StaffLv2());
        weapons.add(new StaffLv3());

        this.currentWeaponIndex = 0;
        this.currentWeapon = weapons.get(currentWeaponIndex);

        setupInput();
    }

    private void setupInput() {
        Input.keyboard().onKeyPressed(e -> {
            int code = e.getKeyCode();
            pressedKeys.add(code);

            if (code == KeyEvent.VK_R && character.isDead()) {
                character.revive();
                dashing = false;
                return;
            }

            if (code == KeyEvent.VK_C && !cKeyLocked) {
                cKeyLocked = true;
                character.nextSkin();
            }
            if (code == KeyEvent.VK_1) character.setSkin(0);
            if (code == KeyEvent.VK_2) character.setSkin(1);
            if (code == KeyEvent.VK_3) character.setSkin(2);
            if (code == KeyEvent.VK_4) character.setSkin(3);

            if (character.isDead()) return;

            if (code == KeyEvent.VK_Q && !qKeyLocked) {
                qKeyLocked = true;
                currentWeaponIndex = (currentWeaponIndex + 1) % weapons.size();
                currentWeapon = weapons.get(currentWeaponIndex);
            }

            if (code == KeyEvent.VK_E && !eKeyLocked) {
                eKeyLocked = true;
                if (currentWeapon == null) {
                    currentWeapon = weapons.get(currentWeaponIndex);
                } else {
                    currentWeapon = null;
                }
            }

            if (code == KeyEvent.VK_J && !jKeyLocked) {
                jKeyLocked = true;
                tryAttack();
            }

            if ((code == KeyEvent.VK_U || code == KeyEvent.VK_L) && !uKeyLocked) {
                uKeyLocked = true;
                trySkill();
            }

            if (code == KeyEvent.VK_K && !kKeyLocked) {
                kKeyLocked = true;
                tryDash();
            }

            if (code == KeyEvent.VK_H) {
                if (!dashing) {
                    character.takeDamage(1);
                }
            }
        });

        Input.keyboard().onKeyReleased(e -> {
            int code = e.getKeyCode();
            pressedKeys.remove(code);
            if (code == KeyEvent.VK_J) jKeyLocked = false;
            if (code == KeyEvent.VK_U || code == KeyEvent.VK_L) uKeyLocked = false;
            if (code == KeyEvent.VK_K) kKeyLocked = false;
            if (code == KeyEvent.VK_C) cKeyLocked = false;
            if (code == KeyEvent.VK_E) eKeyLocked = false;
            if (code == KeyEvent.VK_Q) qKeyLocked = false;
        });
    }

    public void update(double dt) {
        for (Weapon w : weapons) {
            w.update(dt, character);
        }

        if (character.isDead()) {
            dashing = false;
            character.update(Character.State.DIE);
            return;
        }

        long now = System.currentTimeMillis();
        boolean moving = false;

        if (dashing) {
            if (now - dashStartTime >= dashDurationMs) {
                dashing = false;
            } else {
                double nextX = character.getX() + dashDirX * dashSpeed * dt;
                double nextY = character.getY() + dashDirY * dashSpeed * dt;
                character.setPosition(nextX, nextY);
            }
        } else {
            double dx = 0, dy = 0;
            if (pressedKeys.contains(KeyEvent.VK_A)) dx -= 1;
            if (pressedKeys.contains(KeyEvent.VK_D)) dx += 1;
            if (pressedKeys.contains(KeyEvent.VK_W)) dy -= 1;
            if (pressedKeys.contains(KeyEvent.VK_S)) dy += 1;

            if (dx != 0 || dy != 0) {
                double len = Math.hypot(dx, dy);
                dx /= len;
                dy /= len;

                double nextX = character.getX() + dx * moveSpeed * dt;
                double nextY = character.getY() + dy * moveSpeed * dt;
                character.setPosition(nextX, nextY);
                moving = true;

                if (dx > 0) character.setFacingRight(true);
                else if (dx < 0) character.setFacingRight(false);

                aimDirX = dx;
                aimDirY = dy;
            } else {
                aimDirX = character.isFacingRight() ? 1.0 : -1.0;
                aimDirY = 0.0;
            }
        }

        Character.State targetState;
        if (dashing) {
            targetState = Character.State.DASH;
        } else if (moving) {
            targetState = Character.State.RUN;
        } else {
            targetState = Character.State.IDLE;
        }

        character.update(targetState);
    }

    private void tryAttack() {
        if (character.isDead() || currentWeapon == null) return;
        currentWeapon.tryAttack(character, aimDirX, aimDirY);
    }

    private void trySkill() {
        if (character.isDead() || currentWeapon == null) return;
        currentWeapon.trySkill(character, aimDirX, aimDirY);
    }

    private void tryDash() {
        if (character.isDead()) return;
        long now = System.currentTimeMillis();
        if (dashing || (now - lastDashTime < dashCooldownMs)) return;

        double dx = 0, dy = 0;
        if (pressedKeys.contains(KeyEvent.VK_A)) dx -= 1;
        if (pressedKeys.contains(KeyEvent.VK_D)) dx += 1;
        if (pressedKeys.contains(KeyEvent.VK_W)) dy -= 1;
        if (pressedKeys.contains(KeyEvent.VK_S)) dy += 1;

        if (dx != 0 || dy != 0) {
            double len = Math.hypot(dx, dy);
            dashDirX = dx / len;
            dashDirY = dy / len;
            if (dx > 0) character.setFacingRight(true);
            else if (dx < 0) character.setFacingRight(false);
        } else {
            dashDirX = character.isFacingRight() ? 1.0 : -1.0;
            dashDirY = 0.0;
        }

        dashing = true;
        dashStartTime = now;
        lastDashTime = now;
    }

    public void render(Graphics2D g, double scale, double offsetY) {
        character.render(g, scale, offsetY);

        if (currentWeapon != null) {
            currentWeapon.render(g, character, scale, offsetY);
        }

        drawHud(g, scale, offsetY);

        if (character.isDead()) {
            drawLoseMessage(g, scale, offsetY);
        }
    }

    private void drawLoseMessage(Graphics2D g, double scale, double offsetY) {
        int screenW = (int) Math.round(Viewport.UI_W * scale);
        int centerY = (int) Math.round(offsetY + (Viewport.UI_H * 0.42) * scale);

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, (int) Math.round(offsetY), screenW, (int) Math.round(Viewport.UI_H * scale));

        Font loseFont;
        try {
            loseFont = Resources.fonts().get("font/DungeonFont.ttf").deriveFont((float) (88 * scale));
        } catch (Exception e) {
            loseFont = new Font("Monospaced", Font.BOLD, (int) Math.max(36, 72 * scale));
        }

        g.setFont(loseFont);
        FontMetrics fm = g.getFontMetrics();
        String msg = "LOSE";
        int tx = (screenW - fm.stringWidth(msg)) / 2;
        int sh = Math.max(2, (int) Math.round(4 * scale));

        g.setColor(new Color(20, 5, 5));
        g.drawString(msg, tx + sh, centerY + sh);

        g.setColor(new Color(235, 50, 50));
        g.drawString(msg, tx, centerY);

        g.setFont(new Font("Monospaced", Font.BOLD, (int) Math.max(12, 16 * scale)));
        FontMetrics subFm = g.getFontMetrics();
        String subMsg = "An [R] de hoi sinh  |  An [ESC] de ve Menu";
        int subX = (screenW - subFm.stringWidth(subMsg)) / 2;
        g.setColor(new Color(230, 210, 170));
        g.drawString(subMsg, subX, centerY + (int) Math.round(45 * scale));
    }

    private void drawHud(Graphics2D g, double scale, double offsetY) {
        int padX = (int) Math.round(20 * scale);
        int padY = (int) Math.round(offsetY + 20 * scale);
        int boxW = (int) Math.round(210 * scale);
        int boxH = (int) Math.round(88 * scale);

        g.setColor(new Color(24, 20, 28, 220));
        g.fillRect(padX, padY, boxW, boxH);
        g.setColor(new Color(140, 125, 100));
        g.drawRect(padX, padY, boxW, boxH);

        int barX = padX + (int) Math.round(14 * scale);
        int barW = boxW - (int) Math.round(28 * scale);
        int barH = (int) Math.round(16 * scale);

        g.setFont(new Font("Monospaced", Font.BOLD, (int) Math.max(11, 12 * scale)));

        int hpY = padY + (int) Math.round(12 * scale);
        drawStatBar(g, barX, hpY, barW, barH, character.getHp(), character.getMaxHp(),
                new Color(210, 45, 45), "HP: " + character.getHp() + "/" + character.getMaxHp());

        int armorY = hpY + (int) Math.round(24 * scale);
        drawStatBar(g, barX, armorY, barW, barH, character.getArmor(), character.getMaxArmor(),
                new Color(160, 175, 195), "ARMOR: " + character.getArmor() + "/" + character.getMaxArmor());

        int manaY = armorY + (int) Math.round(24 * scale);
        drawStatBar(g, barX, manaY, barW, barH, character.getMana(), character.getMaxMana(),
                new Color(45, 125, 230), "MANA: " + character.getMana() + "/" + character.getMaxMana());

        int infoY = padY + boxH + (int) Math.round(18 * scale);
        g.setColor(Color.WHITE);
        long cd = getDashCooldownRemainingMs();
        String dashText = (cd == 0) ? "SAN SANG" : (cd + "ms");

        String wpName = (currentWeapon != null) ? currentWeapon.getName() : "TAY KHONG [E]";
        String skillText = "";
        if (currentWeapon != null && currentWeapon.hasSkill()) {
            long scd = currentWeapon.getSkillCooldownRemainingMs();
            skillText = " | Skill(U): " + (scd == 0 ? "SAN SANG" : (scd + "ms"));
        }

        g.drawString("Skin(C): #" + (character.getCurrentSkin() + 1)
                + " | " + wpName + " [J:Danh, Q:Doi]"
                + skillText
                + " | Dash(K): " + dashText, padX, infoY);
    }

    private void drawStatBar(Graphics2D g, int x, int y, int w, int h, int val, int max, Color color, String label) {
        g.setColor(new Color(45, 40, 50));
        g.fillRect(x, y, w, h);

        int fillW = (max > 0) ? (int) Math.round((double) val / max * w) : 0;
        g.setColor(color);
        g.fillRect(x, y, fillW, h);

        g.setColor(new Color(15, 10, 15));
        g.drawRect(x, y, w, h);

        g.setColor(Color.WHITE);
        g.drawString(label, x + 6, y + h - 4);
    }

    public long getDashCooldownRemainingMs() {
        long elapsed = System.currentTimeMillis() - lastDashTime;
        return Math.max(0, dashCooldownMs - elapsed);
    }

    public Character getCharacter() {
        return character;
    }
}