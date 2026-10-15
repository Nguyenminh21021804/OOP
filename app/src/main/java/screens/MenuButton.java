package screens;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import de.gurkenlabs.litiengine.gui.GuiComponent;

public class MenuButton extends GuiComponent {
    private static BufferedImage normalTrimmed;
    private static BufferedImage hoveredTrimmed;

    public MenuButton(double x, double y, double width, double height, String text) {
        super(x, y, width, height);
        this.setText(text);

        // 1. Nạp font Peaberry-Base.otf
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/font/Peaberry-Base.otf");
            if (is != null) {
                Font customFont = Font.createFont(Font.TRUETYPE_FONT, is);
                this.setFont(customFont.deriveFont(Font.PLAIN, 22f));
            } else {
                this.setFont(new Font("Arial", Font.BOLD, 22));
            }
        } catch (Exception e) {
            this.setFont(new Font("Arial", Font.BOLD, 22));
        }

        loadButtonSprites();
    }

    /**
     * Nạp ảnh button.png và tự động cắt sạch phần viền trong suốt thừa (Auto-Trim)
     * để 2 trạng thái có mốc tọa độ chuẩn xác tuyệt đối.
     */
    private void loadButtonSprites() {
        if (normalTrimmed != null && hoveredTrimmed != null) {
            return;
        }
        try {
            BufferedImage sheet = ImageIO.read(getClass().getResourceAsStream("/bg/button.png"));
            if (sheet != null) {
                int w = sheet.getWidth();
                int h = sheet.getHeight();
                int halfH = h / 2;

                BufferedImage rawNormal = sheet.getSubimage(0, 0, w, halfH);
                BufferedImage rawHovered = sheet.getSubimage(0, halfH, w, h - halfH);

                normalTrimmed = trimTransparent(rawNormal);
                hoveredTrimmed = trimTransparent(rawHovered);
            }
        } catch (Exception e) {
            System.err.println("Không thể nạp file ảnh button.png: " + e.getMessage());
        }
    }

    /**
     * Hàm quét pixel để cắt bỏ hoàn toàn khoảng trống trong suốt thừa xung quanh nút
     */
    private static BufferedImage trimTransparent(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int top = height, left = width, right = 0, bottom = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (img.getRGB(x, y) >> 24) & 0xff;
                if (alpha > 20) { // Nếu pixel không trong suốt
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left || bottom < top) {
            return img;
        }
        return img.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    @Override
    public void render(Graphics2D g) {
        int x = (int) getX();
        int y = (int) getY();
        int w = (int) getWidth();
        int h = (int) getHeight();
        boolean hovered = isHovered();

        // 1. VẼ KHUNG NÚT BẤM
        if (hovered && hoveredTrimmed != null && normalTrimmed != null) {
            double ratioW = (double) hoveredTrimmed.getWidth() / (double) normalTrimmed.getWidth();
            double ratioH = (double) hoveredTrimmed.getHeight() / (double) normalTrimmed.getHeight();

            int drawW = (int) Math.round(w * ratioW);
            int drawH = (int) Math.round(h * ratioH);
            int drawX = x - (drawW - w) / 2;
            int drawY = y - (drawH - h) / 2;

            g.drawImage(hoveredTrimmed, drawX, drawY, drawW, drawH, null);
        } else if (normalTrimmed != null) {
            g.drawImage(normalTrimmed, x, y, w, h, null);
        }

        if (getText() != null && !getText().isEmpty()) {
            Font dynamicFont = getFont().deriveFont(Font.PLAIN, (float) (h * 0.40));
            g.setFont(dynamicFont);
            java.awt.FontMetrics fm = g.getFontMetrics();

            int textX = x + (w - fm.stringWidth(getText())) / 2;

            FontRenderContext frc = g.getFontRenderContext();
            GlyphVector gv = dynamicFont.createGlyphVector(frc, getText());
            java.awt.geom.Rectangle2D visualBounds = gv.getVisualBounds();
            int textY = (int) Math.round(y + (h - visualBounds.getHeight()) / 2.0 - visualBounds.getY());

            g.setColor(new Color(25, 15, 8));
            g.drawString(getText(), textX + 2, textY + 2);

            g.setColor(new Color(245, 235, 210));
            g.drawString(getText(), textX, textY);
        }
    }
}