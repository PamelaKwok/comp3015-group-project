import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JokerServer {
    public static final int NAME = 0;
    public static final int COMMAND = 1;
    public static final int SETTING = 2;
    public static final int PUZZLE = 3;
    public static final int SCORES = 5;
    public static final int GAME_OVER = 6;
    public static final int LEADERBOARD = 7;
    public static final int TURN = 8;
    public static final int ASK_START = 9;
    public static final int ASK_WAIT = 10;

    public static final Object lockObject = new Object();

    final List<Player> currentPlayers = new ArrayList<>();
    final List<Player> waitingPlayers = new ArrayList<>();
    final GameEngine engine = new GameEngine();

    int currentTurnIndex = 0;
    boolean gameStarted = false;

    public JokerServer(int tcpPort) throws IOException, SQLException {
        Database.connect();

        try (ServerSocket srvSocket = new ServerSocket(tcpPort)) {
            while (true) {
                Socket socket = srvSocket.accept();
                Player player = new Player();
                synchronized (lockObject) {
                    player.out = new DataOutputStream(socket.getOutputStream());
                }

                Thread t = new Thread(() -> {
                    try {
                        serve(player, socket);
                    } catch (IOException ex) {
                        System.out.println("Disconnected... " + socket.getInetAddress());
                        synchronized (lockObject) {
                            currentPlayers.remove(player);
                            waitingPlayers.remove(player);
                        }
                    }
                });
                t.start();
            }
        }
    }

    public void serve(Player player, Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());

        synchronized (lockObject) {
            if (currentPlayers.size() < 4 && !gameStarted) {
                currentPlayers.add(player);
                if (currentPlayers.size() == 1) {
                    sendAskStart(player);
                } else if (currentPlayers.size() == 4) {
                    startGame();
                }
            } else {
                sendAskWait(player);
                waitingPlayers.add(player);
            }

            if (!sendPuzzle(player)) {
                throw new IOException("Unable to send the puzzle to the new player!");
            }
            broadcastScores();
            broadcastTurn();
        }

        byte[] buffer = new byte[1024];

        while (true) {
            int dataType = in.readInt();
            int len = in.readInt();
            if (len > buffer.length) {
                buffer = new byte[len];
            }
            in.readFully(buffer, 0, len);
            String payload = new String(buffer, 0, len, StandardCharsets.UTF_8);

            switch (dataType) {
                case NAME:
                    handleName(player, payload);
                    break;
                case COMMAND:
                    handleCommand(player, payload);
                    break;
                case SETTING:
                    handleSetting(player, payload);
                    break;
                case ASK_START:
                    if ("START".equalsIgnoreCase(payload.trim()) && !gameStarted && !currentPlayers.isEmpty()) {
                        startGame();
                    }
                    break;
                case ASK_WAIT:
                    if ("LEAVE".equalsIgnoreCase(payload.trim())) {
                        waitingPlayers.remove(player);
                        try {
                            player.out.close();
                        } catch (IOException ignored) {}
                    }
                    break;
                default:
                    System.out.println("Unknown data type: " + dataType);
            }
        }
    }

    private void startGame() {
        gameStarted = true;
        currentTurnIndex = 0;
        for (Player p : currentPlayers) {
            p.movesLeft = 4;
        }
        broadcastTurn();
        System.out.println("Game started with " + currentPlayers.size() + " players");
    }

    private void sendAskStart(Player player) {
        try {
            player.out.writeInt(ASK_START);
            player.out.writeInt(0);
            player.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendAskWait(Player player) {
        try {
            player.out.writeInt(ASK_WAIT);
            player.out.writeInt(0);
            player.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleCommand(Player player, String command) {
        if (engine.isGameOver || !gameStarted) {
            return;
        }

        synchronized (lockObject) {
            if (currentPlayers.isEmpty()) {
                return;
            }
            Player current = currentPlayers.get(currentTurnIndex);
            if (player != current || player.movesLeft <= 0) {
                return;
            }

            boolean ended = engine.processCommand(player, command);
            player.movesLeft--;

            broadcastPuzzle();
            broadcastScores();

            if (ended) {
                finishGame();
                return;
            }

            if (player.movesLeft <= 0) {
                currentTurnIndex = (currentTurnIndex + 1) % currentPlayers.size();
                currentPlayers.get(currentTurnIndex).movesLeft = 4;
            }
            broadcastTurn();
        }
    }

    private void finishGame() {
        try {
            finishGameInner();
            synchronized (lockObject) {
                currentPlayers.clear();
                currentPlayers.addAll(waitingPlayers);
                waitingPlayers.clear();
                gameStarted = false;

                engine.reset();

                if (!currentPlayers.isEmpty()) {
                    if (currentPlayers.size() == 1) {
                        sendAskStart(currentPlayers.get(0));
                    } else if (currentPlayers.size() >= 4) {
                        startGame();
                    }
                    broadcastPuzzle();
                    broadcastTurn();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void finishGameInner() throws IOException {
        Player winner = null;
        for (Player p : currentPlayers) {
            if (winner == null || p.score > winner.score) {
                winner = p;
            }
        }
        if (winner != null && winner.name != null && !winner.name.isEmpty()) {
            try {
                Database.putScore(winner.name, winner.score, engine.getLevel());
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        broadcastGameOver();
    }

    public boolean sendPuzzle(Player player) {
        try {
            DataOutputStream out = player.out;
            out.writeInt(PUZZLE);
            out.writeInt(GameEngine.SIZE);
            out.writeInt(GameEngine.SIZE);
            for (int i = 0; i < GameEngine.SIZE * GameEngine.SIZE; i++) {
                out.writeInt(engine.board[i]);
            }
            out.writeBoolean(engine.isGameOver);
            out.writeInt(engine.getLevel());
            out.flush();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public void broadcastPuzzle() {
        List<Player> failed = new ArrayList<>();
        for (Player p : new ArrayList<>(currentPlayers)) {
            if (!sendPuzzle(p)) {
                failed.add(p);
            }
        }
        for (Player p : new ArrayList<>(waitingPlayers)) {
            if (!sendPuzzle(p)) {
                failed.add(p);
            }
        }
        currentPlayers.removeAll(failed);
        waitingPlayers.removeAll(failed);
    }

    public void broadcastScores() {
        List<Player> all = new ArrayList<>();
        all.addAll(currentPlayers);
        all.addAll(waitingPlayers);
        List<Player> failed = new ArrayList<>();
        for (Player p : all) {
            if (!sendScores(p)) {
                failed.add(p);
            }
        }
        currentPlayers.removeAll(failed);
        waitingPlayers.removeAll(failed);
    }

    public boolean sendScores(Player recipient) {
        try {
            DataOutputStream out = recipient.out;
            out.writeInt(SCORES);
            out.writeInt(currentPlayers.size());
            for (Player p : currentPlayers) {
                String name = p.name != null && !p.name.isEmpty() ? p.name : "Unknown";
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                out.writeInt(nameBytes.length);
                out.write(nameBytes);
                out.writeInt(p.score);
                out.writeInt(p.totalMoveCount);
            }
            out.flush();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public void broadcastTurn() {
        List<Player> all = new ArrayList<>();
        all.addAll(currentPlayers);
        all.addAll(waitingPlayers);
        for (Player p : all) {
            sendTurn(p);
        }
    }

    private void sendTurn(Player recipient) {
        try {
            DataOutputStream out = recipient.out;
            out.writeInt(TURN);
            out.writeInt(currentPlayers.size());
            for (int i = 0; i < currentPlayers.size(); i++) {
                Player p = currentPlayers.get(i);
                String name = p.name != null && !p.name.isEmpty() ? p.name : "Unknown";
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                out.writeInt(nameBytes.length);
                out.write(nameBytes);
                out.writeInt(i == currentTurnIndex ? p.movesLeft : 0);
            }
            out.writeInt(currentTurnIndex);
            out.flush();
        } catch (IOException e) {
            // connection lost
        }
    }

    public void broadcastGameOver() throws IOException {
        List<Player> roster = new ArrayList<>(currentPlayers);
        int winnerIndex = 0;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < roster.size(); i++) {
            if (roster.get(i).score > best) {
                best = roster.get(i).score;
                winnerIndex = i;
            }
        }

        List<Player> all = new ArrayList<>();
        all.addAll(currentPlayers);
        all.addAll(waitingPlayers);
        List<Player> failed = new ArrayList<>();
        for (Player p : all) {
            try {
                DataOutputStream out = p.out;
                out.writeInt(GAME_OVER);
                out.writeInt(roster.size());
                for (Player pl : roster) {
                    String name = pl.name != null ? pl.name : "Unknown";
                    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(nameBytes.length);
                    out.write(nameBytes);
                    out.writeInt(pl.score);
                }
                out.writeInt(winnerIndex);
                out.flush();
            } catch (IOException ex) {
                failed.add(p);
            }
        }
        currentPlayers.removeAll(failed);
        waitingPlayers.removeAll(failed);
    }

    public void handleName(Player player, String name) {
        player.name = name.trim();
        System.out.println("Player registered: " + player.name);
        synchronized (lockObject) {
            broadcastScores();
            broadcastTurn();
        }
    }

    public void handleSetting(Player player, String setting) {
        if (!"LEADERBOARD".equalsIgnoreCase(setting.trim())) {
            return;
        }
        synchronized (lockObject) {
            sendLeaderboard(player);
        }
    }

    public void sendLeaderboard(Player player) {
        try {
            ArrayList<HashMap<String, String>> rows = Database.getScores();
            DataOutputStream out = player.out;
            out.writeInt(LEADERBOARD);
            out.writeInt(rows.size());
            for (HashMap<String, String> row : rows) {
                writeString(out, row.get("name"));
                out.writeInt(Integer.parseInt(row.get("score")));
                out.writeInt(Integer.parseInt(row.get("level")));
                writeString(out, row.get("time"));
            }
            out.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = (s != null ? s : "").getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static void main(String[] args) throws IOException, SQLException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 12345;
        new JokerServer(port);
    }
}