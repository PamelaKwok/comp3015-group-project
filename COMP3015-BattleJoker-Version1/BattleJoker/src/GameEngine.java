import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Client-side view of game state (authoritative logic runs on the server). */
public class GameEngine {
    public static final int NAME = 0;
    public static final int COMMAND = 1;
    public static final int SETTING = 2;
    public static final int PUZZLE = 3;
    public static final int SCORES = 5;
    public static final int GAME_OVER = 6;
    public static final int LEADERBOARD = 7;

    public static final int SIZE = 4;

    private static GameEngine instance;

    private final int[] board = new int[SIZE * SIZE];
    private boolean gameOver;
    private int level;
    private String playerName = "";

    private int localScore;
    private int localMoveCount;

    private final Map<String, Integer> scoresByName = new HashMap<>();
    private final List<String> sessionScoreLines = new ArrayList<>();
    private final List<String> leaderboardLines = new ArrayList<>();
    private boolean sessionScoresReady;
    private boolean leaderboardReady;
    private boolean gameOverDialogShown;

    private Consumer<Void> uiRefresh;

    private int currentTurnIndex = -1;
    private List<String> turnNames = new ArrayList<>();
    private List<Integer> turnMovesLeft = new ArrayList<>();
    private boolean myTurn = false;
    private int myMovesLeft = 0; // then isnt it have 2 0????



    private GameEngine() {
    }

    public static GameEngine getInstance() {
        if (instance == null) {
            instance = new GameEngine();
        }
        return instance;
    }

    public void setUiRefresh(Consumer<Void> uiRefresh) {
        this.uiRefresh = uiRefresh;
    }

    private void notifyUi() {
        if (uiRefresh != null) {
            uiRefresh.accept(null);
        }
    }

    public synchronized void applyPuzzle(int[] puzzle, boolean over, int newLevel) {
        System.arraycopy(puzzle, 0, board, 0, puzzle.length);
        gameOver = over;
        level = newLevel;
        notifyUi();
    }

    public synchronized void applyScores(List<String> names, List<Integer> scores, List<Integer> moves) {
        scoresByName.clear();
        for (int i = 0; i < names.size(); i++) {
            scoresByName.put(names.get(i), scores.get(i));
        }
        if (playerName != null && !playerName.isEmpty()) {
            Integer s = scoresByName.get(playerName);
            localScore = s != null ? s : 0;
            int idx = names.indexOf(playerName);
            if (idx >= 0 && idx < moves.size()) {
                localMoveCount = moves.get(idx);
            }
        }
        notifyUi();
    }

    public synchronized void applyGameOver(List<String> names, List<Integer> scores, int winnerIndex) {
        gameOver = true;
        sessionScoreLines.clear();
        for (int i = 0; i < names.size(); i++) {
            String prefix = (i == winnerIndex) ? "* " : "  ";
            sessionScoreLines.add(String.format("%s%s — %d", prefix, names.get(i), scores.get(i)));
        }
        sessionScoresReady = true;
        notifyUi();
    }

    public synchronized void applyLeaderboard(List<String> lines) {
        leaderboardLines.clear();
        leaderboardLines.addAll(lines);
        leaderboardReady = true;
        notifyUi();
    }

    public synchronized void applyTurn(List<String> names, List<Integer> movesLeft, int currentIndex) {
        this.turnNames = new ArrayList<>(names);
        this.turnMovesLeft = new ArrayList<>(movesLeft);
        this.currentTurnIndex = currentIndex;

        myTurn = false;
        myMovesLeft = 0;
        if (playerName != null && !playerName.isEmpty()) {
            for (int i = 0; i < names.size(); i++) {
                if (playerName.equals(names.get(i))) {
                    myMovesLeft = movesLeft.get(i);
                    myTurn = (i == currentIndex && myMovesLeft > 0);
                    break;
                }
            }
        }
        notifyUi();
    }

    public boolean isMyTurn() {
        return myTurn;
    }

    public int getMyMovesLeft() {
        return myMovesLeft;
    }

    public String getCurrentPlayerName() {
        if (currentTurnIndex >= 0 && currentTurnIndex < turnNames.size()) {
            return turnNames.get(currentTurnIndex);
        }
        return "";
    }

    public synchronized void clearLeaderboardReady() {
        leaderboardReady = false;
    }

    public synchronized int getValue(int r, int c) {
        return board[r * SIZE + c];
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setPlayerName(String name) {
        playerName = name;
    }

    public int getScore() {
        return localScore;
    }

    public int getLevel() {
        return level;
    }

    public int getMoveCount() {
        return localMoveCount;
    }

    public boolean isSessionScoresReady() {
        return sessionScoresReady;
    }

    public List<String> getSessionScoreLines() {
        return new ArrayList<>(sessionScoreLines);
    }

    public boolean isLeaderboardReady() {
        return leaderboardReady;
    }

    public List<String> getLeaderboardLines() {
        return new ArrayList<>(leaderboardLines);
    }

    public boolean isGameOverDialogShown() {
        return gameOverDialogShown;
    }

    public void setGameOverDialogShown(boolean shown) {
        gameOverDialogShown = shown;
    }


}
