package screens;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.graphics.ImageRenderer;
import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.gui.screens.Screen;
import de.gurkenlabs.litiengine.resources.Resources;

/**
 * ============================================================================
 * File: screens/MenuScreen.java
 * Nhiệm vụ: Giao diện màn hình chính hiển thị ảnh nền bg.png, logo.png,
 *           đuốc treo tường (fire2) và các chậu lửa (fire3) tham chiếu theo ảnh mẫu.
 *           Chạy hoạt ảnh lửa ở tốc độ 60 frame / giây (60 FPS).
 * ============================================================================
 */
public class MenuScreen extends Screen {
    public static final String SCREEN_NAME = "MENU";

    // Cấu hình tốc độ hoạt ảnh lửa: 60 frame / 1 giây (60 FPS)
    private static final int FIRE_FPS = 60;

    private BufferedImage bgImage;
    private BufferedImage logoImage;
    private Spritesheet torchSheet;   // fire2: Đuốc treo tường
    private Spritesheet brazierSheet; // fire3: Chậu lửa dưới đất

    // Bộ nhớ đệm (Cache) hình ảnh đã co giãn theo độ phân giải màn hình để triệt tiêu hoàn toàn giật lag
    private BufferedImage cachedBg;
    private BufferedImage cachedLogo;
    private double lastWidth = -1;
    private double lastHeight = -1;

    // --- BỔ SUNG: Khai báo 4 nút bấm Menu ---
    private MenuButton newGameBtn;
    private MenuButton settingsBtn;
    private MenuButton howToPlayBtn;
    private MenuButton creditsBtn;

    public MenuScreen() {
        super(SCREEN_NAME);
    }

    @Override
    protected void initializeComponents() {
        super.initializeComponents();

        // 1. Nạp ảnh nền bg/bg.png
        this.bgImage = Resources.images().get("bg/bg.png");
        if (this.bgImage == null) {
            try {
                this.bgImage = ImageIO.read(getClass().getResourceAsStream("/bg/bg.png"));
            } catch (Exception e) {
                System.err.println("Không thể nạp ảnh nền: " + e.getMessage());
            }
        }

        // 2. Nạp ảnh logo bg/logo.png
        this.logoImage = Resources.images().get("bg/logo.png");
        if (this.logoImage == null) {
            try {
                this.logoImage = ImageIO.read(getClass().getResourceAsStream("/bg/logo.png"));
            } catch (Exception e) {
                System.err.println("Không thể nạp logo: " + e.getMessage());
            }
        }

        // 3. Nạp spritesheet đuốc treo tường fire2 từ utiLITI (game.litidata)
        this.torchSheet = Resources.spritesheets().get("fire2");
        if (this.torchSheet == null) {
            this.torchSheet = Resources.spritesheets().get("Fire2");
        }

        // 4. Nạp spritesheet chậu lửa fire3 từ utiLITI (game.litidata)
        this.brazierSheet = Resources.spritesheets().get("fire5");
        if (this.brazierSheet == null) {
            this.brazierSheet = Resources.spritesheets().get("Fire5");
        }

        // --- BỔ SUNG: Khởi tạo 4 nút bấm và gán sự kiện ---
        this.newGameBtn = new MenuButton(0, 0, 220, 48, "NEW GAME");
        this.settingsBtn = new MenuButton(0, 0, 220, 48, "SETTINGS");
        this.howToPlayBtn = new MenuButton(0, 0, 220, 48, "HOW TO PLAY");
        this.creditsBtn = new MenuButton(0, 0, 220, 48, "CREDITS");

        // Bấm nút NEW GAME để sang màn hình chơi chính
        this.newGameBtn.onClicked(e -> {
            Game.screens().display(MainGameScreen.SCREEN_NAME);
        });

        this.settingsBtn.onClicked(e -> System.out.println("Mở SETTINGS"));
        this.howToPlayBtn.onClicked(e -> System.out.println("Mở HOW TO PLAY"));
        this.creditsBtn.onClicked(e -> System.out.println("Mở CREDITS"));

        // Thêm vào danh sách quản lý component của màn hình
        this.getComponents().add(this.newGameBtn);
        this.getComponents().add(this.settingsBtn);
        this.getComponents().add(this.howToPlayBtn);
        this.getComponents().add(this.creditsBtn);
    }

    /**
     * --- BỔ SUNG: Hàm căn chỉnh vị trí 4 nút bấm nằm ngay dưới khu vực giữa màn hình ---
     */
    
