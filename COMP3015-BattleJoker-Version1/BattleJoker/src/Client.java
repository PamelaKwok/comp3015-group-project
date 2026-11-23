import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Client {
    private final String ipAddress;
    private final int tcpPort;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public Client(String ipAddress, int tcpPort) {
        this.ipAddress = ipAddress;
        this.tcpPort = tcpPort;
    }

    public void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException ex) {
            System.out.println("Bye bye~");
        }
        in = null;
        out = null;
        socket = null;
    }

    public void connect() throws IOException {
        socket = new Socket(ipAddress, tcpPort);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        Thread receiveThread = new Thread(() -> {
            try {
                while (true) {
                    receive();
                }
            } catch (IOException ex) {
                System.out.println("Disconnected!");
                Platform.exit();
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void send(int dataType, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        out.writeInt(dataType);
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    public void receive() throws IOException {
        int dataType = in.readInt();
        switch (dataType) {
            case GameEngine.PUZZLE:
                handleReceivePuzzleData();
                break;
            case GameEngine.SCORES:
                handleReceiveScores();
                break;
            case GameEngine.GAME_OVER:
                handleReceiveGameOver();
                break;
            case GameEngine.LEADERBOARD:
                handleReceiveLeaderboard();
                break;
            case 8: // TURN
                handleReceiveTurn();
                break;
            case 9: // ASK_START
                Platform.runLater(this::showStartDialog);
                break;
            case 10: // ASK_WAIT
                Platform.runLater(this::showWaitDialog);
                break;
            default:
                System.out.println("Unknown server message: " + dataType);
        }
    }

    private String readUtf8String() throws IOException {
        int len = in.readInt();
        byte[] buf = new byte[len];
        in.readFully(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }

    public void handleReceivePuzzleData() throws IOException {
        int width = in.readInt();
        int height = in.readInt();
        int[] puzzle = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            puzzle[i] = in.readInt();
        }
        boolean isGameOver = in.readBoolean();
        int level = in.readInt();

        GameEngine engine = GameEngine.getInstance();
        Platform.runLater(() -> engine.applyPuzzle(puzzle, isGameOver, level));
    }

    public void handleReceiveScores() throws IOException {
        int count = in.readInt();
        List<String> names = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        List<Integer> moves = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(readUtf8String());
            scores.add(in.readInt());
            moves.add(in.readInt());
        }
        GameEngine engine = GameEngine.getInstance();
        Platform.runLater(() -> engine.applyScores(names, scores, moves));
    }

    public void handleReceiveGameOver() throws IOException {
        int count = in.readInt();
        List<String> names = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(readUtf8String());
            scores.add(in.readInt());
        }
        int winnerIndex = in.readInt();
        GameEngine engine = GameEngine.getInstance();
        Platform.runLater(() -> engine.applyGameOver(names, scores, winnerIndex));
    }

    public void handleReceiveLeaderboard() throws IOException {
        int count = in.readInt();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = readUtf8String();
            int score = in.readInt();
            int level = in.readInt();
            String time = readUtf8String();
            String scoreStr = String.format("%s (%s)", score, level);
            if (time.length() > 16) {
                time = time.substring(0, 16);
            }
            lines.add(String.format("%10s | %10s | %s", name, scoreStr, time));
        }
        GameEngine engine = GameEngine.getInstance();
        Platform.runLater(() -> engine.applyLeaderboard(lines));
    }

    // ==================== Version 2  ====================

    private void handleReceiveTurn() throws IOException {
        int playerCount = in.readInt();
        List<String> names = new ArrayList<>();
        List<Integer> movesLeft = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            names.add(readUtf8String());
            movesLeft.add(in.readInt());
        }
        int currentTurnIndex = in.readInt();

        GameEngine engine = GameEngine.getInstance();
        Platform.runLater(() -> engine.applyTurn(names, movesLeft, currentTurnIndex));
    }

    private void showStartDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Start Game?");
        alert.setHeaderText("You are the first player.");
        alert.setContentText("Should we start the game now, or wait for others to join?");

        ButtonType startBtn = new ButtonType("Start Now");
        ButtonType waitBtn = new ButtonType("Waiting for the others");
        alert.getButtonTypes().setAll(startBtn, waitBtn);

        Optional<ButtonType> result = alert.showAndWait();
        try {
            if (result.isPresent() && result.get() == startBtn) {
                send(9, "START");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showWaitDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Game already started");
        alert.setHeaderText("Game start / 4 players (Full)");
        alert.setContentText("Do you want to wait for the next round, or leave?");

        ButtonType waitBtn = new ButtonType("Wait for the next round.");
        ButtonType leaveBtn = new ButtonType("leave");
        alert.getButtonTypes().setAll(waitBtn, leaveBtn);

        Optional<ButtonType> result = alert.showAndWait();
        try {
            if (result.isPresent() && result.get() == leaveBtn) {
                send(10, "LEAVE");
                Platform.exit();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}