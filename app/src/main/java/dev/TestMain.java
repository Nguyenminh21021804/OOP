package dev;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import de.gurkenlabs.litiengine.resources.Resources;
import de.gurkenlabs.litiengine.IUpdateable;

import entities.EnemyFactory;
import entities.melee.MeleeEnemy;
import entities.MonsterType;
import java.awt.event.KeyEvent;
import level.RoomData;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Chương trình test độc lập trên Map_01: người chơi giả (TestDummy) và quái sinh ra
 * đúng các điểm spawn_enemy trong file .tmx.
 *
 * Phím: WASD / mũi tên để di chuyển, SPACE để đánh quái đứng gần (trong 40px).
 */
public class TestMain {

    /** Đường dẫn cho engine nạp map (tính từ gốc thư mục resources). */
    private static final String MAP_ENGINE_PATH = "maps/Map_01.tmx";
    /** Đường dẫn trên classpath để RoomData đọc cùng file đó. */
    private static final String MAP_RESOURCE_PATH = "/maps/Map_01.tmx";

    // Loại quái dùng để thử khi spawn chưa khai báo "monster" trong Tiled
    private static final MonsterType[] TEST_TYPES = {
            MonsterType.MASKED_ORC, MonsterType.ZOMBIE, MonsterType.TINY_ZOMBIE,
            MonsterType.CHORT, MonsterType.WOGOL};

    // Spawn thứ mấy (tính từ 0) là elite khi chưa khai báo "variant" trong Tiled; đặt -1 để tắt
    private static final int TEST_ELITE_INDEX = 4;

    // Bật để thử tuần tra: quái chỉ phát hiện khi người chơi lại rất gần (40px), không nghe thấy
    private static final boolean TEST_PATROL_ONLY = true;

    // Bật để xem thử animation: một quái đứng yên giữa phòng, điều khiển bằng phím (xem runAnimationViewer)
    private static final boolean ANIM_VIEWER = false;
    private static final double VIEWER_SPOT_X = 176;
    private static final double VIEWER_SPOT_Y = 110;

    /** Màn chơi tối thiểu: chỉ vẽ environment đang load. */
    private static class TestScreen extends GameScreen {
        TestScreen() {
            super("INGAME");
        }
    }

    /**
     * Sinh quái tại mọi spawn_enemy của phòng. Đọc hai thuộc tính trong Tiled:
     *   monster : tên MonsterType (ví dụ CHORT); trống thì lần lượt lấy từ TEST_TYPES để thử nhiều loại
     *   variant : "elite" cho quái tím mạnh hơn; trống thì chỉ spawn thứ TEST_ELITE_INDEX là elite (để thử)
     * Loại không phải cận chiến (ranged, boss) bị bỏ qua kèm cảnh báo, không làm sập chương trình.
     */
    private static List<MeleeEnemy> spawnEnemies(RoomData room) {
        List<MeleeEnemy> enemies = new ArrayList<>();
        int unnamed = 0;   // đếm spawn chưa khai báo monster, để xoay vòng TEST_TYPES
        int index = 0;     // thứ tự spawn, để chọn con elite thử

        for (RoomData.Spawn s : room.getEnemySpawns()) {
            boolean hasMonster = s.monster != null && !s.monster.trim().isEmpty();
            MonsterType type = hasMonster
                    ? EnemyFactory.typeOrDefault(s.monster, MonsterType.ORC_WARRIOR)
                    : TEST_TYPES[unnamed++ % TEST_TYPES.length];
            boolean elite = (s.variant != null)
                    ? "elite".equalsIgnoreCase(s.variant)
                    : index == TEST_ELITE_INDEX;
            index++;

            try {
                MeleeEnemy enemy = EnemyFactory.createMelee(type, elite);
                enemy.placeFeetAt(s.x, s.y);   // đặt đúng điểm spawn và ghi nhớ làm "nhà"
                Game.world().environment().add(enemy);
                if (TEST_PATROL_ONLY) {
                    enemy.setDetectionRange(40);
                    enemy.setHearingRange(0);
                }
                enemies.add(enemy);
                System.out.println("[Spawn] " + type + (elite ? " (elite)" : "")
                        + " tai (" + s.x + ", " + s.y + ")");
            } catch (UnsupportedOperationException e) {
                System.err.println("[Spawn] Bo qua (" + s.x + ", " + s.y + "): " + e.getMessage());
            }
        }
        return enemies;
    }

