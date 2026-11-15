

import screens.InfoScreen;
import de.gurkenlabs.litiengine.Game;
import screens.MainMenuScreen;
import screens.GamePlayScreen;
import de.gurkenlabs.litiengine.gui.screens.Resolution;
import de.gurkenlabs.litiengine.resources.Resources;

public class App {
    public static void main(String[] args) {
        Game.info().setName("The Last Knight");
        Game.info().setVersion("1.0.0");
        Game.info().setSubTitle("A project by group 17");
        Game.init(args);
        Resources.load("game.litidata");
        Game.window().setResolution(Resolution.custom(1024, 576, "1024x576"));

        Game.screens().add(new MainMenuScreen());
        Game.screens().add(new GamePlayScreen());
        Game.screens().add(new InfoScreen("SETTINGS", "Settings"));
        Game.screens().add(new InfoScreen("HOWTOPLAY", "How to Play"));
        Game.screens().add(new InfoScreen("CREDITS", "Credits"));
        Game.screens().display(MainMenuScreen.NAME);

        Game.start();
    }
}