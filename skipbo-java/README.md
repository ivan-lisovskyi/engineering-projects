# Skip-Bo — Java client/server game

This was a university team project: a Java version of Skip-Bo that runs in the terminal. You can play a local game against computer players or start a server and connect from separate terminals.

There is more to the project than the card rules. The server has to check moves, keep each client's view of the game up to date and handle players taking turns. The code separates these jobs into the game model, network layer, message protocol and terminal display.

I worked on this project as part of a team. This repository contains the shared code; the saved files do not give a reliable breakdown of who wrote each module.

## What the project contains

- Games for 2–6 players, with human players, computer players or both.
- Stock, hand, discard and build piles, wild cards, rounds and match scoring.
- TCP messages for joining a game, making moves, receiving updates and chatting.
- A separate server thread for each client, with synchronized access to shared game state.
- An ANSI terminal display and 154 test methods for the rules, messages and networking.

The computer player follows a set of move priorities; it does not use machine learning. The server supports one active game room.

## Quick start: local game

You need JDK 23 and Maven 3.8 or later, with `java` and `mvn` available in your terminal. The game itself uses only the JDK; the tests use JUnit. Maven needs internet access for its first build.

From this project's directory:

```sh
mvn -DskipTests package
java -cp target/classes controller.MainAdvanced 2 1
```

This starts a game with one human and one computer player, without opening a server. Follow the terminal prompts. The two arguments are the total number of players (2–6) and the number of human players (0 through the total). For example, `4 1` means one human and three computer players. Use a terminal that supports ANSI colours, and press Ctrl+C to stop.

On macOS or Linux, you can also build and play without Maven:

```sh
mkdir -p target/classes
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 23 -encoding UTF-8 -d target/classes
java -cp target/classes controller.MainAdvanced 2 1
```

## Networked game

Use the network mode only on a trusted local network. The server listens on all network interfaces, even when the clients use `localhost`. It has no encrypted connection or account authentication, so do not expose it to the internet or set up port forwarding for it.

After building, open three terminals in this directory:

```sh
# Terminal 1: two players, zero server-side bots
java -cp target/classes networking.SkipBoServer 5555 2 0

# Terminal 2
java -cp target/classes networking.SkipBoClient localhost 5555 Alice 2

# Terminal 3
java -cp target/classes networking.SkipBoClient localhost 5555 Bob 2
```

The server optionally accepts `--log`. The client accepts `--bot` for a rule-based client player. Stop the server with Ctrl+C after the session.

### Network client commands

Indices are one-based. Build and discard pile numbers range from 1 to 4.

| Command | Action |
|---|---|
| `s 1` | Play the stock's top card to build pile 1 |
| `h 2 b 1` | Play hand card 2 to build pile 1 |
| `h 2 d 3` | Discard hand card 2 to discard pile 3 and end the turn |
| `d 3 b 1` | Play discard pile 3's top card to build pile 1 |
| `table` / `hand` | Request current table/hand state |
| `chat Hello` | Send a chat message |
| `game 2` | Request a two-player game |
| `help` / `quit` | Show commands / leave the client |

These examples show the command format. The server still checks whether each move is allowed in the current game.

## Tests

To run tests without opening sockets:

```sh
mvn '-Dtest=!NetworkingTest' test
```

The full suite includes `NetworkingTest`. It uses localhost clients, but its test server also listens on all interfaces. Run it only with suitable network isolation or firewall protection:

```sh
mvn test
```

Checked on 17 September 2026: all 101 Java files compiled with OpenJDK 23.0.1, and 132 non-network tests passed with JUnit 5.9.1. The 22 socket tests were not rerun. Maven was not installed for this check, so compilation and testing used the local JDK and JUnit directly. The `pom.xml` was checked, but the Maven build still needs a full run.

## Where to look in the code

| Directory | Responsibility |
|---|---|
| `src/main/java/model` | Cards, piles, players, moves and game rules |
| `src/main/java/networking` | Server, client, connection handlers and remote state |
| `src/main/java/protocol` | Command objects, encoder/parser and shared wire types |
| `src/main/java/view` | ANSI terminal rendering |
| `src/main/java/controller` | Local-game entry point |
| `src/test/java/tests` | JUnit tests and test helpers |

## About this copy

The Java source and tests are unchanged from the saved project. They have been placed in Maven's usual folder structure, with a new `pom.xml`, README and `.gitignore` to make the project easier to run. IDE files, compiled files, course handouts and student records are not included.

Skip-Bo is a commercial game. This student project is not affiliated with Mattel and includes no Mattel artwork. The team code has no new open-source licence. Permission from teammates and any course restrictions need to be checked before making it public or redistributing it.

Useful next changes would be a server option that accepts connections only from the same computer, and network tests that use that option. Keep any changes in separate commits from the original code, and add a test for the behaviour being changed.
