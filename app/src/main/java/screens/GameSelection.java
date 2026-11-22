package screens;

import java.util.function.Consumer;

/**
 * Luu lua chon nhan vat va 2 vu khi cua nguoi choi.
 * Ben lam Gameplay chi can goi GameSelection.get() de lay du lieu.
 */
public final class GameSelection {
    private static final GameSelection INSTANCE = new GameSelection();

    private String heroId = "hero1";
    private String heroName = "Jade Knight";
    private String primaryWeaponId = "sword";
    private String secondaryWeaponId = "bow";

    private Consumer<GameSelection> onStartGame;

    private GameSelection() {}

    public static GameSelection get() {
        return INSTANCE;
    }

    public void setSelection(String heroId, String heroName, String weapon1Id, String weapon2Id) {
        this.heroId = heroId;
        this.heroName = heroName;
        this.primaryWeaponId = weapon1Id;
        this.secondaryWeaponId = weapon2Id;
    }

    public String getHeroId() {
        return heroId;
    }

    public String getHeroName() {
        return heroName;
    }

    public String getPrimaryWeaponId() {
        return primaryWeaponId;
    }

    public String getSecondaryWeaponId() {
        return secondaryWeaponId;
    }

    /** Dang ky ham chuyen sang man hinh choi game (goi trong App.java khi ghep code). */
    public void setOnStartGame(Consumer<GameSelection> handler) {
        this.onStartGame = handler;
    }

    public void triggerStartGame() {
        if (onStartGame != null) {
            onStartGame.accept(this);
        } else {
            System.out.println("[GameSelection] START GAME -> Hero: " + heroName
                    + " (" + heroId + ") | Weapons: " + primaryWeaponId + ", " + secondaryWeaponId);
        }
    }
}