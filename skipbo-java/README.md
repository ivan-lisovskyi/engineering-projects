# Skip-Bo — Java client/server game

This was a university team project: a Java version of Skip-Bo that runs in the terminal. You can play a local game against computer players or start a server and connect from separate terminals.

The server checks moves, keeps the clients up to date and tracks whose turn it is. The code separates these jobs into the game model, network layer, message protocol and terminal display.

This is our shared team submission. The saved files do not show which person wrote each module.

## What the project contains

- Games for 2–6 players, with human players, computer players or both.
- Stock, hand, discard and build piles, wild cards, rounds and match scoring.
- TCP messages for joining a game, making moves, receiving updates and chatting.
- A separate server thread for each client, with synchronized access to shared game state.
- An ANSI terminal display and 154 test methods for the rules, messages and networking.

The computer player follows a set of move priorities; it does not use machine learning. The server supports one active game room.

## Quick start: local game

You need JDK 23, with `java` and `javac` available in your terminal. The game uses only the JDK; no other libraries are needed for local play. From this project folder on macOS or Linux:

```sh
mkdir -p target/classes
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 23 -encoding UTF-8 -d target/classes
java -cp target/classes controller.MainAdvanced 2 1
```

This starts one human and one computer player, without opening a server. The arguments are total players (2–6) and human players (0 through the total). For example, `4 1` gives one human and three computer players. Follow the terminal prompts; use an ANSI-capable terminal for the board and Ctrl+C to stop.

### Maven build

With Maven 3.8 or later, you can build the same game with:

```sh
mvn -DskipTests package
java -cp target/classes controller.MainAdvanced 2 1
```

Maven downloads build plugins and JUnit on its first run. This build file was added later and has not yet been tested through Maven; the JDK-only build above was checked.

## Network play

The [network-play guide](docs/NETWORK_PLAY.md) covers starting a server, connecting clients and entering moves. Use it only on a trusted local network: the server listens on all interfaces, even when clients connect to `localhost`. It has no encryption or account authentication. Do not expose it to the internet or forward its port.

## Tests

To run tests without opening sockets:

```sh
mvn '-Dtest=!NetworkingTest' test
```

The full suite includes `NetworkingTest`. It uses localhost clients, but its test server also listens on all interfaces. Run it only with suitable network isolation or firewall protection:

```sh
mvn test
```

**Checked on 17 September 2026:** all 101 Java files compiled with OpenJDK 23.0.1, and 132 non-network tests passed using JUnit 5.9.1 directly. The 22 socket tests were not rerun.

## Where to look in the code

| Directory | Responsibility |
|---|---|
| `src/main/java/model` | Cards, piles, players, moves and game rules |
| `src/main/java/networking` | Server, client, connection handlers and remote state |
| `src/main/java/protocol` | Command objects, encoder/parser and shared wire types |
| `src/main/java/view` | ANSI terminal rendering |
| `src/main/java/controller` | Local-game entry point |
| `src/test/java/tests` | JUnit tests and test helpers |

## Notes

The Java source and tests are unchanged; the Maven layout and build file were added for this copy. Skip-Bo is a commercial game, and this student project is not affiliated with Mattel. See the [project notes](../docs/PROJECT_NOTES.md) for credits and reuse permissions.

A useful next improvement would be a server option that accepts connections only from the same computer, with tests using that option.
