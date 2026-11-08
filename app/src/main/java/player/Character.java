package player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class Character {
    private int maxHp;
    private int hp;
    private int maxArmor;
    private int armor;
    private int maxMana;
    private int mana;

    private final long armorRegenDelayMs = 4000;
    private final long armorRegenIntervalMs = 1000;
    private long lastDamageTime = 0;
    private long lastArmorRegenTime = 0;

    private double x;
    private double y;
    private boolean facingRight = true;
    private double weaponOffsetX = 12.0;
    private double weaponOffsetY = 27.0;

    public enum State {
        IDLE, RUN, DASH, DIE
    }

    private State currentState = State.IDLE;
    private long stateStartTime = System.currentTimeMillis();

    // --- HỆ THỐNG TRANG PHỤC (0: knight0, 1: knight1, 2: knight2, 3: knight3) ---
    public static final int SKIN_COUNT = 4;
    private int currentSkin = 0;

    private final BufferedImage[][] idleFrames = new BufferedImage[SKIN_COUNT][];
    private final BufferedImage[][] runFrames  = new BufferedImage[SKIN_COUNT][];
    private final BufferedImage[][] dashFrames = new BufferedImage[SKIN_COUNT][];
    private final BufferedImage[][] dieFrames  = new BufferedImage[SKIN_COUNT][];

    private static final int IDLE_FRAME_MS = 150;
    private static final int RUN_FRAME_MS = 100;
    private static final int DASH_FRAME_MS = 50;
    private static final int DIE_FRAME_MS = 160;

    private double spriteScale = 2.8;

    public Character(double startX, double startY, int maxHp, int maxArmor, int maxMana) {
        this.x = startX;
        this.y = startY;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.maxArmor = maxArmor;
        this.armor = maxArmor;
        this.maxMana = maxMana;
        this.mana = maxMana;

        loadAllSkins();
    }

    /** Nạp toàn bộ 4 bộ trang phục của nhân vật chính (knight0, knight1, knight2, knight3) */
    private void loadAllSkins() {
        for (int skin = 0; skin < SKIN_COUNT; skin++) {
            String prefix = "knight" + skin;
            idleFrames[skin] = loadSpriteSheet(4,
                    "player/" + prefix + "stand.png",
                    "sprites/player/" + prefix + "stand.png");

            runFrames[skin] = loadSpriteSheet(4,
                    "player/" + prefix + "run.png",
                    "sprites/player/" + prefix + "run.png");

            dashFrames[skin] = loadSpriteSheet(4,
                    "player/" + prefix + "dash.png",
                    "sprites/player/" + prefix + "dash.png",
                    "player/knigh" + skin + "tdash.png",
                    "sprites/player/knigh" + skin + "tdash.png");

            dieFrames[skin] = loadSpriteSheet(4,
                    "player/" + prefix + "die.png",
                    "sprites/player/" + prefix + "die.png");
        }
    }

    private BufferedImage[] loadSpriteSheet(int expectedFrames, String... paths) {
        BufferedImage raw = null;
        for (String p : paths) {
            raw = loadImage(p);
            if (raw != null) break;
        }

        if (raw == null) {
            System.err.println("[Character] Khong tim thay file anh: " + paths[0]);
            return new BufferedImage[0];
        }

        return extractFrames(raw, expectedFrames);
    }

    private BufferedImage loadImage(String path) {
        try (InputStream is = Character.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            System.err.println("[Character] Loi load anh " + path + ": " + e.getMessage());
        }
        return null;
    }

    private BufferedImage[] extractFrames(BufferedImage sheet, int frameCount) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        int frameW = w / frameCount;

        List<BufferedImage> frames = new ArrayList<>();
        int canvasW = 32;
        int canvasH = 32;

        for (int i = 0; i < frameCount; i++) {
            int subW = (i == frameCount - 1) ? (w - i * frameW) : frameW;
            BufferedImage sub = sheet.getSubimage(i * frameW, 0, subW, h);
            BufferedImage cleaned = cleanBackgroundOutsideOnly(sub);

            BufferedImage normalized = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = normalized.createGraphics();
            int drawX = (canvasW - subW) / 2;
            int drawY = canvasH - h - 2;
            g2.drawImage(cleaned, drawX, drawY, null);
            g2.dispose();

            frames.add(normalized);
        }
        return frames.toArray(new BufferedImage[0]);
    }

    private BufferedImage cleanBackgroundOutsideOnly(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        boolean[][] isBg = new boolean[w][h];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            if (isWhiteOrTransparent(src.getRGB(x, 0))) { isBg[x][0] = true; queue.add(new int[]{x, 0}); }
            if (isWhiteOrTransparent(src.getRGB(x, h - 1))) { isBg[x][h - 1] = true; queue.add(new int[]{x, h - 1}); }
        }
        for (int y = 0; y < h; y++) {
            if (isWhiteOrTransparent(src.getRGB(0, y))) { isBg[0][y] = true; queue.add(new int[]{0, y}); }
            if (isWhiteOrTransparent(src.getRGB(w - 1, y))) { isBg[w - 1][y] = true; queue.add(new int[]{w - 1, y}); }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0], cy = cur[1];
            for (int d = 0; d < 4; d++) {
                int nx = cx + dx[d], ny = cy + dy[d];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !isBg[nx][ny]) {
                    if (isWhiteOrTransparent(src.getRGB(nx, ny))) {
                        isBg[nx][ny] = true;
                        queue.add(new int[]{nx, ny});
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

    private boolean isWhiteOrTransparent(int argb) {
        int a = (argb >> 24) & 0xFF;
        if (a < 30) return true;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return r > 240 && g > 240 && b > 240;
    }

    /** Chuyển đổi xoay vòng giữa 4 bộ trang phục (0 -> 1 -> 2 -> 3 -> 0) */
    public void nextSkin() {
        currentSkin = (currentSkin + 1) % SKIN_COUNT;
        System.out.println("[Character] Da doi sang Trang phuc " + (currentSkin + 1) + " (knight" + currentSkin + ")");
    }

    public void setSkin(int skinIndex) {
        if (skinIndex >= 0 && skinIndex < SKIN_COUNT) {
            this.currentSkin = skinIndex;
        }
    }

    public int getCurrentSkin() {
        return currentSkin;
    }

    public void update(State newState) {
        long now = System.currentTimeMillis();

        if (isDead()) {
            if (this.currentState != State.DIE) {
                this.currentState = State.DIE;
                this.stateStartTime = now;
            }
            return;
        }

        if (this.currentState != newState) {
            this.currentState = newState;
            this.stateStartTime = now;
        }

        if (armor < maxArmor && (now - lastDamageTime >= armorRegenDelayMs)) {
            if (now - lastArmorRegenTime >= armorRegenIntervalMs) {
                armor++;
                lastArmorRegenTime = now;
            }
        }
    }

    public void render(Graphics2D g, double uiScale, double offsetY) {
        BufferedImage frame = getCurrentFrame();
        if (frame == null) return;

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        double finalScale = spriteScale * uiScale;
        int drawW = (int) Math.round(frame.getWidth() * finalScale);
        int drawH = (int) Math.round(frame.getHeight() * finalScale);

        int screenX = (int) Math.round(x * uiScale);
        int screenY = (int) Math.round(offsetY + y * uiScale);

        int topX = screenX - drawW / 2;
        int topY = screenY - drawH / 2;

        if (facingRight) {
            g.drawImage(frame, topX, topY, drawW, drawH, null);
        } else {
            g.drawImage(frame, topX + drawW, topY, -drawW, drawH, null);
        }

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
    }

    private BufferedImage getCurrentFrame() {
        long elapsed = System.currentTimeMillis() - stateStartTime;
        BufferedImage[] activeList;

        switch (currentState) {
            case DIE:
                activeList = dieFrames[currentSkin];
                if (activeList != null && activeList.length > 0) {
                    int idx = (int) (elapsed / DIE_FRAME_MS);
                    return activeList[Math.min(idx, activeList.length - 1)];
                }
                break;
            case DASH:
                activeList = dashFrames[currentSkin];
                if (activeList != null && activeList.length > 0) {
                    return activeList[(int) ((elapsed / DASH_FRAME_MS) % activeList.length)];
                }
                break;
            case RUN:
                activeList = runFrames[currentSkin];
                if (activeList != null && activeList.length > 0) {
                    return activeList[(int) ((elapsed / RUN_FRAME_MS) % activeList.length)];
                }
                break;
            case IDLE:
            default:
                activeList = idleFrames[currentSkin];
                if (activeList != null && activeList.length > 0) {
                    return activeList[(int) ((elapsed / IDLE_FRAME_MS) % activeList.length)];
                }
                break;
        }
        return null;
    }

    public void takeDamage(int amount) {
        if (isDead()) return;

        lastDamageTime = System.currentTimeMillis();
        lastArmorRegenTime = lastDamageTime;

        if (armor > 0) {
            int absorbed = Math.min(armor, amount);
            armor -= absorbed;
            amount -= absorbed;
        }
        if (amount > 0) {
            hp = Math.max(0, hp - amount);
        }

        if (hp <= 0 && currentState != State.DIE) {
            currentState = State.DIE;
            stateStartTime = System.currentTimeMillis();
        }
    }

    public void revive() {
        this.hp = this.maxHp;
        this.armor = this.maxArmor;
        this.mana = this.maxMana;
        this.currentState = State.IDLE;
        this.stateStartTime = System.currentTimeMillis();
    }

    public boolean isDead() {
        return hp <= 0;
    }

    // --- GETTERS & SETTERS ---
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPosition(double x, double y) { this.x = x; this.y = y; }
    public boolean isFacingRight() { return facingRight; }
    public void setFacingRight(boolean facingRight) { this.facingRight = facingRight; }
    public double getWeaponAnchorX() { return x + (facingRight ? weaponOffsetX : -weaponOffsetX); }
    public double getWeaponAnchorY() { return y + weaponOffsetY; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getArmor() { return armor; }
    public int getMaxArmor() { return maxArmor; }
    public int getMana() { return mana; }
    public int getMaxMana() { return maxMana; }
    public State getCurrentState() { return currentState; }
}