    private void updateButtonLayout(double screenWidth, double screenHeight) {
        if (this.newGameBtn == null) return;

        // Thay vì dùng kích thước cố định, ta dùng tỷ lệ phần trăm theo màn hình
        double btnWidth = screenWidth * 0.26;   
        double btnHeight = screenHeight * 0.09; 
        double gap = screenHeight * 0.025;     

        double startX = (screenWidth - btnWidth) / 2.0;
        double startY = screenHeight * 0.44;    
        MenuButton[] buttons = { this.newGameBtn, this.settingsBtn, this.howToPlayBtn, this.creditsBtn };
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setDimension(btnWidth, btnHeight);
            buttons[i].setLocation(startX, startY + i * (btnHeight + gap));
        }
    }

    /**
     * Hàm tiện ích hỗ trợ vẽ ngọn lửa hoạt ảnh căn chỉnh theo tâm ngang và chân đế/tâm lửa
     * @param g Graphics2D
     * @param sheet Spritesheet hoạt ảnh
     * @param centerX Tọa độ X tâm lửa trên màn hình
     * @param targetY Tọa độ Y mục tiêu (chân đế hoặc tâm lửa)
     * @param scale Tỷ lệ phóng to
     * @param anchorOffsetInSprite Độ lệch Y trong sprite (chân đế = 39, tâm = 23)
     * @param timeOffset Độ trễ thời gian (ms) để lửa không bập bùng đồng loạt
     */
    private void renderFire(Graphics2D g, Spritesheet sheet, double centerX, double targetY, double scale, double anchorOffsetInSprite, long timeOffset) {
        if (sheet == null || sheet.getTotalNumberOfSprites() == 0) return;
        int totalFrames = sheet.getTotalNumberOfSprites();

        // Tính toán frame với tốc độ 60 frame / giây: (thời gian_ms * 60 / 1000)
        long currentTicks = (Game.time().now() + timeOffset) * FIRE_FPS / 1000;
        int frame = (int) (currentTicks % totalFrames);
        BufferedImage img = sheet.getSprite(frame);
        if (img == null) return;

        double width = sheet.getSpriteWidth() * scale;
        double x = centerX - (width / 2.0);
        double y = targetY - (anchorOffsetInSprite * scale);

        ImageRenderer.renderScaled(g, img, x, y, scale, scale);
    }

    @Override
    public void render(Graphics2D g) {
        double screenWidth = Game.window().getResolution().getWidth();
        double screenHeight = Game.window().getResolution().getHeight();
        double centerX = screenWidth / 2.0;

        // Cập nhật bộ nhớ đệm (Cache) cho ảnh nền và logo khi kích thước màn hình thay đổi
        // Tránh việc Graphics2D phải co giãn ảnh gốc siêu to (2752x1536) mỗi khung hình gây tụt FPS
        if (screenWidth != this.lastWidth || screenHeight != this.lastHeight) {
            this.lastWidth = screenWidth;
            this.lastHeight = screenHeight;

            if (this.bgImage != null) {
                this.cachedBg = new BufferedImage((int) screenWidth, (int) screenHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D gBg = this.cachedBg.createGraphics();
                gBg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gBg.drawImage(this.bgImage, 0, 0, (int) screenWidth, (int) screenHeight, null);
                gBg.dispose();
            }

            if (this.logoImage != null) {
                double logoWidth = 1000; // Giữ nguyên kích thước gốc của bạn
                double logoHeight = logoWidth * ((double) this.logoImage.getHeight() / (double) this.logoImage.getWidth());
                this.cachedLogo = new BufferedImage((int) logoWidth, (int) logoHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gLogo = this.cachedLogo.createGraphics();
                gLogo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gLogo.drawImage(this.logoImage, 0, 0, (int) logoWidth, (int) logoHeight, null);
                gLogo.dispose();
            }

            // --- BỔ SUNG: Cập nhật lại tọa độ nút khi resize màn hình ---
            updateButtonLayout(screenWidth, screenHeight);
        }

        // 1. Vẽ ảnh nền bg.png lấp đầy toàn màn hình (Dùng cache đã scale sẵn siêu nhanh)
        if (this.cachedBg != null) {
            ImageRenderer.render(g, this.cachedBg, 0, 0);
        } else if (this.bgImage != null) {
            double scaleX = screenWidth / (double) this.bgImage.getWidth();
            double scaleY = screenHeight / (double) this.bgImage.getHeight();
            ImageRenderer.renderScaled(g, this.bgImage, 0, 0, scaleX, scaleY);
        } else {
            g.setColor(new Color(20, 16, 15));
            g.fillRect(0, 0, (int) screenWidth, (int) screenHeight);
        }

        // 2. Lớp phủ tối nhẹ để làm nền tối lại và nổi bật ánh lửa
        g.setColor(new Color(0, 0, 0, 40));
        g.fillRect(0, 0, (int) screenWidth, (int) screenHeight);

        // Tỷ lệ co giãn theo độ phân giải chuẩn tham chiếu (1024 x 576)
        double refScale = screenWidth / 1024.0;

        // 3.1. Chậu lửa vừa bên trái (cạnh cột nghiêng)
        renderFire(g, this.brazierSheet, screenWidth * 0.3320, screenHeight * 0.63, refScale * 0.25, 39.0, 120);

        // 3.2. Chậu lửa vừa bên phải (cạnh cột gãy)
        renderFire(g, this.brazierSheet, screenWidth * 0.6797, screenHeight * 0.64, refScale * 0.25, 39.0, 240);
        
        // 3.3. Chậu lửa lớn tiền cảnh bên trái (vẽ sau để đè lên tiền cảnh)
        renderFire(g, this.brazierSheet, screenWidth * 0.2754, screenHeight * 0.75, refScale * 0.5, 39.0, 60);

        // 5. Vẽ Logo Game (logo.png) căn giữa ở vòm trên của hầm ngục
        if (this.cachedLogo != null) {
            double logoX = centerX - (this.cachedLogo.getWidth() / 2.0);
            double logoY = screenHeight * 0.02;
            ImageRenderer.render(g, this.cachedLogo, logoX, logoY);
        } else if (this.logoImage != null) {
            double logoWidth = 1000;
            double logoHeight = logoWidth * ((double) this.logoImage.getHeight() / (double) this.logoImage.getWidth());
            double logoX = centerX - (logoWidth / 2.0);
            double logoY = screenHeight * 0.02;
            double scaleX = logoWidth / (double) this.logoImage.getWidth();
            double scaleY = logoHeight / (double) this.logoImage.getHeight();
            ImageRenderer.renderScaled(g, this.logoImage, logoX, logoY, scaleX, scaleY);
        }

        // --- BỔ SUNG: Gọi lệnh vẽ các nút bấm đè lên trên cùng ---
        super.render(g);
    }
}