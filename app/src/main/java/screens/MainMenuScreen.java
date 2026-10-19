package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class MainMenuScreen extends GameScreen {
    public static final String NAME = "MAINMENU";

    private static final double[][] FIRES = {
            {2,  66.8,  67.4,   0},  // đuốc tường trái
            {2, 194.6,  67.0, 180},  // đuốc tường phải
            {1, 109.7,  91.1, 300},  // lửa nhỏ trên bệ đá giữa
            {4,  84.5,  98.2, 120},  // chậu lửa vừa bên trái
            {4, 172.9,  98.9, 240},  // chậu lửa vừa bên phải
            {3,  70.0, 122.2,  60},  // chậu lửa lớn tiền cảnh
    };

    private BufferedImage background;
    private final FireAnimation[] fires = new FireAnimation[4];
    private Font titleFont;
    private Font buttonFont;
    private TitleArt titleArt;

    public MainMenuScreen() {
        super(NAME);
        loadFireAnimation();
    }

    private void loadFireAnimation() {
        for (int i = 0; i < 4; i++) {
            fires[i] = FireAnimation.load(i + 1);
        }
    }

    @Override
    protected void initializeComponents() {
        background = Resources.images().get("sprites/menubackground.png");


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
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        int w = Game.window().getWidth();
        int h = Game.window().getHeight();

        // 1. Nền
        if (background != null) {
            g.drawImage(background, 0, 0, w, h, null);
        }

        // 2. Lửa hoạt ảnh
        double scale = w / 256.0;
        long now = Game.time().now();
        for (double[] f : FIRES) {
            FireAnimation anim = fires[(int) f[0] - 1];
            if (anim != null) {
                anim.draw(g, f[1] * scale, f[2] * scale, scale, now, (long) f[3]);
            }
        }

        // 3. Tên game
        titleArt.draw(g, w, h);

        // 4. Các nút
        super.render(g);
    }

    private void onNewGame()   { System.out.println("New Game clicked"); }
    private void onSettings()  { Game.screens().display("SETTINGS"); }
    private void onHowToPlay() { Game.screens().display("HOWTOPLAY"); }
    private void onCredits()   { Game.screens().display("CREDITS"); }
    private void onQuit()      { Game.exit(); }
}