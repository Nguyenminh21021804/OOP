package screens;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.entities.CollisionBox;
import de.gurkenlabs.litiengine.entities.IEntity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.environment.tilemap.IMap;
import de.gurkenlabs.litiengine.environment.tilemap.IMapObject;
import de.gurkenlabs.litiengine.environment.tilemap.IMapObjectLayer;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import de.gurkenlabs.litiengine.resources.Resources;
import entities.Enemy;
import entities.EnemyFactory;
import entities.MonsterType;
import entities.ranged.Projectile;
import entities.ranged.RangedEnemy;
import level.RoomData;
import player.Player;
import player.PlayerSkin;
import traps.MapHazardLoader;
import weapons.Bow;
import weapons.Spear;
import weapons.Staff;
import weapons.Sword;
import weapons.Weapon;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlayScreen extends GameScreen {
    public static final String NAME = "PLAY";

    private static final String[] DEFAULT_MAP_FILES = {
        "Map_01.tmx",
        "Map_01_challenge_room.tmx",
        "Map_02_vuc_tham.tmx",
        "Map_03_trap.tmx",
        "Map_04_parkour.tmx",
        "Map_04_challenge_room.tmx",
        "Map_05_boss.tmx",
        "Stage2_Map_01.tmx",
        "Stage2_Map_02.tmx",
        "Stage2_Map_03.tmx",
        "Stage2_Map_04.tmx",
        "Stage2_Map_05_Boss.tmx",
        "Stage2_Secret_A.tmx",
        "Stage2_Secret_B.tmx"
    };

    private static final MonsterType[] DEFAULT_ENEMY_ROTATION = {
        MonsterType.ORC_WARRIOR,
        MonsterType.MASKED_ORC,
        MonsterType.ZOMBIE,
        MonsterType.TINY_ZOMBIE,
        MonsterType.CHORT,
        MonsterType.WOGOL,
        MonsterType.SKELET,
        MonsterType.DOC,
        MonsterType.IMP,
        MonsterType.ICE_ZOMBIE,
        MonsterType.LIZARD,
        MonsterType.ORC_SHAMAN,
        MonsterType.NECROMANCER
    };

    private List<String> mapFiles;
    private Player player;
    private int currentMapIndex = 0;
    private double mapWidth = 352.0;
    private double mapHeight = 208.0;
    private String loadError = null;

    private final Rectangle2D.Double nextMapButton = new Rectangle2D.Double();
    private Font uiFont;

    public PlayScreen() {
        super(NAME);
    }

    @Override
    protected void initializeComponents() {
        discoverMapFiles();
        try {
            uiFont = Resources.fonts().get("font/PeaberryBase.ttf");
        } catch (Exception e) {
            uiFont = null;
        }

        // Click chuột trái vào nút "NEXT MAP >"
        Input.mouse().onPressed(e -> {
            if (Game.screens().current() != this) return;
            if (e.getButton() != MouseEvent.BUTTON1) return;

            Point2D mouse = Input.mouse().getLocation();
            if (mouse != null && nextMapButton.contains(mouse)) {
                nextMap();
            }
        });

        // Chuyển tiếp toàn bộ phím bấm cho Player khi đang ở PlayScreen
        Input.keyboard().onKeyPressed(e -> {
            if (Game.screens().current() != this) return;
            int code = e.getKeyCode();

            if (code == KeyEvent.VK_ESCAPE) {
                if (player != null) player.clearPressedKeys();
                unloadActiveEnvironment();
                Game.screens().display(MainMenuScreen.NAME);
                return;
            }

            if (code == KeyEvent.VK_RIGHT) {
                nextMap();
                return;
            }

            if (code == KeyEvent.VK_LEFT) {
                prevMap();
                return;
            }

            if (player != null) {
                player.handleKeyPressed(code);
            }
        });

        Input.keyboard().onKeyReleased(e -> {
            if (player != null) {
                player.handleKeyReleased(e.getKeyCode());
            }
        });
    }

    @Override
    public void prepare() {
        super.prepare();
        discoverMapFiles();
        currentMapIndex = 0;
        // Tạo lại Player mới mỗi lần bấm Start để nhận đúng nhân vật & 2 vũ khí vừa chọn
        player = null;
        if (!ensurePlayerReady()) {
            return;
        }
        loadMapByIndex(currentMapIndex);
    }

    private void discoverMapFiles() {
        if (mapFiles == null) {
            mapFiles = new ArrayList<>();
        } else {
            mapFiles.clear();
        }
        Collections.addAll(mapFiles, DEFAULT_MAP_FILES);

        String[] roots = {
            "app/src/main/resources/maps",
            "src/main/resources/maps",
            "app/build/resources/main/maps",
            "build/resources/main/maps"
        };
        List<String> extraMaps = new ArrayList<>();
        for (String rootPath : roots) {
            File dir = new File(rootPath);
            if (!dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".tmx"));
            if (files == null) continue;
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                String fn = f.getName();
                if (!mapFiles.contains(fn) && !extraMaps.contains(fn)) {
                    extraMaps.add(fn);
                }
            }
        }
        mapFiles.addAll(extraMaps);
    }

    private boolean ensurePlayerReady() {
        if (player != null) {
            player.clearPressedKeys();
            if (player.isDead()) {
                player.revive();
            }
            return true;
        }

        try {
            GameSelection sel = GameSelection.get();
            PlayerSkin chosenSkin = resolveSkin(sel.getHeroId());
            List<Weapon> chosenWeapons = buildSelectedWeapons(
                    sel.getPrimaryWeaponId(),
                    sel.getSecondaryWeaponId()
            );
            player = new Player(0, 0, chosenSkin, chosenWeapons);
            return true;
        } catch (Throwable ex) {
            loadError = "Loi khoi tao Player: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
            System.err.println("[PlayScreen] " + loadError);
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public void suspend() {
        super.suspend();
        if (player != null) {
            player.clearPressedKeys();
        }
        unloadActiveEnvironment();
    }

    private static PlayerSkin resolveSkin(String heroId) {
        PlayerSkin[] skins = PlayerSkin.values();
        if (skins.length == 0) {
            return PlayerSkin.KNIGHT_01;
        }
        int idx = 0;
        if (heroId != null) {
            switch (heroId.toLowerCase(Locale.ROOT)) {
                case "hero1" -> idx = 0;
                case "hero2" -> idx = 1;
                case "hero3" -> idx = 2;
                case "hero4" -> idx = 3;
                default -> idx = 0;
            }
        }
        return skins[Math.min(idx, skins.length - 1)];
    }

    private static List<Weapon> buildSelectedWeapons(String primaryId, String secondaryId) {
        List<Weapon> weapons = new ArrayList<>();
        Weapon w1 = createWeaponById(primaryId);
        if (w1 != null) {
            weapons.add(w1);
        }
        if (secondaryId != null && !secondaryId.equalsIgnoreCase(primaryId)) {
            Weapon w2 = createWeaponById(secondaryId);
            if (w2 != null) {
                weapons.add(w2);
            }
        }
        if (weapons.isEmpty()) {
            weapons.add(new Sword(1));
        }
        return weapons;
    }

    private static Weapon createWeaponById(String weaponId) {
        if (weaponId == null) return new Sword(1);
        return switch (weaponId.toLowerCase(Locale.ROOT)) {
            case "spear" -> new Spear(1);
            case "bow" -> new Bow(1);
            case "staff" -> new Staff(1);
            default -> new Sword(1);
        };
    }

    private void nextMap() {
        if (mapFiles == null || mapFiles.isEmpty()) return;
        currentMapIndex = (currentMapIndex + 1) % mapFiles.size();
        loadMapByIndex(currentMapIndex);
    }

    private void prevMap() {
        if (mapFiles == null || mapFiles.isEmpty()) return;
        currentMapIndex = (currentMapIndex - 1 + mapFiles.size()) % mapFiles.size();
        loadMapByIndex(currentMapIndex);
    }

    private void unloadActiveEnvironment() {
        Environment activeEnv = Game.world().environment();
        if (activeEnv != null) {
            for (IEntity entity : new ArrayList<>(activeEnv.getEntities())) {
                if ((entity instanceof Enemy || entity instanceof Projectile) && entity instanceof IUpdateable up) {
                    Game.loop().detach(up);
                }
            }
        }
        RoomData.setCurrent(null);
        if (activeEnv != null) {
            Game.world().unloadEnvironment();
        }
    }

    private void loadMapByIndex(int index) {
        unloadActiveEnvironment();
        loadError = null;

        if (mapFiles == null || mapFiles.isEmpty()) {
            loadError = "Khong tim thay file map .tmx nao trong maps/";
            return;
        }
        if (index < 0 || index >= mapFiles.size()) {
            index = 0;
        }

        if (!ensurePlayerReady()) {
            return;
        }

        String mapFileName = mapFiles.get(index);
        String mapPath = "maps/" + mapFileName;
        try {
            IMap map = Resources.maps().get(mapPath);
            if (map == null) {
                loadError = "Khong tim thay file: " + mapPath;
                System.err.println("[PlayScreen] " + loadError);
                return;
            }

            mapWidth = map.getSizeInPixels().getWidth();
            mapHeight = map.getSizeInPixels().getHeight();

            Environment env = new Environment(map);
            List<Rectangle2D> wallRects = new ArrayList<>();

            double spawnX = mapWidth / 2.0 - player.getWidth() / 2.0;
            double spawnY = mapHeight / 2.0 - player.getHeight() / 2.0;

            for (IMapObjectLayer layer : map.getMapObjectLayers()) {
                for (IMapObject obj : layer.getMapObjects()) {
                    String name = (obj.getName() != null) ? obj.getName() : "";
                    if ("collision".equalsIgnoreCase(name) || name.startsWith("door_")) {
                        if (obj.getWidth() > 0 && obj.getHeight() > 0) {
                            CollisionBox box = new CollisionBox();
                            box.setLocation(obj.getX(), obj.getY());
                            box.setWidth(obj.getWidth());
                            box.setHeight(obj.getHeight());
                            box.setCollisionBoxWidth(obj.getWidth());
                            box.setCollisionBoxHeight(obj.getHeight());
                            box.setCollision(true);
                            env.add(box);
                            wallRects.add(new Rectangle2D.Double(obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight()));
                        }
                    } else if ("spawn_player".equalsIgnoreCase(name)) {
                        spawnX = obj.getX() - player.getWidth() / 2.0;
                        spawnY = obj.getY() - player.getHeight();
                    }
                }
            }

            MapHazardLoader.LoadResult hazards = MapHazardLoader.loadHazards(
                    mapFileName, env, player, wallRects
            );
            player.setMapPits(hazards.pits);

            player.setLocation(spawnX, spawnY);
            nudgeOutOfHazards(wallRects, hazards.pits);
            player.recordSafePosition();

            player.clearPressedKeys();
            if (player.isDead()) {
                player.revive();
            } else {
                player.triggerSpawn();
            }

            env.add(player);
            int enemyCount = spawnMapEnemies(mapPath, env);
            Game.world().loadEnvironment(env);

            if (Game.world().camera() != null) {
                Game.world().camera().setClampToMap(false);
                updateCamera();
            }

            System.out.println("[PlayScreen] Loaded " + mapPath
                    + " (" + (int) mapWidth + "x" + (int) mapHeight
                    + "), colliders=" + wallRects.size()
                    + ", traps=" + hazards.trapCount
                    + ", pits=" + hazards.pits.size()
                    + ", enemies=" + enemyCount);
        } catch (Exception ex) {
            loadError = "Loi nap " + mapPath + ": " + ex.getMessage();
            ex.printStackTrace();
        }
    }

    private int spawnMapEnemies(String mapPath, Environment env) {
        try {
            GamePlayScreen.loadAllMonsterSprites();
            RangedEnemy.DISTANCE_SCALE = 0.35;
            RangedEnemy.SPEED_SCALE = 0.5;

            RoomData room = RoomData.load("/" + mapPath);
            RoomData.setCurrent(room);

            int spawned = 0;
            int unnamedIdx = 0;
            for (RoomData.Spawn s : room.getEnemySpawns()) {
                boolean hasExplicit = s.monster != null && !s.monster.trim().isEmpty();
                MonsterType type = hasExplicit
                        ? EnemyFactory.typeOrDefault(s.monster, MonsterType.ORC_WARRIOR)
                        : DEFAULT_ENEMY_ROTATION[unnamedIdx++ % DEFAULT_ENEMY_ROTATION.length];
                boolean elite = "elite".equalsIgnoreCase(s.variant);

                try {
                    Enemy enemy = EnemyFactory.create(type, elite);
                    int scaledHp = (type.getRole() == MonsterType.Role.RANGED)
                            ? Math.max(12, (int) Math.round(enemy.getMaxHealth() * 0.28))
                            : enemy.getMaxHealth();
                    int scaledDmg = Math.max(1, (int) Math.round(enemy.getDamage() / 5.0));
                    enemy.scaleForMap(scaledHp, scaledDmg);
                    enemy.placeFeetAt(s.x, s.y);
                    env.add(enemy);
                    spawned++;
                } catch (UnsupportedOperationException ex) {
                    System.err.println("[PlayScreen] Bo qua quai " + type + ": " + ex.getMessage());
                }
            }
            return spawned;
        } catch (Exception ex) {
            System.err.println("[PlayScreen] Loi spawn quai cho " + mapPath + ": " + ex.getMessage());
            return 0;
        }
    }

    private void nudgeOutOfHazards(List<Rectangle2D> wallRects, List<Rectangle2D> pitRects) {
        double centerX = mapWidth / 2.0;
        double centerY = mapHeight / 2.0;

        for (int attempt = 0; attempt < 24; attempt++) {
            boolean blocked = false;
            for (Rectangle2D wall : wallRects) {
                if (player.getHitbox().intersectsFoot(wall)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                for (Rectangle2D pit : pitRects) {
                    if (player.getHitbox().intersectsFoot(pit)) {
                        blocked = true;
                        break;
                    }
                }
            }
            if (!blocked) return;

            double vx = centerX - (player.getX() + player.getWidth() / 2.0);
            double vy = centerY - (player.getY() + player.getHeight() / 2.0);
            double len = Math.hypot(vx, vy);
            if (len < 0.001) return;

            player.setLocation(player.getX() + (vx / len) * 4.0, player.getY() + (vy / len) * 4.0);
        }
    }

    private void updateCamera() {
        if (Game.world().camera() == null || Game.window().getResolution() == null) return;

        double resW = Game.window().getResolution().getWidth();
        double resH = Game.window().getResolution().getHeight();
        if (resW <= 0 || resH <= 0 || mapWidth <= 0 || mapHeight <= 0) return;

        double targetRenderScale = Math.min(resW / mapWidth, resH / mapHeight);

        float curZoom = Game.world().camera().getZoom();
        float curRenderScale = Game.world().camera().getRenderScale();
        float baseScale = (curZoom > 0.0001f && curRenderScale > 0.0001f)
                ? (curRenderScale / curZoom)
                : Game.graphics().getBaseRenderScale();
        if (baseScale <= 0.0001f) {
            baseScale = 1.0f;
        }

        float finalZoom = (float) (targetRenderScale / baseScale);
        Game.world().camera().setZoom(finalZoom, 0);
        Game.world().camera().setFocus(mapWidth / 2.0, mapHeight / 2.0);
        Game.world().camera().updateFocus();
    }

    @Override
    public void render(Graphics2D g) {
        Viewport v = Viewport.current();

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, (int) Math.ceil(v.w), (int) Math.ceil(v.h));

        if (loadError == null) {
            updateCamera();
            super.render(g);
        } else {
            g.setColor(Color.RED);
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            g.drawString(loadError, 24, 90);
        }

        drawTopOverlay(g, v);

        if (player != null) {
            drawHud(g, v);
            if (player.isDead()) {
                drawLoseOverlay(g, v);
            }
        }
    }

    private void drawTopOverlay(Graphics2D g, Viewport v) {
        double k = v.k;
        int pad = (int) Math.round(14 * k);
        int topY = (int) Math.round(v.offsetY + 14 * k);

        Font labelFont = (uiFont != null)
                ? uiFont.deriveFont(Font.BOLD, (float) Math.max(12, 13 * k))
                : new Font("Monospaced", Font.BOLD, (int) Math.max(12, 13 * k));
        g.setFont(labelFont);
        FontMetrics fm = g.getFontMetrics();
        int barH = (int) Math.round(34 * k);

        String mapInfo = (mapFiles == null || mapFiles.isEmpty())
                ? "[0/0] None"
                : String.format("[%d/%d] %s",
                        currentMapIndex + 1, mapFiles.size(), mapFiles.get(currentMapIndex));
        int infoW = fm.stringWidth(mapInfo) + (int) Math.round(24 * k);
        int infoX = (int) Math.round((v.w - infoW) / 2.0);

        g.setColor(new Color(20, 18, 26, 210));
        g.fillRect(infoX, topY, infoW, barH);
        g.setColor(new Color(145, 130, 105));
        g.setStroke(new BasicStroke((float) Math.max(1.5, 2.0 * k)));
        g.drawRect(infoX, topY, infoW, barH);

        g.setColor(new Color(240, 230, 200));
        int infoTextY = topY + (barH - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(mapInfo, infoX + (int) Math.round(12 * k), infoTextY);

        String btnText = "NEXT MAP >";
        int btnW = Math.max((int) Math.round(144 * k), fm.stringWidth(btnText) + (int) Math.round(28 * k));
        int btnX = (int) Math.round(v.w) - pad - btnW;
        nextMapButton.setRect(btnX, topY, btnW, barH);

        Point2D mouse = Input.mouse().getLocation();
        boolean hovered = (mouse != null && nextMapButton.contains(mouse));

        g.setColor(hovered ? new Color(180, 115, 45, 240) : new Color(42, 36, 52, 230));
        g.fillRect(btnX, topY, btnW, barH);
        g.setColor(hovered ? new Color(255, 225, 130) : new Color(165, 145, 115));
        g.drawRect(btnX, topY, btnW, barH);

        g.setColor(hovered ? Color.WHITE : new Color(240, 225, 195));
        int btnTextX = btnX + (btnW - fm.stringWidth(btnText)) / 2;
        int btnTextY = topY + (barH - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(btnText, btnTextX, btnTextY);
    }

    private void drawHud(Graphics2D g, Viewport v) {
        double k = v.k;
        int padX = (int) Math.round(14 * k);
        int padY = (int) Math.round(v.offsetY + 14 * k);
        int boxW = (int) Math.round(205 * k);
        int boxH = (int) Math.round(86 * k);

        g.setColor(new Color(24, 20, 28, 220));
        g.fillRect(padX, padY, boxW, boxH);
        g.setColor(new Color(140, 125, 100));
        g.drawRect(padX, padY, boxW, boxH);

        int barX = padX + (int) Math.round(12 * k);
        int barW = boxW - (int) Math.round(24 * k);
        int barH = (int) Math.round(16 * k);

        g.setFont(new Font("Monospaced", Font.BOLD, (int) Math.max(11, 12 * k)));

        int hpY = padY + (int) Math.round(10 * k);
        drawStatBar(g, barX, hpY, barW, barH, player.getHp(), player.getMaxHp(),
                new Color(210, 45, 45), "HP: " + player.getHp() + "/" + player.getMaxHp());

        int armorY = hpY + (int) Math.round(24 * k);
        drawStatBar(g, barX, armorY, barW, barH, player.getArmor(), player.getMaxArmor(),
                new Color(155, 175, 195), "ARMOR: " + player.getArmor() + "/" + player.getMaxArmor());

        int manaY = armorY + (int) Math.round(24 * k);
        drawStatBar(g, barX, manaY, barW, barH, player.getMana(), player.getMaxMana(),
                new Color(45, 125, 230), "MANA: " + player.getMana() + "/" + player.getMaxMana());

        long dashCd = player.getDashCooldownRemainingMs();
        String dashText = (dashCd == 0) ? "OK" : (dashCd + "ms");
        String weaponText = "None";
        String skillText = "OK";
        if (player.getCurrentWeapon() != null) {
            weaponText = player.getCurrentWeapon().getName();
            long skillCd = player.getCurrentWeapon().getSkillCooldownRemainingMs();
            skillText = (skillCd == 0) ? "OK" : (skillCd + "ms");
        }
        String statusLine = player.getSkin().getDisplayName()
                + " | [" + (player.getCurrentWeaponIndex() + 1) + "/" + player.getWeaponArsenal().size() + "] "
                + weaponText + " (1/2,Q:DoiVK, V:Up)"
                + " | J:Danh | U:Skill(" + skillText + ")"
                + " | K:Dash(" + dashText + ")";

        int bottomH = (int) Math.round(26 * k);
        int bottomY = (int) Math.round(v.h) - bottomH - (int) Math.round(8 * k);
        g.setColor(new Color(18, 16, 22, 195));
        g.fillRect(padX, bottomY, (int) Math.round(v.w) - padX * 2, bottomH);
        g.setColor(new Color(235, 230, 215));
        g.drawString(statusLine, padX + (int) Math.round(10 * k), bottomY + (int) Math.round(17 * k));

        String notice = player.getActiveNotice();
        if (!notice.isEmpty()) {
            g.setColor(new Color(255, 225, 120));
            g.drawString(notice, padX, padY + boxH + (int) Math.round(18 * k));
        }
    }

    private void drawStatBar(Graphics2D g, int x, int y, int w, int h, int val, int max, Color color, String label) {
        g.setColor(new Color(45, 40, 50));
        g.fillRect(x, y, w, h);

        int fillW = (max > 0) ? (int) Math.round((double) val / max * w) : 0;
        g.setColor(color);
        g.fillRect(x, y, fillW, h);

        g.setColor(new Color(15, 10, 15));
        g.drawRect(x, y, w, h);

        g.setColor(Color.WHITE);
        g.drawString(label, x + 6, y + h - 4);
    }

    private void drawLoseOverlay(Graphics2D g, Viewport v) {
        double k = v.k;
        int screenW = (int) Math.round(v.w);
        int centerY = (int) Math.round(v.h * 0.44);

        g.setColor(new Color(0, 0, 0, 130));
        g.fillRect(0, 0, screenW, (int) Math.ceil(v.h));

        Font loseFont;
        try {
            loseFont = Resources.fonts().get("font/DungeonFont.ttf").deriveFont((float) (84 * k));
        } catch (Exception e) {
            loseFont = new Font("Monospaced", Font.BOLD, (int) Math.max(36, 72 * k));
        }

        g.setFont(loseFont);
        FontMetrics fm = g.getFontMetrics();
        String msg = "YOU DIED";
        int tx = (screenW - fm.stringWidth(msg)) / 2;
        int sh = Math.max(2, (int) Math.round(4 * k));

        g.setColor(new Color(20, 5, 5));
        g.drawString(msg, tx + sh, centerY + sh);
        g.setColor(new Color(235, 50, 50));
        g.drawString(msg, tx, centerY);

        g.setFont(new Font("Monospaced", Font.BOLD, (int) Math.max(12, 16 * k)));
        String subMsg = "An [R] de hoi sinh  |  An [ESC] de ve Menu";
        int subX = (screenW - g.getFontMetrics().stringWidth(subMsg)) / 2;
        g.setColor(new Color(230, 210, 170));
        g.drawString(subMsg, subX, centerY + (int) Math.round(45 * k));
    }
}