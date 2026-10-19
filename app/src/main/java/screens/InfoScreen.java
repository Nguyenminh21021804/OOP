package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.ImageComponent;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;


public class InfoScreen extends GameScreen {
    private final String title;
    private BufferedImage background;

    private Font buttonFont;

    public InfoScreen(String name, String title) {
        super(name);
        this.title = title;
    }

    @Override
    protected void initializeComponents() {
        background = Resources.images().get("sprites/menubackground.png");
        buttonFont = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont(32f);

        double h = Game.window().getResolution().getHeight();
        PixelButton back = new PixelButton(40, h - 100, "Back");
        back.onClicked(e -> Game.screens().display(MainMenuScreen.NAME));
        this.getComponents().add(back);
    }

    @Override
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        buttonFont = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont(32f);

        int width = Game.window().getWidth();
        int height = Game.window().getHeight();

        if (background != null) {
            g.drawImage(background, 0, 0, width, height, null);
        }

        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, width, height);

        g.setFont(buttonFont);
        g.setColor(new Color(255, 214, 90));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (width - fm.stringWidth(title)) / 2, 120);

        super.render(g);
    }
}
