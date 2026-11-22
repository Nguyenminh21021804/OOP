

import screens.CharacterSelectScreen;
import screens.CreditsScreen;
import screens.HowToPlayScreen;
import screens.SettingsScreen;
import de.gurkenlabs.litiengine.Game;
import screens.MainMenuScreen;
import de.gurkenlabs.litiengine.gui.screens.Resolution;

public class App {
    public static void main(String[] args) {
        Game.info().setName("The Last Knight");
        Game.info().setVersion("1.0.0");
        Game.info().setSubTitle("A project by group 17");
        Game.init(args);
        Game.window().setResolution(Resolution.custom(1024, 576, "1024x576"));

        Game.screens().add(new MainMenuScreen());
        Game.screens().add(new CharacterSelectScreen());
        Game.screens().add(new SettingsScreen());
        Game.screens().add(new HowToPlayScreen());
        Game.screens().add(new CreditsScreen());
        Game.screens().display(MainMenuScreen.NAME);

        Game.start();
    }
}