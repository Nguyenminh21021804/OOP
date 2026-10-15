package screens;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.graphics.TextRenderer;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;

/*
 * ============================================================================
 * File: screens/MainGameScreen.java
 * Nhiệm vụ: Màn hình Gameplay (chuyển tới khi bấm START GAME).
 * ============================================================================
 */
public class MainGameScreen extends GameScreen {
    public static final String SCREEN_NAME = "INGAME";

    public MainGameScreen() {
        super(SCREEN_NAME);
    }

    @Override
    protected void initializeComponents() {
        super.initializeComponents();

        // Bắt phím ESC để quay trở lại MenuScreen
        Input.keyboard().onKeyReleased(java.awt.event.KeyEvent.VK_ESCAPE, e -> {
            Game.screens().display("MENU");
        });
    }

    @Override
    public void render(Graphics2D g) {
        super.render(g);

        // Hiển thị tạm chỉ dẫn
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        TextRenderer.render(g, "[MÀN CHƠI CHÍNH] - Nhấn phím [ESC] để quay lại Menu", 30, 40);
    }
}
