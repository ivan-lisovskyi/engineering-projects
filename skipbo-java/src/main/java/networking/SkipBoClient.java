package networking;

import model.moves.CardMove;
import model.moves.Move;
import model.moves.StockMove;
import view.RemoteTuiView;

import protocol.ProtocolCodec;
import protocol.ProtocolException;
import protocol.client.Chat;
import protocol.client.End;
import protocol.client.Game;
import protocol.client.Hand;
import protocol.client.Hello;
import protocol.client.Play;
import protocol.client.Table;
import protocol.common.Card;
import protocol.common.Feature;
import protocol.common.position.HandPosition;
import protocol.common.position.NumberedPilePosition;
import protocol.common.position.StockPilePosition;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Command-line entry point for the Skip-Bo client.
 * This class establishes a TCP connection, performs the initial HELLO/GAME handshake,
 * and then runs either a human-driven input loop or a naive bot loop. It also renders
 * the remote state to the terminal by applying incoming protocol messages to
 * RemoteGameState.
 */
public class SkipBoClient {

    /**
     * Entry point for the Skip-Bo client application.
     * Arguments:
     *   args[0]: server host
     *   args[1]: server port
     *   args[2]: player name
     *   args[3]: optional player count
     *   bot: run in naive bot mode
     *   assume-stock: assume correct stock counts<
     * Connects to the server, sends HELLO (and optional GAME), starts a reader thread,
     * then runs either a human input loop or a bot loop until the connection ends.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        // Parse basic CLI arguments and optional flags.
        if (args.length < 3) {
            System.out.println("Usage: java networking.SkipBoClient <host> <port> <name> [players] [--bot] [--assume-stock]");
            return;
        }

        String host = args[0];
        int port;
        String name = args[2];
        Integer players = null;
        boolean assumeStockCounts = false;
        boolean botMode = false;

        try {
            port = Integer.parseInt(args[1]);
            for (int i = 3; i < args.length; i++) {
                String arg = args[i];
                if ("--assume-stock".equalsIgnoreCase(arg)) {
                    assumeStockCounts = true;
                    continue;
                }
                if ("--bot".equalsIgnoreCase(arg)) {
                    botMode = true;
                    continue;
                }
                if (players == null) {
                    players = Integer.parseInt(arg);
                } else {
                    System.out.println("Unexpected argument: " + arg);
                    return;
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Port (and players) must be numbers.");
            return;
        }

        // Localhost runs typically keep stock counts in sync, so allow it by default.
        if (!assumeStockCounts && isLocalHost(host)) {
            assumeStockCounts = true;
        }

        final boolean botEnabled = botMode;

        // Connect to server and initialize local state.
        try (ClientConnection connection = new ClientConnection(host, port)) {
            RemoteGameState state = new RemoteGameState(name, assumeStockCounts);
            AtomicBoolean running = new AtomicBoolean(true);

            // HELLO handshake (and optional GAME request for humans).
            Feature[] features = parseFeatures("");
            connection.send(hello(name, features));
            if (!botMode && players != null) {
                connection.send(game(players));
            }

            // Reader thread applies server updates and triggers rendering.
            AtomicBoolean deferLocalPlayRender = new AtomicBoolean(false);
            AtomicBoolean awaitTurnRender = new AtomicBoolean(false);
            // Background reader keeps state in sync and drives rendering.
            Thread reader = new Thread(() -> {
                try {
                    // Read lines until the server closes the connection.
                    String line;
                    while ((line = connection.readLine()) != null) {
                        // Split command for lightweight routing (render decisions use it).
                        String[] parts = line.split(Pattern.quote(protocol.Command.SEPERATOR), -1);
                        String command = parts.length > 0 ? parts[0].trim().toUpperCase(Locale.ROOT) : "";
                        // Special-case disconnect to force a table refresh if the match continues.
                        boolean disconnectNotice = line.startsWith("ERROR" + protocol.Command.SEPERATOR + "103");
                        // Apply message to cached state.
                        RemoteGameState.Update update = state.applyMessage(line);
                        if (disconnectNotice && state.isStarted() && state.getPlayers().size() > 1) {
                            connection.send(table());
                        }
                        if (update.notice != null) {
                            System.out.println(update.notice);
                        }
                        if (!botEnabled && update.shouldRender && state.isStarted()) {
                            // Delay render until TURN/WINNER/ROUND after local END.
                            if (awaitTurnRender.get()) {
                                if ("TURN".equals(command) || "WINNER".equals(command) || "ROUND".equals(command)) {
                                    awaitTurnRender.set(false);
                                    RemoteTuiView.printBoard(System.out, state, name);
                                }
                                continue;
                            }
                            // Defer render for plays from hand/stock to avoid double draw.
                            // Stock plays always have a STOCK message following, so we defer
                            // for all players (not just local) to avoid showing "??" briefly.
                            if ("PLAY".equals(command)) {
                                String player = parts.length > 1 ? parts[1].trim() : "";
                                String fromToken = parts.length > 2 ? parts[2].trim() : "";
                                ProtocolCodec.PositionToken from = ProtocolCodec.parsePositionToken(fromToken);
                                // Defer for any stock play (STOCK message follows) or local hand play
                                if (from != null && from.kind == ProtocolCodec.PositionKind.STOCK) {
                                    deferLocalPlayRender.set(true);
                                    continue;
                                }
                                if (player.equals(name) && from != null
                                        && from.kind == ProtocolCodec.PositionKind.HAND) {
                                    deferLocalPlayRender.set(true);
                                    continue;
                                }
                                RemoteTuiView.printBoard(System.out, state, name);
                                continue;
                            }

                            if (deferLocalPlayRender.get()) {
                                // Render once after the follow-up server update arrives.
                                deferLocalPlayRender.set(false);
                                RemoteTuiView.printBoard(System.out, state, name);
                                continue;
                            }
                            RemoteTuiView.printBoard(System.out, state, name);
                        }
                    }
                } catch (IOException ignored) {
                    // Connection closed.
                } finally {
                    running.set(false);
                }
            }, "server-reader");
            reader.setDaemon(true);
            reader.start();

            // Run either the bot loop or the interactive human loop.
            if (botEnabled) {
                runBot(connection, state, name, players, running);
            } else {
                runHuman(connection, state, name, running, awaitTurnRender);
            }
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    /**
     * Executes the main loop for a human player's gameplay interaction
     * Handles user commands and routes them to the game server or performs local actions
     * @param connection The client connection used to send commands to the game server
     * @param state The remote game state, which represents the current state of the game and is used to
     *            validate actions and manage updates
     * @param localName The name of the local player
     * @param running A flag indicating whether the game loop should continue running. This allows
     *             external control to terminate the loop, effectively stopping gameplay interaction.
     */
    private static void runHuman(ClientConnection connection, RemoteGameState state,
                                 String localName, AtomicBoolean running,
                                 AtomicBoolean awaitTurnRender) {
        printHelp();
        Scanner scanner = new Scanner(System.in);
        while (running.get()) {
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.equals("quit") || lower.equals("exit")) {
                break;
            }
            if (lower.equals("help")) {
                printHelp();
                continue;
            }
            if (lower.startsWith("game")) {
                Integer count = parseGameCount(lower);
                if (count != null) {
                    connection.send(game(count));
                }
                continue;
            }

            if (!state.isStarted()) {
                System.out.println("Game has not started yet.");
                continue;
            }

            if (lower.equals("table")) {
                connection.send(table());
                continue;
            }
            if (lower.equals("hand")) {
                connection.send(hand());
                continue;
            }
            if (lower.equals("chat")) {
                System.out.println("Usage: chat <message>");
                continue;
            }
            if (lower.startsWith("chat ")) {
                String message = line.substring(5).trim();
                if (message.isEmpty()) {
                    System.out.println("Usage: chat <message>");
                } else {
                    connection.send(chat(message));
                }
                continue;
            }
            if (lower.equals("end")) {
                connection.send(end());
                awaitTurnRender.set(true);
                state.setLocalEvent("Turn ended. Waiting for next player.");
                continue;
            }

            if (!state.isYourTurn()) {
                System.out.println("Not your turn.");
                continue;
            }

            if (!handleMove(connection, state, line, awaitTurnRender)) {
                System.out.println("Invalid command. Type 'help' for options.");
            }
        }
    }

    /**
     * Executes the main bot loop for interacting with the game autonomously.
     * The bot optionally initializes a game request, continuously checks the
     * game state, and plays its turn when it is allowed to do so. The loop runs
     * until the game is finished or the running flag is set to false.
     * @param connection The client connection used to interact with the game server.
     * @param state The remote game state that represents the current state of the game.
     * @param localName The name of the local bot player.
     * @param players The number of players for the game. If null, no game request is made.
     * @param running A flag indicating whether the bot loop should continue running.
     */
    private static void runBot(ClientConnection connection, RemoteGameState state, String localName,
                               Integer players, AtomicBoolean running) {
        if (players != null) {
            maybeRequestGame(connection, state, players);
        }

        while (running.get()) {
            if (state.isFinished()) {
                break;
            }
            if (state.isStarted() && state.isYourTurn()) {
                playTurn(connection, state, localName);
            }
            try {
                Thread.sleep(40L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Plays a single turn for the current Bot in the game, managing moves, synchronization,
     * and state updates as needed. The method ensures valid gameplay by handling errors,
     * waiting for updates, and maintaining synchronization with the game server.
     * @param connection The client connection used to send and receive messages from the game server.
     * @param state The remote game state that tracks the current state of the game, used for validation and updates.
     * @param localName The name of the local player currently taking the turn.
     */
    private static void playTurn(ClientConnection connection, RemoteGameState state, String localName) {
        if (state.isFinished()) {
            return;
        }
        syncState(connection, state); //sync bot with server
        while (state.isYourTurn()) {
            long version = state.getVersion();
            String lastEvent = state.getLastEvent();
            Move move = determineNaiveMove(state, localName);
            // if no legal move available -> do random discard
            if (move == null) {
                if (sendRandomDiscard(connection, state)) {
                    connection.send(end());
                    waitForUpdate(state, version, lastEvent);
                } else {
                    syncState(connection, state);
                }
                return;
            }
            boolean sent = sendMove(connection, state, move);
            if (!sent) {
                if (sendRandomDiscard(connection, state)) {
                    connection.send(end());
                    waitForUpdate(state, version, lastEvent);
                } else {
                    syncState(connection, state);
                }
                return;
            }
            if (move.endTurn()) {
                boolean error = waitForUpdate(state, version, lastEvent);
                if (error) {
                    syncState(connection, state);
                    continue;
                }
                long endVersion = state.getVersion();
                String endEvent = state.getLastEvent();
                connection.send(end());
                waitForUpdate(state, endVersion, endEvent);
                return;
            }
            boolean error = waitForUpdate(state, version, lastEvent);
            if (error) {
                syncState(connection, state);
            }
        }
    }

    /**
     * Processes a player's move command and determines the corresponding action to perform.
     * Validates the input command, parses necessary details, and sends appropriate messages
     * to the game server for executing the move. The method supports moves such as playing
     * cards from the stock, hand, or discard piles, and handles scenarios where input commands
     * are incomplete or invalid.
     * @param connection The client connection used to send game actions to the server.
     * @param state The current remote game state used for validating the move and checking the game rules.
     * @param line The input command provided by the player to perform a move.
     * @param awaitTurnRender Flag to set when the turn ends to defer rendering.
     * @return true if the move command was successfully processed and sent; false if the command
     *         was invalid or could not be executed.
     */
    private static boolean handleMove(ClientConnection connection, RemoteGameState state,
                                      String line,
                                      AtomicBoolean awaitTurnRender) {
        String[] parts = line.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length == 0) {
            return false;
        }

        if (parts[0].equals("s")) {
            if (parts.length < 2) {
                return false;
            }
            Integer buildIdx = parseIndex(parts[1], RemoteGameState.BUILD_PILES);
            if (buildIdx == null) {
                return false;
            }
            StockPilePosition from = new StockPilePosition();
            NumberedPilePosition to = new NumberedPilePosition(
                    NumberedPilePosition.Pile.BUILDING_PILE,
                    buildIdx - 1
            );
            connection.send(play(from, to));
            return true;
        }

        if (parts[0].equals("h")) {
            if (parts.length < 3) {
                return false;
            }
            List<String> hand = state.getHand();
            Integer handIdx = parseIndex(parts[1], hand.size());
            if (handIdx == null) {
                return false;
            }
            String cardValue = hand.get(handIdx - 1);
            Card card = parseCardToken(cardValue);
            if (card == null) {
                return false;
            }
            HandPosition from = new HandPosition(card);

            if (parts[2].equals("b")) {
                if (parts.length < 4) {
                    return false;
                }
                Integer buildIdx = parseIndex(parts[3], RemoteGameState.BUILD_PILES);
                if (buildIdx == null) {
                    return false;
                }
                NumberedPilePosition to = new NumberedPilePosition(
                        NumberedPilePosition.Pile.BUILDING_PILE,
                        buildIdx - 1
                );
                connection.send(play(from, to));
                return true;
            }

            if (parts[2].equals("d")) {
                if (parts.length < 4) {
                    return false;
                }
                Integer discardIdx = parseIndex(parts[3], RemoteGameState.DISCARD_PILES);
                if (discardIdx == null) {
                    return false;
                }
                NumberedPilePosition to = new NumberedPilePosition(
                        NumberedPilePosition.Pile.DISCARD_PILE,
                        discardIdx - 1
                );
                connection.send(play(from, to));
                connection.send(end());
                awaitTurnRender.set(true);
                state.setLocalEvent("Turn ended. Waiting for next player.");
                return true;
            }
            // Unknown destination for hand card
            return false;
        }

        if (parts[0].equals("d")) {
            if (parts.length < 3) {
                return false;
            }
            Integer discardIdx = parseIndex(parts[1], RemoteGameState.DISCARD_PILES);
            if (discardIdx == null) {
                return false;
            }
            if (parts[2].equals("b")) {
                if (parts.length < 4) {
                    return false;
                }
                Integer buildIdx = parseIndex(parts[3], RemoteGameState.BUILD_PILES);
                if (buildIdx == null) {
                    return false;
                }
                NumberedPilePosition from = new NumberedPilePosition(
                        NumberedPilePosition.Pile.DISCARD_PILE,
                        discardIdx - 1
                );
                NumberedPilePosition to = new NumberedPilePosition(
                        NumberedPilePosition.Pile.BUILDING_PILE,
                        buildIdx - 1
                );
                connection.send(play(from, to));
                return true;
            }
            // Unknown destination for discard card
            return false;
        }
        // Unknown command
        return false;
    }

    /**
     * Sends a Bots move to the game server and returns whether the move was successfully processed.
     * The method prepares and determines the appropriate game action based on the type of move (stock pile,
     * hand pile, or discard pile) and executes the corresponding server interaction. Validations are performed
     * to ensure the move adheres to the game rules.
     * @param connection The client connection used to communicate with the game server.
     * @param state The current remote game state, which is used for validating and managing the move.
     * @param move The move to be executed, specifying the origin and destination piles.
     * @return true if the move was successfully processed and sent; false if the move was invalid or could not be executed.
     */
    private static boolean sendMove(ClientConnection connection, RemoteGameState state, Move move) {
        if (move instanceof StockMove sm) {
            StockPilePosition from = new StockPilePosition();
            NumberedPilePosition to = new NumberedPilePosition(
                    NumberedPilePosition.Pile.BUILDING_PILE,
                    sm.getBuildIndex()
            );
            connection.send(play(from, to));
            return true;
        }
        if (move instanceof CardMove cm) {
            if (cm.getFromPile() == 'h') {
                List<String> hand = state.getHand();
                int idx = cm.getFromIndex();
                if (idx < 1 || idx > hand.size()) {
                    return false;
                }
                Card card = parseCardToken(hand.get(idx - 1));
                if (card == null) {
                    return false;
                }
                HandPosition from = new HandPosition(card);
                if (cm.getToPile() == 'b') {
                    NumberedPilePosition to = new NumberedPilePosition(
                            NumberedPilePosition.Pile.BUILDING_PILE,
                            cm.getToIndex()
                    );
                    connection.send(play(from, to));
                    return true;
                }
                if (cm.getToPile() == 'd') {
                    NumberedPilePosition to = new NumberedPilePosition(
                            NumberedPilePosition.Pile.DISCARD_PILE,
                            cm.getToIndex() - 1
                    );
                    connection.send(play(from, to));
                    return true;
                }
            }
            if (cm.getFromPile() == 'd' && cm.getToPile() == 'b') {
                NumberedPilePosition from = new NumberedPilePosition(
                        NumberedPilePosition.Pile.DISCARD_PILE,
                        cm.getFromIndex() - 1
                );
                NumberedPilePosition to = new NumberedPilePosition(
                        NumberedPilePosition.Pile.BUILDING_PILE,
                        cm.getToIndex()
                );
                connection.send(play(from, to));
                return true;
            }
        }
        return false;
    }

    /**
     * Waits for the game state to be updated while ensuring that the current player's turn is monitored.
     * If a new event with an error is detected or the thread is interrupted, the method will terminate.
     * @param state The remote game state which represents the current state of the game and tracks changes;
     * @param version The version number of the game state to compare against for updates;
     * @param lastEvent The last recorded event to monitor for changes or new error events;
     * @return true if an error event is detected or the thread is interrupted; false otherwise.
     */
    private static boolean waitForUpdate(RemoteGameState state, long version, String lastEvent) {
        while (state.getVersion() == version && state.isYourTurn()) {
            String event = state.getLastEvent();
            if (event != null && !event.equals(lastEvent) && isErrorEvent(event)) {
                return true;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        return false;
    }

    /**
     * Selects a random card from the bots hand and sends a discard action to the game server.
     * The method chooses a random card from the hand, determines a random discard pile,
     * and sends the discard move if valid. If the hand is empty or the card is invalid, the action fails.
     * @param connection The client connection used to send the discard action to the game server;
     * @param state The current remote game state, used to access the player's hand and validate moves;
     * @return true if a valid discard action was successfully sent; false if the hand is empty
     *         or the card could not be parsed and the action failed.
     */
    private static boolean sendRandomDiscard(ClientConnection connection, RemoteGameState state) {
        List<String> hand = state.getHand();
        if (hand.isEmpty()) {
            return false;
        }
        int cardIndex = ThreadLocalRandom.current().nextInt(hand.size());
        Card card = parseCardToken(hand.get(cardIndex));
        if (card == null) {
            return false;
        }
        int discardIndex = ThreadLocalRandom.current().nextInt(RemoteGameState.DISCARD_PILES);
        HandPosition from = new HandPosition(card);
        NumberedPilePosition to = new NumberedPilePosition(
                NumberedPilePosition.Pile.DISCARD_PILE,
                discardIndex
        );
        connection.send(play(from, to));
        return true;
    }

    /**
     * Attempts to request a game if the current game state is neither started nor queued within
     * a specified time frame. If the game state remains unchanged after the deadline, a game
     * request is sent to the server.
     * @param connection The client connection used to send the game request to the server;
     * @param state The remote game state used to determine if the game has started or is queued;
     * @param players The number of players for the requested game.
     */
    private static void maybeRequestGame(ClientConnection connection, RemoteGameState state, int players) {
        long deadline = System.currentTimeMillis() + 800L;
        while (System.currentTimeMillis() < deadline) {
            if (state.isStarted() || state.isQueued()) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!state.isStarted() && !state.isQueued()) {
            connection.send(game(players));
        }
    }

    /**
     * Synchronizes the current game state with the client connection to update the bot's
     * view of the game. This method sends the current table and hand state to the client
     * and waits for any updates to ensure consistency.
     * @param connection The client connection used to send game state updates;
     * @param state The remote game state representing the current state of the game,
     *              including version and event tracking.
     */
    private static void syncState(ClientConnection connection, RemoteGameState state) {
        long version = state.getVersion();
        String lastEvent = state.getLastEvent();
        connection.send(table());
        connection.send(hand());
        waitForUpdate(state, version, lastEvent);
    }

    /**
     * Parses a given string token into an integer and validates it against specified minimum
     * and maximum bounds. If the token is not a valid integer or falls outside the range,
     * a message will be printed, and null is returned.
     * @param token The string to be parsed as an integer;
     * @param max   The maximum allowable value for the parsed integer;
     * @return The parsed integer if valid; null if the input is not a valid integer or does
     * not fall within the specified range.
     */
    private static Integer parseIndex(String token, int max) {
        try {
            int value = Integer.parseInt(token);
            if (value < 1 || value > max) {
                System.out.printf("Index must be between %d and %d.%n", 1, max);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("Not a number: " + token);
            return null;
        }
    }

    /**
     * Parses the input string to extract the number of players for a game.
     * Validates that the number of players is within the allowed range (2 to 6).
     * If the input is invalid or out of range, appropriate error messages will
     * be printed, and null will be returned.
     * @param input The input string to be parsed, expected to contain the command and player count;
     * @return The number of players if the input is valid; null if the input is invalid or out of range.
     */
    private static Integer parseGameCount(String input) {
        String[] parts = input.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Usage: game <players>");
            return null;
        }
        try {
            int count = Integer.parseInt(parts[1]);
            if (count < 2 || count > 6) {
                System.out.println("Players must be between 2 and 6.");
                return null;
            }
            return count;
        } catch (NumberFormatException e) {
            System.out.println("Players must be a number.");
            return null;
        }
    }

    /** Prints a help menu outlining the available commands for interacting with the game*/
    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  s <build#>      - play stock top to build pile (1-4)");
        System.out.println("  h <idx> b <#>   - play hand card to build");
        System.out.println("  h <idx> d <#>   - discard hand card to a discard pile (1-4) and end turn");
        System.out.println("  d <idx> b <#>   - play top of discard pile to build");
        System.out.println("  table           - request table state from server");
        System.out.println("  hand            - refresh your hand");
        System.out.println("  chat <message>  - send a message to all clients");
        System.out.println("  game <players>  - request a game (2-6 players)");
        System.out.println("  help            - show this help");
        System.out.println("  quit            - exit client");
    }

    /** Checks if the given event represents an error condition*/
    private static boolean isErrorEvent(String event) {
        String value = event == null ? "" : event.trim();
        return value.startsWith("Error ") || value.startsWith("Server error");
    }

    /** Creates a protocol string for a hello message with the specified name and features*/
    private static String hello(String name, Feature[] features) {
        return new Hello(name, features).transformToProtocolString();
    }

    /** Creates a protocol string for a game with the specified number of players*/
    private static String game(int players) {
        return new Game(players).transformToProtocolString();
    }

    /** Creates a protocol string for a play move from one position to another */
    private static String play(protocol.common.position.Position from, protocol.common.position.Position to) {
        return new Play(from, to).transformToProtocolString();
    }

    /** Creates a protocol string to signal the end of a game */
    private static String end() {
        return new End().transformToProtocolString();
    }

    /** Creates a protocol string for retrieving the game table */
    private static String table() {
        return new Table().transformToProtocolString();
    }

    /** Creates a protocol string for retrieving the current hand */
    private static String hand() {
        return new Hand().transformToProtocolString();
    }

    /** Creates a protocol string for sending a chat message */
    private static String chat(String message) {
        return new Chat(message).transformToProtocolString();
    }

    /**
     * Parses a rawFeature string representing feature flags and converts it into an array of Feature objects.
     *
     * @param rawFeature the input string containing feature flags, where each character corresponds to a specific feature
     * @return an array of Feature objects representing the enabled features; returns an empty array if the input is
     *         null or blank
     */
    private static Feature[] parseFeatures(String rawFeature) {
        if (rawFeature == null || rawFeature.isBlank()) {
            return new Feature[0];
        }
        Set<Feature> features = new HashSet<>();
        for (char c : rawFeature.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == Feature.CHAT.getLetter()) {
                features.add(Feature.CHAT);
            } else if (c == Feature.LOBBY.getLetter()) {
                features.add(Feature.LOBBY);
            } else if (c == Feature.MASTER.getLetter()) {
                features.add(Feature.MASTER);
            }
        }
        return features.toArray(new Feature[0]);
    }

    /**
     * Parses a card token and converts it into a Card object.
     * @param token the token representing card information to be parsed
     * @return a Card object if the token is successfully parsed and valid,
     *         or null if the token is invalid, parsing fails, or specific conditions are encountered
     */
    private static Card parseCardToken(String token) {
        ProtocolCodec.CardToken parsed = ProtocolCodec.parseCardToken(token, false);
        if (parsed == null) {
            return null;
        }
        if (parsed.skipBo) {
            return new Card((Integer) null);
        }
        try {
            return new Card(parsed.number.intValue());
        } catch (ProtocolException e) {
            return null;
        }
    }

    /**
     * Determines if the provided host represents a local host address.
     * A host is considered local if it is a loopback address, a local network interface address,
     * or explicitly named "localhost".
     * @param host the host name or IP address to check, can be null or blank
     * @return true if the host is a representation of the local machine, false otherwise
     */
    private static boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        String value = host.trim();
        if (value.isEmpty()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
                return true;
            }
            try {
                return NetworkInterface.getByInetAddress(address) != null;
            } catch (SocketException ignored) {
                return false;
            }
        } catch (IOException e) {
            String lowered = value.toLowerCase(Locale.ROOT);
            return lowered.equals("localhost");
        }
    }

    /**
     * Determines the next move in a naive manner based on the current state of the game.
     * @param state The current state of the remote game, including player and pile information.
     * @param localName The name of the local player whose move is to be determined.
     * @return A {@code Moves} object representing the next move to be made, or {@code null} if no valid move exists.
     */
    private static Move determineNaiveMove(RemoteGameState state, String localName) {
        RemoteGameState.PlayerView view = state.getPlayerView(localName);
        if (view == null) {
            return null;
        }
        RemoteGameState.BuildPileView[] piles = state.getBuildPiles();
        int[] expected = expectedBuildValues(piles);

        String stockTop = view.stockTop;
        if (stockTop != null) {
            for (int b = 0; b < expected.length; b++) {
                if (canPlaceToken(stockTop, expected[b])) {
                    return new StockMove('s', b);
                }
            }
        }

        for (int d = 0; d < view.discardTops.length; d++) {
            String top = view.discardTops[d];
            if (top == null) {
                continue;
            }
            for (int b = 0; b < expected.length; b++) {
                if (canPlaceToken(top, expected[b])) {
                    return new CardMove('d', d + 1, 'b', b);
                }
            }
        }

        List<String> hand = state.getHand();
        if (!hand.isEmpty()) {
            for (int h = 0; h < hand.size(); h++) {
                String token = hand.get(h);
                for (int b = 0; b < expected.length; b++) {
                    if (canPlaceToken(token, expected[b])) {
                        return new CardMove('h', h + 1, 'b', b);
                    }
                }
            }
            int handIndex = pickDiscardCardIndex(hand);
            int discardIndex = pickDiscardPileIndex(state.getDiscardStacks(localName));
            return new CardMove('h', handIndex, 'd', discardIndex);
        }

        return null;
    }

    /**
     * Computes an array of expected build values for each pile based on the provided build pile views.
     * @param piles an array of RemoteGameState.BuildPileView objects representing the build piles; may be null
     * @return an array of integers where each value represents the next valid build value for the corresponding pile
     */
    private static int[] expectedBuildValues(RemoteGameState.BuildPileView[] piles) {
        int count = piles == null ? 0 : piles.length;
        int[] expected = new int[count];
        for (int i = 0; i < count; i++) {
            int size = piles[i] == null ? 0 : Math.max(0, piles[i].size);
            int next = size + 1;
            expected[i] = next > model.Card.MAX_VALUE ? model.Card.MIN_VALUE : next;
        }
        return expected;
    }

    /**
     * Determines if a given token can be placed based on certain conditions and an expected value.
     * @param token the input string token to be verified; may represent a numeric value or other specific patterns
     * @param expected the integer value that the token is compared against in certain cases
     * @return true if the token meets the required conditions and can be placed, false otherwise
     */
    private static boolean canPlaceToken(String token, int expected) {
        if (token == null) {
            return false;
        }
        String value = token.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("X")) {
            return false;
        }
        if (value.startsWith("SB")) {
            return true;
        }
        try {
            return Integer.parseInt(value) == expected;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Determines the index of the card in the hand that should be discarded
     * based on a calculated value for each card.
     * @param hand a list of strings representing the cards in the player's hand
     * @return the 1-based index of the card to discard from the hand
     */
    private static int pickDiscardCardIndex(List<String> hand) {
        int bestIdx = 1;
        int bestVal = Integer.MIN_VALUE;
        for (int i = 0; i < hand.size(); i++) {
            String token = hand.get(i);
            int val = discardValue(token);
            if (val > bestVal) {
                bestVal = val;
                bestIdx = i + 1;
            }
        }
        return bestIdx;
    }

    /**
     * Discards or processes the given string token and returns an integer value.
     * If the input token is null, empty, represents a specific discarded value
     * (e.g., "X" or those starting with "SB"), or is not a valid integer, it
     * returns -100 as the default discarded value.
     * @param token the string input to be processed; can be null, empty, or any string.
     * @return an integer parsed from the valid token, or -100 if the input is null,
     *         empty, matches discard criteria, or cannot be parsed into an integer.
     */
    private static int discardValue(String token) {
        if (token == null) {
            return -100; // Return -100 to indicate an invalid or discarded input
        }
        String value = token.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("X") || value.startsWith("SB")) {
            return -100;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -100;
        }
    }

    /**
     * Determines the discard pile index with the smallest stack size
     * from a given list of stacks. If the stacks are null or empty,
     * defaults to returning index 1.
     * @param stacks a list of lists where each inner list represents a stack of strings
     * @return the 1-based index of the discard pile with the smallest stack size
     */
    private static int pickDiscardPileIndex(List<List<String>> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return 1;
        }
        int bestIdx = 1;
        int bestSize = stacks.getFirst() == null ? 0 : stacks.getFirst().size();
        for (int i = 1; i < stacks.size(); i++) {
            int size = stacks.get(i) == null ? 0 : stacks.get(i).size();
            if (size < bestSize) {
                bestSize = size;
                bestIdx = i + 1;
            }
        }
        return bestIdx;
    }

}
