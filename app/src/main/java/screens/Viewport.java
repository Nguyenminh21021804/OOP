package screens;

import de.gurkenlabs.litiengine.Game;

/** Tính vị trí và tỉ lệ để mọi thứ luôn căn giữa khi cửa sổ đổi kích thước. */
public final class Viewport {
    public static final double BG_W = 256, BG_H = 144;   // ảnh nền gốc
    public static final double UI_W = 1024, UI_H = 576;  // kích thước thiết kế của giao diện

    public final double w, h;          // kích thước cửa sổ hiện tại
    public final double k;             // tỉ lệ giao diện
    public final double offsetY;       // căn giữa giao diện theo chiều dọc
    public final double bgScale;       // tỉ lệ phóng nền
    public final double bgX, bgY;      // góc trên trái của nền

    public Viewport(double w, double h) {
        this.w = Math.max(1, w);
        this.h = Math.max(1, h);
        this.k = Math.min(this.w / UI_W, this.h / UI_H);
        this.offsetY = (this.h - UI_H * k) / 2.0;
        this.bgScale = Math.max(this.w / BG_W, this.h / BG_H);
        this.bgX = (this.w - BG_W * bgScale) / 2.0;
        this.bgY = (this.h - BG_H * bgScale) / 2.0;
    }

    public static Viewport current() {
        return new Viewport(
                Game.window().getResolution().getWidth(),
                Game.window().getResolution().getHeight());
    }
}