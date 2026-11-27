import de.gurkenlabs.litiengine.Game;
<<<<<<< HEAD
import de.gurkenlabs.litiengine.gui.screens.Resolution;
import screens.CharacterSelectScreen;
import screens.CreditsScreen;
import screens.GameSelection;
import screens.HowToPlayScreen;
import screens.MainMenuScreen;
import screens.PlayScreen;
import screens.SettingsScreen;
=======
import screens.MainMenuScreen;
import screens.GamePlayScreen;
import de.gurkenlabs.litiengine.gui.screens.Resolution;
import de.gurkenlabs.litiengine.resources.Resources;
>>>>>>> origin/feat/enemy-v2

public class App {
    public static void main(String[] args) {
        Game.info().setName("The Last Knight");
        Game.info().setVersion("1.0.0");
        Game.info().setSubTitle("A project by group 17");
        Game.init(args);
        Resources.load("game.litidata");
        Game.window().setResolution(Resolution.custom(1024, 576, "1024x576"));

        Game.screens().add(new MainMenuScreen());
<<<<<<< HEAD
        Game.screens().add(new CharacterSelectScreen());
        Game.screens().add(new SettingsScreen());
        Game.screens().add(new HowToPlayScreen());
        Game.screens().add(new CreditsScreen());
        Game.screens().add(new PlayScreen());
=======
        Game.screens().add(new GamePlayScreen());
        Game.screens().add(new InfoScreen("SETTINGS", "Settings"));
        Game.screens().add(new InfoScreen("HOWTOPLAY", "How to Play"));
        Game.screens().add(new InfoScreen("CREDITS", "Credits"));
        Game.screens().display(MainMenuScreen.NAME);
>>>>>>> origin/feat/enemy-v2

        GameSelection.get().setOnStartGame(selection -> Game.screens().display(PlayScreen.NAME));

        Game.screens().display(MainMenuScreen.NAME);
        Game.start();
    }
}