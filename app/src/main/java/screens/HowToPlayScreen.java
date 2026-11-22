package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class HowToPlayScreen extends GameScreen {
    public static final String NAME = "HOWTOPLAY";

    private static final int PANEL_W = 780;
    private static final int PANEL_H = 316;
    private static final int PANEL_Y = 128;

    private static final Color PANEL_BG = new Color(16, 12, 18, 195);
    private static final Color BORDER_OUTER = new Color(12, 8, 6);
    private static final Color BORDER_INNER = new Color(110, 96, 84);
    private static final Color DIVIDER_COLOR = new Color(90, 78, 68, 170);

    private static final Color SHADOW = new Color(20, 8, 4);
    private static final Color TITLE_GOLD = new Color(255, 214, 90);
    private static final Color SECTION_GOLD = new Color(245, 195, 95);
    private static final Color TEXT_MAIN = new Color(236, 224, 200);
    private static final Color TEXT_SUB = new Color(205, 194, 175);

    private static final Color KEY_BG = new Color(68, 62, 58);
    private static final Color KEY_TOP = new Color(112, 102, 92);
    private static final Color KEY_BOTTOM = new Color(36, 32, 30);

    private static final Color HP_COLOR = new Color(210, 52, 52);
    private static final Color ARMOR_COLOR = new Color(155, 168, 184);
    private static final Color MANA_COLOR = new Color(56, 136, 232);

    private static final String[][] CONTROLS = {
            {"WASD", "Move Knight"},
            {"J", "Attack"},
            {"K", "Dash / Dodge"},
            {"U", "Cast Skill"},
            {"Q", "Switch Weapon"},
            {"C", "Change Skin"}
    };

    private static final String[] TIPS = {
            "Clear rooms & defeat the Boss.",
            "Avoid floor spikes & fire traps.",
            "Open chests for loot & potions."
    };

    private static final String[] STAT_LABELS = {
            "HP: Survive enemy attacks.",
            "ARMOR: Absorbs incoming hits.",
            "MANA: Fuels weapons & skills."
    };

    private static final Color[] STAT_COLORS = {HP_COLOR, ARMOR_COLOR, MANA_COLOR};

    private PixelButton backBtn;
    private Font titleFont;
    private Font sectionFont;
    private Font bodyFont;
    private Font keyFont;
    private double fontScale = -1;
    private double lastW = -1, lastH = -1;

    public HowToPlayScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        backBtn = new PixelButton(0, 0, "Back");
        backBtn.onClicked(e -> Game.screens().display(MainMenuScreen.NAME));
        getComponents().add(backBtn);
    }

    private void layout(Viewport v) {
        backBtn.setScale(v.k);
        backBtn.setLocation(
                (v.w - PixelButton.BODY_W * v.k) / 2.0,
                v.offsetY + (Viewport.UI_H - 96) * v.k
        );
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
            sectionFont = base.deriveFont((float) Math.max(14, Math.round(21 * v.k)));
            bodyFont = base.deriveFont((float) Math.max(12, Math.round(17 * v.k)));
            keyFont = base.deriveFont((float) Math.max(12, Math.round(16 * v.k)));
        }

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // 1. Nền hầm ngục + đuốc cháy
        MenuBackground.get().draw(g, v);

        // 2. Tiêu đề How to Play
        g.setFont(titleFont);
        FontMetrics tfm = g.getFontMetrics();
        String title = "How to Play";
        int tx = ((int) v.w - tfm.stringWidth(title)) / 2;
        int ty = (int) Math.round(v.offsetY + 94 * v.k);
        int tsh = Math.max(1, (int) Math.round(3 * v.k));
        drawText(g, title, tx, ty, TITLE_GOLD, tsh);

        // 3. Khung tối bán trong suốt
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

        // Đường kẻ chia 2 cột ở giữa
        int divX = (int) Math.round(px + 345 * v.k);
        int divTop = (int) Math.round(py + 22 * v.k);
        int divBot = (int) Math.round(py + ph - 22 * v.k);
        g.setColor(DIVIDER_COLOR);
        g.fillRect(divX, divTop, Math.max(2, (int) Math.round(2 * v.k)), divBot - divTop);

        // 4. Cột trái: CONTROLS
        int leftX = (int) Math.round(px + 28 * v.k);
        int headerY = (int) Math.round(py + 38 * v.k);
        g.setFont(sectionFont);
        drawText(g, "CONTROLS", leftX, headerY, SECTION_GOLD, sh);

        int keyW = (int) Math.round(74 * v.k);
        int keyH = (int) Math.round(28 * v.k);
        int rowStartY = (int) Math.round(py + 58 * v.k);
        int rowPitch = (int) Math.round(40 * v.k);
        int descX = leftX + keyW + (int) Math.round(16 * v.k);

        g.setFont(bodyFont);
        FontMetrics bfm = g.getFontMetrics();

        for (int i = 0; i < CONTROLS.length; i++) {
            int ry = rowStartY + i * rowPitch;
            drawKeycap(g, CONTROLS[i][0], leftX, ry, keyW, keyH, v.k);

            g.setFont(bodyFont);
            int textY = ry + (keyH - (bfm.getAscent() + bfm.getDescent())) / 2 + bfm.getAscent();
            drawText(g, CONTROLS[i][1], descX, textY, TEXT_MAIN, sh);
        }

        // 5. Cột phải: DUNGEON GUIDE + VITAL STATS
        int rightX = (int) Math.round(px + 372 * v.k);
        g.setFont(sectionFont);
        drawText(g, "DUNGEON GUIDE", rightX, headerY, SECTION_GOLD, sh);

        g.setFont(bodyFont);
        int bulletSize = Math.max(4, (int) Math.round(6 * v.k));
        int tipStartY = (int) Math.round(py + 76 * v.k);
        int tipPitch = (int) Math.round(30 * v.k);

        for (int i = 0; i < TIPS.length; i++) {
            int tyRow = tipStartY + i * tipPitch;
            int by = tyRow - bfm.getAscent() / 2 - bulletSize / 2 + Math.max(1, (int) Math.round(2 * v.k));
            g.setColor(TITLE_GOLD);
            g.fillRect(rightX, by, bulletSize, bulletSize);
            drawText(g, TIPS[i], rightX + (int) Math.round(16 * v.k), tyRow, TEXT_SUB, sh);
        }

        // Đường kẻ ngang nhỏ phân tách Guide và Stats
        int subDivY = (int) Math.round(py + 162 * v.k);
        int subDivW = (int) Math.round(378 * v.k);
        g.setColor(DIVIDER_COLOR);
        g.fillRect(rightX, subDivY, subDivW, Math.max(1, (int) Math.round(2 * v.k)));

        // VITAL STATS
        int statHeaderY = (int) Math.round(py + 194 * v.k);
        g.setFont(sectionFont);
        drawText(g, "VITAL STATS", rightX, statHeaderY, SECTION_GOLD, sh);

        g.setFont(bodyFont);
        int boxSize = Math.max(8, (int) Math.round(14 * v.k));
        int statStartY = (int) Math.round(py + 226 * v.k);
        int statPitch = (int) Math.round(30 * v.k);

        for (int i = 0; i < STAT_LABELS.length; i++) {
            int syRow = statStartY + i * statPitch;
            int boxY = syRow - bfm.getAscent() + (bfm.getAscent() - boxSize) / 2 + Math.max(1, (int) Math.round(2 * v.k));
            g.setColor(STAT_COLORS[i]);
            g.fillRect(rightX, boxY, boxSize, boxSize);
            g.setColor(BORDER_OUTER);
            g.drawRect(rightX, boxY, boxSize, boxSize);

            drawText(g, STAT_LABELS[i], rightX + boxSize + (int) Math.round(12 * v.k), syRow, TEXT_MAIN, sh);
        }

        // 6. Vẽ nút Back
        super.render(g);
    }

    private void drawKeycap(Graphics2D g, String key, int x, int y, int w, int h, double k) {
        int bevel = Math.max(2, (int) Math.round(3 * k));

        g.setColor(KEY_BG);
        g.fillRect(x, y, w, h);

        g.setColor(KEY_TOP);
        g.fillRect(x, y, w, bevel);

        g.setColor(KEY_BOTTOM);
        g.fillRect(x, y + h - bevel, w, bevel);

        g.setColor(BORDER_OUTER);
        g.drawRect(x, y, w, h);

        g.setFont(keyFont);
        FontMetrics kfm = g.getFontMetrics();
        int kx = x + (w - kfm.stringWidth(key)) / 2;
        int ky = y + (h - (kfm.getAscent() + kfm.getDescent())) / 2 + kfm.getAscent();
        int sh = Math.max(1, (int) Math.round(1.5 * k));
        drawText(g, key, kx, ky, TITLE_GOLD, sh);
    }

    private void drawText(Graphics2D g, String text, int x, int y, Color color, int sh) {
        g.setColor(SHADOW);
        g.drawString(text, x + sh, y + sh);
        g.setColor(color);
        g.drawString(text, x, y);
    }
}
