package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.environment.tilemap.IMap;
import de.gurkenlabs.litiengine.environment.tilemap.IMapObject;
import de.gurkenlabs.litiengine.environment.tilemap.IMapObjectLayer;
import de.gurkenlabs.litiengine.environment.tilemap.MapRenderer;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import de.gurkenlabs.litiengine.resources.Resources;
import player.PlayerController;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PlayScreen extends GameScreen {
    public static final String NAME = "PLAY";

    private PlayerController player;
    private BufferedImage baseMapImage;
    private BufferedImage workingMapImage;
    private BufferedImage renderedMapImage;

    private final List<Rectangle2D.Double> mapColliders = new ArrayList<>();
    private final List<AnimatedMapItem> animatedObjects = new ArrayList<>();
    private boolean showDebugHitbox = false;
    private long lastUpdateNano = System.nanoTime();
    private int lastAnimTick = -1;

    // Các bộ frame hoạt ảnh đọc từ item.tsx và atlas_walls_high-16x32.png
    private BufferedImage floorAtlas;
    private BufferedImage wallAtlas;
    private BufferedImage[] candle1Frames;
    private BufferedImage[] candle2Frames;
    private BufferedImage[] torchFrames;
    private BufferedImage[] flagFrames;
    private BufferedImage[] chestBoxFrames;
    private BufferedImage[] potionHpFrames;
    private BufferedImage[] potionMpFrames;

    // Vùng bản đồ thực tế trong Map_01.tmx (bỏ cột 0 trống bên trái và mép trống phía trên)
    private static final double CROP_X = 16.0;
    private static final double CROP_Y = 8.0;
    private static final double CROP_W = 336.0; // 21 cột * 16px (từ x=16 đến x=352)
    private static final double CROP_H = 200.0; // từ y=8 đến y=208

    // Tọa độ phần thân trên của 4 cụm cột đá (trên bản đồ gốc 352x208) để vẽ đè khi nhân vật đi ra sau cột
    private static final int[][] PILLAR_TOPS = {
        {112, 48, 32, 36, 88},   // Cột đá trên - trái (x, y, w, h, baseY)
        {272, 64, 32, 36, 104},  // Cột đá giữa - phải
        {80, 128, 32, 26, 154},  // Cột đá dưới - trái
        {224, 144, 32, 26, 170}  // Cột đá dưới - phải
    };

    private static class AnimatedMapItem {
        final int x, y, w, h;
        final BufferedImage[] frames;

        AnimatedMapItem(int x, int y, int w, int h, BufferedImage[] frames) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.frames = frames;
        }
    }

    public PlayScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        player = new PlayerController(Viewport.UI_W / 2.0, Viewport.UI_H / 2.0);

        Input.keyboard().onKeyPressed(KeyEvent.VK_ESCAPE, e -> {
            if (Game.screens().current() == this) {
                Game.screens().display(MainMenuScreen.NAME);
            }
        });

        // Bấm F3 để bật/tắt hiển thị Hitbox kiểm tra va chạm
        Input.keyboard().onKeyPressed(KeyEvent.VK_F3, e -> {
            if (Game.screens().current() == this) {
                showDebugHitbox = !showDebugHitbox;
            }
        });
    }

    private void loadAnimationFrames() {
        floorAtlas = Resources.images().get("sprites/0x72_DungeonTilesetII_v1.7/atlas_floor-16x16.png");
        wallAtlas = Resources.images().get("sprites/0x72_DungeonTilesetII_v1.7/atlas_walls_high-16x32.png");

        candle1Frames = loadFrames("2D Pixel Dungeon Asset Pack/items and trap_animation/torch/candlestick_1_", 4);
        candle2Frames = loadFrames("2D Pixel Dungeon Asset Pack/items and trap_animation/torch/candlestick_2_", 4);
        torchFrames = loadFrames("2D Pixel Dungeon Asset Pack/items and trap_animation/torch/torch_", 4);
        flagFrames = loadFrames("2D Pixel Dungeon Asset Pack/items and trap_animation/flag/flag_", 4);
        chestBoxFrames = loadFrames("2D Pixel Dungeon Asset Pack/items and trap_animation/box_1/box_1_", 4);
        potionHpFrames = loadFrames("2D Pixel Dungeon Asset Pack/items and trap_animation/flasks/flasks_1_", 4);
        potionMpFrames = loadFrames("2D Pixel Dungeon Asset Pack/items and trap_animation/flasks/flasks_2_", 4);
    }

    private BufferedImage[] loadFrames(String prefix, int count) {
        BufferedImage[] arr = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            try {
                arr[i] = Resources.images().get(prefix + (i + 1) + ".png");
            } catch (Exception ignored) {}
        }
        return arr;
    }

    private BufferedImage getFloorTile(int tileId) {
        if (floorAtlas == null) return null;
        int col = tileId % 7;
        int row = tileId / 7;
        return floorAtlas.getSubimage(col * 16, row * 16, 16, 16);
    }

    private BufferedImage getWallTile(int tileId) {
        if (wallAtlas == null) return null;
        int col = tileId % 24;
        int row = tileId / 24;
        return wallAtlas.getSubimage(col * 16, row * 16, 16, 16);
    }

    @Override
    public void prepare() {
        super.prepare();
        Game.world().unloadEnvironment();

        try {
            loadAnimationFrames();

            IMap map = Resources.maps().get("maps/Map_01.tmx");
            if (map != null) {
                Environment env = new Environment(map);
                env.init();

                int fullW = map.getSizeInPixels().width;   // 352
                int fullH = map.getSizeInPixels().height;  // 208

                // 1. Vẽ bản đồ gốc vào baseMapImage (352x208)
                baseMapImage = new BufferedImage(fullW, fullH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D mg = baseMapImage.createGraphics();
                MapRenderer.render(mg, map, new Rectangle2D.Double(0, 0, fullW, fullH));
                mg.dispose();

                workingMapImage = new BufferedImage(fullW, fullH, BufferedImage.TYPE_INT_ARGB);

                // 2. Tính tỷ lệ phóng tràn toàn màn hình (1024x576)
                double scaleX = Viewport.UI_W / CROP_W;
                double scaleY = Viewport.UI_H / CROP_H;

                mapColliders.clear();
                animatedObjects.clear();
                double spawnX = Viewport.UI_W / 2.0;
                double spawnY = Viewport.UI_H / 2.0;

                // 3. Quét toàn bộ Object từ Map_01.tmx (Hitbox + Rương + Bình máu/mana)
                for (IMapObjectLayer layer : map.getMapObjectLayers()) {
                    for (IMapObject obj : layer.getMapObjects()) {
                        String objName = obj.getName() != null ? obj.getName() : "";
                        String objType = obj.getType() != null ? obj.getType() : "";

                        // Tạo Hitbox cho tường, cửa và rương
                        if ("collision".equalsIgnoreCase(objName)
                                || objName.startsWith("door_")
                                || "chest".equalsIgnoreCase(objType)) {
                            double rx = (obj.getX() - CROP_X) * scaleX;
                            double ry = (obj.getY() - CROP_Y) * scaleY;
                            double rw = obj.getWidth() * scaleX;
                            double rh = obj.getHeight() * scaleY;

                            if ("chest".equalsIgnoreCase(objType)) {
                                ry -= rh;
                            }

                            if (rw > 0 && rh > 0) {
                                mapColliders.add(new Rectangle2D.Double(rx, ry, rw, rh));
                            }
                        } else if ("spawn_player".equalsIgnoreCase(objName)) {
                            spawnX = (obj.getX() + 14.0 - CROP_X) * scaleX;
                            spawnY = (obj.getY() - CROP_Y) * scaleY - 24.0;
                        }

                        // Nạp các Tile Object có hoạt ảnh trong layer objects (gid 306, 338, 342)
                        int ox = (int) Math.round(obj.getX());
                        int oy = (int) Math.round(obj.getY() - obj.getHeight());
                        int ow = Math.max(16, (int) Math.round(obj.getWidth()));
                        int oh = Math.max(16, (int) Math.round(obj.getHeight()));

                        if ("chest".equalsIgnoreCase(objType) || objName.startsWith("chest")) {
                            animatedObjects.add(new AnimatedMapItem(ox, oy, ow, oh, chestBoxFrames));
                        } else if (objName.startsWith("potion_hp")) {
                            animatedObjects.add(new AnimatedMapItem(ox, oy, ow, oh, potionHpFrames));
                        } else if (objName.startsWith("potion_mp")) {
                            animatedObjects.add(new AnimatedMapItem(ox, oy, ow, oh, potionMpFrames));
                        }
                    }
                }

                lastAnimTick = -1;
                updateAnimatedMapFrame(0);

                if (player != null) {
                    player.setMapColliders(mapColliders);
                    player.getCharacter().setSkin(0);
                    player.getCharacter().revive();
                    player.getCharacter().setPosition(spawnX, spawnY);
                }

                System.out.println("[PlayScreen] Nạp bản đồ & Object THÀNH CÔNG! Số Object động: " + animatedObjects.size());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        lastUpdateNano = System.nanoTime();
    }

    /** Cập nhật toàn bộ các frame chuyển động trên bản đồ mỗi 100ms */
    private void updateAnimatedMapFrame(int tick) {
        if (baseMapImage == null || workingMapImage == null) return;

        Graphics2D g = workingMapImage.createGraphics();
        g.drawImage(baseMapImage, 0, 0, null);

        int f4 = tick % 4;
        int f3 = tick % 3;
        int f2 = tick % 2;

        // 1. Cập nhật các tile động trên layer decor (chân nến, đuốc, cờ)
        drawAnimatedTileOnMap(g, 2, 5, getFloorTile(1), candle1Frames, f4);
        drawAnimatedTileOnMap(g, 2, 8, getFloorTile(1), candle1Frames, f4);
        drawAnimatedTileOnMap(g, 20, 5, getFloorTile(0), candle2Frames, f4);
        drawAnimatedTileOnMap(g, 20, 8, getFloorTile(0), candle2Frames, f4);
        drawAnimatedTileOnMap(g, 6, 1, getWallTile(173), torchFrames, f4);
        drawAnimatedTileOnMap(g, 15, 1, getWallTile(122), flagFrames, f4);

        // 2. Cập nhật 3 đài phun nước động (Đỏ, Xanh lá, Xanh dương) trên tường
        drawFountainTile(g, 4, 2, getFloorTile(9), 36 + f3);     // Đài phun dung nham đỏ
        drawFountainTile(g, 8, 2, getFloorTile(0), 39 + f2);     // Đài phun axit xanh lá
        drawFountainTile(g, 19, 2, getFloorTile(1), 132 + f3);   // Đài phun nước xanh dương

        // 3. Vẽ các Tile Object trong layer objects (rương chest_01 và 4 bình máu/mana)
        for (AnimatedMapItem item : animatedObjects) {
            if (item.frames != null && item.frames.length > 0) {
                BufferedImage frame = item.frames[tick % item.frames.length];
                if (frame != null) {
                    g.drawImage(frame, item.x, item.y, item.w, item.h, null);
                }
            }
        }

        g.dispose();

        int cx = (int) CROP_X;
        int cy = (int) CROP_Y;
        int cw = Math.min((int) CROP_W, workingMapImage.getWidth() - cx);
        int ch = Math.min((int) CROP_H, workingMapImage.getHeight() - cy);
        renderedMapImage = workingMapImage.getSubimage(cx, cy, cw, ch);
    }

    private void drawAnimatedTileOnMap(Graphics2D g, int col, int row, BufferedImage bgTile, BufferedImage[] frames, int frameIdx) {
        int px = col * 16;
        int py = row * 16;
        if (bgTile != null) {
            g.drawImage(bgTile, px, py, 16, 16, null);
        }
        if (frames != null && frames[frameIdx] != null) {
            g.drawImage(frames[frameIdx], px, py, 16, 16, null);
        }
    }

    private void drawFountainTile(Graphics2D g, int col, int row, BufferedImage bgTile, int wallTileId) {
        int px = col * 16;
        int py = row * 16;
        if (bgTile != null) {
            g.drawImage(bgTile, px, py, 16, 16, null);
        }
        BufferedImage fountainFrame = getWallTile(wallTileId);
        if (fountainFrame != null) {
            g.drawImage(fountainFrame, px, py, 16, 16, null);
        }
    }

    @Override
    public void render(Graphics2D g) {
        long nowNano = System.nanoTime();
        double dt = Math.min(0.1, (nowNano - lastUpdateNano) / 1_000_000_000.0);
        lastUpdateNano = nowNano;

        if (player != null) {
            player.update(dt);
        }

        // Cập nhật frame hoạt ảnh của bản đồ mỗi 100ms (10 FPS đúng theo cấu hình item.tsx)
        int currentTick = (int) ((System.currentTimeMillis() / 100) % 12);
        if (currentTick != lastAnimTick) {
            lastAnimTick = currentTick;
            updateAnimatedMapFrame(currentTick);
        }

        Viewport v = Viewport.current();

        // 1. Tô nền đen toàn cửa sổ
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, (int) Math.ceil(v.w), (int) Math.ceil(v.h));

        int drawW = (int) Math.round(Viewport.UI_W * v.k);
        int drawH = (int) Math.round(Viewport.UI_H * v.k);
        int drawX = (int) Math.round((v.w - drawW) / 2.0);
        int drawY = (int) Math.round(v.offsetY);

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // 2. Vẽ bản đồ TRÀN TOÀN MÀN HÌNH
        if (renderedMapImage != null) {
            g.drawImage(renderedMapImage, drawX, drawY, drawW, drawH, null);
        }

        // 3. Vẽ nhân vật, vũ khí và thanh HUD
        if (player != null) {
            player.render(g, v.k, v.offsetY);
        }

        // 4. Vẽ đè phần thân trên của cột đá nếu nhân vật đi vòng ra phía sau cột đá
        if (workingMapImage != null && player != null) {
            double scaleX = Viewport.UI_W / CROP_W;
            double scaleY = Viewport.UI_H / CROP_H;
            Rectangle2D.Double foot = player.getFootHitbox(
                    player.getCharacter().getX(),
                    player.getCharacter().getY()
            );
            double playerFootY = foot.getCenterY();

            for (int[] p : PILLAR_TOPS) {
                int px = p[0], py = p[1], pw = p[2], ph = p[3];
                double pillarBaseScreenY = (p[4] - CROP_Y) * scaleY;

                // Khi chân nhân vật nằm cao hơn chân cột đá (tức là đi ra sau lưng cột)
                if (playerFootY < pillarBaseScreenY) {
                    BufferedImage pillarPatch = workingMapImage.getSubimage(px, py, pw, ph);
                    int sx = drawX + (int) Math.round((px - CROP_X) * scaleX * v.k);
                    int sy = drawY + (int) Math.round((py - CROP_Y) * scaleY * v.k);
                    int sw = (int) Math.round(pw * scaleX * v.k);
                    int sh = (int) Math.round(ph * scaleY * v.k);
                    g.drawImage(pillarPatch, sx, sy, sw, sh, null);
                }
            }
        }

        if (oldInterp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        }

        // 5. Vẽ khung kiểm tra Hitbox nếu bấm phím F3
        if (showDebugHitbox && player != null) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(255, 40, 40, 190));
            for (Rectangle2D.Double wall : mapColliders) {
                int wx = (int) Math.round(wall.x * v.k);
                int wy = (int) Math.round(v.offsetY + wall.y * v.k);
                int ww = (int) Math.round(wall.width * v.k);
                int wh = (int) Math.round(wall.height * v.k);
                g.drawRect(wx, wy, ww, wh);
            }

            Rectangle2D.Double foot = player.getFootHitbox(
                    player.getCharacter().getX(),
                    player.getCharacter().getY()
            );
            g.setColor(new Color(40, 255, 80, 230));
            g.drawRect(
                    (int) Math.round(foot.x * v.k),
                    (int) Math.round(v.offsetY + foot.y * v.k),
                    (int) Math.round(foot.width * v.k),
                    (int) Math.round(foot.height * v.k)
            );
        }
    }
}