    /**
     * Nạp mọi spritesheet của các loại quái cận chiến, cả thường và elite:
     *  - idle, move: 4 hướng (left, right, up, down); engine tự chọn theo hướng nhìn và việc đang đi hay đứng.
     *  - windup, strike, hurt: 2 hướng (left, right); do Enemy.playAction phát.
     * Kích thước frame lấy từ MonsterType.Size. File thiếu thì cảnh báo và bỏ qua, không làm sập chương trình.
     */
    private static void loadSprites() {
        String[] loopStates = {"idle", "move"};
        String[] allDirections = {"left", "right", "up", "down"};
        String[] actions = {"windup", "strike", "hurt", "alert", "death"};
        String[] sideDirections = {"left", "right"};

        int loaded = 0;
        int missing = 0;
        for (MonsterType type : MonsterType.values()) {
            if (type.getRole() != MonsterType.Role.MELEE) {
                continue;   // quái đánh xa và boss chưa có sprite
            }
            MonsterType.Size size = type.getSize();
            for (boolean elite : new boolean[] {false, true}) {
                for (String state : loopStates) {
                    for (String dir : allDirections) {
                        if (loadSheet(type.getSpritePath(state, dir, elite), size)) {
                            loaded++;
                        } else {
                            missing++;
                        }
                    }
                }
                for (String action : actions) {
                    for (String dir : sideDirections) {
                        if (loadSheet(type.getSpritePath(action, dir, elite), size)) {
                            loaded++;
                        } else {
                            missing++;
                        }
                    }
                }
            }
        }
        System.out.println("[Sprites] Da nap " + loaded + " sheet, thieu " + missing);
    }

    /** Nạp một sheet nếu file có trên classpath; false nếu thiếu. */
    private static boolean loadSheet(String path, MonsterType.Size size) {
        if (TestMain.class.getResource("/" + path) == null) {
            System.err.println("[Sprites] Thieu file: " + path);
            return false;
        }
        Resources.spritesheets().load(path, size.spriteW, size.spriteH);
        return true;
    }

    /**
     * Chế độ xem thử animation. Một quái cận chiến đứng yên (không phát hiện, không đi tuần), phím:
     *   N / B  quái tiếp theo / quái trước      E  bật tắt elite       F  đổi hướng nhìn trái, phải
     *   1 lấy đà    2 chém    3 bị đánh (chỉ phát animation)
     *   H  bị đánh thật (nháy trắng, tia lửa, giật ngửa, mất 1 máu; hết máu thì chết và tan pixel)
     *   R  sinh lại quái hiện tại              L  tự động lặp lấy đà, chém, bị đánh
     */
    private static void runAnimationViewer() {
        List<MonsterType> types = new ArrayList<>();
        for (MonsterType t : MonsterType.values()) {
            if (t.getRole() == MonsterType.Role.MELEE) {
                types.add(t);
            }
        }

        final int[] typeIndex = {0};
        final boolean[] elite = {false};
        final boolean[] facingLeft = {false};
        final boolean[] autoLoop = {false};
        final MeleeEnemy[] current = {null};
        final long[] loopStart = {0};
        final int[] loopStep = {0};

        Runnable respawn = () -> {
            if (current[0] != null) {
                Game.loop().detach(current[0]);
                Game.world().environment().remove(current[0]);
            }
            MonsterType type = types.get(typeIndex[0]);
            MeleeEnemy enemy = EnemyFactory.createMelee(type, elite[0]);
            enemy.placeFeetAt(VIEWER_SPOT_X, VIEWER_SPOT_Y);
            enemy.setPatrolRadius(0);      // đứng yên
            enemy.setDetectionRange(0);    // không phát hiện ai
            enemy.setHearingRange(0);
            enemy.faceHorizontal(facingLeft[0]);
            Game.world().environment().add(enemy);
            current[0] = enemy;
            loopStart[0] = 0;
            System.out.println("[Viewer] " + type + (elite[0] ? " (elite)" : "")
                    + ", huong " + (facingLeft[0] ? "trai" : "phai"));
        };
        respawn.run();

        Input.keyboard().onKeyPressed(KeyEvent.VK_N, e -> {
            typeIndex[0] = (typeIndex[0] + 1) % types.size();
            respawn.run();
        });
        Input.keyboard().onKeyPressed(KeyEvent.VK_B, e -> {
            typeIndex[0] = (typeIndex[0] - 1 + types.size()) % types.size();
            respawn.run();
        });
        Input.keyboard().onKeyPressed(KeyEvent.VK_E, e -> {
            elite[0] = !elite[0];
            respawn.run();
        });
        Input.keyboard().onKeyPressed(KeyEvent.VK_F, e -> {
            facingLeft[0] = !facingLeft[0];
            if (current[0] != null) {
                current[0].faceHorizontal(facingLeft[0]);
            }
            System.out.println("[Viewer] huong " + (facingLeft[0] ? "trai" : "phai"));
        });
        Input.keyboard().onKeyPressed(KeyEvent.VK_R, e -> respawn.run());
        Input.keyboard().onKeyPressed(KeyEvent.VK_1, e -> previewAction(current[0], types.get(typeIndex[0]), "windup"));
        Input.keyboard().onKeyPressed(KeyEvent.VK_2, e -> previewAction(current[0], types.get(typeIndex[0]), "strike"));
        Input.keyboard().onKeyPressed(KeyEvent.VK_3, e -> previewAction(current[0], types.get(typeIndex[0]), "hurt"));
        Input.keyboard().onKeyPressed(KeyEvent.VK_4, e -> previewAction(current[0], types.get(typeIndex[0]), "alert"));
        Input.keyboard().onKeyPressed(KeyEvent.VK_H, e -> {
            if (current[0] != null && !current[0].isDead()) {
                current[0].takeDamage(1);
                System.out.println("[Viewer] mau con " + current[0].getCurrentHealth() + "/" + current[0].getMaxHealth());
            }
        });
        Input.keyboard().onKeyPressed(KeyEvent.VK_L, e -> {
            autoLoop[0] = !autoLoop[0];
            loopStart[0] = 0;
            System.out.println("[Viewer] tu dong lap: " + (autoLoop[0] ? "bat" : "tat"));
        });

        // Tự động lặp: lấy đà, rồi chém, rồi bị đánh, nghỉ, rồi lại từ đầu (theo nhịp thật của loại quái đang xem)
        IUpdateable autoPlayer = () -> {
            MeleeEnemy enemy = current[0];
            if (!autoLoop[0] || enemy == null || enemy.isDead()) {
                return;
            }
            long now = Game.time().now();
            MonsterType type = types.get(typeIndex[0]);
            if (loopStart[0] == 0) {
                loopStart[0] = now;
                loopStep[0] = 0;
            }
            long t = now - loopStart[0];
            long strikeAt = type.getWindupMs() + 180;
            long hurtAt = strikeAt + 500;
            long restartAt = hurtAt + 900;
            if (loopStep[0] == 0) {
                previewAction(enemy, type, "windup");
                loopStep[0] = 1;
            } else if (loopStep[0] == 1 && t >= strikeAt) {
                previewAction(enemy, type, "strike");
                loopStep[0] = 2;
            } else if (loopStep[0] == 2 && t >= hurtAt) {
                previewAction(enemy, type, "hurt");
                loopStep[0] = 3;
            } else if (loopStep[0] == 3 && t >= restartAt) {
                loopStart[0] = 0;
            }
        };
        Game.loop().attach(autoPlayer);

        System.out.println("[Viewer] Phim: N/B doi quai, E elite, F doi huong, 1 lay da, 2 chem, 3 bi danh, "
                + "H bi danh that, R sinh lai, L tu dong lap");
    }

