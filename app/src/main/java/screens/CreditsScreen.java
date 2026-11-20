package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class CreditsScreen extends GameScreen {
    public static final String NAME = "CREDITS";

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

    private static final String[] TEAM = {
            "Le Anh Tuan",
            "Quach Manh Toan",
            "Nguyen Duc Bao Minh",
            "Luong Dang Minh",
            "Nguyen Duc Viet"
    };

    private static final String[] TOOLS = {
            "Java & LITIENGINE 0.12",
            "Tiled Map Editor"
    };

    private static final String[] ASSETS = {
            "0x72 DungeonTileset",
            "Peaberry & DungeonFont"
    };

    private PixelButton backBtn;
    private Font titleFont;
    private Font sectionFont;
    private Font nameFont;
    private Font bodyFont;
    private double fontScale = -1;
    private double lastW = -1, lastH = -1;

    public CreditsScreen() {
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
            nameFont = base.deriveFont((float) Math.max(14, Math.round(20 * v.k)));
            bodyFont = base.deriveFont((float) Math.max(13, Math.round(18 * v.k)));
        }

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // 1. Nen ham nguc + duoc chay
        MenuBackground.get().draw(g, v);

        // 2. Tieu de Credits
        g.setFont(titleFont);
        FontMetrics tfm = g.getFontMetrics();
        String title = "Credits";
        int tx = ((int) v.w - tfm.stringWidth(title)) / 2;
        int ty = (int) Math.round(v.offsetY + 94 * v.k);
        int tsh = Math.max(1, (int) Math.round(3 * v.k));
        drawText(g, title, tx, ty, TITLE_GOLD, tsh);

        // 3. Khung toi ban trong suot
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

        // Duong ke chia doi khung
        int divX = (int) Math.round(px + 390 * v.k);
        int divTop = (int) Math.round(py + 22 * v.k);
        int divBot = (int) Math.round(py + ph - 22 * v.k);
        g.setColor(DIVIDER_COLOR);
        g.fillRect(divX, divTop, Math.max(2, (int) Math.round(2 * v.k)), divBot - divTop);

        // 4. Cot trai: GROUP 17 - TEAM (5 thanh vien)
        int leftX = (int) Math.round(px + 36 * v.k);
        int headerY = (int) Math.round(py + 42 * v.k);
        g.setFont(sectionFont);
        drawText(g, "GROUP 17 - TEAM", leftX, headerY, SECTION_GOLD, sh);

        g.setFont(nameFont);
        FontMetrics nfm = g.getFontMetrics();
        int bulletSize = Math.max(4, (int) Math.round(6 * v.k));
        int memberStartY = (int) Math.round(py + 90 * v.k);
        int memberPitch = (int) Math.round(44 * v.k);

        for (int i = 0; i < TEAM.length; i++) {
            int nameY = memberStartY + i * memberPitch;
            int by = nameY - nfm.getAscent() / 2 - bulletSize / 2 + Math.max(1, (int) Math.round(2 * v.k));
            g.setColor(TITLE_GOLD);
            g.fillRect(leftX, by, bulletSize, bulletSize);
            drawText(g, TEAM[i], leftX + (int) Math.round(18 * v.k), nameY, TEXT_MAIN, sh);
        }

        // 5. Cot phai: TOOLS & ASSETS
        int rightX = (int) Math.round(px + 420 * v.k);
        int subDivW = (int) Math.round(328 * v.k);

        g.setFont(sectionFont);
        drawText(g, "TOOLS & ENGINE", rightX, headerY, SECTION_GOLD, sh);

        g.setFont(bodyFont);
        FontMetrics bfm = g.getFontMetrics();
        int toolStartY = (int) Math.round(py + 78 * v.k);
        int linePitch = (int) Math.round(30 * v.k);
        for (int i = 0; i < TOOLS.length; i++) {
            int ly = toolStartY + i * linePitch;
            int by = ly - bfm.getAscent() / 2 - bulletSize / 2 + Math.max(1, (int) Math.round(2 * v.k));
            g.setColor(TITLE_GOLD);
            g.fillRect(rightX, by, bulletSize, bulletSize);
            drawText(g, TOOLS[i], rightX + (int) Math.round(16 * v.k), ly, TEXT_MAIN, sh);
        }

        int subDiv1Y = (int) Math.round(py + 130 * v.k);
        g.setColor(DIVIDER_COLOR);
        g.fillRect(rightX, subDiv1Y, subDivW, Math.max(1, (int) Math.round(2 * v.k)));

        int artHeaderY = (int) Math.round(py + 164 * v.k);
        g.setFont(sectionFont);
        drawText(g, "ART & FONTS", rightX, artHeaderY, SECTION_GOLD, sh);

        g.setFont(bodyFont);
        int assetStartY = (int) Math.round(py + 200 * v.k);
        for (int i = 0; i < ASSETS.length; i++) {
            int ly = assetStartY + i * linePitch;
            int by = ly - bfm.getAscent() / 2 - bulletSize / 2 + Math.max(1, (int) Math.round(2 * v.k));
            g.setColor(TITLE_GOLD);
            g.fillRect(rightX, by, bulletSize, bulletSize);
            drawText(g, ASSETS[i], rightX + (int) Math.round(16 * v.k), ly, TEXT_MAIN, sh);
        }

        int subDiv2Y = (int) Math.round(py + 248 * v.k);
        g.setColor(DIVIDER_COLOR);
        g.fillRect(rightX, subDiv2Y, subDivW, Math.max(1, (int) Math.round(2 * v.k)));

        g.setFont(sectionFont);
        drawText(g, "Thank you for playing!", rightX, (int) Math.round(py + 286 * v.k), TITLE_GOLD, sh);

        // 6. Ve nut Back
        super.render(g);
    }

    private void drawText(Graphics2D g, String text, int x, int y, Color color, int sh) {
        g.setColor(SHADOW);
        g.drawString(text, x + sh, y + sh);
        g.setColor(color);
        g.drawString(text, x, y);
    }
}