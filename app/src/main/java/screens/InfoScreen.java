package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public class InfoScreen extends GameScreen {
    private final String title;
    private PixelButton back;
    private Font titleFont;
    private double fontScale = -1;
    private double lastW = -1, lastH = -1;

    public InfoScreen(String name, String title) {
        super(name);
        this.title = title;
    }

    @Override
    protected void initializeComponents() {
        back = new PixelButton(0, 0, "Back");
        back.onClicked(e -> Game.screens().display(MainMenuScreen.NAME));
        this.getComponents().add(back);
    }

    @Override
    public void render(Graphics2D g) {
        Viewport v = Viewport.current();
        if (v.w != lastW || v.h != lastH) {
            lastW = v.w;
            lastH = v.h;
            back.setScale(v.k);
            back.setLocation((v.w - PixelButton.BODY_W * v.k) / 2.0,
                    v.offsetY + (Viewport.UI_H - 100) * v.k);
        }

        MenuBackground.get().draw(g, v);

        if (titleFont == null || fontScale != v.k) {
            fontScale = v.k;
            if (titleFont == null || fontScale != v.k) {
                fontScale = v.k;
                int size = 16 * Math.max(1, (int) Math.floor(3 * v.k + 0.25));
                titleFont = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont((float) size);
            }
        }
        g.setFont(titleFont);
        FontMetrics fm = g.getFontMetrics();
        int tx = ((int) v.w - fm.stringWidth(title)) / 2;
        int ty = (int) Math.round(v.offsetY + 100 * v.k);
        int sh = Math.max(1, (int) Math.round(3 * v.k));
        g.setColor(new Color(20, 8, 4));
        g.drawString(title, tx + sh, ty + sh);
        g.setColor(new Color(255, 214, 90));
        g.drawString(title, tx, ty);

        super.render(g);
    }
}