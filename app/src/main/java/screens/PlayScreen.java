package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import player.PlayerController;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

public class PlayScreen extends GameScreen {
    public static final String NAME = "PLAY";

    private PlayerController player;
    private long lastUpdateNano = System.nanoTime();

    public PlayScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        // Đặt nhân vật ở chính giữa màn hình (dựa trên kích thước thiết kế 1024x576 của Viewport)
        player = new PlayerController(Viewport.UI_W / 2.0, Viewport.UI_H / 2.0);

        // Ấn ESC để thoát về Main Menu
        Input.keyboard().onKeyPressed(KeyEvent.VK_ESCAPE, e -> {
            if (Game.screens().current() == this) {
                Game.screens().display(MainMenuScreen.NAME);
            }
        });
    }

    @Override
    public void render(Graphics2D g) {
        // Tính toán thời gian trôi qua giữa 2 khung hình (delta time - giây)
        long nowNano = System.nanoTime();
        double dt = Math.min(0.1, (nowNano - lastUpdateNano) / 1_000_000_000.0);
        lastUpdateNano = nowNano;

        // Cập nhật logic điều khiển nhân vật
        if (player != null) {
            player.update(dt);
        }

        Viewport v = Viewport.current();

        // 1. Tô nền đen
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, (int) Math.ceil(v.w), (int) Math.ceil(v.h));

        // 2. Vẽ nhân vật tạm thời (khối hộp) và thông số để kiểm tra nút bấm
        // 2. Vẽ nhân vật, hoạt ảnh và bảng HUD
        if (player != null) {
            player.render(g, v.k, v.offsetY);
        }

        super.render(g);
    }
}