import java.awt.Dimension;

import de.gurkenlabs.litiengine.Game;
import screens.MainGameScreen;
import screens.MenuScreen;

/**
 * ============================================================================
 * File: App.java
 * Nhiệm vụ: Điểm khởi chạy (Entry Point) của Game.
 * ============================================================================
 */
public class App {
    public static void main(String[] args) {
        // 1. Cấu hình thông tin game
        Game.info().setName("Dungeon Adventure");
        Game.info().setVersion("1.0.0");
        Game.info().setDescription("An OOP Game built with LITIengine");

        // 2. Khởi tạo engine
        Game.init(args);

        // Nạp tài nguyên dự án utiLITI (game.litidata)
        java.net.URL litidataUrl = App.class.getResource("/game.litidata");
        if (litidataUrl != null) {
            de.gurkenlabs.litiengine.resources.Resources.load(litidataUrl);
        } else if (new java.io.File("app/src/main/resources/game.litidata").exists()) {
            de.gurkenlabs.litiengine.resources.Resources.load("app/src/main/resources/game.litidata");
        } else {
            de.gurkenlabs.litiengine.resources.Resources.load("game.litidata");
        }

        // 3. Đặt kích thước cửa sổ tỷ lệ 16:9 (1280x720) khớp với ảnh nền bg.png
        Game.window().getRenderComponent().setPreferredSize(new Dimension(1280, 720));

        // 4. Đăng ký các màn hình
        Game.screens().add(new MenuScreen());
        Game.screens().add(new MainGameScreen());

        // 5. Bắt đầu game và hiển thị màn hình chính (MENU)
        Game.start();
        Game.screens().display("MENU");
    }
}