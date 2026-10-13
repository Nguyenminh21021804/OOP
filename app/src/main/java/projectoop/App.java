package projectoop;

import de.gurkenlabs.litiengine.Game;

public class App {
    public static void main(String[] args) {
        Game.info().setName("My First Liti Game");
        Game.info().setVersion("1.0.0");
        Game.init(args);
        Game.start();
    }
}