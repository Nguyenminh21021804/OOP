package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;

/**
 * Màn hình xem từng map: hiện tên map ở góc trên trái, nút "NEXT MAP >" ở góc trên phải.
 * Bấm nút hoặc phím mũi tên phải để chuyển sang map tiếp theo.
 */
public class MapViewerScreen extends GameScreen {

    public static final String NAME = "MAP_VIEWER";

    // Tên map KHÔNG kèm đuôi .tmx. Thêm các map khác của nhóm vào đây.
    private static final String[] MAPS = {
            // ----- Stage 1 -----
            "Map_01",
            "Map_02_vuc_tham",
            "Map_03_trap",
            "Map_04_parkour",
            "Map_05_boss",
            "Map_01_challenge_room",
            "Map_04_challenge_room",
            // ----- Stage 2 -----
            "Stage2_Map_01",
            "Stage2_Map_02",
            "Stage2_Map_03",
            "Stage2_Map_04",
            "Stage2_Map_05_Boss",
            "Stage2_Secret_A",
            "Stage2_Secret_B"
    };

    private int index = 0;
    private double zoomAdjust = 0.41;
    private boolean loaded = false;
    private Rectangle2D nextButton = new Rectangle2D.Double();

    public MapViewerScreen() {
        super(NAME);

        // Bấm chuột vào nút NEXT MAP
        Input.mouse().onClicked(e -> {
            if (Game.screens().current() == this && nextButton.contains(Input.mouse().getLocation())) {
                next();
            }
        });
        // Hoặc dùng phím mũi tên phải / trái
        Input.keyboard().onKeyTyped(KeyEvent.VK_RIGHT, e -> {
            if (Game.screens().current() == this) next();
        });
        Input.keyboard().onKeyTyped(KeyEvent.VK_LEFT, e -> {
            if (Game.screens().current() == this) previous();
        });
        // ESC: quay về menu chính
        Input.keyboard().onKeyTyped(KeyEvent.VK_ESCAPE, e -> {
            if (Game.screens().current() == this) Game.screens().display(MainMenuScreen.NAME);
        });
        Input.keyboard().onKeyTyped(KeyEvent.VK_UP, e -> {
            if (Game.screens().current() == this) zoomAdjust *= 1.1;
        });
        Input.keyboard().onKeyTyped(KeyEvent.VK_DOWN, e -> {
            if (Game.screens().current() == this) zoomAdjust /= 1.1;
        });
    }

    @Override
    public void prepare() {
        super.prepare();
        if (!loaded) {
            loadMap(index);
            loaded = true;
        }
    }

    private void next() {
        index = (index + 1) % MAPS.length;
        loadMap(index);
    }

    private void previous() {
        index = (index - 1 + MAPS.length) % MAPS.length;
        loadMap(index);
    }

    private void loadMap(int i) {
        Game.world().loadEnvironment("maps/" + MAPS[i] + ".tmx");
        fitCameraToMap();
    }

    /** Đặt camera vào giữa map và phóng to/thu nhỏ cho vừa màn hình. */
    private void fitCameraToMap() {
        if (Game.world().environment() == null) {
            return;
        }
        Dimension size = Game.world().environment().getMap().getSizeInPixels();
        double sw = Game.window().getResolution().getWidth();
        double sh = Game.window().getResolution().getHeight();
        double zoom = Math.min(sw / size.width, sh / size.height) * 0.95 * zoomAdjust;
        Game.world().camera().setZoom((float) zoom, 0);
        Game.world().camera().setFocus(size.width / 2.0, size.height / 2.0);
    }

    @Override
    public void render(Graphics2D g) {
        fitCameraToMap();
        super.render(g); // vẽ map

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();

        // Ô tên map (trên trái)
        String title = "[" + (index + 1) + "/" + MAPS.length + "] " + MAPS[index] + ".tmx";
        drawPanel(g, 16, 16, fm.stringWidth(title) + 28, 34, title);

        // Nút NEXT MAP (trên phải)
        String label = "NEXT MAP >";
        int w = fm.stringWidth(label) + 28;
        int x = (int) (getWidth() - w - 16);
        nextButton = new Rectangle2D.Double(x, 16, w, 34);
        drawPanel(g, x, 16, w, 34, label);

        // Gợi ý phím ở góc dưới
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.setColor(new Color(255, 255, 255, 190));
        g.drawString("ESC: menu    <- / ->: doi map", 16, (int) (getHeight() - 14));
    }

    private void drawPanel(Graphics2D g, int x, int y, int w, int h, String text) {
        g.setColor(new Color(26, 22, 48, 235));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(150, 140, 160));
        g.setStroke(new BasicStroke(2));
        g.drawRect(x, y, w, h);
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + 14, y + (h + fm.getAscent() - fm.getDescent()) / 2);
    }
}
