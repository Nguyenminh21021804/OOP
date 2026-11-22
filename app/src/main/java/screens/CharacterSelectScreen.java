package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class CharacterSelectScreen extends GameScreen {
    public static final String NAME = "CHARACTER_SELECT";

    private static final int PANEL_W = 820;
    private static final int PANEL_H = 330;
    private static final int PANEL_Y = 114;

    private static final int ARROW_W = 46;
    private static final int ARROW_H = 44;
    private static final int WEAPON_BTN_W = 180;
    private static final int WEAPON_BTN_H = 96;
    private static final int BOTTOM_W = 220;

    private static final int HERO_FRAMES = 4;
    private static final long FRAME_MS = 160;

    private static final Color PANEL_BG = new Color(16, 12, 18, 195);
    private static final Color PEDESTAL_BG = new Color(28, 22, 30, 215);
    private static final Color BORDER_OUTER = new Color(12, 8, 6);
    private static final Color BORDER_INNER = new Color(110, 96, 84);
    private static final Color DIVIDER_COLOR = new Color(90, 78, 68, 170);

    private static final Color SHADOW = new Color(20, 8, 4);
    private static final Color TITLE_GOLD = new Color(255, 214, 90);
    private static final Color SECTION_GOLD = new Color(245, 195, 95);
    private static final Color TEXT_MAIN = new Color(236, 224, 200);
    private static final Color TEXT_SUB = new Color(185, 174, 155);
    private static final Color WARN_RED = new Color(255, 110, 95);
    private static final Color BADGE_BG = new Color(255, 196, 72);
    private static final Color DOT_IDLE = new Color(85, 76, 70);

    // =========================================================
    // SUA TEN NHAN VAT HOAC DUONG DAN ANH O DAY (TIENG ANH KHONG DAU)
    // =========================================================
    private static final String[][] HEROES = {
            {"hero1", "Jade Knight", "sprites/heroes/hero01.png"},
            {"hero2", "Iron Knight", "sprites/heroes/hero02.png"},
            {"hero3", "Frost Mage",  "sprites/heroes/hero03.png"},
            {"hero4", "Dark Knight", "sprites/heroes/hero04.png"}
    };

    private static final String[][] WEAPONS = {
            {"sword", "Sword", "sprites/weapons/sword.png"},
            {"spear", "Spear", "sprites/weapons/spear.png"},
            {"bow",   "Bow",   "sprites/weapons/bow.png"},
            {"staff", "Staff", "sprites/weapons/staff.png"}
    };

    private BufferedImage[][] heroFrames; // <--- Da bo final va new
    private BufferedImage[] weaponImgs;   // <--- Da bo final va new

    private int selectedHero = 0;
    private int slot1 = 0; // Mac dinh: Sword
    private int slot2 = 2; // Mac dinh: Bow
    private String warningText = "";
    private long openedAt = 0;

    private PixelButton prevHeroBtn, nextHeroBtn;
    private PixelButton[] weaponBtns;     // <--- Da bo final va new
    private PixelButton backBtn, startBtn;

    private Font titleFont;
    private Font sectionFont;
    private Font nameFont;
    private Font smallFont;
    private Font badgeFont;
    private double fontScale = -1;
    private double lastW = -1, lastH = -1;

    public CharacterSelectScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        heroFrames = new BufferedImage[HEROES.length][]; // <--- Cap phat tai day
        weaponImgs = new BufferedImage[WEAPONS.length];  // <--- Cap phat tai day
        weaponBtns = new PixelButton[WEAPONS.length];    // <--- Cap phat tai day
        loadSprites();

        prevHeroBtn = new PixelButton(0, 0, ARROW_W, ARROW_H, "<");
        nextHeroBtn = new PixelButton(0, 0, ARROW_W, ARROW_H, ">");
        prevHeroBtn.onClicked(e -> {
            if (canClick()) {
                selectedHero = (selectedHero - 1 + HEROES.length) % HEROES.length;
            }
        });
        nextHeroBtn.onClicked(e -> {
            if (canClick()) {
                selectedHero = (selectedHero + 1) % HEROES.length;
            }
        });
        getComponents().add(prevHeroBtn);
        getComponents().add(nextHeroBtn);

        for (int i = 0; i < WEAPONS.length; i++) {
            final int idx = i;
            weaponBtns[i] = new PixelButton(0, 0, WEAPON_BTN_W, WEAPON_BTN_H, "");
            weaponBtns[i].onClicked(e -> {
                if (canClick()) {
                    toggleWeapon(idx);
                }
            });
            getComponents().add(weaponBtns[i]);
        }

        backBtn = new PixelButton(0, 0, BOTTOM_W, PixelButton.BODY_H, "Back");
        startBtn = new PixelButton(0, 0, BOTTOM_W, PixelButton.BODY_H, "Start");

        backBtn.onClicked(e -> {
            if (canClick()) {
                Game.screens().display(MainMenuScreen.NAME);
            }
        });
        startBtn.onClicked(e -> {
            if (canClick()) {
                onStartClicked();
            }
        });

        getComponents().add(backBtn);
        getComponents().add(startBtn);
    }

    @Override
    public void prepare() {
        super.prepare();
        openedAt = System.currentTimeMillis();
        warningText = "";
    }

    private boolean canClick() {
        return Game.screens().current() == this
                && System.currentTimeMillis() - openedAt > 250;
    }

    private void loadSprites() {
        for (int i = 0; i < HEROES.length; i++) {
            BufferedImage sheet = loadSafe(HEROES[i][2]);
            if (sheet != null && sheet.getWidth() >= HERO_FRAMES) {
                int fw = sheet.getWidth() / HERO_FRAMES;
                int fh = sheet.getHeight();
                heroFrames[i] = new BufferedImage[HERO_FRAMES];
                for (int f = 0; f < HERO_FRAMES; f++) {
                    heroFrames[i][f] = sheet.getSubimage(f * fw, 0, fw, fh);
                }
            }
        }
        for (int i = 0; i < WEAPONS.length; i++) {
            weaponImgs[i] = loadSafe(WEAPONS[i][2]);
        }
    }

    private static BufferedImage loadSafe(String path) {
        if (CharacterSelectScreen.class.getClassLoader().getResource(path) == null) {
            System.err.println("[CharacterSelect] CHUA TIM THAY ANH: " + path);
            return null;
        }
        try {
            return Resources.images().get(path);
        } catch (Exception e) {
            System.err.println("[CharacterSelect] LOI NAP " + path + ": " + e.getMessage());
            return null;
        }
    }

    private void toggleWeapon(int idx) {
        warningText = "";
        if (slot1 == idx) {
            slot1 = slot2;
            slot2 = -1;
        } else if (slot2 == idx) {
            slot2 = -1;
        } else if (slot1 == -1) {
            slot1 = idx;
        } else if (slot2 == -1) {
            slot2 = idx;
        } else {
            slot2 = idx;
        }
    }

    private void onStartClicked() {
        if (slot1 == -1 || slot2 == -1) {
            warningText = "Please select 2 weapons!";
            return;
        }
        GameSelection.get().setSelection(
                HEROES[selectedHero][0],
                HEROES[selectedHero][1],
                WEAPONS[slot1][0],
                WEAPONS[slot2][0]
        );
        GameSelection.get().triggerStartGame();
    }

    private void layout(Viewport v) {
        double k = v.k;
        double px = (v.w - PANEL_W * k) / 2.0;
        double py = v.offsetY + PANEL_Y * k;

        // Nut chuyen tuong ben trai
        double arrowY = py + 236 * k;
        setBtn(prevHeroBtn, px + 34 * k, arrowY, k);
        setBtn(nextHeroBtn, px + 290 * k, arrowY, k);

        // Luoi 2x2 vu khi ben phai
        double gridX = px + 402 * k;
        double gridY = py + 58 * k;
        double gapX = 24 * k;
        double gapY = 18 * k;
        for (int i = 0; i < WEAPONS.length; i++) {
            int col = i % 2;
            int row = i / 2;
            double bx = gridX + col * (WEAPON_BTN_W * k + gapX);
            double by = gridY + row * (WEAPON_BTN_H * k + gapY);
            setBtn(weaponBtns[i], bx, by, k);
        }

        // Nut Back & Start o duoi cung
        double bottomY = v.offsetY + (Viewport.UI_H - 96) * k;
        setBtn(backBtn, v.w / 2.0 - (BOTTOM_W + 20) * k, bottomY, k);
        setBtn(startBtn, v.w / 2.0 + 20 * k, bottomY, k);
    }

    private void setBtn(PixelButton btn, double x, double y, double k) {
        btn.setScale(k);
        btn.setLocation(x, y);
    }

    @Override
    public void render(Graphics2D g) {
        Viewport v = Viewport.current();
        if (v.w != lastW || v.h != lastH) {
            lastW = v.w;
            lastH = v.h;
            layout(v);
        }

        if (titleFont == null || fontScale != v.k) {
            fontScale = v.k;
            Font base = Resources.fonts().get("font/PeaberryBase.ttf");
            int titleSize = 16 * Math.max(1, (int) Math.floor(3 * v.k + 0.25));
            titleFont = base.deriveFont((float) titleSize);
            sectionFont = base.deriveFont((float) Math.max(14, Math.round(20 * v.k)));
            nameFont = base.deriveFont((float) Math.max(14, Math.round(20 * v.k)));
            smallFont = base.deriveFont((float) Math.max(12, Math.round(16 * v.k)));
            badgeFont = base.deriveFont((float) Math.max(11, Math.round(14 * v.k)));
        }

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // 1. Nen ham nguc + duoc chay
        MenuBackground.get().draw(g, v);

        // 2. Tieu de
        g.setFont(titleFont);
        FontMetrics tfm = g.getFontMetrics();
        String title = "Select Loadout";
        int tx = ((int) v.w - tfm.stringWidth(title)) / 2;
        int ty = (int) Math.round(v.offsetY + 84 * v.k);
        int tsh = Math.max(1, (int) Math.round(3 * v.k));
        drawText(g, title, tx, ty, TITLE_GOLD, tsh);

        // 3. Khung toi chia 2 cot
        int px = (int) Math.round((v.w - PANEL_W * v.k) / 2.0);
        int py = (int) Math.round(v.offsetY + PANEL_Y * v.k);
        int pw = (int) Math.round(PANEL_W * v.k);
        int ph = (int) Math.round(PANEL_H * v.k);
        int border = Math.max(2, (int) Math.round(3 * v.k));
        int sh = Math.max(1, (int) Math.round(2 * v.k));

        g.setColor(PANEL_BG);
        g.fillRect(px, py, pw, ph);
        g.setColor(BORDER_OUTER);
        g.drawRect(px, py, pw, ph);
        g.drawRect(px + 1, py + 1, pw - 2, ph - 2);
        g.setColor(BORDER_INNER);
        g.drawRect(px + border, py + border, pw - border * 2, ph - border * 2);

        int divX = (int) Math.round(px + 370 * v.k);
        g.setColor(DIVIDER_COLOR);
        g.fillRect(divX, (int) Math.round(py + 22 * v.k),
                Math.max(2, (int) Math.round(2 * v.k)),
                (int) Math.round(ph - 44 * v.k));

        // 4. Cot trai: HERO PREVIEW
        int leftCenterX = (int) Math.round(px + 185 * v.k);
        int headerY = (int) Math.round(py + 38 * v.k);
        g.setFont(sectionFont);
        drawCenteredText(g, "CHOOSE HERO", leftCenterX, headerY, SECTION_GOLD, sh);

        // Khung be dung nhan vat
        int pedW = (int) Math.round(176 * v.k);
        int pedH = (int) Math.round(160 * v.k);
        int pedX = leftCenterX - pedW / 2;
        int pedY = (int) Math.round(py + 58 * v.k);
        g.setColor(PEDESTAL_BG);
        g.fillRect(pedX, pedY, pedW, pedH);
        g.setColor(BORDER_INNER);
        g.drawRect(pedX, pedY, pedW, pedH);

        // Bong chan nhan vat
        int ovalW = (int) Math.round(76 * v.k);
        int ovalH = (int) Math.round(18 * v.k);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillOval(leftCenterX - ovalW / 2, pedY + pedH - (int) Math.round(30 * v.k), ovalW, ovalH);

        // Ve sprite nhan vat co animation 4 frame
        BufferedImage[] frames = heroFrames[selectedHero];
        if (frames != null) {
            int frameIdx = (int) ((Game.time().now() / FRAME_MS) % HERO_FRAMES);
            BufferedImage frame = frames[frameIdx];
            drawSpriteFit(g, frame, pedX + (int) Math.round(24 * v.k), pedY + (int) Math.round(18 * v.k),
                    pedW - (int) Math.round(48 * v.k), pedH - (int) Math.round(38 * v.k));
        } else {
            g.setFont(smallFont);
            drawCenteredText(g, HEROES[selectedHero][0] + ".png", leftCenterX, pedY + pedH / 2, TEXT_SUB, sh);
        }

        // Ten nhan vat giua 2 nut < >
        g.setFont(nameFont);
        FontMetrics nfm = g.getFontMetrics();
        int nameY = (int) Math.round(py + 236 * v.k)
                + ((int) Math.round(ARROW_H * v.k) - (nfm.getAscent() + nfm.getDescent())) / 2
                + nfm.getAscent();
        drawCenteredText(g, HEROES[selectedHero][1], leftCenterX, nameY, TEXT_MAIN, sh);

        // 4 cham chi bao nhan vat dang chon
        int dotSize = Math.max(6, (int) Math.round(10 * v.k));
        int dotGap = Math.max(6, (int) Math.round(10 * v.k));
        int dotsTotalW = HEROES.length * dotSize + (HEROES.length - 1) * dotGap;
        int dotStartX = leftCenterX - dotsTotalW / 2;
        int dotY = (int) Math.round(py + 296 * v.k);
        for (int i = 0; i < HEROES.length; i++) {
            int dx = dotStartX + i * (dotSize + dotGap);
            g.setColor(i == selectedHero ? TITLE_GOLD : DOT_IDLE);
            g.fillRect(dx, dotY, dotSize, dotSize);
            g.setColor(BORDER_OUTER);
            g.drawRect(dx, dotY, dotSize, dotSize);
        }

        // 5. Cot phai: Tieu de WEAPONS
        int rightCenterX = (int) Math.round(px + 595 * v.k);
        g.setFont(sectionFont);
        drawCenteredText(g, "CHOOSE 2 WEAPONS", rightCenterX, headerY, SECTION_GOLD, sh);

        // Dong trang thai 2 slot vu khi o day cot phai
        g.setFont(smallFont);
        int statusY = (int) Math.round(py + 306 * v.k);
        if (!warningText.isEmpty()) {
            drawCenteredText(g, warningText, rightCenterX, statusY, WARN_RED, sh);
        } else {
            String w1 = slot1 >= 0 ? WEAPONS[slot1][1] : "---";
            String w2 = slot2 >= 0 ? WEAPONS[slot2][1] : "---";
            drawCenteredText(g, "Slot 1: " + w1 + "   |   Slot 2: " + w2, rightCenterX, statusY, TITLE_GOLD, sh);
        }

        // 6. Ve toan bo nut bam (PixelButton)
        super.render(g);

        // 7. Ve icon vu khi + ten + huy hieu [1]/[2] de len 4 nut vu khi
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < WEAPONS.length; i++) {
            PixelButton btn = weaponBtns[i];
            int bx = (int) Math.round(btn.getX());
            int by = (int) Math.round(btn.getY());
            int bw = (int) Math.round(btn.getWidth());
            int bh = (int) Math.round(btn.getHeight());
            int pressOff = btn.isPressed() ? (int) Math.round(3 * v.k) : 0;

            int slotNum = (slot1 == i) ? 1 : (slot2 == i ? 2 : 0);

            // Vien vang bao hieu dang chon
            if (slotNum > 0) {
                g.setColor(TITLE_GOLD);
                g.drawRect(bx - 1, by - 1, bw + 2, bh + 2);
                g.drawRect(bx, by, bw, bh);
            }

            // Icon vu khi o phan tren cua o
            BufferedImage wImg = weaponImgs[i];
            if (wImg != null) {
                int padX = (int) Math.round(14 * v.k);
                int padTop = (int) Math.round(10 * v.k) + pressOff;
                int iconH = (int) Math.round(bh * 0.56);
                drawSpriteFit(g, wImg, bx + padX, by + padTop, bw - padX * 2, iconH);
            }

            // Ten vu khi o phan duoi cua o
            g.setFont(smallFont);
            int labelY = by + bh - (int) Math.round(12 * v.k) + pressOff;
            drawCenteredText(g, WEAPONS[i][1], bx + bw / 2, labelY,
                    slotNum > 0 ? TITLE_GOLD : TEXT_MAIN, sh);

            // Huy hieu [1] hoac [2] o goc tren phai
            if (slotNum > 0) {
                int badgeSize = Math.max(16, (int) Math.round(22 * v.k));
                int bgX = bx + bw - badgeSize - (int) Math.round(6 * v.k);
                int bgY = by + (int) Math.round(6 * v.k) + pressOff;
                g.setColor(BADGE_BG);
                g.fillRect(bgX, bgY, badgeSize, badgeSize);
                g.setColor(BORDER_OUTER);
                g.drawRect(bgX, bgY, badgeSize, badgeSize);

                g.setFont(badgeFont);
                FontMetrics bfm = g.getFontMetrics();
                String numStr = String.valueOf(slotNum);
                int nx = bgX + (badgeSize - bfm.stringWidth(numStr)) / 2;
                int ny = bgY + (badgeSize - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent();
                g.setColor(BORDER_OUTER);
                g.drawString(numStr, nx, ny);
            }
        }

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }
    }

    private void drawSpriteFit(Graphics2D g, BufferedImage img, int boxX, int boxY, int boxW, int boxH) {
        double scale = Math.min((double) boxW / img.getWidth(), (double) boxH / img.getHeight());
        int dw = Math.max(1, (int) Math.round(img.getWidth() * scale));
        int dh = Math.max(1, (int) Math.round(img.getHeight() * scale));
        int dx = boxX + (boxW - dw) / 2;
        int dy = boxY + (boxH - dh) / 2;
        g.drawImage(img, dx, dy, dw, dh, null);
    }

    private void drawCenteredText(Graphics2D g, String text, int centerX, int y, Color color, int sh) {
        FontMetrics fm = g.getFontMetrics();
        drawText(g, text, centerX - fm.stringWidth(text) / 2, y, color, sh);
    }

    private void drawText(Graphics2D g, String text, int x, int y, Color color, int sh) {
        g.setColor(SHADOW);
        g.drawString(text, x + sh, y + sh);
        g.setColor(color);
        g.drawString(text, x, y);
    }
}