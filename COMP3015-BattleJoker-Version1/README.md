# COMP3015-BattleJoker

Version 1 client–server Battle Joker (shared puzzle, server-authoritative rules).

## Layout

| Folder | Role |
|--------|------|
| `JockerServer/` | Server: `GameEngine`, `Database`, SQLite JARs (`lib/`), DB file `data/battleJoker.db` |
| `BattleJoker/` | JavaFX client: UI, networking only |

## Run

1. Put `slf4j-api-*.jar` and `sqlite-jdbc-*.jar` in `JockerServer/lib/` (classpath when compiling/running the server).
2. Start server: `JokerServer` (default port `12345`, or pass port as first argument).
3. Start client: `BattleJoker` — enter **name**, **server IP**, and **port**.
4. **Game → Top 10 scores...** requests historical leaderboard from the server.

## Version 1 checklist

- Card logic and SQLite on the server
- Client dialog: name, IP, port
- Moves via arrow keys → server → broadcast puzzle + scores to all clients
- Game over: server saves winner, sends session scores; client shows scoreboard
- Top 10 historical scores on client request (server `Database`)