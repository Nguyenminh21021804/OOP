package player;

public enum PlayerSkin {
    KNIGHT_01("01", "Knight 01", 6),
    KNIGHT_02("02", "Knight 02", 11),
    KNIGHT_03("03", "Knight 03", 11),
    KNIGHT_04("04", "Knight 04", 11);

    private final String id;
    private final String displayName;
    private final int spawnFrames;

    PlayerSkin(String id, String displayName, int spawnFrames) {
        this.id = id;
        this.displayName = displayName;
        this.spawnFrames = spawnFrames;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSpawnFrames() {
        return spawnFrames;
    }

    public PlayerSkin next() {
        PlayerSkin[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }
}