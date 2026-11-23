import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class BattleJoker extends Application {
    public static Client client;

    public static void exit() {
        if (client != null) {
            client.disconnect();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            GetNameDialog dialog = new GetNameDialog();
            if (!dialog.isConfirmed()) {
                PlatformExit();
                return;
            }

            client = new Client(dialog.getIpAddress(), dialog.getPort());
            client.connect();
            client.send(GameEngine.NAME, dialog.getPlayername());

            GameWindow win = new GameWindow(primaryStage);
            win.setName(dialog.getPlayername());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void PlatformExit() {
        javafx.application.Platform.exit();
    }

    @Override
    public void stop() {
        exit();
    }

    public static void main(String[] args) {
        System.setErr(new FilteredStream(System.err));
        launch(args);
    }
}

class FilteredStream extends PrintStream {

    public FilteredStream(OutputStream out) {
        super(out);
    }

    @Override
    public void println(String x) {
        if (x != null && !x.contains("SLF4J: "))
            super.println(x);
    }

    @Override
    public void print(String x) {
        if (x != null && !x.contains("WARNING: Loading FXML document with JavaFX API of version 18"))
            super.print(x);
    }
}