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

    public InfoScreen(String name, String title) {
        super(name);
        this.title = title;
    }

    @Override
    protected void initializeComponents() {
        double w = Game.window().getResolution().getWidth();
        double h = Game.window().getResolution().getHeight();

        PixelButton back = new PixelButton((w - PixelButton.BODY_W) / 2.0, h - 100, "Back");
        back.onClicked(e -> Game.screens().display(MainMenuScreen.NAME));
        this.getComponents().add(back);
    }

    @Override
    public void render(Graphics2D g) {
        int w = Game.window().getWidth();
        int h = Game.window().getHeight();

        MenuBackground.get().draw(g, w, h); // cùng nền và lửa với màn hình chính

        // Tiêu đề màn hình
        g.setFont(Resources.fonts().get("font/PeaberryBase.ttf").deriveFont(32f));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2;
        g.setColor(new Color(20, 8, 4));
        g.drawString(title, tx + 3, 103);  // bóng đổ để dễ đọc trên nền
        g.setColor(new Color(255, 214, 90));
        g.drawString(title, tx, 100);

        super.render(g); // nút Back
    }
}