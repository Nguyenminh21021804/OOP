package player;

import de.gurkenlabs.litiengine.graphics.Spritesheet;
import de.gurkenlabs.litiengine.graphics.animation.Animation;
import de.gurkenlabs.litiengine.graphics.animation.EntityAnimationController;
import de.gurkenlabs.litiengine.resources.Resources;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PlayerAnimationController extends EntityAnimationController<Player> {

    public static final String EMPTY_ANIM_KEY = "player-empty";

    private static final int SPAWN_FRAME_MS = 75;
    private static final int DIE_FRAME_MS = 130;

    private final Map<String, Integer> frameWidthBySkin = new HashMap<>();
    private final Map<String, Integer> frameHeightBySkin = new HashMap<>();
    private final Map<String, Long> spawnDurationBySkin = new HashMap<>();
    private final Map<String, Long> dieHoldDelayBySkin = new HashMap<>();
    private final Map<String, BufferedImage> fallRightBySkin = new HashMap<>();
    private final Map<String, BufferedImage> fallLeftBySkin = new HashMap<>();

    public PlayerAnimationController(Player entity) {
        super(entity);

        registerEmptyAnimation();

        Animation firstDefault = null;
        for (PlayerSkin skin : PlayerSkin.values()) {
            Animation standAnim = registerSkin(skin);
            if (firstDefault == null && standAnim != null) {
                firstDefault = standAnim;
            }
        }

        if (firstDefault != null) {
            this.setDefault(firstDefault);
        }

        this.addRule(p -> true, Player::getCurrentAnimationName);
    }

    /** Hoạt ảnh rỗng 1x1 trong suốt dùng trong lúc Player tự vẽ hiệu ứng thu nhỏ rơi xuống hố */
    private void registerEmptyAnimation() {
        Spritesheet emptySheet = Resources.spritesheets().get(EMPTY_ANIM_KEY);
        if (emptySheet == null) {
            BufferedImage emptyImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            emptySheet = Resources.spritesheets().load(emptyImg, EMPTY_ANIM_KEY, 1, 1);
        }
        this.add(createAnimation(emptySheet, true, 1000));
    }

    private Animation registerSkin(PlayerSkin skin) {
        String id = skin.getId();
        int frameW = 16;
        int frameH = 20;

        BufferedImage standImg = Resources.images().get("player/" + id + "stand.png");
        if (standImg != null) {
            frameW = Math.max(1, standImg.getWidth() / 4);
            frameH = Math.max(1, standImg.getHeight());
        }
        frameWidthBySkin.put(id, frameW);
        frameHeightBySkin.put(id, frameH);

        // Lưu sẵn 1 frame làm ảnh lúc nhân vật rơi xuống hố
        BufferedImage fallSource = Resources.images().get("player/" + id + "hurt.png");
        if (fallSource == null) fallSource = standImg;
        if (fallSource != null) {
            int cw = Math.max(1, fallSource.getWidth() / 4);
            int ch = fallSource.getHeight();
            BufferedImage rightFrame = fallSource.getSubimage(0, 0, cw, ch);
            fallRightBySkin.put(id, rightFrame);
            fallLeftBySkin.put(id, flipFramesHorizontally(rightFrame, cw, ch));
        }

        Animation standRight = registerStrip(id, "stand", frameW, 4, true, true, 160, false);
        registerStrip(id, "run", frameW, 4, true, true, 100, false);
        registerStrip(id, "hurt", frameW, 4, true, true, 105, false);
        registerStrip(id, "die", frameW, 4, true, false, DIE_FRAME_MS, false);
        registerDeadPose(id, 4);
        dieHoldDelayBySkin.put(id, 3L * DIE_FRAME_MS);

        registerStrip(id, "dash", frameW, 2, false, true, 65, true);
        Animation spawnAnim = registerStrip(
                id, "spawn", frameW, skin.getSpawnFrames(), true, false, SPAWN_FRAME_MS, false
        );

        long spawnMs = skin.getSpawnFrames() * (long) SPAWN_FRAME_MS;
        if (spawnAnim != null && spawnAnim.getSpritesheet() != null) {
            int totalFrames = Math.max(1, spawnAnim.getSpritesheet().getTotalNumberOfSprites());
            spawnMs = totalFrames * (long) SPAWN_FRAME_MS;
        }
        spawnDurationBySkin.put(id, spawnMs);

        return standRight;
    }

    private Animation registerStrip(
            String skinId,
            String stateName,
            int baseFrameW,
            int fallbackFrames,
            boolean forceFrameCount,
            boolean loop,
            int durationMs,
            boolean trimTrailingEmpty
    ) {
        String path = "player/" + skinId + stateName + ".png";
        BufferedImage strip = Resources.images().get(path);
        if (strip == null) {
            System.err.println("[PlayerAnimationController] Khong tim thay file: " + path);
            return null;
        }

        int cellW;
        if (forceFrameCount) {
            cellW = Math.max(1, strip.getWidth() / fallbackFrames);
        } else {
            cellW = (strip.getWidth() >= baseFrameW && strip.getWidth() % baseFrameW == 0)
                    ? baseFrameW
                    : Math.max(1, strip.getWidth() / fallbackFrames);
        }
        int cellH = strip.getHeight();

        if (trimTrailingEmpty) {
            strip = trimTrailingEmptyFrames(strip, cellW, cellH);
        }

        String rightKey = skinId + "-" + stateName + "-right";
        Spritesheet rightSheet = Resources.spritesheets().get(rightKey);
        if (rightSheet == null) {
            rightSheet = Resources.spritesheets().load(strip, rightKey, cellW, cellH);
        }
        Animation rightAnim = createAnimation(rightSheet, loop, durationMs);
        this.add(rightAnim);

        String leftKey = skinId + "-" + stateName + "-left";
        Spritesheet leftSheet = Resources.spritesheets().get(leftKey);
        if (leftSheet == null) {
            BufferedImage flipped = flipFramesHorizontally(strip, cellW, cellH);
            leftSheet = Resources.spritesheets().load(flipped, leftKey, cellW, cellH);
        }
        this.add(createAnimation(leftSheet, loop, durationMs));

        return rightAnim;
    }

    private void registerDeadPose(String skinId, int totalFrames) {
        String path = "player/" + skinId + "die.png";
        BufferedImage strip = Resources.images().get(path);
        if (strip == null) return;

        int cellW = Math.max(1, strip.getWidth() / totalFrames);
        int cellH = strip.getHeight();
        int lastFrameX = Math.max(0, (totalFrames - 1) * cellW);
        BufferedImage lastFrame = strip.getSubimage(lastFrameX, 0, cellW, cellH);

        String deadRightKey = skinId + "-dead-right";
        Spritesheet deadRightSheet = Resources.spritesheets().get(deadRightKey);
        if (deadRightSheet == null) {
            deadRightSheet = Resources.spritesheets().load(lastFrame, deadRightKey, cellW, cellH);
        }
        this.add(createAnimation(deadRightSheet, true, 1000));

        String deadLeftKey = skinId + "-dead-left";
        Spritesheet deadLeftSheet = Resources.spritesheets().get(deadLeftKey);
        if (deadLeftSheet == null) {
            BufferedImage flipped = flipFramesHorizontally(lastFrame, cellW, cellH);
            deadLeftSheet = Resources.spritesheets().load(flipped, deadLeftKey, cellW, cellH);
        }
        this.add(createAnimation(deadLeftSheet, true, 1000));
    }

    private Animation createAnimation(Spritesheet sheet, boolean loop, int durationMs) {
        int totalFrames = Math.max(1, sheet.getTotalNumberOfSprites());
        int[] durations = new int[totalFrames];
        Arrays.fill(durations, durationMs);
        return new Animation(sheet, loop, durations);
    }

    private BufferedImage trimTrailingEmptyFrames(BufferedImage strip, int cellW, int cellH) {
        int total = Math.max(1, strip.getWidth() / cellW);
        int valid = total;
        while (valid > 1 && isFrameEmpty(strip, (valid - 1) * cellW, cellW, cellH)) {
            valid--;
        }
        if (valid == total) return strip;
        return strip.getSubimage(0, 0, valid * cellW, cellH);
    }

    private boolean isFrameEmpty(BufferedImage img, int startX, int w, int h) {
        int endX = Math.min(img.getWidth(), startX + w);
        for (int y = 0; y < h; y++) {
            for (int x = startX; x < endX; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private BufferedImage flipFramesHorizontally(BufferedImage strip, int cellW, int cellH) {
        int count = Math.max(1, strip.getWidth() / cellW);
        BufferedImage out = new BufferedImage(strip.getWidth(), strip.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int i = 0; i < count; i++) {
            int sx = i * cellW;
            g.drawImage(strip,
                    sx + cellW, 0, sx, cellH,
                    sx, 0, sx + cellW, cellH,
                    null);
        }
        g.dispose();
        return out;
    }

    public BufferedImage getFallSprite(PlayerSkin skin, boolean facingRight) {
        return facingRight ? fallRightBySkin.get(skin.getId()) : fallLeftBySkin.get(skin.getId());
    }

    public int getFrameWidth(PlayerSkin skin) {
        return frameWidthBySkin.getOrDefault(skin.getId(), 16);
    }

    public int getFrameHeight(PlayerSkin skin) {
        return frameHeightBySkin.getOrDefault(skin.getId(), 20);
    }

    public long getSpawnDurationMs(PlayerSkin skin) {
        return spawnDurationBySkin.getOrDefault(skin.getId(), 510L);
    }

    public long getDieHoldDelayMs(PlayerSkin skin) {
        return dieHoldDelayBySkin.getOrDefault(skin.getId(), 390L);
    }
}