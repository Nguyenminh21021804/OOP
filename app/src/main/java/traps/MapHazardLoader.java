package traps;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.entities.CollisionBox;
import de.gurkenlabs.litiengine.entities.Entity;
import de.gurkenlabs.litiengine.environment.Environment;
import de.gurkenlabs.litiengine.graphics.IRenderable;
import de.gurkenlabs.litiengine.graphics.RenderType;
import de.gurkenlabs.litiengine.resources.Resources;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import player.Player;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class MapHazardLoader {

    private static final long FLIP_H_BIT = 0x80000000L;
    private static final long FLIP_V_BIT = 0x40000000L;
    private static final long FLIP_MASK = 0x0FFFFFFFL;

    /** Sát thương mặc định mỗi lần dính bẫy gai / bẫy lửa: trừ đúng 1 Giáp (hết Giáp mới trừ 1 HP) */
    private static final int DEFAULT_TRAP_DAMAGE = 1;

    private static final Pattern TRAILING_INDEX_PATTERN = Pattern.compile("^(.*?[_\\-]?)(\\d+)(\\.png)$", Pattern.CASE_INSENSITIVE);

    private enum TileKind {
        TRANSPARENT,
        DECAL,
        FLAT_DARK_PIT,
        SOLID_FLOOR
    }

    public static class LoadResult {
        public final List<Rectangle2D> pits = new ArrayList<>();
        public int trapCount = 0;
        public int objectCount = 0;
        public int pickupCount = 0;
        public String pitSource = "none";
    }

    private static class TilesetRef {
        int firstGid;
        TilesetData data;
    }

    private static class TilesetData {
        int tileWidth = 16;
        int tileHeight = 16;
        int spacing = 0;
        int margin = 0;
        int columns = 1;
        BufferedImage atlasImage;
        Map<Integer, BufferedImage> tileImages = new HashMap<>();
        Map<Integer, String> tileImagePaths = new HashMap<>();
        Map<Integer, int[]> animTileIds = new HashMap<>();
        Map<Integer, int[]> animDurations = new HashMap<>();
    }

    private static class TemplateData {
        TilesetRef tileset;
        String name = "";
        String type = "";
        int gid = 0;
        boolean flipH = false;
        boolean flipV = false;
        double width = 0;
        double height = 0;
        double rotation = 0.0;
        Map<String, String> properties = new HashMap<>();
    }

    private static class ParsedTileLayer {
        String name;
        boolean visible = true;
        int width;
        int height;
        int[] gids;
    }

    private static final Map<String, TilesetData> TSX_CACHE = new HashMap<>();
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    private static boolean resourceIndexBuilt = false;
    private static final Map<String, File> FILES_BY_NAME = new HashMap<>();

    private MapHazardLoader() {
    }

    public static LoadResult loadHazards(
            String mapFileName,
            Environment env,
            Player player,
            List<Rectangle2D> wallRects
    ) {
        LoadResult result = new LoadResult();
        if (env == null || mapFileName == null) return result;

        ensureResourceIndex();

        String mapPath = "maps/" + mapFileName;
        Document doc = parseXml(mapPath);
        if (doc == null) {
            System.err.println("[MapHazardLoader] Khong doc duoc XML cua: " + mapPath);
            return result;
        }

        Element mapEl = doc.getDocumentElement();
        int mapCols = parseInt(mapEl.getAttribute("width"), 22);
        int mapRows = parseInt(mapEl.getAttribute("height"), 13);
        int tileW = parseInt(mapEl.getAttribute("tilewidth"), 16);
        int tileH = parseInt(mapEl.getAttribute("tileheight"), 16);

        List<TilesetRef> mapTilesets = new ArrayList<>();
        NodeList tsNodes = mapEl.getElementsByTagName("tileset");
        for (int i = 0; i < tsNodes.getLength(); i++) {
            Element tsEl = (Element) tsNodes.item(i);
            if (tsEl.getParentNode() != mapEl) continue;
            mapTilesets.add(parseTilesetRef(tsEl, "maps/"));
        }
        mapTilesets.sort((a, b) -> Integer.compare(b.firstGid, a.firstGid));

        List<ParsedTileLayer> tileLayers = new ArrayList<>();
        List<String> layerNames = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();

        NodeList allLayers = mapEl.getElementsByTagName("layer");
        for (int i = 0; i < allLayers.getLength(); i++) {
            Element el = (Element) allLayers.item(i);
            ParsedTileLayer tl = parseTileLayer(el, mapCols, mapRows);
            if (tl != null) {
                tileLayers.add(tl);
                layerNames.add(tl.name);
            }
        }

        NodeList allGroups = mapEl.getElementsByTagName("objectgroup");
        for (int i = 0; i < allGroups.getLength(); i++) {
            Element el = (Element) allGroups.item(i);
            if ("0".equals(el.getAttribute("visible"))) continue;
            groupNames.add(el.getAttribute("name"));
            processObjectGroup(el, "maps/", mapTilesets, env, player, wallRects, result);
        }

        spawnExplicitTrapTileLayers(tileLayers, mapTilesets, tileW, tileH, env, player, result);

        if (result.pits.isEmpty()) {
            for (ParsedTileLayer tl : tileLayers) {
                if (!tl.visible) continue;
                if (isPitKeyword(tl.name)) {
                    for (int r = 0; r < tl.height; r++) {
                        for (int c = 0; c < tl.width; c++) {
                            if (tl.gids[r * tl.width + c] != 0) {
                                addPitCellIfNotWall(result.pits, c * tileW, r * tileH, tileW, tileH, wallRects);
                            }
                        }
                    }
                    if (!result.pits.isEmpty()) {
                        result.pitSource = "tile_layer:" + tl.name;
                        break;
                    }
                }
            }
        }

        if (result.pits.isEmpty()) {
            detectPitsTopDown(tileLayers, mapTilesets, mapCols, mapRows, tileW, tileH, wallRects, result);
        }

        if (!result.pits.isEmpty()) {
            env.add(new PitDebugOverlayEntity(result.pits));
        }

        System.out.println("[MapHazardLoader] " + mapFileName
                + " | layers=" + layerNames
                + " | objGroups=" + groupNames
                + " | objects=" + result.objectCount
                + " | pickups=" + result.pickupCount
                + " | traps=" + result.trapCount
                + " | pits=" + result.pits.size()
                + " (" + result.pitSource + ")");
        return result;
    }

    // ==================== XỬ LÝ OBJECT LAYER ====================

    private static void processObjectGroup(
            Element groupEl,
            String baseDir,
            List<TilesetRef> mapTilesets,
            Environment env,
            Player player,
            List<Rectangle2D> wallRects,
            LoadResult result
    ) {
        String layerName = groupEl.getAttribute("name");
        NodeList objNodes = groupEl.getElementsByTagName("object");

        for (int i = 0; i < objNodes.getLength(); i++) {
            Element objEl = (Element) objNodes.item(i);
            if (objEl.getParentNode() != groupEl) continue;
            if ("0".equals(objEl.getAttribute("visible"))) continue;

            String templateAttr = objEl.getAttribute("template");
            TemplateData tpl = null;
            if (templateAttr != null && !templateAttr.isEmpty()) {
                tpl = loadTemplate(normalizePath(baseDir, decodeUrlPath(templateAttr)));
            }

            String name = firstNonEmpty(objEl.getAttribute("name"), tpl != null ? tpl.name : "");
            String type = firstNonEmpty(
                    firstNonEmpty(objEl.getAttribute("type"), objEl.getAttribute("class")),
                    tpl != null ? tpl.type : ""
            );

            long rawGidLong = parseRawGidLong(objEl.getAttribute("gid"));
            int rawGid = (int) (rawGidLong & FLIP_MASK);
            boolean flipH = (rawGidLong & FLIP_H_BIT) != 0;
            boolean flipV = (rawGidLong & FLIP_V_BIT) != 0;
            boolean usedTemplateGid = false;

            if (rawGid == 0 && tpl != null && tpl.gid != 0) {
                rawGid = tpl.gid;
                flipH = tpl.flipH;
                flipV = tpl.flipV;
                usedTemplateGid = true;
            }

            double x = parseDouble(objEl.getAttribute("x"), 0.0);
            double y = parseDouble(objEl.getAttribute("y"), 0.0);
            double w = parseDouble(objEl.getAttribute("width"), tpl != null ? tpl.width : 0.0);
            double h = parseDouble(objEl.getAttribute("height"), tpl != null ? tpl.height : 0.0);
            double rotation = parseDouble(objEl.getAttribute("rotation"), tpl != null ? tpl.rotation : 0.0);

            Map<String, String> props = new HashMap<>();
            if (tpl != null) {
                props.putAll(tpl.properties);
            }
            readProperties(objEl, props);

            String lowName = name.toLowerCase(Locale.ROOT);
            if (lowName.startsWith("spawn_")) {
                continue;
            }

            if (tpl != null && ("collision".equals(lowName) || lowName.startsWith("door_"))) {
                double bw = (w > 0) ? w : 16.0;
                double bh = (h > 0) ? h : 16.0;
                double by = (rawGid != 0) ? (y - bh) : y;
                addCollisionIfMissing(env, wallRects, x, by, bw, bh);
            }

            String objIdentity = (templateAttr + " " + name + " " + type).toLowerCase(Locale.ROOT);

            if (rawGid == 0 && (isPitKeyword(objIdentity) || isPitKeyword(layerName))) {
                if (w >= 4.0 && h >= 4.0) {
                    result.pits.add(new Rectangle2D.Double(x, y, w, h));
                    result.pitSource = "object_layer:" + layerName;
                }
                continue;
            }

            boolean explicitTrap = isExplicitTrapObject(objIdentity)
                    || (isTrapLayerName(layerName) && !isDecorativeOrStructure(objIdentity));

            if (rawGid == 0 && !explicitTrap) {
                continue;
            }

            TilesetRef ref = usedTemplateGid && tpl != null
                    ? tpl.tileset
                    : findTilesetForGid(mapTilesets, rawGid);

            String combined = (layerName + " " + objIdentity).toLowerCase(Locale.ROOT);
            FloorTrapEntity.TrapKind trapKind = explicitTrap ? resolveTrapKind(combined, props) : null;

            List<BufferedImage> frameList = new ArrayList<>();
            List<Integer> durList = new ArrayList<>();
            if (rawGid > 0 && ref != null && ref.data != null) {
                extractTileAnimation(ref.data, rawGid - ref.firstGid, frameList, durList, trapKind);
            }

            if (explicitTrap) {
                if (w <= 0) w = !frameList.isEmpty() ? frameList.get(0).getWidth() : 16.0;
                if (h <= 0) h = !frameList.isEmpty() ? frameList.get(0).getHeight() : 16.0;
                boolean anchorAtBottom = (rawGid != 0);

                int damage = Math.max(1, parseInt(props.get("damage"), DEFAULT_TRAP_DAMAGE));
                long delayMs = parseDelayMs(props);
                String tag = firstNonEmpty(templateAttr, firstNonEmpty(name, layerName));

                env.add(new FloorTrapEntity(
                        player, trapKind, tag, x, y, anchorAtBottom, w, h,
                        frameList.toArray(new BufferedImage[0]),
                        toIntArray(durList), flipH, flipV, rotation, damage, delayMs, true
                ));
                result.trapCount++;
                continue;
            }

            if (w <= 0) w = !frameList.isEmpty() ? frameList.get(0).getWidth() : 16.0;
            if (h <= 0) h = !frameList.isEmpty() ? frameList.get(0).getHeight() : 16.0;
            double topY = y - h;

            if (isPitKeyword(objIdentity)) {
                result.pits.add(new Rectangle2D.Double(x, topY, w, h));
                result.pitSource = "object_layer:" + layerName;
                continue;
            }

            String tileImagePath = (rawGid > 0 && ref != null && ref.data != null)
                    ? ref.data.tileImagePaths.get(rawGid - ref.firstGid)
                    : null;
            PotionPickupEntity.PotionKind potionKind = resolvePotionKind(name, type, templateAttr, props, tileImagePath);
            if (potionKind != null && !frameList.isEmpty()) {
                int potionAmount = resolvePotionAmount(potionKind, props, tileImagePath);
                env.add(new PotionPickupEntity(
                        env, player, potionKind, potionAmount,
                        x, y, w, h,
                        frameList.toArray(new BufferedImage[0]),
                        toIntArray(durList),
                        flipH, flipV, rotation
                ));
                result.pickupCount++;
                continue;
            }

            if (!frameList.isEmpty()) {
                RenderType rt = isGroundDecorLayer(layerName, objIdentity)
                        ? RenderType.SURFACE
                        : RenderType.NORMAL;
                env.add(new TiledObjectEntity(
                        x, y, w, h,
                        frameList.toArray(new BufferedImage[0]),
                        toIntArray(durList),
                        flipH, flipV, rotation, rt
                ));
                result.objectCount++;
            }
        }
    }

    private static void addCollisionIfMissing(
            Environment env,
            List<Rectangle2D> wallRects,
            double x,
            double y,
            double w,
            double h
    ) {
        if (w <= 0 || h <= 0) return;
        for (Rectangle2D existing : wallRects) {
            if (Math.abs(existing.getX() - x) < 1.0
                    && Math.abs(existing.getY() - y) < 1.0
                    && Math.abs(existing.getWidth() - w) < 1.0
                    && Math.abs(existing.getHeight() - h) < 1.0) {
                return;
            }
        }
        CollisionBox box = new CollisionBox();
        box.setLocation(x, y);
        box.setWidth(w);
        box.setHeight(h);
        box.setCollisionBoxWidth(w);
        box.setCollisionBoxHeight(h);
        box.setCollision(true);
        env.add(box);
        wallRects.add(new Rectangle2D.Double(x, y, w, h));
    }

    private static void spawnExplicitTrapTileLayers(
            List<ParsedTileLayer> tileLayers,
            List<TilesetRef> mapTilesets,
            int tileW,
            int tileH,
            Environment env,
            Player player,
            LoadResult result
    ) {
        if (result.trapCount > 0) return;

        for (ParsedTileLayer tl : tileLayers) {
            if (!tl.visible || !isTrapLayerName(tl.name)) continue;

            for (int r = 0; r < tl.height; r++) {
                for (int c = 0; c < tl.width; c++) {
                    int gid = tl.gids[r * tl.width + c];
                    if (gid == 0) continue;

                    // Không biến gạch hố phẳng màu tối trên layer trap thành bẫy gai
                    BufferedImage tileImg = getSingleTileImage(mapTilesets, gid);
                    if (classifyTile(tileImg) == TileKind.FLAT_DARK_PIT) {
                        continue;
                    }

                    FloorTrapEntity.TrapKind kind = resolveTrapKind(tl.name.toLowerCase(Locale.ROOT), null);
                    TilesetRef ref = findTilesetForGid(mapTilesets, gid);
                    List<BufferedImage> frameList = new ArrayList<>();
                    List<Integer> durList = new ArrayList<>();
                    if (ref != null && ref.data != null) {
                        extractTileAnimation(ref.data, gid - ref.firstGid, frameList, durList, kind);
                    }

                    // Tile Layer đã được LITIengine MapRenderer tự vẽ đúng đồ họa gốc của map
                    boolean drawOverTile = frameList.size() > 1;
                    env.add(new FloorTrapEntity(
                            player, kind, "layer:" + tl.name,
                            c * tileW, r * tileH, false, tileW, tileH,
                            frameList.toArray(new BufferedImage[0]),
                            toIntArray(durList), false, false, 0.0, DEFAULT_TRAP_DAMAGE, 0L, drawOverTile
                    ));
                    result.trapCount++;
                }
            }
        }
    }

    // ==================== NHẬN DIỆN TỪ KHÓA ====================

    private static boolean isExplicitTrapObject(String s) {
        if (s == null || s.isEmpty()) return false;
        String low = s.toLowerCase(Locale.ROOT);
        if (isDecorativeOrStructure(low) && !low.contains("trap") && !low.contains("spike") && !low.contains("peak")) {
            return false;
        }
        return low.contains("spike") || low.contains("peak") || low.contains("gai")
                || low.contains("fire_cycle") || low.contains("fire_perm")
                || low.contains("fire_trap") || low.contains("flame_trap")
                || low.contains("flamethrower") || low.contains("floor_trap")
                || low.contains("bay_gai") || low.contains("bay_lua");
    }

    private static boolean isTrapLayerName(String layerName) {
        if (layerName == null || layerName.isEmpty()) return false;
        String low = layerName.toLowerCase(Locale.ROOT);
        return low.contains("trap") || low.contains("spike") || low.contains("bay");
    }

    private static boolean isDecorativeOrStructure(String s) {
        if (s == null) return false;
        String low = s.toLowerCase(Locale.ROOT);
        return low.contains("wall") || low.contains("tuong") || low.contains("pillar") || low.contains("column")
                || low.contains("cot") || low.contains("collision") || low.contains("door")
                || low.contains("torch") || low.contains("brazier") || low.contains("candle")
                || low.contains("banner") || low.contains("chest") || low.contains("potion")
                || low.contains("flask") || low.contains("crate") || low.contains("barrel")
                || low.contains("skull") || low.contains("key") || low.contains("coin");
    }

    private static boolean isGroundDecorLayer(String layerName, String objIdentity) {
        String low = (layerName + " " + objIdentity).toLowerCase(Locale.ROOT);
        return low.contains("floor") || low.contains("ground") || low.contains("carpet")
                || low.contains("rug") || low.contains("decal") || low.contains("shadow")
                || low.contains("under");
    }

    private static FloorTrapEntity.TrapKind resolveTrapKind(String combined, Map<String, String> props) {
        String mode = (props != null) ? firstNonEmpty(props.get("mode"), "").toLowerCase(Locale.ROOT) : "";
        boolean permanent = "permanent".equals(mode)
                || "const".equals(mode)
                || "static".equals(mode)
                || combined.contains("perm")
                || combined.contains("const")
                || combined.contains("static");
        if (combined.contains("fire") || combined.contains("flame") || combined.contains("lua")) {
            return permanent ? FloorTrapEntity.TrapKind.FIRE_PERMANENT : FloorTrapEntity.TrapKind.FIRE_CYCLE;
        }
        return permanent ? FloorTrapEntity.TrapKind.SPIKE_PERMANENT : FloorTrapEntity.TrapKind.SPIKE_CYCLE;
    }

    private static long parseDelayMs(Map<String, String> props) {
        for (String key : new String[] { "delay", "offset", "offset_ms", "phase", "startDelay", "start_delay", "startdelay" }) {
            String val = props.get(key);
            if (val != null && !val.isEmpty()) {
                double d = parseDouble(val, 0.0);
                return (d > 0 && d < 20.0) ? Math.round(d * 1000.0) : Math.round(d);
            }
        }
        return 0L;
    }

    private static boolean isPitKeyword(String s) {
        if (s == null) return false;
        String low = s.toLowerCase(Locale.ROOT).trim();
        return low.contains("pit") || low.contains("hole") || low.contains("void")
                || low.contains("vuc") || low.contains("vực") || low.contains("abyss")
                || low.contains("chasm") || low.contains("fall") || low.contains("death_zone")
                || low.contains("kill") || low.contains("ho_bay") || low.contains("ho_den")
                || low.contains("hố") || low.equals("ho");
    }

    private static PotionPickupEntity.PotionKind resolvePotionKind(
            String name,
            String type,
            String templateAttr,
            Map<String, String> props,
            String tileImagePath
    ) {
        String propKind = (props != null) ? firstNonEmpty(props.get("kind"), "").trim().toLowerCase(Locale.ROOT) : "";
        if ("hp".equals(propKind) || "heal".equals(propKind) || "health".equals(propKind)) {
            return PotionPickupEntity.PotionKind.HEALTH;
        }
        if ("mp".equals(propKind) || "mana".equals(propKind)) {
            return PotionPickupEntity.PotionKind.MANA;
        }

        String tileLow = (tileImagePath != null) ? tileImagePath.toLowerCase(Locale.ROOT) : "";
        if (tileLow.contains("flasks_1_") || tileLow.contains("flasks_4_")) {
            return PotionPickupEntity.PotionKind.HEALTH;
        }
        if (tileLow.contains("flasks_2_") || tileLow.contains("flasks_3_")) {
            return PotionPickupEntity.PotionKind.MANA;
        }

        String combined = (firstNonEmpty(templateAttr, "") + " " + firstNonEmpty(name, "") + " " + firstNonEmpty(type, ""))
                .toLowerCase(Locale.ROOT);
        boolean looksLikePotion = combined.contains("potion") || combined.contains("flask")
                || combined.contains("elixir") || "pickup".equalsIgnoreCase(type);
        if (!looksLikePotion) return null;

        for (String token : combined.split("[^a-z0-9]+")) {
            if ("hp".equals(token) || "heal".equals(token) || "health".equals(token)) {
                return PotionPickupEntity.PotionKind.HEALTH;
            }
            if ("mp".equals(token) || "mana".equals(token)) {
                return PotionPickupEntity.PotionKind.MANA;
            }
        }
        return null;
    }

    private static int resolvePotionAmount(
            PotionPickupEntity.PotionKind kind,
            Map<String, String> props,
            String tileImagePath
    ) {
        String tileLow = (tileImagePath != null) ? tileImagePath.toLowerCase(Locale.ROOT) : "";
        boolean isLargeFlask = tileLow.contains("flasks_1_") || tileLow.contains("flasks_3_");
        int propAmount = (props != null) ? parseInt(props.get("amount"), 0) : 0;

        if (kind == PotionPickupEntity.PotionKind.HEALTH) {
            if (propAmount >= 1 && propAmount <= 6) {
                return propAmount;
            }
            return isLargeFlask ? 3 : 2;
        }

        if (propAmount > 0) {
            return propAmount;
        }
        return isLargeFlask ? 50 : 30;
    }

    // ==================== TỰ DÒ HỐ BẪY TRÊN TẤT CẢ CÁC LAYER HIỂN THỊ ====================

    private static void detectPitsTopDown(
            List<ParsedTileLayer> tileLayers,
            List<TilesetRef> mapTilesets,
            int mapCols,
            int mapRows,
            int tileW,
            int tileH,
            List<Rectangle2D> wallRects,
            LoadResult result
    ) {
        if (tileLayers.isEmpty() || mapCols <= 2 || mapRows <= 2) return;

        Map<Integer, TileKind> tileCache = new HashMap<>();
        // 0 = ô rỗng (không có gạch / hoàn toàn trong suốt)
        // 1 = ô hố tối phẳng đồng màu (xám đen #222222 hoặc tím sẫm #25131a)
        // 2 = ô sàn đá / tường / gạch có họa tiết
        int[][] cellState = new int[mapRows][mapCols];
        boolean[][] isWallCell = new boolean[mapRows][mapCols];

        for (int r = 0; r < mapRows; r++) {
            for (int c = 0; c < mapCols; c++) {
                double cx = c * tileW + tileW / 2.0;
                double cy = r * tileH + tileH / 2.0;
                for (Rectangle2D wall : wallRects) {
                    if (wall.contains(cx, cy)) {
                        isWallCell[r][c] = true;
                        break;
                    }
                }

                boolean hasVisibleDecal = false;
                int resolved = -1;

                // Quét mọi Tile Layer đang hiển thị từ TRÊN xuống DƯỚI, không bỏ sót layer tường hay layer trap
                for (int i = tileLayers.size() - 1; i >= 0; i--) {
                    ParsedTileLayer tl = tileLayers.get(i);
                    if (!tl.visible) continue;
                    if (c >= tl.width || r >= tl.height) continue;

                    int gid = tl.gids[r * tl.width + c];
                    if (gid == 0) continue;

                    TileKind kind = tileCache.computeIfAbsent(gid, g -> classifyTile(getSingleTileImage(mapTilesets, g)));
                    if (kind == TileKind.TRANSPARENT) {
                        continue;
                    } else if (kind == TileKind.DECAL) {
                        hasVisibleDecal = true;
                        continue;
                    } else if (kind == TileKind.FLAT_DARK_PIT) {
                        resolved = 1;
                        break;
                    } else {
                        resolved = 2;
                        break;
                    }
                }

                if (resolved != -1) {
                    cellState[r][c] = resolved;
                } else if (isWallCell[r][c] || hasVisibleDecal) {
                    // Ô chỉ có viền tường mỏng hoặc va chạm tường -> khóa kín biên ngoài
                    cellState[r][c] = 2;
                } else {
                    cellState[r][c] = 0;
                }
            }
        }

        // Loang từ 4 cạnh ngoài bản đồ CHỈ qua các ô rỗng tuyệt đối (cellState == 0) và không vướng wallRects
        boolean[][] outsideVoid = new boolean[mapRows][mapCols];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int c = 0; c < mapCols; c++) {
            enqueueOutside(cellState, isWallCell, outsideVoid, queue, c, 0, mapCols);
            enqueueOutside(cellState, isWallCell, outsideVoid, queue, c, mapRows - 1, mapCols);
        }
        for (int r = 0; r < mapRows; r++) {
            enqueueOutside(cellState, isWallCell, outsideVoid, queue, 0, r, mapCols);
            enqueueOutside(cellState, isWallCell, outsideVoid, queue, mapCols - 1, r, mapCols);
        }

        while (!queue.isEmpty()) {
            int pos = queue.poll();
            int c = pos % mapCols;
            int r = pos / mapCols;
            if (c > 0) enqueueOutside(cellState, isWallCell, outsideVoid, queue, c - 1, r, mapCols);
            if (c + 1 < mapCols) enqueueOutside(cellState, isWallCell, outsideVoid, queue, c + 1, r, mapCols);
            if (r > 0) enqueueOutside(cellState, isWallCell, outsideVoid, queue, c, r - 1, mapCols);
            if (r + 1 < mapRows) enqueueOutside(cellState, isWallCell, outsideVoid, queue, c, r + 1, mapCols);
        }

        List<Rectangle2D> candidates = new ArrayList<>();
        int s0 = 0, s1 = 0, s2 = 0;

        for (int r = 1; r < mapRows - 1; r++) {
            for (int c = 1; c < mapCols - 1; c++) {
                if (outsideVoid[r][c] || isWallCell[r][c]) continue;

                int st = cellState[r][c];
                if (st == 2) {
                    s2++;
                } else {
                    if (st == 1) s1++; else s0++;
                    candidates.add(new Rectangle2D.Double(c * tileW, r * tileH, tileW, tileH));
                }
            }
        }

        if (!candidates.isEmpty() && s2 >= 4) {
            result.pits.addAll(candidates);
            result.pitSource = "top_down(s0=" + s0 + ",s1=" + s1 + ",s2=" + s2 + ")";
        } else {
            result.pitSource = "none(s0=" + s0 + ",s1=" + s1 + ",s2=" + s2 + ")";
        }
    }

    private static void enqueueOutside(
            int[][] cellState,
            boolean[][] isWallCell,
            boolean[][] outside,
            ArrayDeque<Integer> q,
            int c,
            int r,
            int cols
    ) {
        if (outside[r][c] || isWallCell[r][c]) return;
        if (cellState[r][c] == 0) {
            outside[r][c] = true;
            q.add(r * cols + c);
        }
    }

    /**
     * Phân loại ô gạch theo mật độ pixel và độ phẳng màu:
     * - TRANSPARENT: ô rỗng hoàn toàn (< 5% pixel đặc)
     * - DECAL: đầu lâu, xương, vết nứt, viền tường mỏng (5%..70% pixel đặc)
     * - FLAT_DARK_PIT: gạch hố tối phẳng đồng màu (>= 80% đặc, avgLum <= 60, stdDev <= 7.0)
     * - SOLID_FLOOR: gạch sàn có viền vữa / họa tiết đá hoặc tường gạch
     */
    private static TileKind classifyTile(BufferedImage img) {
        if (img == null) return TileKind.SOLID_FLOOR;

        int w = img.getWidth();
        int h = img.getHeight();
        int total = w * h;
        if (total <= 0) return TileKind.SOLID_FLOOR;

        int opaqueCount = 0;
        double sumLum = 0.0;
        double sumSq = 0.0;
        double sumChroma = 0.0;
        double minLum = 255.0;
        double maxLum = 0.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 25) continue;

                opaqueCount++;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));

                sumLum += lum;
                sumSq += lum * lum;
                sumChroma += chroma;
                if (lum < minLum) minLum = lum;
                if (lum > maxLum) maxLum = lum;
            }
        }

        double opaqueRatio = (double) opaqueCount / total;
        if (opaqueRatio < 0.05) {
            return TileKind.TRANSPARENT;
        }
        if (opaqueRatio < 0.70) {
            return TileKind.DECAL;
        }

        double avgLum = sumLum / opaqueCount;
        double variance = Math.max(0.0, (sumSq / opaqueCount) - avgLum * avgLum);
        double stdDev = Math.sqrt(variance);
        double avgChroma = sumChroma / opaqueCount;

        if (opaqueRatio >= 0.80
                && avgLum <= 60.0
                && stdDev <= 7.0
                && (maxLum - minLum) <= 22.0
                && avgChroma <= 45.0) {
            return TileKind.FLAT_DARK_PIT;
        }

        return TileKind.SOLID_FLOOR;
    }

    private static void addPitCellIfNotWall(
            List<Rectangle2D> pits,
            double x,
            double y,
            double w,
            double h,
            List<Rectangle2D> wallRects
    ) {
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        for (Rectangle2D wall : wallRects) {
            if (wall.contains(cx, cy)) {
                return;
            }
        }
        pits.add(new Rectangle2D.Double(x, y, w, h));
    }

    // ==================== ĐỌC .TX VÀ .TSX CỦA TILED ====================

    private static TemplateData loadTemplate(String txPath) {
        Document doc = parseXml(txPath);
        if (doc == null) return null;

        Element root = doc.getDocumentElement();
        String baseDir = parentDir(txPath);
        TemplateData tpl = new TemplateData();

        NodeList tsNodes = root.getElementsByTagName("tileset");
        if (tsNodes.getLength() > 0) {
            tpl.tileset = parseTilesetRef((Element) tsNodes.item(0), baseDir);
        }

        NodeList objNodes = root.getElementsByTagName("object");
        if (objNodes.getLength() > 0) {
            Element objEl = (Element) objNodes.item(0);
            tpl.name = objEl.getAttribute("name");
            tpl.type = firstNonEmpty(objEl.getAttribute("type"), objEl.getAttribute("class"));
            long fullGid = parseRawGidLong(objEl.getAttribute("gid"));
            tpl.gid = (int) (fullGid & FLIP_MASK);
            tpl.flipH = (fullGid & FLIP_H_BIT) != 0;
            tpl.flipV = (fullGid & FLIP_V_BIT) != 0;
            tpl.width = parseDouble(objEl.getAttribute("width"), 0.0);
            tpl.height = parseDouble(objEl.getAttribute("height"), 0.0);
            tpl.rotation = parseDouble(objEl.getAttribute("rotation"), 0.0);
            readProperties(objEl, tpl.properties);
        }
        return tpl;
    }

    private static TilesetRef parseTilesetRef(Element tsEl, String baseDir) {
        int firstGid = parseInt(tsEl.getAttribute("firstgid"), 1);
        String source = tsEl.getAttribute("source");

        TilesetData data;
        if (source != null && !source.isEmpty()) {
            String tsxPath = normalizePath(baseDir, decodeUrlPath(source));
            data = loadTsx(tsxPath);
        } else {
            data = parseTilesetElement(tsEl, baseDir);
        }

        TilesetRef ref = new TilesetRef();
        ref.firstGid = firstGid;
        ref.data = data;
        return ref;
    }

    private static TilesetData loadTsx(String tsxPath) {
        if (TSX_CACHE.containsKey(tsxPath)) {
            return TSX_CACHE.get(tsxPath);
        }
        Document doc = parseXml(tsxPath);
        if (doc == null) return null;

        TilesetData data = parseTilesetElement(doc.getDocumentElement(), parentDir(tsxPath));
        TSX_CACHE.put(tsxPath, data);
        return data;
    }

    private static TilesetData parseTilesetElement(Element tsEl, String baseDir) {
        TilesetData data = new TilesetData();
        data.tileWidth = parseInt(tsEl.getAttribute("tilewidth"), 16);
        data.tileHeight = parseInt(tsEl.getAttribute("tileheight"), 16);
        data.spacing = parseInt(tsEl.getAttribute("spacing"), 0);
        data.margin = parseInt(tsEl.getAttribute("margin"), 0);
        int declaredCols = parseInt(tsEl.getAttribute("columns"), 0);
        data.columns = Math.max(1, declaredCols);

        NodeList children = tsEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element el)) continue;

            if ("image".equals(el.getTagName())) {
                String imgSource = el.getAttribute("source");
                if (imgSource != null && !imgSource.isEmpty()) {
                    String imgPath = normalizePath(baseDir, decodeUrlPath(imgSource));
                    data.atlasImage = loadImage(imgPath);
                    if (data.atlasImage == null) {
                        System.err.println("[MapHazardLoader] Khong nap duoc anh tileset: " + imgPath);
                    } else if (data.tileWidth > 0 && declaredCols <= 0) {
                        int usableW = Math.max(data.tileWidth, data.atlasImage.getWidth() - data.margin * 2 + data.spacing);
                        data.columns = Math.max(1, usableW / (data.tileWidth + data.spacing));
                    }
                }
            } else if ("tile".equals(el.getTagName())) {
                int id = parseInt(el.getAttribute("id"), -1);
                if (id < 0) continue;

                NodeList imgNodes = el.getElementsByTagName("image");
                if (imgNodes.getLength() > 0) {
                    String imgSource = ((Element) imgNodes.item(0)).getAttribute("source");
                    if (imgSource != null && !imgSource.isEmpty()) {
                        String imgPath = normalizePath(baseDir, decodeUrlPath(imgSource));
                        BufferedImage img = loadImage(imgPath);
                        if (img != null) {
                            data.tileImages.put(id, img);
                            data.tileImagePaths.put(id, imgPath);
                        }
                    }
                }

                NodeList animNodes = el.getElementsByTagName("animation");
                if (animNodes.getLength() > 0) {
                    NodeList frameNodes = ((Element) animNodes.item(0)).getElementsByTagName("frame");
                    int count = frameNodes.getLength();
                    if (count > 0) {
                        int[] fIds = new int[count];
                        int[] fDurs = new int[count];
                        for (int k = 0; k < count; k++) {
                            Element fEl = (Element) frameNodes.item(k);
                            fIds[k] = parseInt(fEl.getAttribute("tileid"), id);
                            fDurs[k] = parseInt(fEl.getAttribute("duration"), 150);
                        }
                        data.animTileIds.put(id, fIds);
                        data.animDurations.put(id, fDurs);
                    }
                }
            }
        }
        return data;
    }

    private static void extractTileAnimation(
            TilesetData ts,
            int localId,
            List<BufferedImage> outFrames,
            List<Integer> outDurs,
            FloorTrapEntity.TrapKind cycleTrapKind
    ) {
        int[] animIds = ts.animTileIds.get(localId);
        int[] animDurs = ts.animTileIds.containsKey(localId) ? ts.animDurations.get(localId) : null;
        if (animIds != null && animIds.length > 0) {
            for (int i = 0; i < animIds.length; i++) {
                BufferedImage img = getTileFromData(ts, animIds[i]);
                if (img != null) {
                    outFrames.add(img);
                    outDurs.add((animDurs != null && i < animDurs.length) ? animDurs[i] : 150);
                }
            }
            if (!outFrames.isEmpty()) return;
        }

        // Chỉ mở rộng frame kế tiếp TRONG CHÍNH TILESET CỦA MAP (khi dùng bẫy theo chu kỳ chưa gắn thẻ <animation>)
        boolean isCycleTrap = (cycleTrapKind == FloorTrapEntity.TrapKind.SPIKE_CYCLE
                || cycleTrapKind == FloorTrapEntity.TrapKind.FIRE_CYCLE);
        if (isCycleTrap) {
            String path = ts.tileImagePaths.get(localId);
            if (path != null) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                String dir = parentDir(path);
                Matcher m = TRAILING_INDEX_PATTERN.matcher(fileName);
                if (m.matches()) {
                    String prefix = m.group(1);
                    String ext = m.group(3);
                    List<Map.Entry<Integer, BufferedImage>> siblings = new ArrayList<>();
                    for (Map.Entry<Integer, String> entry : ts.tileImagePaths.entrySet()) {
                        String siblingPath = entry.getValue();
                        if (!parentDir(siblingPath).equals(dir)) continue;
                        String sibName = siblingPath.substring(siblingPath.lastIndexOf('/') + 1);
                        Matcher sm = TRAILING_INDEX_PATTERN.matcher(sibName);
                        if (sm.matches()
                                && sm.group(1).equalsIgnoreCase(prefix)
                                && sm.group(3).equalsIgnoreCase(ext)) {
                            BufferedImage img = getTileFromData(ts, entry.getKey());
                            if (img != null) {
                                siblings.add(Map.entry(parseInt(sm.group(2), 0), img));
                            }
                        }
                    }
                    if (siblings.size() > 1) {
                        siblings.sort(Comparator.comparingInt(Map.Entry::getKey));
                        for (Map.Entry<Integer, BufferedImage> s : siblings) {
                            outFrames.add(s.getValue());
                        }
                        return;
                    }
                }
            }
        }

        BufferedImage single = getTileFromData(ts, localId);
        if (single != null) {
            outFrames.add(single);
        }
    }

    private static BufferedImage getSingleTileImage(List<TilesetRef> tilesets, int gid) {
        TilesetRef ref = findTilesetForGid(tilesets, gid);
        if (ref == null || ref.data == null) return null;
        return getTileFromData(ref.data, gid - ref.firstGid);
    }

    private static TilesetRef findTilesetForGid(List<TilesetRef> sortedDesc, int gid) {
        if (gid <= 0) return null;
        for (TilesetRef ref : sortedDesc) {
            if (gid >= ref.firstGid) {
                return ref;
            }
        }
        return null;
    }

    private static BufferedImage getTileFromData(TilesetData ts, int localId) {
        if (localId < 0) return null;
        if (ts.tileImages.containsKey(localId)) {
            return ts.tileImages.get(localId);
        }
        if (ts.atlasImage != null && ts.tileWidth > 0 && ts.tileHeight > 0) {
            int col = localId % ts.columns;
            int row = localId / ts.columns;
            int sx = ts.margin + col * (ts.tileWidth + ts.spacing);
            int sy = ts.margin + row * (ts.tileHeight + ts.spacing);
            if (sx >= 0 && sy >= 0
                    && sx + ts.tileWidth <= ts.atlasImage.getWidth()
                    && sy + ts.tileHeight <= ts.atlasImage.getHeight()) {
                return ts.atlasImage.getSubimage(sx, sy, ts.tileWidth, ts.tileHeight);
            }
        }
        return null;
    }

    // ==================== CHỈ MỤC TÀI NGUYÊN & NẠP FILE ====================

    private static void ensureResourceIndex() {
        if (resourceIndexBuilt) return;
        resourceIndexBuilt = true;

        String[] roots = {
            "app/src/main/resources",
            "src/main/resources",
            "app/build/resources/main",
            "build/resources/main"
        };
        for (String rootPath : roots) {
            File root = new File(rootPath);
            if (root.exists() && root.isDirectory()) {
                indexDirectory(root);
                if (!FILES_BY_NAME.isEmpty()) break;
            }
        }
    }

    private static void indexDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                indexDirectory(f);
            } else if (f.isFile()) {
                String lowName = f.getName().toLowerCase(Locale.ROOT);
                FILES_BY_NAME.putIfAbsent(lowName, f);
            }
        }
    }

    private static BufferedImage loadImage(String path) {
        if (path == null || path.isEmpty()) return null;
        if (IMAGE_CACHE.containsKey(path)) {
            return IMAGE_CACHE.get(path);
        }

        BufferedImage img = null;
        try (InputStream is = openResource(path)) {
            if (is != null) {
                img = ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }

        if (img == null) {
            try {
                img = Resources.images().get(path);
            } catch (Exception ignored) {
            }
        }

        if (img != null) {
            IMAGE_CACHE.put(path, img);
        }
        return img;
    }

    private static InputStream openResource(String path) {
        if (path == null || path.isEmpty()) return null;
        String clean = path.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);

        InputStream is = openExactResource(clean);
        if (is != null) return is;

        String deduped = collapseDuplicateSegments(clean);
        if (!deduped.equals(clean)) {
            is = openExactResource(deduped);
            if (is != null) return is;
        }

        ensureResourceIndex();
        String baseName = clean.substring(clean.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        File indexed = FILES_BY_NAME.get(baseName);
        if (indexed != null && indexed.exists()) {
            try {
                return new FileInputStream(indexed);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static InputStream openExactResource(String clean) {
        InputStream is = MapHazardLoader.class.getResourceAsStream("/" + clean);
        if (is != null) return is;

        is = Thread.currentThread().getContextClassLoader().getResourceAsStream(clean);
        if (is != null) return is;

        for (String prefix : new String[] { "app/src/main/resources/", "src/main/resources/" }) {
            File f = new File(prefix + clean);
            if (f.exists() && f.isFile()) {
                try {
                    return new FileInputStream(f);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static String collapseDuplicateSegments(String clean) {
        String[] parts = clean.split("/");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).equals(part)) continue;
            out.add(part);
        }
        return String.join("/", out);
    }

    // ==================== ĐỌC DỮ LIỆU TILE LAYER ====================

    private static ParsedTileLayer parseTileLayer(Element layerEl, int defaultCols, int defaultRows) {
        NodeList dataNodes = layerEl.getElementsByTagName("data");
        if (dataNodes.getLength() == 0) return null;

        Element dataEl = (Element) dataNodes.item(0);
        int w = parseInt(layerEl.getAttribute("width"), defaultCols);
        int h = parseInt(layerEl.getAttribute("height"), defaultRows);
        int total = w * h;
        int[] gids = new int[total];

        String encoding = dataEl.getAttribute("encoding");
        String compression = dataEl.getAttribute("compression");

        if ("csv".equalsIgnoreCase(encoding)) {
            String text = dataEl.getTextContent().trim();
            String[] tokens = text.split("\\s*,\\s*");
            int len = Math.min(total, tokens.length);
            for (int i = 0; i < len; i++) {
                gids[i] = parseGid(tokens[i].trim());
            }
        } else if ("base64".equalsIgnoreCase(encoding)) {
            String text = dataEl.getTextContent().trim();
            try {
                byte[] raw = Base64.getDecoder().decode(text.replaceAll("\\s+", ""));
                byte[] decoded;
                if ("gzip".equalsIgnoreCase(compression)) {
                    try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                        decoded = is.readAllBytes();
                    }
                } else if ("zlib".equalsIgnoreCase(compression)) {
                    try (InputStream is = new InflaterInputStream(new ByteArrayInputStream(raw))) {
                        decoded = is.readAllBytes();
                    }
                } else {
                    decoded = raw;
                }
                ByteBuffer bb = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < total && bb.remaining() >= 4; i++) {
                    gids[i] = (int) (Integer.toUnsignedLong(bb.getInt()) & FLIP_MASK);
                }
            } catch (Exception ex) {
                System.err.println("[MapHazardLoader] Loi giai ma base64 layer " + layerEl.getAttribute("name"));
            }
        } else {
            NodeList tileNodes = dataEl.getElementsByTagName("tile");
            int len = Math.min(total, tileNodes.getLength());
            for (int i = 0; i < len; i++) {
                gids[i] = parseGid(((Element) tileNodes.item(i)).getAttribute("gid"));
            }
        }

        ParsedTileLayer tl = new ParsedTileLayer();
        tl.name = layerEl.getAttribute("name");
        tl.visible = !"0".equals(layerEl.getAttribute("visible"));
        tl.width = w;
        tl.height = h;
        tl.gids = gids;
        return tl;
    }

    // ==================== TIỆN ÍCH XML & ĐƯỜNG DẪN ====================

    private static Document parseXml(String resourcePath) {
        try (InputStream is = openResource(resourcePath)) {
            if (is == null) return null;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(is);
        } catch (Exception ex) {
            System.err.println("[MapHazardLoader] Loi parse XML " + resourcePath + ": " + ex.getMessage());
            return null;
        }
    }

    private static String decodeUrlPath(String raw) {
        if (raw == null) return "";
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    private static void readProperties(Element parent, Map<String, String> out) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && "properties".equals(el.getTagName())) {
                NodeList pNodes = el.getElementsByTagName("property");
                for (int k = 0; k < pNodes.getLength(); k++) {
                    Element p = (Element) pNodes.item(k);
                    String kName = p.getAttribute("name");
                    String kVal = p.getAttribute("value");
                    if (kVal == null || kVal.isEmpty()) {
                        kVal = p.getTextContent().trim();
                    }
                    if (kName != null && !kName.isEmpty()) {
                        out.put(kName.toLowerCase(Locale.ROOT), kVal);
                    }
                }
            }
        }
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static String parentDir(String path) {
        int idx = path.lastIndexOf('/');
        return (idx >= 0) ? path.substring(0, idx + 1) : "";
    }

    private static String normalizePath(String baseDir, String relative) {
        String combined = (baseDir + relative).replace('\\', '/');
        String[] parts = combined.split("/");
        List<String> stack = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) {
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
            } else {
                stack.add(p);
            }
        }
        return String.join("/", stack);
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : (b != null ? b : "");
    }

    private static long parseRawGidLong(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int parseGid(String s) {
        return (int) (parseRawGidLong(s) & FLIP_MASK);
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Vẽ khung tím đánh dấu vùng hố bẫy khi bấm F3 */
    private static class PitDebugOverlayEntity extends Entity implements IRenderable {
        private final List<Rectangle2D> pits;

        PitDebugOverlayEntity(List<Rectangle2D> pits) {
            super();
            this.pits = pits;
            this.setRenderType(RenderType.OVERLAY);
        }

        @Override
        public void render(Graphics2D g) {
            if (!Game.config().debug().renderCollisionBoxes() || Game.world().camera() == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            for (Rectangle2D pit : pits) {
                Point2D vp = Game.world().camera().getViewportLocation(new Point2D.Double(pit.getX(), pit.getY()));
                int x = (int) Math.round(vp.getX());
                int y = (int) Math.round(vp.getY());
                int w = (int) Math.round(pit.getWidth());
                int h = (int) Math.round(pit.getHeight());
                g2.setColor(new Color(145, 70, 255, 65));
                g2.fillRect(x, y, w, h);
                g2.setColor(new Color(195, 130, 255, 210));
                g2.drawRect(x, y, w, h);
            }
            g2.dispose();
        }
    }
}