    /** Phát một animation hành động với nhịp thật của loại quái (như trong lúc chiến đấu). */
    private static void previewAction(MeleeEnemy enemy, MonsterType type, String action) {
        if (enemy == null || enemy.isDead()) {
            return;
        }
        switch (action) {
            case "windup":
                enemy.playAction("windup", (int) type.getWindupMs() + 120, 4);
                break;
            case "strike":
                enemy.playAction("strike", (int) Math.max(type.getStrikeMs(), 120), 3);
                break;
            case "alert":
                enemy.playAction("alert", 240, 3);
                break;
            default:
                enemy.playAction("hurt", 160, 2);
        }
    }

    public static void main(String[] args) {
        Game.info().setName("Enemy Test");
        Game.info().setSubTitle("Kiem tra quai");
        Game.info().setVersion("v0.0.1");

        Game.init(args);
        Game.graphics().setBaseRenderScale(ANIM_VIEWER ? 8f : 4f);   // xem thử thì phóng to hơn

        Game.screens().add(new TestScreen());
        Game.world().loadEnvironment(MAP_ENGINE_PATH);
        Game.world().camera().setFocus(176, 104);   // giữa map 22x13 tile (352x208 px)

        loadSprites();

        // Dữ liệu phòng đọc từ chính file .tmx
        RoomData room = RoomData.load(MAP_RESOURCE_PATH);
        RoomData.setCurrent(room);

        if (ANIM_VIEWER) {
            runAnimationViewer();
            Game.start();
            return;
        }

        // Người chơi giả tại spawn_player. Điểm spawn là tâm hộp chân: hộp thực thể 16x23, chân 10x8
        TestDummy dummy = new TestDummy();
        Point2D ps = room.getPlayerSpawn();
        dummy.setLocation(ps.getX() - 8, ps.getY() - 19);
        Game.world().environment().add(dummy);

        List<MeleeEnemy> enemies = spawnEnemies(room);
        System.out.println("[TestMain] Da tao " + enemies.size() + " quai, "
                + room.getCollisions().size() + " hop va cham");

        // SPACE: đánh mọi quái trong 40px quanh dummy (có chống phím lặp)
        final long[] lastHit = {0};
        Input.keyboard().onKeyPressed(KeyEvent.VK_SPACE, e -> {
            long now = Game.time().now();
            if (now - lastHit[0] < 400) {
                return;
            }
            lastHit[0] = now;
            for (MeleeEnemy enemy : enemies) {
                if (!enemy.isDead() && enemy.getCenter().distance(dummy.getCenter()) <= 40) {
                    enemy.takeDamage(3);
                    System.out.println("[TEST] danh quai: mau con " + enemy.getCurrentHealth()
                            + "/" + enemy.getMaxHealth());
                }
            }
        });

        Game.start();
    }
}

