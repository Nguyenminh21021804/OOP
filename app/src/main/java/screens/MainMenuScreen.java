package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

public class MainMenuScreen extends GameScreen {
    public static final String NAME = "MAINMENU";

    private static final String[] LABELS = {"New Game", "Settings", "How to Play", "Credits", "Quit"};
    private static final double START_Y = 232;
    private static final double PITCH = 64;

    private PixelButton[] buttons;
    private TitleArt titleArt;
    private double lastW = -1, lastH = -1;

    public MainMenuScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        Font titleFont  = Resources.fonts().get("font/DungeonFont.ttf").deriveFont(96f);
        Font buttonFont = Resources.fonts().get("font/PeaberryBase.ttf").deriveFont(32f);
        titleArt = new TitleArt(titleFont, buttonFont);

        Runnable[] actions = {
                this::onNewGame, this::onSettings, this::onHowToPlay, this::onCredits, this::onQuit
        };

        buttons = new PixelButton[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) {
            buttons[i] = new PixelButton(0, 0, LABELS[i]);
            final Runnable action = actions[i];
            buttons[i].onClicked(e -> action.run());
            this.getComponents().add(buttons[i]);
        }
    }

    private void layoutButtons(Viewport v) {
        double x = (v.w - PixelButton.BODY_W * v.k) / 2.0;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setScale(v.k);
            buttons[i].setLocation(x, v.offsetY + (START_Y + i * PITCH) * v.k);
        }
    }

    @Override
    public void render(Graphics2D g) {
        Viewport v = Viewport.current();
        if (v.w != lastW || v.h != lastH) {
            lastW = v.w;
            lastH = v.h;
            layoutButtons(v);
            System.out.println("[Layout] resolution=" + (int) v.w + "x" + (int) v.h
                    + " window=" + Game.window().getWidth() + "x" + Game.window().getHeight());
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        MenuBackground.get().draw(g, v); // nền + lửa

        AffineTransform old = g.getTransform();
        g.translate(0, v.offsetY);
        g.scale(v.k, v.k);
        titleArt.draw(g, (int) Math.round(v.w / v.k), (int) Math.round(v.h / v.k));
        g.setTransform(old);

        super.render(g); // các nút
    }

    private void onNewGame()   { System.out.println("New Game clicked"); }
    private void onSettings()  { Game.screens().display("SETTINGS"); }
    private void onHowToPlay() { Game.screens().display("HOWTOPLAY"); }
    private void onCredits()   { Game.screens().display("CREDITS"); }
    private void onQuit()      { Game.exit(); }
}