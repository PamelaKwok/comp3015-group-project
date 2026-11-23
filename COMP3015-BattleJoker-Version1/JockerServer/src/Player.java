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
    public int movesLeft = 4;
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
                    // 第一個玩家，問佢開始定等
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
            if (len > buffer.length) buffer = new byte[len];
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
                    if ("START".equalsIgnoreCase(payload.trim()) && currentPlayers.size() >= 1 && !gameStarted) {
                        startGame();
                    }
                    break;
                case ASK_WAIT:
                    if ("LEAVE".equalsIgnoreCase(payload.trim())) {
                        waitingPlayers.remove(player);
                        player.out.close();
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
        if (engine.isGameOver || !gameStarted) return;

        synchronized (lockObject) {
            if (currentPlayers.isEmpty()) return;
            Player current = currentPlayers.get(currentTurnIndex);
            if (player != current || player.movesLeft <= 0) {
                return; // 唔係你回合或者冇步數
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
                // 換下一個人
                currentTurnIndex = (currentTurnIndex + 1) % currentPlayers.size();
                currentPlayers.get(currentTurnIndex).movesLeft = 4;
                broadcastTurn();
            } else {
                broadcastTurn(); // 更新剩餘步數
            }
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
                engine.isGameOver = false;
                if (!currentPlayers.isEmpty()) {
                    if (currentPlayers.size() == 1) {
                        sendAskStart(currentPlayers.get(0));
                    } else if (currentPlayers.size() == 4) {
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

    public boolean sendPuzzle(Player player) {//  }
    public void broadcastPuzzle() {
        for (Player p : new ArrayList<>(currentPlayers)) {
            if (!sendPuzzle(p)) currentPlayers.remove(p);
        }
        for (Player p : new ArrayList<>(waitingPlayers)) {
            if (!sendPuzzle(p)) waitingPlayers.remove(p);
        }
    }

    public void broadcastTurn() {
        for (Player p : new ArrayList<>(currentPlayers)) {
            sendTurn(p);
        }
        for (Player p : new ArrayList<>(waitingPlayers)) {
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
                writeString(out, p.name.isEmpty() ? "Unknown" : p.name);
                out.writeInt(i == currentTurnIndex ? p.movesLeft : 0);
            }
            out.writeInt(currentTurnIndex);
            out.flush();
        } catch (IOException e) {
        }
    }


    public static void main(String[] args) throws IOException, SQLException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 12345;
        new JokerServer(port);
    }
}