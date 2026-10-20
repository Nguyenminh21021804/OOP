package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class MainMenuScreen extends GameScreen {
    public static final String NAME = "MAINMENU";

    private Font titleFont;
    private Font buttonFont;
    private TitleArt titleArt;

    public MainMenuScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        titleFont  = Resources.fonts().get("font/DungeonFont.ttf").deriveFont(96f);
        buttonFont = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont(32f);
        titleArt   = new TitleArt(titleFont, buttonFont);

        double w = Game.window().getResolution().getWidth();
        double x = (w - PixelButton.BODY_W) / 2.0;
        double startY = 232, pitch = 64;

        String[] labels = {"New Game", "Settings", "How to Play", "Credits", "Quit"};
        Runnable[] actions = {
                this::onNewGame, this::onSettings, this::onHowToPlay, this::onCredits, this::onQuit
        };

        for (int i = 0; i < labels.length; i++) {
            PixelButton btn = new PixelButton(x, startY + i * pitch, labels[i]);
            final Runnable action = actions[i];
            btn.onClicked(e -> action.run());
            this.getComponents().add(btn);
        }
    }

    @Override
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        int w = Game.window().getWidth();
        int h = Game.window().getHeight();

        MenuBackground.get().draw(g, w, h); // nền + lửa
        titleArt.draw(g, w, h);             // tên game
        super.render(g);                    // các nút
    }

    private void onNewGame()   { System.out.println("New Game clicked"); }
    private void onSettings()  { Game.screens().display("SETTINGS"); }
    private void onHowToPlay() { Game.screens().display("HOWTOPLAY"); }
    private void onCredits()   { Game.screens().display("CREDITS"); }
    private void onQuit()      { Game.exit(); }
}