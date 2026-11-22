package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.configuration.DisplayMode;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class SettingsScreen extends GameScreen {
    public static final String NAME = "SETTINGS";

    private static final int PANEL_W = 680;
    private static final int PANEL_H = 304;
    private static final int PANEL_Y = 134;

    private static final int SMALL_W = 46;
    private static final int ROW_BTN_H = 44;
    private static final int TOGGLE_W = 140;
    private static final int BOTTOM_W = 220;

    private static final int SEGMENTS = 10;
    private static final int SEG_W = 15;
    private static final int SEG_H = 22;
    private static final int SEG_GAP = 4;

    private static final Color PANEL_BG = new Color(16, 12, 18, 190);
    private static final Color PANEL_BORDER_OUTER = new Color(12, 8, 6);
    private static final Color PANEL_BORDER_INNER = new Color(110, 96, 84);
    private static final Color SHADOW = new Color(20, 8, 4);
    private static final Color TITLE_GOLD = new Color(255, 214, 90);
    private static final Color LABEL_COLOR = new Color(236, 224, 200);
    private static final Color BAR_ACTIVE = new Color(255, 196, 72);
    private static final Color BAR_ACTIVE_TOP = new Color(255, 232, 140);
    private static final Color BAR_EMPTY = new Color(56, 50, 48);

    private PixelButton musicMinus, musicPlus;
    private PixelButton sfxMinus, sfxPlus;
    private PixelButton fullscreenBtn, fpsBtn;
    private PixelButton resetBtn, backBtn;

    private int musicLevel = 7; // 0..10
    private int sfxLevel = 8;   // 0..10
    private boolean fullscreen = false;
    private boolean showFps = false;

    private Font titleFont;
    private Font labelFont;
    private double fontScale = -1;
    private double lastW = -1, lastH = -1;

    public SettingsScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        musicMinus = new PixelButton(0, 0, SMALL_W, ROW_BTN_H, "-");
        musicPlus = new PixelButton(0, 0, SMALL_W, ROW_BTN_H, "+");
        sfxMinus = new PixelButton(0, 0, SMALL_W, ROW_BTN_H, "-");
        sfxPlus = new PixelButton(0, 0, SMALL_W, ROW_BTN_H, "+");
        fullscreenBtn = new PixelButton(0, 0, TOGGLE_W, ROW_BTN_H, "OFF");
        fpsBtn = new PixelButton(0, 0, TOGGLE_W, ROW_BTN_H, "OFF");
        resetBtn = new PixelButton(0, 0, BOTTOM_W, PixelButton.BODY_H, "Reset");
        backBtn = new PixelButton(0, 0, BOTTOM_W, PixelButton.BODY_H, "Back");

        musicMinus.onClicked(e -> changeMusic(-1));
        musicPlus.onClicked(e -> changeMusic(1));
        sfxMinus.onClicked(e -> changeSfx(-1));
        sfxPlus.onClicked(e -> changeSfx(1));
        fullscreenBtn.onClicked(e -> toggleFullscreen());
        fpsBtn.onClicked(e -> toggleFps());
        resetBtn.onClicked(e -> resetDefaults());
        backBtn.onClicked(e -> {
            try {
                Game.config().save();
            } catch (Exception ex) {
                System.err.println("[SettingsScreen] Loi luu config: " + ex.getMessage());
            }
            Game.screens().display(MainMenuScreen.NAME);
        });

        getComponents().add(musicMinus);
        getComponents().add(musicPlus);
        getComponents().add(sfxMinus);
        getComponents().add(sfxPlus);
        getComponents().add(fullscreenBtn);
        getComponents().add(fpsBtn);
        getComponents().add(resetBtn);
        getComponents().add(backBtn);

        syncFromConfig();
    }

    @Override
    public void prepare() {
        super.prepare();
        syncFromConfig();
    }

    private void syncFromConfig() {
        musicLevel = Math.max(0, Math.min(10, Math.round(Game.config().sound().getMusicVolume() * 10f)));
        sfxLevel = Math.max(0, Math.min(10, Math.round(Game.config().sound().getSoundVolume() * 10f)));
        fullscreen = Game.config().graphics().getDisplayMode() == DisplayMode.FULLSCREEN;
        showFps = Game.config().client().showGameMetrics();
        updateToggleLabels();
    }

    private void updateToggleLabels() {
        if (fullscreenBtn != null) {
            fullscreenBtn.setLabel(fullscreen ? "ON" : "OFF");
        }
        if (fpsBtn != null) {
            fpsBtn.setLabel(showFps ? "ON" : "OFF");
        }
    }

    private void changeMusic(int delta) {
        musicLevel = Math.max(0, Math.min(10, musicLevel + delta));
        Game.config().sound().setMusicVolume(musicLevel / 10f);
    }

    private void changeSfx(int delta) {
        sfxLevel = Math.max(0, Math.min(10, sfxLevel + delta));
        Game.config().sound().setSoundVolume(sfxLevel / 10f);
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        Game.config().graphics().setDisplayMode(fullscreen ? DisplayMode.FULLSCREEN : DisplayMode.WINDOWED);
        updateToggleLabels();
    }

    private void toggleFps() {
        showFps = !showFps;
        Game.config().client().setShowGameMetrics(showFps);
        updateToggleLabels();
    }

    private void resetDefaults() {
        musicLevel = 7;
        sfxLevel = 8;
        fullscreen = false;
        showFps = false;
        Game.config().sound().setMusicVolume(musicLevel / 10f);
        Game.config().sound().setSoundVolume(sfxLevel / 10f);
        Game.config().graphics().setDisplayMode(DisplayMode.WINDOWED);
        Game.config().client().setShowGameMetrics(showFps);
        Game.config().save();
        updateToggleLabels();
    }

    private void layout(Viewport v) {
        double k = v.k;
        double panelX = (v.w - PANEL_W * k) / 2.0;
        double ctrlX = panelX + 270 * k;
        double plusX = ctrlX + (SMALL_W + 12 + SEGMENTS * SEG_W + (SEGMENTS - 1) * SEG_GAP + 12) * k;

        double row0Y = v.offsetY + (PANEL_Y + 24) * k;
        double row1Y = v.offsetY + (PANEL_Y + 92) * k;
        double row2Y = v.offsetY + (PANEL_Y + 160) * k;
        double row3Y = v.offsetY + (PANEL_Y + 228) * k;

        setBtn(musicMinus, ctrlX, row0Y, k);
        setBtn(musicPlus, plusX, row0Y, k);
        setBtn(sfxMinus, ctrlX, row1Y, k);
        setBtn(sfxPlus, plusX, row1Y, k);
        setBtn(fullscreenBtn, ctrlX, row2Y, k);
        setBtn(fpsBtn, ctrlX, row3Y, k);

        double bottomY = v.offsetY + (Viewport.UI_H - 96) * k;
        setBtn(resetBtn, v.w / 2.0 - (BOTTOM_W + 20) * k, bottomY, k);
        setBtn(backBtn, v.w / 2.0 + 20 * k, bottomY, k);
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
            int titleSize = 16 * Math.max(1, (int) Math.floor(3 * v.k + 0.25));
            int labelSize = Math.max(14, (int) Math.round(24 * v.k));
            titleFont = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont((float) titleSize);
            labelFont = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont((float) labelSize);
        }

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // 1. Nền hầm ngục + đuốc cháy
        MenuBackground.get().draw(g, v);

        // 2. Tiêu đề Settings (giữ nguyên style cũ)
        g.setFont(titleFont);
        FontMetrics tfm = g.getFontMetrics();
        String title = "Settings";
        int tx = ((int) v.w - tfm.stringWidth(title)) / 2;
        int ty = (int) Math.round(v.offsetY + 96 * v.k);
        int tsh = Math.max(1, (int) Math.round(3 * v.k));
        g.setColor(SHADOW);
        g.drawString(title, tx + tsh, ty + tsh);
        g.setColor(TITLE_GOLD);
        g.drawString(title, tx, ty);

        // 3. Khung tối bán trong suốt chứa các mục cài đặt
        int px = (int) Math.round((v.w - PANEL_W * v.k) / 2.0);
        int py = (int) Math.round(v.offsetY + PANEL_Y * v.k);
        int pw = (int) Math.round(PANEL_W * v.k);
        int ph = (int) Math.round(PANEL_H * v.k);
        int border = Math.max(2, (int) Math.round(3 * v.k));

        g.setColor(PANEL_BG);
        g.fillRect(px, py, pw, ph);
        g.setColor(PANEL_BORDER_OUTER);
        g.drawRect(px, py, pw, ph);
        g.drawRect(px + 1, py + 1, pw - 2, ph - 2);
        g.setColor(PANEL_BORDER_INNER);
        g.drawRect(px + border, py + border, pw - border * 2, ph - border * 2);

        // 4. Nhãn chữ & thanh âm lượng 10 nấc
        g.setFont(labelFont);
        FontMetrics lfm = g.getFontMetrics();
        int labelX = (int) Math.round(px + 38 * v.k);
        int barX = (int) Math.round(px + (270 + SMALL_W + 12) * v.k);
        int pctX = (int) Math.round(px + (270 + SMALL_W + 12 + SEGMENTS * SEG_W + (SEGMENTS - 1) * SEG_GAP + 12 + SMALL_W + 14) * v.k);

        int[] rowTops = {
                (int) Math.round(v.offsetY + (PANEL_Y + 24) * v.k),
                (int) Math.round(v.offsetY + (PANEL_Y + 92) * v.k),
                (int) Math.round(v.offsetY + (PANEL_Y + 160) * v.k),
                (int) Math.round(v.offsetY + (PANEL_Y + 228) * v.k)
        };
        String[] labels = {"Music Volume", "SFX Volume", "Fullscreen", "Show FPS"};
        int btnH = (int) Math.round(ROW_BTN_H * v.k);
        int sh = Math.max(1, (int) Math.round(2 * v.k));

        for (int i = 0; i < labels.length; i++) {
            int baseline = rowTops[i] + (btnH - (lfm.getAscent() + lfm.getDescent())) / 2 + lfm.getAscent();
            drawText(g, labels[i], labelX, baseline, LABEL_COLOR, sh);
        }

        drawVolumeBar(g, barX, rowTops[0], btnH, musicLevel, v.k);
        drawVolumeBar(g, barX, rowTops[1], btnH, sfxLevel, v.k);

        int musicBase = rowTops[0] + (btnH - (lfm.getAscent() + lfm.getDescent())) / 2 + lfm.getAscent();
        int sfxBase = rowTops[1] + (btnH - (lfm.getAscent() + lfm.getDescent())) / 2 + lfm.getAscent();
        drawText(g, (musicLevel * 10) + "%", pctX, musicBase, TITLE_GOLD, sh);
        drawText(g, (sfxLevel * 10) + "%", pctX, sfxBase, TITLE_GOLD, sh);

        // 5. Vẽ toàn bộ nút bấm
        super.render(g);
    }

    private void drawText(Graphics2D g, String text, int x, int y, Color color, int sh) {
        g.setColor(SHADOW);
        g.drawString(text, x + sh, y + sh);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawVolumeBar(Graphics2D g, int startX, int rowTop, int rowH, int level, double k) {
        int sw = Math.max(4, (int) Math.round(SEG_W * k));
        int sh = Math.max(8, (int) Math.round(SEG_H * k));
        int gap = Math.max(1, (int) Math.round(SEG_GAP * k));
        int y = rowTop + (rowH - sh) / 2;

        for (int i = 0; i < SEGMENTS; i++) {
            int x = startX + i * (sw + gap);
            if (i < level) {
                g.setColor(BAR_ACTIVE);
                g.fillRect(x, y, sw, sh);
                g.setColor(BAR_ACTIVE_TOP);
                g.fillRect(x, y, sw, Math.max(2, sh / 4));
            } else {
                g.setColor(BAR_EMPTY);
                g.fillRect(x, y, sw, sh);
            }
            g.setColor(PANEL_BORDER_OUTER);
            g.drawRect(x, y, sw, sh);
        }
    }
}