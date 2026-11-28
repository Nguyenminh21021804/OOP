package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.environment.tilemap.MapOrientations;
import de.gurkenlabs.litiengine.environment.tilemap.xml.TmxMap;
import de.gurkenlabs.litiengine.graphics.Camera;
import de.gurkenlabs.litiengine.graphics.RenderType;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import entities.DemonBossEnemy;
import entities.Enemy;
import entities.PlayableBoss;
import entities.TrainingDummy;
import entities.ZombieBossEnemy;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.resources.Resources;

/**
 * GamePlayScreen — Màn hình đấu trường thử nghiệm (Battle Test Arena).
 * Người chơi trực tiếp điều khiển Boss Ma Vương Khổng Lồ (Demon Lord Gerald)
 * và tự do thi triển bộ kỹ năng để luyện tập trên Bia tập đánh (Training Dummy).
 */
public class GamePlayScreen extends GameScreen {
    public static final String NAME = "GAMEPLAY";

    private DemonBossEnemy demonBoss;
    private ZombieBossEnemy zombieBoss;
    private Enemy bossPlayer;
    private PlayableBoss playableBoss;
    private int selectedBossIndex = 1;
    private TrainingDummy dummy;
    private Environment environment;
    private long screenEnteredTime = 0;

    public GamePlayScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        super.initializeComponents();
        loadAllMonsterSprites();
        initBattleArena();
    }

    private boolean isGameplayActive() {
        return Game.screens().current() != null && NAME.equals(Game.screens().current().getName());
    }

    @Override
    public void prepare() {
        super.prepare();
        this.screenEnteredTime = Game.time().now();
        loadAllMonsterSprites();
        if (playableBoss != null) {
            playableBoss.resetBossState();
        }
        if (environment != null) {
            Game.world().loadEnvironment(environment);
            resetArena();
        }
        if (Game.window() != null && Game.window().getRenderComponent() != null) {
            Game.window().getRenderComponent().requestFocus();
            Game.window().getRenderComponent().requestFocusInWindow();
        }
    }

    /**
     * Khởi tạo đấu trường, Boss người chơi và bù nhìn tập đánh.
     */
    private void initBattleArena() {
        // Tắt toàn bộ hiển thị debug tên và khung va chạm trôi nổi
        Game.config().debug().setRenderEntityNames(false);
        Game.config().debug().setDebugEnabled(false);
        Game.config().debug().setRenderHitBoxes(false);
        Game.config().debug().setRenderBoundingBoxes(false);
        Game.config().debug().setRenderCollisionBoxes(false);
        Game.config().debug().setShowMouseTargetMetric(false);

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

        // 1. Tạo Bù Nhìn (TrainingDummy) đứng yên CHÍNH GIỮA ĐẤU TRƯỜNG (512, 288) (Team 2)
        dummy = new TrainingDummy(512, 288);
        dummy.setTeam(2);
        dummy.setName("");
        environment.add(dummy);

        // 2. Khởi tạo cả 2 Boss (mặc định kiểm thử Boss 2: Frost Titan / Big Zombie, có thể đổi bằng [1] / [2] / [TAB])
        demonBoss = new DemonBossEnemy();
        demonBoss.setName("");
        demonBoss.setLocation(380, 288);
        demonBoss.setPlayerControlled(false);
        demonBoss.setTeam(1);
        Game.loop().detach(demonBoss);

        zombieBoss = new ZombieBossEnemy();
        zombieBoss.setName("");
        zombieBoss.setLocation(380, 288);
        zombieBoss.setPlayerControlled(true);
        zombieBoss.setTeam(1);

        bossPlayer = zombieBoss;
        playableBoss = zombieBoss;
        selectedBossIndex = 2;
        environment.add(bossPlayer);

        // Vòng tròn mục tiêu quanh Training Dummy
        environment.add(g -> {
            if (dummy != null && !dummy.isDead()) {
                g.setColor(new Color(255, 60, 60, 180));
                Point2D dView = (Game.world().camera() != null)
                    ? Game.world().camera().getViewportLocation(dummy.getCenter())
                    : dummy.getCenter();
                int dx = (int) Math.round(dView.getX());
                int dy = (int) Math.round(dView.getY());
                g.drawOval(dx - 24, dy - 24, 48, 48);
                g.setColor(Color.RED);
                g.setFont(new Font("Monospaced", Font.BOLD, 12));
                g.drawString("🎯 TRAINING DUMMY", dx - 55, dy + 35);
            }
        }, RenderType.OVERLAY);

        // Camera khóa chuẩn giữa màn hình 1024x576
        Camera camera = new Camera();
        camera.setFocus(512, 288);
        Game.world().setCamera(camera);

        // Đăng ký phím điều khiển
        Input.keyboard().onKeyReleased(KeyEvent.VK_ESCAPE, e -> {
            if (!isGameplayActive()) return;
            Game.screens().display(MainMenuScreen.NAME);
        });

        Input.keyboard().onKeyReleased(KeyEvent.VK_R, e -> {
            if (!isGameplayActive()) return;
            resetArena();
        });

        // Chuyển đổi giữa Boss 1 (Demon Lord) và Boss 2 (Frost Titan) bằng [1], [2] hoặc [TAB]
        Input.keyboard().onKeyReleased(KeyEvent.VK_1, e -> {
            if (!isGameplayActive()) return;
            switchBoss(1);
        });
        Input.keyboard().onKeyReleased(KeyEvent.VK_2, e -> {
            if (!isGameplayActive()) return;
            switchBoss(2);
        });
        Input.keyboard().onKeyReleased(KeyEvent.VK_TAB, e -> {
            if (!isGameplayActive()) return;
            switchBoss(selectedBossIndex == 1 ? 2 : 1);
        });

        // Chiêu 1: Tấn công chính (Bite / Boulder Toss) bằng [SPACE] hoặc [J]
        Runnable onPrimary = () -> {
            if (!isGameplayActive()) return;
            if (playableBoss != null && bossPlayer != null && !bossPlayer.isDead()) {
                playableBoss.playerPrimaryAttack();
            }
        };
        Input.keyboard().onKeyPressed(KeyEvent.VK_SPACE, e -> onPrimary.run());
        Input.keyboard().onKeyPressed(KeyEvent.VK_J, e -> onPrimary.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_SPACE, e -> onPrimary.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_J, e -> onPrimary.run());

        // Chiêu 2: Bắn đạn / Hàng gai đất bằng [Q] hoặc [K]
        Runnable onSpit = () -> {
            if (!isGameplayActive()) return;
            if (playableBoss != null && bossPlayer != null && !bossPlayer.isDead()) {
                playableBoss.playerFireSpit();
            }
        };
        Input.keyboard().onKeyPressed(KeyEvent.VK_Q, e -> onSpit.run());
        Input.keyboard().onKeyPressed(KeyEvent.VK_K, e -> onSpit.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_Q, e -> onSpit.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_K, e -> onSpit.run());

        // Chiêu 3: Vòng xoáy nước / Bão lửa 360 độ bằng [C] hoặc [L]
        Runnable onRing = () -> {
            if (!isGameplayActive()) return;
            if (playableBoss != null && bossPlayer != null && !bossPlayer.isDead()) {
                playableBoss.playerFireRing();
            }
        };
        Input.keyboard().onKeyPressed(KeyEvent.VK_C, e -> onRing.run());
        Input.keyboard().onKeyPressed(KeyEvent.VK_L, e -> onRing.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_C, e -> onRing.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_L, e -> onRing.run());

        // Chiêu 4: Cơn lốc xoáy / Lao húc bằng [SHIFT], [E], [R] hoặc [H]
        Runnable onCharge = () -> {
            if (!isGameplayActive()) return;
            if (playableBoss != null && bossPlayer != null && !bossPlayer.isDead()) {
                playableBoss.playerCharge();
            }
        };
        Input.keyboard().onKeyPressed(KeyEvent.VK_SHIFT, e -> onCharge.run());
        Input.keyboard().onKeyPressed(KeyEvent.VK_E, e -> onCharge.run());
        Input.keyboard().onKeyPressed(KeyEvent.VK_R, e -> onCharge.run());
        Input.keyboard().onKeyPressed(KeyEvent.VK_H, e -> onCharge.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_SHIFT, e -> onCharge.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_E, e -> onCharge.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_R, e -> onCharge.run());
        Input.keyboard().onKeyReleased(KeyEvent.VK_H, e -> onCharge.run());

        // Chiêu 5: Bật/Tắt Cuồng Nộ Pha 2 (Enrage) bằng [F]
        Input.keyboard().onKeyPressed(KeyEvent.VK_F, e -> {
            if (!isGameplayActive()) return;
            if (playableBoss != null && bossPlayer != null && !bossPlayer.isDead()) {
                playableBoss.toggleEnrage();
            }
        });
        Input.keyboard().onKeyReleased(KeyEvent.VK_F, e -> {
            if (!isGameplayActive()) return;
            if (playableBoss != null && bossPlayer != null && !bossPlayer.isDead()) {
                playableBoss.toggleEnrage();
            }
        });

        // Fire on right-button press so the attack does not depend on a full click event.
        Input.mouse().onPressed(e -> {
            if (!isGameplayActive()) return;
            if (playableBoss == null || bossPlayer == null || bossPlayer.isDead()) return;
            if (Input.mouse().isRightButton(e) || e.getButton() == MouseEvent.BUTTON3) {
                playableBoss.playerFireSpit();
            }
        });

        // Chuột trái = Tấn công chính
        Input.mouse().onClicked(e -> {
            if (!isGameplayActive()) return;
            // Chống trôi cú click từ Menu chính khi vừa bấm "NEW GAME" vào màn chơi
            if (Game.time().now() - screenEnteredTime < 150) return;
            if (playableBoss == null || bossPlayer == null || bossPlayer.isDead()) return;
            if (e.getButton() == MouseEvent.BUTTON1) {
                playableBoss.playerPrimaryAttack();
            }
        });
    }

    private void switchBoss(int index) {
        if (environment == null || index == selectedBossIndex) return;
        double x = bossPlayer != null ? bossPlayer.getX() : 380;
        double y = bossPlayer != null ? bossPlayer.getY() : 288;

        if (bossPlayer != null && playableBoss != null) {
            playableBoss.setPlayerControlled(false);
            Game.loop().detach(bossPlayer);
            environment.remove(bossPlayer);
            if (bossPlayer instanceof de.gurkenlabs.litiengine.graphics.IRenderable r) {
                environment.removeRenderable(r);
            }
        }

        selectedBossIndex = index;
        if (index == 2) {
            bossPlayer = zombieBoss;
            playableBoss = zombieBoss;
        } else {
            bossPlayer = demonBoss;
            playableBoss = demonBoss;
        }

        if (bossPlayer != null && playableBoss != null) {
            bossPlayer.resurrect();
            bossPlayer.getHitPoints().setValue(bossPlayer.getHitPoints().getMax());
            bossPlayer.setLocation(x, y);
            bossPlayer.setTeam(1);
            playableBoss.setPlayerControlled(true);
            playableBoss.resetBossState();
            Game.loop().attach(bossPlayer);
            environment.add(bossPlayer);
        }
    }

    /**
     * Hồi phục và đặt lại vị trí cho Boss người chơi và bù nhìn.
     */
    private void resetArena() {
        if (environment == null) return;

        // Dọn sạch các thực thể phụ (đạn, quái đệ tử nếu có)
        for (var entity : new java.util.ArrayList<>(environment.getCombatEntities())) {
            if (entity != dummy && entity != bossPlayer) {
                entity.die();
                environment.remove(entity);
            }
        }

        // Hồi máu và đặt lại bù nhìn ở trung tâm
        if (dummy != null) {
            dummy.resurrect();
            dummy.getHitPoints().setValue(dummy.getHitPoints().getMax());
            dummy.setLocation(512, 288);
            dummy.setTeam(2);
        }

        // Hồi máu và đặt lại Boss người chơi
        if (bossPlayer != null && playableBoss != null) {
            bossPlayer.resurrect();
            playableBoss.resetBossState();
            bossPlayer.getHitPoints().setValue(bossPlayer.getHitPoints().getMax());
            bossPlayer.setLocation(380, 288);
            bossPlayer.setTeam(1);
        }
    }

    @Override
    public void render(Graphics2D g) {
        super.render(g);
        drawHUD(g);
    }

    private void drawHUD(Graphics2D g) {
        g.setFont(new Font("Monospaced", Font.BOLD, 13));

        boolean isFrost = (selectedBossIndex == 2);
        // Bảng thông tin điều khiển Boss ở góc trên bên trái
        g.setColor(new Color(15, 10, 25, 220));
        g.fillRect(15, 15, 680, 133);
        g.setColor(isFrost ? new Color(80, 210, 255) : new Color(255, 60, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(15, 15, 680, 133);

        g.setColor(isFrost ? new Color(110, 235, 255) : new Color(255, 175, 40));
        String bossTitle = isFrost
            ? "🧟 BOSS 2: TITAN ZOMBIE (4 NGUYÊN TỐ)  |  [1]/[2]/[TAB]: Đổi Boss"
            : "👹 BOSS 1: DEMON LORD GERALD           |  [1]/[2]/[TAB]: Đổi Boss";
        g.drawString(bossTitle, 25, 36);
        g.setColor(Color.WHITE);
        g.drawString("WASD / Mũi tên: Di chuyển tự do | Chuột: Hướng mặt theo con trỏ", 25, 56);
        g.drawString(isFrost
            ? "[Chuột Trái] / [SPACE] / [J]: 🪨 Ném Đá Khổng Lồ (Boulder Toss - 70 DMG nổ AoE)"
            : "[Chuột Trái] / [SPACE] / [J]: 🦷 Đòn Cắn (Tam giác báo trước 0.20s -> Táp 50 DMG)", 25, 76);
        g.drawString(isFrost
            ? "[Chuột Phải] / [Q]   / [K]: 🏔 Hàng Gai Đất (Earth Spike Row - 55 DMG + Chậm)"
            : "[Chuột Phải] / [Q]   / [K]: 🔥 Phun 5 Cầu Lửa Tỏa Hình Quạt (Hellfire)", 25, 94);
        g.setColor(isFrost ? new Color(120, 255, 210) : new Color(255, 140, 80));
        g.drawString(isFrost
            ? "[C] / [L]: 🌀 Vòng Xoáy Nước (Whirlpool - Hút kẻ địch + 14 DMG/tick liên tục)"
            : "[C] / [L]: 🌀 Vòng Tròn Lửa 360° (12 viên / Pha 2: 16 viên x 20 DMG)", 25, 112);
        g.setColor(Color.YELLOW);
        g.drawString(isFrost
            ? "[SHIFT] / [E] / [R] / [H]: 🪨 Mưa Đá (Rockfall - Vùng tròn 6x6 quanh Boss) | [F]: Pha 2"
            : "[SHIFT] / [E] / [R] / [H]: ⚡ Lao Húc (Chữ nhật 2x5 ô -> Bonk!) | [F]: Pha 2", 25, 130);

        // Vẽ thanh máu Boss hoành tráng ở cạnh dưới màn hình
        if (playableBoss != null && bossPlayer != null && !bossPlayer.isDead()) {
            playableBoss.renderHealthBar(g, Game.window().getWidth(), Game.window().getHeight());
        }
    }

    private static boolean spritesLoaded = false;

    /**
     * Tự động đăng ký toàn bộ spritesheet của Boss và các thực thể vào Resources.spritesheets()
     */
    public static void loadAllMonsterSprites() {
        if (spritesLoaded) return;
        spritesLoaded = true;

        // 0. Boss Ma Vương (Big Demon) & Boss Gerald (Big Zombie) (kích thước 32x36 per frame)
        loadSpritesheet("big_demon-idle", "sprites/monsters/big_demon-idle.png", 32, 36);
        loadSpritesheet("big_demon-walk", "sprites/monsters/big_demon-walk.png", 32, 36);
        loadSpritesheet("big_demon-move", "sprites/monsters/big_demon-walk.png", 32, 36);
        loadSpritesheet("big_demon-bite", "sprites/monsters/big_demon-bite.png", 50, 36);
        loadSpritesheet("big_demon-bite-left", "sprites/monsters/big_demon-bite-left.png", 50, 36);
        loadSpritesheet("big_zombie-idle", "sprites/monsters/big_zombie-idle.png", 32, 36);
        loadSpritesheet("big_zombie-walk", "sprites/monsters/big_zombie-walk.png", 32, 36);
        loadSpritesheet("big_zombie-move", "sprites/monsters/big_zombie-walk.png", 32, 36);

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
                registerSheet(moveName + "-right", sheet);
                registerSheet(moveName + "-down", sheet);
                registerSheet(moveName + "-up", sheet);
                Spritesheet leftSheet = Resources.spritesheets().get(name + "-left");
                if (leftSheet != null) {
                    registerSheet(moveName + "-left", leftSheet);
                }
            } else if (name.contains("-move")) {
                String walkName = name.replace("-move", "-walk");
                registerSheet(walkName, sheet);
                registerSheet(walkName + "-right", sheet);
                registerSheet(walkName + "-down", sheet);
                registerSheet(walkName + "-up", sheet);
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
