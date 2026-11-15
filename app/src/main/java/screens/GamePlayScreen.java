package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.Creature;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.environment.tilemap.MapOrientations;
import de.gurkenlabs.litiengine.environment.tilemap.xml.TmxMap;
import de.gurkenlabs.litiengine.graphics.Camera;
import de.gurkenlabs.litiengine.graphics.RenderType;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import de.gurkenlabs.litiengine.input.KeyboardEntityController;
import entities.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.resources.Resources;

/**
 * GamePlayScreen — Màn hình đấu trường thử nghiệm (Battle Test Arena).
 * Có một Bia tập bắn (TrainingDummy) đứng yên ở giữa.
 * Toàn bộ các quái đánh xa sẽ liên tục lượn qua lại trái - phải (strafe)
 * và dồn hỏa lực bắn vào bia tập!
 */
public class GamePlayScreen extends GameScreen {
    public static final String NAME = "GAMEPLAY";

    private Creature player;
    private TrainingDummy dummy;
    private Environment environment;

    public GamePlayScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        super.initializeComponents();
        loadAllMonsterSprites();
        initBattleArena();
    }

    @Override
    public void prepare() {
        super.prepare();
        loadAllMonsterSprites();
        if (environment != null) {
            Game.world().loadEnvironment(environment);
            resetArena();
        }
    }

    /**
     * Khởi tạo đấu trường, bù nhìn đứng yên và sinh quái.
     */
    private void initBattleArena() {
        TmxMap map = new TmxMap();
        map.setWidth(32);
        map.setHeight(18);
        map.setTileWidth(32);
        map.setTileHeight(32);
        map.setOrientation(MapOrientations.ORTHOGONAL);

        environment = new Environment(map);

        // Vẽ sàn đấu trường bên dưới các thực thể (RenderType.GROUND)
        environment.add(g -> {
            g.setColor(new Color(24, 20, 37));
            g.fillRect(0, 0, 1024, 576);

            g.setColor(new Color(38, 33, 56));
            for (int x = 0; x < 1024; x += 32) {
                g.drawLine(x, 0, x, 576);
            }
            for (int y = 0; y < 576; y += 32) {
                g.drawLine(0, y, 1024, y);
            }
        }, RenderType.GROUND);

        // 1. Tạo Bù Nhìn (TrainingDummy) đứng yên ở trung tâm phía trên (Team 1)
        dummy = new TrainingDummy(512, 180);
        environment.add(dummy);

        // 2. Tạo Người Chơi (Player) điều khiển bằng WASD / Mũi tên để quan sát (Team 2)
        player = new Creature("doc");
        player.setLocation(100, 480);
        player.setTeam(2);
        player.setVelocity(120f);
        player.getHitPoints().setMax(300);
        player.getHitPoints().setValue(300);
        player.addController(new KeyboardEntityController<>(player));
        environment.add(player);

        // Vẽ vòng mục tiêu quanh Training Dummy
        environment.add(g -> {
            if (dummy != null && !dummy.isDead()) {
                g.setColor(new Color(255, 60, 60, 180));
                int dx = (int) dummy.getCenter().getX();
                int dy = (int) dummy.getCenter().getY();
                g.drawOval(dx - 24, dy - 24, 48, 48);
                g.setColor(Color.RED);
                g.setFont(new Font("Monospaced", Font.BOLD, 12));
                g.drawString("🎯 TARGET", dx - 30, dy + 35);
            }
        }, RenderType.OVERLAY);

        // Camera khóa chuẩn giữa màn hình 1024x576 (không bị xô lệch lên trần)
        Camera camera = new Camera();
        camera.setFocus(512, 288);
        Game.world().setCamera(camera);

        // 3. Sinh toàn bộ quái xếp hàng ngang ở DƯỚI con dummy, mỗi con cách nhau 1 ô vuông (64px)
        spawnAllEnemies();
    }

    /**
     * Sinh 5 quái vật tầm xa xếp hàng ngang ở DƯỚI con Dummy, mỗi con cách nhau 1 ô vuông (64px)
     * và cùng dồn hỏa lực bắn thẳng lên con Dummy!
     */
    private void spawnAllEnemies() {
        if (environment == null) return;

        double rowY = 360; // Hàng ngang ở dưới con dummy (dummy ở Y=180)

        // 1. Ice Zombie (bắn đạn băng)
        IceZombieEnemy iceZombie = new IceZombieEnemy();
        iceZombie.setLocation(384, rowY);
        environment.add(iceZombie);

        // 2. Imp (bắn mưa cầu lửa nhanh)
        ImpEnemy imp = new ImpEnemy();
        imp.setLocation(448, rowY);
        environment.add(imp);

        // 3. Doc (phóng dao găm - chính diện dưới Dummy)
        DocEnemy doc = new DocEnemy();
        doc.setLocation(512, rowY);
        environment.add(doc);

        // 4. Shaman (cầm gậy ma pháp)
        ShamanEnemy shaman = new ShamanEnemy();
        shaman.setLocation(576, rowY);
        environment.add(shaman);

        // 5. Lizard (bắn cung tên)
        LizardEnemy lizard = new LizardEnemy();
        lizard.setLocation(640, rowY);
        environment.add(lizard);
    }

    /**
     * Hồi sinh lại toàn bộ quái và đặt lại vị trí.
     */
    private void resetArena() {
        if (environment == null) return;

        // Xóa quái cũ an toàn tránh ConcurrentModificationException
        for (var entity : new java.util.ArrayList<>(environment.getCombatEntities())) {
            if (entity != dummy && entity != player && entity.getTeam() == 2) {
                entity.die();
                environment.remove(entity);
            }
        }

        // Hồi máu bù nhìn và người chơi
        if (dummy != null) {
            dummy.getHitPoints().setValue(dummy.getHitPoints().getMax());
            dummy.setLocation(512, 180);
        }
        if (player != null) {
            player.getHitPoints().setValue(player.getHitPoints().getMax());
            player.setLocation(100, 480);
        }

        // Sinh lại quái
        spawnAllEnemies();
    }

    @Override
    public void render(Graphics2D g) {
        super.render(g);

        // Vẽ giao diện HUD thông số chiến đấu
        drawHUD(g);
    }

    private void drawHUD(Graphics2D g) {
        g.setFont(new Font("Monospaced", Font.BOLD, 14));

        // Bảng thông tin góc trên bên trái
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(15, 15, 460, 95);
        g.setColor(Color.WHITE);
        g.drawRect(15, 15, 460, 95);

        g.setColor(Color.CYAN);
        g.drawString("🎯 BIA TẬP BẮN (TRAINING DUMMY): ĐỨNG YÊN", 25, 38);
        g.setColor(Color.GREEN);
        g.drawString("QUÁI VẬT: Đang lượn TRÁI - PHẢI và dồn hỏa lực vào bia!", 25, 58);
        g.setColor(Color.YELLOW);
        g.drawString("WASD: Di chuyển tự do   |   [R] Đặt lại quái", 25, 78);
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("[ESC] Quay về Menu chính", 25, 98);
    }

    private static boolean spritesLoaded = false;

    /**
     * Tự động đăng ký toàn bộ spritesheet của quái vật vào Resources.spritesheets()
     * để CreatureAnimationController của LITIengine tìm thấy và hiển thị ảnh quái.
     */
    public static void loadAllMonsterSprites() {
        if (spritesLoaded) return;
        spritesLoaded = true;

        // 1. Quái đánh xa và vũ khí trong sprites/monsters
        loadSpritesheet("doc-idle", "sprites/monsters/doc-idle.png", 16, 23);
        loadSpritesheet("doc-walk", "sprites/monsters/doc-walk.png", 16, 23);

        loadSpritesheet("lizard-f-idle", "sprites/monsters/lizard-f-idle.png", 16, 28);
        loadSpritesheet("lizard-f-walk", "sprites/monsters/lizard-f-walk.png", 16, 28);
        loadSpritesheet("lizard-f-hit", "sprites/monsters/lizard-f-hit.png", 16, 28);
        loadSpritesheet("lizard-idle", "sprites/monsters/lizard-f-idle.png", 16, 28);
        loadSpritesheet("lizard-walk", "sprites/monsters/lizard-f-walk.png", 16, 28);

        loadSpritesheet("orc-shaman-idle", "sprites/monsters/orc-shaman-idle.png", 16, 23);
        loadSpritesheet("orc-shaman-walk", "sprites/monsters/orc-shaman-walk.png", 16, 23);

        loadSpritesheet("imp-idle", "sprites/monsters/imp-idle.png", 16, 16);
        loadSpritesheet("imp-walk", "sprites/monsters/imp-walk.png", 16, 16);

        loadSpritesheet("ice_zombie-idle", "sprites/monsters/ice_zombie-idle.png", 16, 16);
        loadSpritesheet("ice_zombie-walk", "sprites/monsters/ice-zombie-walk.png", 16, 16);
        loadSpritesheet("ice-zombie-idle", "sprites/monsters/ice_zombie-idle.png", 16, 16);
        loadSpritesheet("ice-zombie-walk", "sprites/monsters/ice-zombie-walk.png", 16, 16);

        loadSpritesheet("skelet-idle", "sprites/monsters/skelet-idle.png", 16, 16);
        loadSpritesheet("skelet-walk", "sprites/monsters/skelet-walk.png", 16, 16);

        loadSpritesheet("goblin-idle", "sprites/monsters/goblin-idle.png", 16, 16);
        loadSpritesheet("necromancer-idle", "sprites/monsters/necromancer-idle.png", 16, 23);
        loadSpritesheet("necromancer-walk", "sprites/monsters/necromancer-walk.png", 16, 23);
        loadSpritesheet("zombie-walk", "sprites/monsters/zombie-walk.png", 16, 16);

        // Vũ khí & Projectile
        loadSpritesheet("weapon_arrow", "sprites/monsters/weapon_arrow.png", 7, 21);
        loadSpritesheet("weapon_knife", "sprites/monsters/weapon_knife.png", 6, 13);
        loadSpritesheet("weapon_red_magic_staff", "sprites/monsters/weapon_red_magic_staff.png", 8, 30);
        loadSpritesheet("bow-shoot", "sprites/monsters/bow-shoot.png", 14, 26);
        loadSpritesheet("bow-shoot-3f", "sprites/monsters/bow-shoot-3f.png", 14, 26);

        // Đạn lửa của Imp
        loadSpritesheet("Fire bullet", "sprites/Fire Effect and Bullet 16x16.png", 17, 17);
        loadSpritesheet("fire-bullet", "sprites/Fire Effect and Bullet 16x16.png", 17, 17);
        loadSpritesheet("fire_bullet", "sprites/Fire Effect and Bullet 16x16.png", 17, 17);

        // Đạn băng của Ice Zombie (4 frames x 16x16)
        loadSpritesheet("ice bullet", "sprites/monsters/ice-bullet.png", 16, 16);
        loadSpritesheet("ice-bullet", "sprites/monsters/ice-bullet.png", 16, 16);
        loadSpritesheet("ice_bullet", "sprites/monsters/ice-bullet.png", 16, 16);

        loadSpritesheet("summon", "sprites/summon.png", 16, 16);
        loadSpritesheet("ice", "sprites/ice.png", 70, 17);
        loadSpritesheet("ice1", "sprites/ice1.png", 70, 17);

        // 2. Quái cận chiến từ các thư mục Frame
        String[] prefixes = {"chort", "orc_warrior", "masked_orc", "tiny_zombie", "wogol", "zombie"};
        String[] frameDirs = {"ChortFrame", "OrcWarriorFrame", "MaskedOrcFrame", "TinyZombieFrame", "WogolFrame", "ZombieFrame"};
        String[] actions = {"idle", "move", "strike", "windup", "hurt"};
        String[] directions = {"down", "left", "right", "up"};

        for (int i = 0; i < prefixes.length; i++) {
            String p = prefixes[i];
            String dir = frameDirs[i];

            String[] variants = {p, p + "_elite"};
            for (String v : variants) {
                loadSpritesheet(v + "-flash", "sprites/" + dir + "/" + v + "-flash.png", 16, 0);

                for (String act : actions) {
                    for (String d : directions) {
                        String sheetName = v + "-" + act + "-" + d;
                        String path = "sprites/" + dir + "/" + sheetName + ".png";
                        loadSpritesheet(sheetName, path, 16, 0);
                    }
                }
            }
        }
    }

    private static BufferedImage createFlippedSheet(BufferedImage src, int spriteWidth, int spriteHeight) {
        int w = spriteWidth > 0 ? spriteWidth : src.getWidth();
        int h = spriteHeight > 0 ? spriteHeight : src.getHeight();
        int cols = Math.max(1, src.getWidth() / w);
        int rows = Math.max(1, src.getHeight() / h);

        BufferedImage flipped = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = flipped.createGraphics();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sx = c * w;
                int sy = r * h;
                // Lật ngang từng khung hình riêng lẻ tại chỗ
                g.drawImage(src,
                        sx + w, sy, sx, sy + h,
                        sx, sy, sx + w, sy + h,
                        null);
            }
        }
        g.dispose();
        return flipped;
    }

    private static void loadSpritesheet(String name, String resourcePath, int spriteWidth, int spriteHeight) {
        try {
            BufferedImage img = Resources.images().get(resourcePath);
            if (img == null) return;

            int w = spriteWidth > 0 ? spriteWidth : img.getWidth();
            int h = spriteHeight > 0 ? spriteHeight : img.getHeight();

            Spritesheet sheet = new Spritesheet(img, name, w, h);

            // Đăng ký tên chuẩn vào Resources.spritesheets()
            registerSheet(name, sheet);

            // Tự động alias hướng cho idle/walk nếu tên chưa có hướng (dành cho Ranged)
            if (!name.contains("-left") && !name.contains("-right") && !name.contains("-up") && !name.contains("-down")) {
                registerSheet(name + "-down", sheet);
                registerSheet(name + "-right", sheet);
                registerSheet(name + "-up", sheet);

                // Tạo bản lật ngang chuẩn xác cho hướng LEFT
                BufferedImage flippedImg = createFlippedSheet(img, w, h);
                Spritesheet leftSheet = new Spritesheet(flippedImg, name + "-left", w, h);
                registerSheet(name + "-left", leftSheet);
            }

            // Alias giữa walk và move
            if (name.contains("-walk")) {
                String moveName = name.replace("-walk", "-move");
                registerSheet(moveName, sheet);
                Spritesheet leftSheet = Resources.spritesheets().get(name + "-left");
                if (leftSheet != null) {
                    registerSheet(moveName + "-left", leftSheet);
                }
            } else if (name.contains("-move")) {
                String walkName = name.replace("-move", "-walk");
                registerSheet(walkName, sheet);
                Spritesheet leftSheet = Resources.spritesheets().get(name + "-left");
                if (leftSheet != null) {
                    registerSheet(walkName + "-left", leftSheet);
                }

                if (name.endsWith("-down") || name.endsWith("-right")) {
                    String baseMove = name.substring(0, name.lastIndexOf('-'));
                    String baseWalk = baseMove.replace("-move", "-walk");
                    registerSheet(baseMove, sheet);
                    registerSheet(baseWalk, sheet);
                }
            } else if (name.endsWith("-idle-down") || name.endsWith("-idle-right")) {
                String baseIdle = name.substring(0, name.lastIndexOf('-'));
                registerSheet(baseIdle, sheet);
            }
        } catch (Exception ignored) {
        }
    }

    private static void registerSheet(String name, Spritesheet sheet) {
        Resources.spritesheets().add(name, sheet);
        Resources.spritesheets().add(name + ".png", sheet);
    }
}
