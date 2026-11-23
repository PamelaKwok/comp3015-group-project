import java.util.Random;

public class GameEngine {
    public static final int LIMIT = 14;
    public static final int SIZE = 4;

    final int[] board = new int[SIZE * SIZE];
    int level = 1;
    private int numOfTilesMoved;

    public boolean isGameOver;

    private final Random random = new Random(0);

    public GameEngine() {
        nextRound();
    }

    public void reset() {
        for (int i = 0; i < board.length; i++) {
            board[i] = 0;
        }
        level = 1;
        isGameOver = false;
        numOfTilesMoved = 0;
        nextRound();
    }

    /**
     * @return true if the game ended on this command
     */
    public boolean processCommand(Player player, String dir) {
        if (isGameOver) {
            return true;
        }

        player.combo = 0;
        numOfTilesMoved = 0;

        switch (dir) {
            case "UP":
                for (int i = 0; i < SIZE; i++)
                    moveMerge(player, -SIZE, i, SIZE * (SIZE - 1) + i);
                break;
            case "DOWN":
                for (int i = 0; i < SIZE; i++)
                    moveMerge(player, SIZE, SIZE * (SIZE - 1) + i, i);
                break;
            case "LEFT":
                for (int i = 0; i <= SIZE * (SIZE - 1); i += SIZE)
                    moveMerge(player, -1, i, SIZE - 1 + i);
                break;
            case "RIGHT":
                for (int i = 0; i <= SIZE * (SIZE - 1); i += SIZE)
                    moveMerge(player, 1, SIZE - 1 + i, i);
                break;
            default:
                return false;
        }

        player.score += player.combo / 5 * 2;

        if (numOfTilesMoved > 0) {
            player.totalMoveCount++;
            if (level == LIMIT || !nextRound()) {
                isGameOver = true;
            }
        } else if (isFull()) {
            isGameOver = true;
        }

        return isGameOver;
    }

    private void moveMerge(Player player, int d, int s, int l) {
        int v, j;
        for (int i = s - d; i != l - d; i -= d) {
            j = i;
            if (board[j] <= 0) continue;
            v = board[j];
            board[j] = 0;
            while (j + d != s && board[j + d] == 0)
                j += d;

            if (board[j + d] == 0) {
                j += d;
                board[j] = v;
            } else {
                while (j != s && board[j + d] == v) {
                    j += d;
                    board[j] = 0;
                    v++;
                    player.score++;
                    player.combo++;
                }
                board[j] = v;
                if (v > level) level = v;
            }
            if (i != j)
                numOfTilesMoved++;
        }
    }

    private boolean isFull() {
        for (int v : board)
            if (v == 0) return false;
        return true;
    }

    private boolean nextRound() {
        if (isFull()) return false;
        int i;
        do {
            i = random.nextInt(SIZE * SIZE);
        } while (board[i] > 0);
        board[i] = random.nextInt(level) / 4 + 1;
        return true;
    }

    public int getLevel() {
        return level;
    }
}