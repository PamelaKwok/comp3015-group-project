import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GameWindow {
    @FXML
    MenuBar menuBar;

    @FXML
    MenuItem mnuTopScores;

    @FXML
    Label nameLabel;

    @FXML
    Label scoreLabel;

    @FXML
    Label levelLabel;

    @FXML
    Label comboLabel;

    @FXML
    Label moveCountLabel;

    @FXML
    Label turnLabel;

    @FXML
    Pane boardPane;

    @FXML
    Canvas canvas;

    Stage stage;
    AnimationTimer animationTimer;

    final String imagePath = "images/";
    final String[] symbols = {"bg", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "Joker"};
    final Image[] images = new Image[symbols.length];
    final GameEngine gameEngine = GameEngine.getInstance();

    public GameWindow(Stage stage) throws IOException {
        loadImages();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("mainUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        this.stage = stage;

        stage.setScene(scene);
        stage.setTitle("Battle Joker");
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        stage.widthProperty().addListener(w -> onWidthChangedWindow(((ReadOnlyDoubleProperty) w).getValue()));
        stage.heightProperty().addListener(h -> onHeightChangedWindow(((ReadOnlyDoubleProperty) h).getValue()));
        stage.setOnCloseRequest(event -> quit());

        gameEngine.setUiRefresh(v -> Platform.runLater(this::refreshStats));

        if (mnuTopScores != null) {
            mnuTopScores.setOnAction(e -> requestLeaderboard());
        }

        stage.show();
        initCanvas();
        refreshStats();
        gameStart();
    }

    private void requestLeaderboard() {
        try {
            gameEngine.clearLeaderboardReady();
            BattleJoker.client.send(GameEngine.SETTING, "LEADERBOARD");
        } catch (IOException ex) {
            System.out.println("Disconnected!");
        }
    }

    private void refreshStats() {
        scoreLabel.setText("Score: " + gameEngine.getScore());
        levelLabel.setText("Level: " + gameEngine.getLevel());
        comboLabel.setText("Combo: —");
        moveCountLabel.setText("# of Moves: " + gameEngine.getMoveCount());

        if (gameEngine.isMyTurn()) {
            turnLabel.setText("YOUR TURN (" + gameEngine.getMyMovesLeft() + " left)");
            turnLabel.setTextFill(Color.GREEN);
        } else {
            String current = gameEngine.getCurrentPlayerName();
            if (current.isEmpty()) {
                turnLabel.setText("Waiting for players...");
            } else {
                turnLabel.setText("Waiting for " + current);
            }
            turnLabel.setTextFill(Color.RED);
        }

        if (gameEngine.isLeaderboardReady()) {
            gameEngine.clearLeaderboardReady();
            try {
                new ScoreboardWindow(ScoreboardWindow.Mode.HISTORICAL, gameEngine.getLeaderboardLines());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void gameStart() {
        animationTimer.start();
    }

    private void loadImages() throws IOException {
        for (int i = 0; i < symbols.length; i++) {
            images[i] = new Image(Files.newInputStream(Paths.get(imagePath + symbols[i] + ".png")));
        }
    }

    private void initCanvas() {
        canvas.setOnKeyPressed(event -> {
            String keyPressed = event.getCode().toString();
            if (!"UP".equals(keyPressed) && !"DOWN".equals(keyPressed)
                    && !"LEFT".equals(keyPressed) && !"RIGHT".equals(keyPressed)) {
                return;
            }
            if (gameEngine.isGameOver() || !gameEngine.isMyTurn()) {
                return;
            }
            try {
                BattleJoker.client.send(GameEngine.COMMAND, keyPressed);
            } catch (IOException ex) {
                System.out.println("Disconnected!");
                quit();
            }
        });

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
                if (gameEngine.isGameOver() && gameEngine.isSessionScoresReady() && !gameEngine.isGameOverDialogShown()) {
                    gameEngine.setGameOverDialogShown(true);
                    animationTimer.stop();
                    Platform.runLater(() -> {
                        try {
                            new ScoreboardWindow(ScoreboardWindow.Mode.SESSION, gameEngine.getSessionScoreLines());
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                }
            }
        };
        canvas.requestFocus();
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double sceneSize = Math.min(w, h);
        double blockSize = sceneSize / GameEngine.SIZE;
        double padding = blockSize * .05;
        double startX = (w - sceneSize) / 2;
        double startY = (h - sceneSize) / 2;
        double cardSize = blockSize - (padding * 2);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double y = startY;
        int v;

        for (int i = 0; i < GameEngine.SIZE; i++) {
            double x = startX;
            for (int j = 0; j <