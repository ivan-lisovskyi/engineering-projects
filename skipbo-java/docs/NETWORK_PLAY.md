# Playing over a network

[Back to Skip-Bo](../README.md)

Build the project using its README first. Run all commands below from the `skipbo-java` folder.

## Start a game

Use a trusted local network only. The server binds to all available interfaces, not just localhost. It has no encrypted connection or account authentication. Do not forward its port or expose it to the internet.

Open three terminals:

```sh
# Terminal 1: two players, zero server-side bots
java -cp target/classes networking.SkipBoServer 5555 2 0

# Terminal 2
java -cp target/classes networking.SkipBoClient localhost 5555 Alice 2

# Terminal 3
java -cp target/classes networking.SkipBoClient localhost 5555 Bob 2
```

The server accepts `--log` to enable logging. A client can use `--bot` for a rule-based computer player. Stop the server with Ctrl+C after the session. The server supports one active game room.

## Entering moves

Card indices start at 1. Build and discard pile numbers run from 1 to 4.

| Command | Action |
| --- | --- |
| `s 1` | Play the stock's top card to build pile 1 |
| `h 2 b 1` | Play hand card 2 to build pile 1 |
| `h 2 d 3` | Discard hand card 2 to discard pile 3 and end the turn |
| `d 3 b 1` | Play discard pile 3's top card to build pile 1 |
| `table` / `hand` | Request the current table or hand |
| `chat Hello` | Send a chat message |
| `game 2` | Request a two-player game |
| `help` / `quit` | Show commands or leave the client |

The server checks each move against the game state. An example command may not be legal on your current turn.

## Network tests

`mvn test` includes 22 socket tests in `NetworkingTest`. Their clients connect to localhost, but the temporary server still binds to all interfaces. Run those tests only with suitable network isolation or firewall protection. They were not part of the 132-test check recorded in the README.
