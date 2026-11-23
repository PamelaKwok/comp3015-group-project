import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Database {
    final static String url = "jdbc:sqlite:data/battleJoker.db";
    static Connection conn;

    public static void connect() throws SQLException {
        if (conn == null) {
            try {
                Files.createDirectories(Paths.get("data"));
            } catch (java.io.IOException ex) {
                throw new SQLException("Cannot create data directory", ex);
            }
            conn = DriverManager.getConnection(url);
            try (Statement statement = conn.createStatement()) {
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS scores (" +
                        "name TEXT NOT NULL," +
                        "score INTEGER NOT NULL," +
                        "level INTEGER NOT NULL," +
                        "time TEXT NOT NULL DEFAULT (datetime('now'))" +
                        ")"
                );
            }
        }
    }

    public static void disconnect() throws SQLException {
        if (conn != null) {
            conn.close();
            conn = null;
        }
    }

    public static ArrayList<HashMap<String, String>> getScores() throws SQLException {
        String sql = "SELECT * FROM scores ORDER BY score DESC LIMIT 10";
        ArrayList<HashMap<String, String>> data = new ArrayList<>();
        try (Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                HashMap<String, String> m = new HashMap<>();
                m.put("name", resultSet.getString("name"));
                m.put("score", resultSet.getString("score"));
                m.put("level", resultSet.getString("level"));
                m.put("time", resultSet.getString("time"));
                data.add(m);
            }
        }
        return data;
    }

    public static void putScore(String name, int score, int level) throws SQLException {
        String sql = "INSERT INTO scores (name, score, level, time) VALUES (?, ?, ?, datetime('now'))";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, score);
            ps.setInt(3, level);
            ps.executeUpdate();
        }
    }
}