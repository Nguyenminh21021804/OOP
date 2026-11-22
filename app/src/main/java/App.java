import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.Resolution;
import screens.InfoScreen;
import screens.MainMenuScreen;
import screens.MapViewerScreen;

public class App {
    public static void main(String[] args) {
        Game.info().setName("The Last Knight");
        Game.info().setVersion("1.0.0");
        Game.info().setSubTitle("A project by group 17");
        Game.init(args);
        Game.window().setResolution(Resolution.custom(1024, 576, "1024x576"));

        Game.screens().add(new MainMenuScreen());
        Game.screens().add(new InfoScreen("SETTINGS", "Settings"));
        Game.screens().add(new InfoScreen("HOWTOPLAY", "How to Play"));
        Game.screens().add(new InfoScreen("CREDITS", "Credits"));
        Game.screens().add(new MapViewerScreen());
        Game.screens().display(MainMenuScreen.NAME);

        Game.start();
    }
}