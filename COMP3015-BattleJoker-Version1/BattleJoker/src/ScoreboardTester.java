import javafx.application.Application;
import javafx.stage.Stage;

import java.util.List;

/** UI smoke test for the scoreboard layout (no database on client). */
public class ScoreboardTester extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        new ScoreboardWindow(
                ScoreboardWindow.Mode.HISTORICAL,
                List.of("      Alice |   100 (13) | 2026-01-01 12:00")
        );
        primaryStage.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}