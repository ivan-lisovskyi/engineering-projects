package tests;

import networking.ClientHandler;
import networking.RemoteGameState;
import networking.ServerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import protocol.Command;
import protocol.ProtocolException;
import protocol.client.Game;
import protocol.client.Hand;
import protocol.client.Hello;
import protocol.client.Play;
import protocol.client.Table;
import protocol.common.ErrorCode;
import protocol.common.Feature;
import protocol.common.position.NumberedPilePosition;
import protocol.common.position.Position;
import protocol.common.position.StockPilePosition;
import protocol.server.Queue;
import protocol.server.Start;
import protocol.server.Welcome;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-style tests for networking handshake and gameplay protocol flows.
 * @ensures Server/client interactions are validated using test sockets.
 */
public class NetworkingTest {
    private TestServer server;

    /**
     * Closes the test server if it was started.
     * @ensures Server sockets and threads are shut down.
     */
    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    /**
     * Verifies HELLO handshake receives WELCOME.
     * @requires Server is running and a client connects.
     * @ensures WELCOME is received after HELLO.
     */
    @Test
    void helloHandshakeSendsWelcome() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new Welcome("Alice", new Feature[0]).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies HELLO without a feature field is accepted.
     * @requires Server is running and a client connects.
     * @ensures WELCOME is received for HELLO without features.
     */
    @Test
    void helloWithoutFeatureFieldIsAccepted() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send("HELLO~Alice");
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new Welcome("Alice", new Feature[0]).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies duplicate player names are rejected.
     * @requires One client has already claimed a name.
     * @ensures Second client receives NAME_IN_USE error.
     */
    @Test
    void duplicateNameIsRejected() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient first = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(1));
            try (TestClient second = server.connect()) {
                second.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
                List<String> lines = second.drainLines(Duration.ofSeconds(1));
                String expected = new protocol.server.Error(ErrorCode.NAME_IN_USE).transformToProtocolString();
                assertTrue(lines.contains(expected));
            }
        }
    }

    /**
     * Verifies invalid player names are rejected.
     * @requires Client sends an invalid name token.
     * @ensures INVALID_PLAYER_NAME error is returned.
     */
    @Test
    void invalidNameIsRejected() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send(new Hello("Bad Name", new Feature[0]).transformToProtocolString());
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.INVALID_PLAYER_NAME).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies HELLO rejects unsorted features.
     * @requires Client sends features out of order.
     * @ensures INVALID_COMMAND error is returned.
     */
    @Test
    void helloRejectsUnsortedFeatures() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send("HELLO~Alice~LC");
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies HELLO rejects duplicate features.
     * @requires Client sends duplicate feature flags.
     * @ensures INVALID_COMMAND error is returned.
     */
    @Test
    void helloRejectsDuplicateFeatures() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send("HELLO~Alice~CC");
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies the second client can start the game without COMMAND_NOT_ALLOWED.
     * @requires Two clients connect and request a 2-player game.
     * @ensures START is delivered without a COMMAND_NOT_ALLOWED error for the second client.
     */
    @Test
    void secondClientStartDoesNotGetNotAllowed() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient first = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(1));
            first.send(new Game(2).transformToProtocolString());
            List<String> firstLines = first.drainLines(Duration.ofSeconds(1));
            String queue = new Queue().transformToProtocolString();
            assertTrue(firstLines.contains(queue));

            try (TestClient second = server.connect()) {
                second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());
                second.send(new Game(2).transformToProtocolString());
                List<String> secondLines = second.drainLines(Duration.ofSeconds(2));
                String start = new Start(new String[]{"Alice", "Bob"}).transformToProtocolString();
                assertTrue(secondLines.contains(start));
                String notAllowed = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED)
                        .transformToProtocolString();
                assertFalse(secondLines.contains(notAllowed));
            }
        }
    }

    /**
     * Verifies welcome broadcasts and START delivery for lobby game creation.
     * @requires Two clients connect and request a game.
     * @ensures WELCOME and START are broadcast to both clients.
     */
    @Test
    void welcomeBroadcastsAndStartForLobby() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient first = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(1));
            first.send(new Game(2).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(1));

            try (TestClient second = server.connect()) {
                second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());
                second.send(new Game(2).transformToProtocolString());

                List<String> firstLines = first.drainLines(Duration.ofSeconds(2));
                List<String> secondLines = second.drainLines(Duration.ofSeconds(2));

                String welcomeBob = new Welcome("Bob", new Feature[0]).transformToProtocolString();
                String start = new Start(new String[]{"Alice", "Bob"}).transformToProtocolString();
                String notAllowed = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED)
                        .transformToProtocolString();

                assertTrue(firstLines.contains(welcomeBob));
                assertTrue(firstLines.contains(start));
                assertTrue(secondLines.contains(start));
                assertFalse(firstLines.contains(notAllowed));
                assertFalse(secondLines.contains(notAllowed));
            }
        }
    }

    /**
     * Verifies game requests are rejected when too many players are connected.
     * @requires More clients are connected than the requested game size.
     * @ensures COMMAND_NOT_ALLOWED error is returned.
     */
    @Test
    void gameRequestRejectedWhenTooManyPlayers() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient first = server.connect();
             TestClient second = server.connect();
             TestClient third = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());
            third.send(new Hello("Charlie", new Feature[0]).transformToProtocolString());

            first.drainLines(Duration.ofSeconds(1));
            second.drainLines(Duration.ofSeconds(1));
            third.drainLines(Duration.ofSeconds(1));

            first.send(new Game(2).transformToProtocolString());
            List<String> response = first.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            assertTrue(response.contains(expected));
        }
    }

    /**
     * Verifies commands before HELLO are rejected.
     * @requires Client sends a command before HELLO.
     * @ensures COMMAND_NOT_ALLOWED error is returned.
     */
    @Test
    void commandBeforeHelloIsRejected() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send(new Table().transformToProtocolString());
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies invalid player counts are rejected on GAME.
     * @requires Client requests a game with an invalid player count.
     * @ensures INVALID_COMMAND error is returned.
     */
    @Test
    void gameRequestRejectsInvalidPlayerCount() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            client.drainLines(Duration.ofSeconds(1));
            client.send(new Game(1).transformToProtocolString());
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies HAND is rejected before the game starts.
     * @requires Client sends HAND before START.
     * @ensures COMMAND_NOT_ALLOWED error is returned.
     */
    @Test
    void handBeforeGameStartIsRejected() throws IOException {
        server = new TestServer(null, 0);
        try (TestClient client = server.connect()) {
            client.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            client.drainLines(Duration.ofSeconds(1));
            client.send(new Hand().transformToProtocolString());
            List<String> lines = client.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            assertTrue(lines.contains(expected));
        }
    }

    /**
     * Verifies HAND with extra arguments is rejected.
     * @requires Client sends HAND with extra payload.
     * @ensures INVALID_COMMAND error is returned.
     */
    @Test
    void handRejectsExtraArgs() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient first = server.connect(); TestClient second = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(2));
            second.drainLines(Duration.ofSeconds(2));

            first.send("HAND~X");
            List<String> response = first.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            assertTrue(response.contains(expected));
        }
    }

    /**
     * Verifies disconnect ends the room and rejects subsequent commands.
     * @requires Two clients are connected and one disconnects.
     * @ensures Remaining client receives disconnect error and future commands return it.
     */
    @Test
    void disconnectEndsRoomAndRejectsLaterCommands() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient first = server.connect(); TestClient second = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(2));
            second.drainLines(Duration.ofSeconds(2));

            second.close();
            String disconnect = new protocol.server.Error(ErrorCode.PLAYER_DISCONNECTED).transformToProtocolString();
            List<String> firstLines = first.drainLines(Duration.ofSeconds(2));
            assertTrue(firstLines.contains(disconnect));

            first.send(new Table().transformToProtocolString());
            List<String> response = first.drainLines(Duration.ofSeconds(1));
            assertTrue(response.contains(disconnect));
        }
    }

    /**
     * Verifies HELLO is rejected after the game has started.
     * @requires A game has started with existing clients.
     * @ensures Late HELLO receives COMMAND_NOT_ALLOWED error.
     */
    @Test
    void helloAfterGameStartsIsRejected() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient first = server.connect(); TestClient second = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(2));
            second.drainLines(Duration.ofSeconds(2));

            try (TestClient late = server.connect()) {
                late.send(new Hello("Charlie", new Feature[0]).transformToProtocolString());
                List<String> lines = late.drainLines(Duration.ofSeconds(1));
                String expected = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
                assertTrue(lines.contains(expected));
            }
        }
    }

    /**
     * Verifies a non-current player cannot PLAY.
     * @requires Game is started and it is another player's turn.
     * @ensures COMMAND_NOT_ALLOWED error is returned.
     */
    @Test
    void playFromNonCurrentPlayerIsRejected() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient first = server.connect(); TestClient second = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());

            String turnPlayer = findTurnPlayer(first.drainLines(Duration.ofSeconds(2)));
            if (turnPlayer == null) {
                turnPlayer = findTurnPlayer(second.drainLines(Duration.ofSeconds(2)));
            }
            assertNotNull(turnPlayer);

            TestClient nonCurrent = "Alice".equals(turnPlayer) ? second : first;
            Play play = new Play(
                    new StockPilePosition(),
                    new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, 0)
            );
            nonCurrent.send(play.transformToProtocolString());
            List<String> response = nonCurrent.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            assertTrue(response.contains(expected));
        }
    }

    /**
     * Verifies invalid commands during a game are rejected.
     * @requires Game is in progress.
     * @ensures INVALID_COMMAND error is returned.
     */
    @Test
    void invalidCommandInGameIsRejected() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient first = server.connect(); TestClient second = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());
            first.drainLines(Duration.ofSeconds(2));
            second.drainLines(Duration.ofSeconds(2));

            first.send(new Start(new String[]{"Alice", "Bob"}).transformToProtocolString());
            List<String> response = first.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            assertTrue(response.contains(expected));
        }
    }

    /**
     * Verifies invalid move tokens are rejected for the current player.
     * @requires Game is in progress and it is the sender's turn.
     * @ensures INVALID_COMMAND error is returned.
     */
    @Test
    void invalidMoveTokenRejectedForCurrentPlayer() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient first = server.connect(); TestClient second = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());

            List<String> firstLines = first.drainLines(Duration.ofSeconds(2));
            List<String> secondLines = second.drainLines(Duration.ofSeconds(2));
            String turnPlayer = findTurnPlayer(firstLines);
            if (turnPlayer == null) {
                turnPlayer = findTurnPlayer(secondLines);
            }
            assertNotNull(turnPlayer);

            TestClient current = "Alice".equals(turnPlayer) ? first : second;
            Play play = new Play(
                    new InvalidPosition("X"),
                    new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, 0)
            );
            current.send(play.transformToProtocolString());
            List<String> response = current.drainLines(Duration.ofSeconds(1));
            String expected = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            assertTrue(response.contains(expected));
        }
    }

    /**
     * Verifies discard plays broadcast PLAY and send HAND only to the mover.
     * @requires Game is in progress and current player discards.
     * @ensures PLAY is broadcast and HAND is only sent to the mover.
     */
    @Test
    void discardPlayBroadcastsPlayAndHandToMover() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient first = server.connect(); TestClient second = server.connect()) {
            first.send(new Hello("Alice", new Feature[0]).transformToProtocolString());
            second.send(new Hello("Bob", new Feature[0]).transformToProtocolString());

            List<String> firstLines = first.drainLines(Duration.ofSeconds(2));
            List<String> secondLines = second.drainLines(Duration.ofSeconds(2));
            String turnPlayer = findTurnPlayer(firstLines);
            if (turnPlayer == null) {
                turnPlayer = findTurnPlayer(secondLines);
            }
            assertNotNull(turnPlayer);

            TestClient current = "Alice".equals(turnPlayer) ? first : second;
            TestClient other = current == first ? second : first;
            List<String> currentLines = current == first ? firstLines : secondLines;

            String cardToken = firstHandToken(currentLines);
            if (cardToken == null) {
                current.send(new Hand().transformToProtocolString());
                cardToken = firstHandToken(current.drainLines(Duration.ofSeconds(1)));
            }
            assertNotNull(cardToken);

            current.drainLines(Duration.ofMillis(200));
            other.drainLines(Duration.ofMillis(200));

            protocol.common.Card card = parseProtocolCard(cardToken);
            assertNotNull(card);
            Play play = new Play(
                    new protocol.common.position.HandPosition(card),
                    new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, 0)
            );
            current.send(play.transformToProtocolString());

            List<String> currentAfter = current.drainLines(Duration.ofSeconds(1));
            List<String> otherAfter = other.drainLines(Duration.ofSeconds(1));
            String expectedPlay = "PLAY~" + turnPlayer + "~H." + cardToken + "~D.0";
            assertTrue(currentAfter.contains(expectedPlay));
            assertTrue(otherAfter.contains(expectedPlay));

            boolean currentHand = currentAfter.stream().anyMatch(line -> line.startsWith("HAND~"));
            boolean otherHand = otherAfter.stream().anyMatch(line -> line.startsWith("HAND~"));
            assertTrue(currentHand);
            assertFalse(otherHand);

            current.send(new protocol.client.End().transformToProtocolString());
            current.drainLines(Duration.ofSeconds(1));
            other.drainLines(Duration.ofSeconds(1));
        }
    }

    /**
     * Verifies stock plays are broadcast to all players.
     * @requires Game is in progress and a stock play is attempted.
     * @ensures PLAY is broadcast and stock updates are sent.
     */
    @Test
    void stockPlayBroadcastsToAllPlayers() throws IOException {
        server = new TestServer(2, 0);
        try (TestClient alice = server.connect(); TestClient bob = server.connect()) {
            alice.send("HELLO~Alice~");
            bob.send("HELLO~Bob~");
            List<String> aliceLines = alice.drainLines(Duration.ofSeconds(2));
            List<String> bobLines = bob.drainLines(Duration.ofSeconds(2));

            String turnPlayer = findTurnPlayer(aliceLines);
            if (turnPlayer == null) {
                turnPlayer = findTurnPlayer(bobLines);
            }
            if (turnPlayer == null) {
                aliceLines.addAll(alice.drainLines(Duration.ofSeconds(1)));
                bobLines.addAll(bob.drainLines(Duration.ofSeconds(1)));
                turnPlayer = findTurnPlayer(aliceLines);
                if (turnPlayer == null) {
                    turnPlayer = findTurnPlayer(bobLines);
                }
            }
            assertNotNull(turnPlayer);

            TestClient current = "Alice".equals(turnPlayer) ? alice : bob;
            TestClient other = current == alice ? bob : alice;

            current.drainLines(Duration.ofMillis(100));
            other.drainLines(Duration.ofMillis(100));

            current.send("PLAY~S~B.0");

            List<String> currentResp = current.drainLines(Duration.ofSeconds(1));
            List<String> otherResp = other.drainLines(Duration.ofSeconds(1));

            boolean hasPlay = currentResp.stream().anyMatch(line -> line.startsWith("PLAY~"));
            boolean hasError = currentResp.stream().anyMatch(line -> line.startsWith("ERROR~206"));
            assertTrue(hasPlay || hasError, "Should get PLAY broadcast or invalid move error");

            if (hasPlay) {
                assertTrue(otherResp.stream().anyMatch(line -> line.startsWith("PLAY~")),
                        "Other player should receive PLAY broadcast");
                boolean hasStock = currentResp.stream().anyMatch(line -> line.startsWith("STOCK~"))
                        || otherResp.stream().anyMatch(line -> line.startsWith("STOCK~"));
                assertTrue(hasStock, "STOCK update should be broadcast after stock play");
            }
        }
    }

    /**
     * Verifies a seventh player is rejected in a six-player lobby.
     * @requires Six players are already connected.
     * @ensures Seventh player receives COMMAND_NOT_ALLOWED error.
     */
    @Test
    void gameRejectsSeventhPlayer() throws IOException {
        server = new TestServer(6, 0);
        List<TestClient> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                TestClient client = server.connect();
                clients.add(client);
                client.send("HELLO~Player" + i + "~");
                client.drainLines(Duration.ofSeconds(1));
            }

            try (TestClient extra = server.connect()) {
                extra.send("HELLO~Extra~");
                List<String> response = extra.drainLines(Duration.ofSeconds(1));
                assertTrue(response.stream().anyMatch(line -> line.contains("ERROR~205")),
                        "7th player should get COMMAND_NOT_ALLOWED");
            }
        } finally {
            for (TestClient client : clients) {
                try {
                    client.close();
                } catch (Exception ignored) {
                    // Ignore close errors.
                }
            }
        }
    }

    /**
     * Verifies ROUND messages update scores and trigger render.
     * @requires RemoteGameState is synchronized.
     * @ensures Scores are updated after a ROUND message.
     */
    @Test
    void roundScoresAreBroadcast() throws IOException {
        RemoteGameState state = new RemoteGameState("Alice");
        state.applyMessage("START~Alice,Bob");
        state.applyMessage("HAND~1,2,3,4,5");
        state.applyMessage("STOCK~Alice~5");
        state.applyMessage("STOCK~Bob~3");
        state.applyMessage("TURN~Alice");

        RemoteGameState.Update update = state.applyMessage("ROUND~Alice.100,Bob.50");

        assertTrue(update.shouldRender);
        assertEquals(100, state.getScore("Alice"));
        assertEquals(50, state.getScore("Bob"));
    }

    /**
     * Finds the current turn player from a list of protocol lines.
     * @param lines lines to search
     * @return player name or null
     * @requires lines is not null.
     * @ensures Returns the turn player if present.
     */
    private String findTurnPlayer(List<String> lines) {
        String prefix = "TURN" + Command.SEPERATOR;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    /**
     * Extracts the first card token from a HAND message.
     * @param lines lines to search
     * @return card token in upper case or null
     * @requires lines is not null.
     * @ensures Returns the first hand token if present.
     */
    private String firstHandToken(List<String> lines) {
        String prefix = "HAND" + Command.SEPERATOR;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                String payload = line.substring(prefix.length()).trim();
                if (!payload.isEmpty()) {
                    String[] cards = payload.split(Command.LIST_SEPERATOR);
                    if (cards.length > 0 && !cards[0].isBlank()) {
                        return cards[0].trim().toUpperCase(Locale.ROOT);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Parses a protocol card token into a protocol.common.Card.
     * @param token card token
     * @return parsed card or null if invalid
     * @ensures Returns a card for valid tokens and null otherwise.
     */
    private protocol.common.Card parseProtocolCard(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = token.trim().toUpperCase(Locale.ROOT);
        try {
            if (value.startsWith("SB")) {
                return new protocol.common.Card((Integer) null);
            }
            return new protocol.common.Card(Integer.parseInt(value));
        } catch (ProtocolException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * Invalid position token implementation for negative tests.
     * @ensures #toString() returns the provided token.
     */
    private static final class InvalidPosition implements Position {
        private final String token;

        /**
         * Creates an invalid position with the given token.
         * @param token raw token string
         * @requires token is not null.
         * @ensures #toString() returns token.
         */
        private InvalidPosition(String token) {
            this.token = token;
        }

        /**
         * Returns the raw token.
         * @return token string
         * @ensures Returns the token passed at construction.
         */
        @Override
        public String toString() {
            return token;
        }
    }

    /**
     * Test server that accepts clients and wires them to ServerState.
     * @ensures Accepts client connections on a local port.
     */
    private static final class TestServer implements AutoCloseable {
        private final ServerState state;
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean running = true;

        /**
         * Creates and starts a test server.
         * @param expectedPlayers expected number of human players
         * @param bots number of bot players
         * @ensures Server socket is listening and accept thread is started.
         */
        private TestServer(Integer expectedPlayers, int bots) throws IOException {
            this.state = new ServerState(expectedPlayers, bots);
            this.serverSocket = new ServerSocket(0);
            this.acceptThread = new Thread(this::acceptLoop, "test-accept");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        /**
         * Returns the local port for the server socket.
         * @return port number
         * @requires Server socket is open.
         * @ensures Returns the bound local port.
         */
        private int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * Accepts client sockets and starts a ClientHandler per client.
         * @requires Server is running.
         * @ensures Each accepted socket has a handler thread started.
         */
        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    sockets.add(socket);
                    ClientHandler handler = new ClientHandler(socket, state);
                    Thread thread = new Thread(handler, "test-client-" + socket.getPort());
                    thread.setDaemon(true);
                    thread.start();
                } catch (SocketException e) {
                    break;
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                }
            }
        }

        /**
         * Opens a new client connection to the server.
         * @return connected TestClient
         * @requires Server is running.
         * @ensures Returned client is connected and configured.
         */
        private TestClient connect() throws IOException {
            Socket socket = new Socket("localhost", getPort());
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(500);
            sockets.add(socket);
            return new TestClient(socket);
        }

        /**
         * Closes all sockets and stops the accept thread.
         * @ensures Server sockets are closed and accept thread is stopped.
         */
        @Override
        public void close() throws IOException {
            running = false;
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Ignore close errors.
                }
            }
            serverSocket.close();
            try {
                acceptThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Simple client wrapper for sending and receiving protocol lines.
     * @ensures Provides buffered I/O for a connected socket.
     */
    private static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;

        /**
         * Creates a client wrapper for a connected socket.
         * @param socket connected socket
         * @requires socket is connected.
         * @ensures Input/output streams are initialized.
         */
        private TestClient(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        }

        /**
         * Sends a line to the server.
         * @param message protocol line
         * @requires message is not null.
         * @ensures Message is written to the socket output.
         */
        private void send(String message) {
            writer.println(message);
            writer.flush();
        }

        /**
         * Drains all available lines until the timeout elapses.
         * @param timeout maximum time to wait
         * @return list of received lines
         * @requires timeout is non-null and non-negative.
         * @ensures Returns all lines received before the deadline.
         */
        private List<String> drainLines(Duration timeout) throws IOException {
            long deadline = System.nanoTime() + timeout.toNanos();
            List<String> lines = new ArrayList<>();
            while (System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                int waitMs = (int) Math.min(200, TimeUnit.NANOSECONDS.toMillis(Math.max(remaining, 0)));
                if (waitMs <= 0) {
                    break;
                }
                socket.setSoTimeout(waitMs);
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    lines.add(line);
                } catch (SocketTimeoutException ignored) {
                    // Keep waiting until deadline.
                }
            }
            return lines;
        }

        /**
         * Closes the client socket.
         * @ensures Socket is closed.
         */
        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
