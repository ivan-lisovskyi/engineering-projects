package networking;

import model.Card;
import model.Game;
import protocol.Command;
import protocol.ProtocolCodec;
import protocol.server.Chat;
import protocol.server.Error;
import protocol.server.Hand;
import protocol.server.Play;
import protocol.server.Queue;
import protocol.server.Round;
import protocol.server.Start;
import protocol.server.Stock;
import protocol.server.Table;
import protocol.server.Turn;
import protocol.server.Welcome;
import protocol.server.Winner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client-side cached view of the game state, reconstructed from server messages.
 * The state is updated by #applyMessage(String), which parses protocol lines
 * and maintains derived information used for rendering and bot decisions. The class
 * also tracks synchronization progress during initial state hydration.
 */
public class RemoteGameState {
    // Number of piles for building in the game
    public static final int BUILD_PILES = 4;

    // Number of piles for discarding in the game
    public static final int DISCARD_PILES = 4;

    // Regular expression for separating commands
    private static final String SEP = Pattern.quote(Command.SEPERATOR);

    // Regular expression for separating list elements
    private static final String LIST = Pattern.quote(Command.LIST_SEPERATOR);

    // Regular expression for separating values
    private static final String VALUE = Pattern.quote(Command.VALUE_SEPERATOR);

    // Name of the local player
    private final String localPlayer;

    // Indicates whether to assume stock counts are available
    private final boolean assumeStockCounts;

    // Game state flags
    private boolean started; // Whether the game has started
    private boolean queued;  // Whether the game is queued
    private boolean finished; // Whether the game has ended
    private boolean syncing;  // Whether syncing is in progress

    // List of all players in the game
    private final List<String> players = new ArrayList<>();

    // State of each player, keyed by player name
    private final Map<String, PlayerState> playerStates = new HashMap<>();

    // Cards in the local player's hand
    private final List<String> hand = new ArrayList<>();

    // Build pile counts, one for each pile
    private final int[] buildCounts = new int[BUILD_PILES];

    // Top cards of each build pile
    private final String[] buildTops = new String[BUILD_PILES];

    // The player whose turn it currently is
    private String currentTurn;

    // The last move made in the game
    private String lastMove;

    // The last significant event in the game
    private String lastEvent;

    // Version of the game state
    private long version;

    // Number of stock syncs expected
    private int expectedStockSync;

    // Set of players who have synced their stocks
    private final Set<String> stockSynced = new HashSet<>();

    // Sync state for the local player's hand
    private boolean handSynced;

    // Sync state for the current turn
    private boolean turnSynced;

    /**
     * Constructor for the RemoteGameState class.
     * @param localPlayer The name of the local player in the game.
     */
    public RemoteGameState(String localPlayer) {
        this(localPlayer, false);
    }

    /**
     * Constructor for the RemoteGameState class.
     * @param localPlayer        The name of the local player in the game.
     * @param assumeStockCounts  A boolean indicating whether stock counts should be assumed.
     */
    public RemoteGameState(String localPlayer, boolean assumeStockCounts) {
        this.localPlayer = localPlayer;
        this.assumeStockCounts = assumeStockCounts;
        Arrays.fill(buildTops, null);
        Arrays.fill(buildCounts, 0);
    }

    /**
     * Applies a single server message to the local cached state.
     * This method is synchronized to keep state updates atomic across threads.
     * @param line raw protocol line from the server
     * @return an Update describing whether a re-render is needed and any user-facing notice
     */
    public synchronized Update applyMessage(String line) {
        if (line == null) {
            return new Update(false, null);
        }
        String[] parts = line.split(SEP, -1);
        if (parts.length == 0) {
            return new Update(false, null);
        }
        // Normalize command token for consistent handling.
        String command = parts[0].trim().toUpperCase(Locale.ROOT);
        boolean changed = false;
        String notice = null;
        switch (command) {
            case Start.COMMAND -> {
                handleStart(parts);
                lastEvent = "Game started.";
                changed = true;
                notice = lastEvent;
            }
            case Hand.COMMAND -> {
                handleHand(parts);
                changed = true;
            }
            case Stock.COMMAND -> {
                handleStock(parts);
                changed = true;
            }
            case Turn.COMMAND -> {
                handleTurn(parts);
                changed = true;
            }
            case Play.COMMAND -> {
                handlePlay(parts);
                changed = true;
            }
            case Table.COMMAND -> {
                handleTable(parts);
                changed = true;
            }
            case Round.COMMAND -> {
                handleScores(parts);
                lastEvent = "Round ended.";
                changed = true;
                notice = lastEvent + " " + buildScoreSummary();
            }
            case Winner.COMMAND -> {
                handleScores(parts);
                finished = true;
                currentTurn = null;
                lastEvent = buildWinnerAnnouncement();
                changed = true;
                notice = lastEvent + " " + buildScoreSummary();
            }
            case Queue.COMMAND -> {
                queued = true;
                lastEvent = buildQueueNotice();
                notice = lastEvent;
                return buildUpdate(false, notice);
            }
            case Chat.COMMAND -> {
                notice = handleChat(parts);
                return buildUpdate(false, notice);
            }
            case Welcome.COMMAND -> {
                notice = handleWelcome(parts);
                return buildUpdate(false, notice);
            }
            case Error.COMMAND -> {
                String errorCode = extractErrorCode(parts);
                boolean errorChanged = handleErrorCode(errorCode);
                lastEvent = formatError(errorCode);
                notice = lastEvent;
                if (errorChanged) {
                    version++;
                }
                // Errors can be informational; update version only when state is changed.
                return buildUpdate(errorChanged, notice);
            }
            default -> {
                return new Update(false, null);
            }
        }
        // Version increments only when state mutations occurred.
        if (changed) {
            version++;
        }
        return buildUpdate(changed, notice);
    }

    /**
     * Checks if the process has started.
     * @return true if started, false otherwise.
     */
    public synchronized boolean isStarted() {
        return started;
    }

    /**
     * Checks if the process has finished.
     * @return true if finished, false otherwise.
     */
    public synchronized boolean isFinished() {
        return finished;
    }

    /**
     * Checks if the process is queued.
     * @return true if queued, false otherwise.
     */
    public synchronized boolean isQueued() {
        return queued;
    }

    /**
     * Gets the current turn.
     * @return the current turn as a String.
     */
    public synchronized String getCurrentTurn() {
        return currentTurn;
    }

    /**
     * Gets the last move made.
     * @return the last move as a String.
     */
    public synchronized String getLastMove() {
        return lastMove;
    }

    /**
     * Gets the last event that occurred.
     * @return the last event as a String.
     */
    public synchronized String getLastEvent() {
        return lastEvent;
    }

    /**
     * Updates the local event to the specified value.
     * @param event the new event to set.
     */
    public synchronized void setLocalEvent(String event) {
        lastEvent = event;
    }

    /**
     * Determines if it is the local player's turn in the game.
     * @return true if the game is not finished, the local player is set, and the local player
     *         matches the player whose turn it currently is; otherwise, false.
     */
    public synchronized boolean isYourTurn() {
        return !finished && localPlayer != null && localPlayer.equals(currentTurn);
    }

    /**
     * Gets the list of players.
     * @return a copy of the player list.
     */
    public synchronized List<String> getPlayers() {
        return new ArrayList<>(players);
    }

    /**
     * Gets the current version.
     * @return the version as a long.
     */
    public synchronized long getVersion() {
        return version;
    }

    /**
     * Retrieves the score of a player by their name.
     * If the player does not exist in the game state, a default score of 0 is returned.
     * @param name the name of the player whose score is to be retrieved
     * @return the score of the specified player, or 0 if the player is not found
     */
    public synchronized Integer getScore(String name) {
        PlayerState state = playerStates.get(name);
        return state == null ? 0 : state.score;
    }

    /**
     * Retrieves the discard stacks for a specified player by their name.
     * If the player does not exist in the current game state, an empty list is returned.
     * @param name the name of the player whose discard stacks are to be retrieved
     * @return a list of lists representing the discard stacks of the specified player,
     *         where each inner list is a copy of the respective discard stack
     */
    public synchronized List<List<String>> getDiscardStacks(String name) {
        PlayerState state = playerStates.get(name);
        List<List<String>> stacks = new ArrayList<>();
        if (state == null) {
            return stacks;
        }
        for (int i = 0; i < DISCARD_PILES; i++) {
            stacks.add(new ArrayList<>(state.discardStacks[i]));
        }
        return stacks;
    }

    /**
     * Retrieves a PlayerView object for the specified player's current state in the game.
     * This method compiles a summary view of the player's key data, including their name,
     * top card and count of their stock pile, top cards of their discard piles,
     * and their score. If the specified player does not exist, the method returns null.
     * @param name the name of the player whose PlayerView is to be retrieved
     * @return a PlayerView object representing the player's current game state,
     *         or null if the player does not exist
     */
    public synchronized PlayerView getPlayerView(String name) {
        PlayerState state = playerStates.get(name);
        if (state == null) {
            return null;
        }
        String[] discardTops = new String[DISCARD_PILES];
        for (int i = 0; i < DISCARD_PILES; i++) {
            discardTops[i] = peekDiscard(state, i);
        }
        return new PlayerView(state.name, state.stockTop, state.stockCount, discardTops, state.score);
    }

    public synchronized List<String> getHand() {
        return new ArrayList<>(hand);
    }

    /**
     * Retrieves the current state of the build piles in the game. Each build pile's
     * size and top card are encapsulated in a BuildPileView object.
     * The method returns a copy of the build piles to ensure immutability and thread safety.
     * @return an array of BuildPileView objects, each representing the size and
     *         top card of a build pile at the time of invocation
     */
    public synchronized BuildPileView[] getBuildPiles() {
        BuildPileView[] copy = new BuildPileView[BUILD_PILES];
        for (int i = 0; i < BUILD_PILES; i++) {
            copy[i] = new BuildPileView(buildCounts[i], buildTops[i]);
        }
        return copy;
    }

    /**
     * Resets and initializes the game state when a new game starts, incorporating data
     * from the provided server message parts.
     * @param parts an array of strings representing components of a server message;
     *              the second element, if present, specifies a comma-separated list of player names
     */
    private void handleStart(String[] parts) {
        Map<String, Integer> previousScores = new HashMap<>();
        if (started && !finished) {
            for (PlayerState state : playerStates.values()) {
                previousScores.put(state.name, state.score);
            }
        }

        players.clear();
        playerStates.clear();
        hand.clear();
        currentTurn = null;
        queued = false;
        lastMove = null;
        lastEvent = null;
        finished = false;

        syncing = true;
        stockSynced.clear();
        handSynced = false;
        turnSynced = false;

        if (parts.length > 1 && !parts[1].isBlank()) {
            players.addAll(Arrays.asList(parts[1].split(LIST)));
        }
        expectedStockSync = players.size();

        int startStock = assumeStockCounts ? startingStockCount(players.size()) : -1;
        for (String name : players) {
            PlayerState state = new PlayerState(name);
            state.stockCount = assumeStockCounts ? startStock : null;
            Integer prior = previousScores.get(name);
            if (prior != null) {
                state.score = prior;
            }
            playerStates.put(name, state);
        }

        Arrays.fill(buildTops, null);
        Arrays.fill(buildCounts, 0);
        started = true;
    }

    /**
     * Processes a server message component related to the player's hand in the game.
     * This method clears the current hand, parses the provided input, and updates the hand
     * with new cards, ensuring proper formatting. If syncing is active, it sets the hand
     * synchronization status.
     * @param parts an array of strings representing the components of a server message;
     *              the second element, if present, specifies a comma-separated list of cards
     *              for the hand
     */
    private void handleHand(String[] parts) {
        hand.clear();
        if (parts.length < 2 || parts[1].isBlank()) {
            return;
        }
        for (String card : parts[1].split(LIST)) {
            if (!card.isBlank()) {
                hand.add(card.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (syncing) {
            handSynced = true;
        }
    }

    /**
     * Processes a server message component related to a player's stock pile.
     * Updates the stock top card and count for the specified player based on
     * the input parts array. If the stock data indicates no stock ("X"), the
     * stock fields are cleared for the player. Synchronization states are updated
     * if syncing is active.
     * @param parts an array of strings representing components of a server message;
     *              the first element is expected to identify the message type,
     *              the second element is the name of the player,
     *              and the third element specifies the stock top card or "X"
     *              to denote no stock
     */
    private void handleStock(String[] parts) {
        if (parts.length < 3) {
            return;
        }
        String name = parts[1].trim();
        String top = parts[2].trim().toUpperCase(Locale.ROOT);
        PlayerState state = playerStates.computeIfAbsent(name, PlayerState::new);
        if (top.equals("X")) {
            state.stockTop = null;
            state.stockCount = 0;
        } else {
            state.stockTop = top;
            if (!assumeStockCounts) {
                state.stockCount = null;
            }
        }
        if (syncing) {
            stockSynced.add(name);
        }
    }

    /**
     * Processes a server message to update the current turn in the game state.
     * Adjusts the current turn information based on the provided input, updates
     * the event message to reflect the current player's turn, and handles the
     * synchronization status if syncing is active.
     * @param parts an array of strings representing components of a server message;
     *              the second element, if present, specifies the name of the player
     *              whose turn it currently is
     */
    private void handleTurn(String[] parts) {
        if (parts.length < 2) {
            return;
        }
        currentTurn = parts[1].trim();
        if (currentTurn.equals(localPlayer)) {
            lastEvent = "Your turn.";
        } else {
            lastEvent = "Waiting for " + currentTurn + ".";
        }
        if (syncing) {
            turnSynced = true;
        }
    }

    /**
     * Processes a "play" action based on components of a server message. This method validates
     * the input, updates the game state, and performs the required operations depending on
     * the source and destination of the play.
     * @param parts an array of strings representing the components of a server message;
     *              the second element specifies the player's name, the third element specifies
     *              the source position (e.g., hand, discard, stock), and the fourth element
     *              specifies the destination position (e.g., discard, build)
     */
    private void handlePlay(String[] parts) {
        if (parts.length < 4) {
            return;
        }
        String playerName = parts[1].trim();
        String fromToken = parts[2].trim();
        String toToken = parts[3].trim();
        lastMove = playerName + ": " + fromToken + " -> " + toToken;
        ProtocolCodec.PositionToken from = ProtocolCodec.parsePositionToken(fromToken);
        ProtocolCodec.PositionToken to = ProtocolCodec.parsePositionToken(toToken);
        if (from == null || to == null) {
            return;
        }
        if (from.kind == ProtocolCodec.PositionKind.DRAW || to.kind == ProtocolCodec.PositionKind.DRAW) {
            return;
        }
        PlayerState state = playerStates.get(playerName);
        if (state == null) {
            return;
        }

        String cardToken = null;
        if (from.kind == ProtocolCodec.PositionKind.HAND) {
            cardToken = from.cardToken;
        } else if (from.kind == ProtocolCodec.PositionKind.DISCARD) {
            cardToken = popDiscard(state, from.index);
        } else if (from.kind == ProtocolCodec.PositionKind.STOCK) {
            cardToken = state.stockTop;
            if (state.stockCount != null && state.stockCount > 0) {
                state.stockCount = state.stockCount - 1;
            }
            state.stockTop = null;
        }

        if (to.kind == ProtocolCodec.PositionKind.DISCARD) {
            pushDiscard(state, to.index, cardToken);
        }

        if (to.kind == ProtocolCodec.PositionKind.BUILD) {
            applyBuildPlay(to.index, cardToken);
        }
    }

    /**
     * Processes a server message corresponding to the "table" command.
     * Updates the game state to reflect changes to the build piles,
     * players, and their stock and discard piles based on the provided input.
     * This method handles scenarios where player or pile information
     * is partial or missing to ensure the game state remains consistent.
     * @param parts an array of strings representing components of a server message.
     *              The expected structure is as follows:
     *              - The second element specifies tokens for the build piles,
     *                separated by a delimiter.
     *              - The third element, if present, contains player details.
     *                Each player's details are separated by a delimiter, with fields specifying
     *                their name, stock, and top cards of discard piles.
     */
    private void handleTable(String[] parts) {
        if (parts.length < 2) {
            return;
        }
        String[] buildTokens = parts[1].split(VALUE);
        for (int i = 0; i < BUILD_PILES; i++) {
            String token = i < buildTokens.length ? buildTokens[i].trim().toUpperCase(Locale.ROOT) : "X";
            setBuildToken(i, token);
        }

        if (parts.length < 3 || parts[2].isBlank()) {
            return;
        }
        String[] playerDetails = parts[2].split(LIST);
        List<String> tablePlayers = new ArrayList<>();
        for (String detail : playerDetails) {
            String[] fields = detail.split(VALUE);
            if (fields.length < 2) {
                continue;
            }
            String name = fields[0].trim();
            if (!name.isEmpty()) {
                tablePlayers.add(name);
            }
            PlayerState state = playerStates.computeIfAbsent(name, PlayerState::new);
            String stock = fields[1].trim().toUpperCase(Locale.ROOT);
            boolean stockEmpty = stock.equals("X");
            state.stockTop = stockEmpty ? null : stock;
            if (stockEmpty) {
                state.stockCount = 0;
            }
            resetDiscards(state);
            for (int i = 0; i < DISCARD_PILES; i++) {
                int fieldIndex = 2 + i;
                if (fieldIndex >= fields.length) {
                    break;
                }
                String discard = fields[fieldIndex].trim().toUpperCase(Locale.ROOT);
                pushDiscard(state, i, discard);
            }
        }
        if (!tablePlayers.isEmpty()) {
            players.clear();
            players.addAll(tablePlayers);
            playerStates.keySet().retainAll(new HashSet<>(tablePlayers));
            if (currentTurn != null && !tablePlayers.contains(currentTurn)) {
                currentTurn = null;
            }
        }
    }

    /**
     * Processes a server message to update player scores and maintains the list of players
     * in the game state. Parses and validates score entries, updates player scores, and
     * adds new players to the player list if they are not already present.
     * @param parts an array of strings representing components of a server message;
     *              the second element, if present and not blank, contains a list of score entries
     *              separated by a delimiter, where each entry specifies a player name and score
     */
    private void handleScores(String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            return;
        }
        String[] scores = parts[1].split(LIST);
        List<String> scoreNames = new ArrayList<>();
        for (String scoreEntry : scores) {
            String[] fields = scoreEntry.split(VALUE);
            if (fields.length < 2) {
                continue;
            }
            String name = fields[0].trim();
            if (!name.isEmpty()) {
                scoreNames.add(name);
            }
            PlayerState state = playerStates.computeIfAbsent(name, PlayerState::new);
            try {
                state.score = Integer.parseInt(fields[1].trim());
            } catch (NumberFormatException ignored) {
                // Ignore bad score.
            }
        }
        if (!scoreNames.isEmpty()) {
            if (players.isEmpty()) {
                players.addAll(scoreNames);
            } else {
                for (String name : scoreNames) {
                    if (!players.contains(name)) {
                        players.add(name);
                    }
                }
            }
        }
    }

    /**
     * Updates a specific build pile in the game state with a new card token and modifies its count.
     * If the build pile completes (reaches a size of 12), it is reset.
     * @param index     the index of the build pile to update; must be within valid range [0, BUILD_PILES)
     * @param cardToken the token representing the card to add to the build pile
     */
    private void applyBuildPlay(int index, String cardToken) {
        if (index < 0 || index >= BUILD_PILES) {
            return;
        }
        int next = buildCounts[index] + 1;
        if (next >= Card.MAX_VALUE) {
            buildCounts[index] = 0;
            buildTops[index] = null;
            return;
        }
        buildCounts[index] = next;
        buildTops[index] = normalizeBuildToken(cardToken, next);
    }

    /**
     * Updates the build token and corresponding count at the specified index.
     * @param index the index of the build pile to update. Must be non-negative and less than the total number of build piles.
     * @param token the new token to set. If the token is null, blank, or equals "X", the count and token at the specified
     *              index will be reset. Otherwise, the token is converted to uppercase, and the count is updated.
     */
    private void setBuildToken(int index, String token) {
        if (index < 0 || index >= BUILD_PILES) {
            return;
        }
        if (token == null || token.isBlank() || token.equals("X")) {
            buildCounts[index] = 0;
            buildTops[index] = null;
            return;
        }
        buildTops[index] = token.toUpperCase(Locale.ROOT);
        buildCounts[index] = parseBuildCount(buildTops[index]);
    }

    /**
     * Parses the build count from the given string token. The method handles various input formats
     * and returns an integer representation of the build count. If the token is null, empty,
     * not a valid number, or contains specific placeholders (e.g., "X"), a default value of 0 is returned.
     * @param token the input string token representing the build count, which may include prefixes
     *              or invalid values.
     * @return the parsed integer build count, or 0 if the input is invalid or cannot be parsed.
     */
    private int parseBuildCount(String token) {
        if (token == null) {
            return 0;
        }
        String value = token.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("X")) {
            return 0;
        }
        if (value.startsWith("SB")) {
            value = value.substring(2);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Normalizes the build token string based on the provided card token and build size.
     * @param cardToken the card token string to be normalized; may be null
     * @param buildSize the size of the build to append or use as a replacement
     * @return the normalized build token; if cardToken is null, the build size is returned as a string,
     *         if the card token starts with "SB", the token is replaced with "SB" followed by build size,
     *         otherwise, the trimmed and uppercased card token is returned
     */
    private String normalizeBuildToken(String cardToken, int buildSize) {
        if (cardToken == null) {
            return String.valueOf(buildSize);
        }
        String value = cardToken.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("SB")) {
            return "SB" + buildSize;
        }
        return value;
    }

    /**
     * Extracts the error code from the provided array of string parts.
     * The error code is expected to be at the second position in the array.
     * If the array is too short or the extracted code is empty, null is returned.
     * @param parts the array of strings from which the error code is to be extracted
     * @return the trimmed error code if successfully extracted, otherwise null
     */
    private String extractErrorCode(String[] parts) {
        if (parts.length < 2) {
            return null;
        }
        String code = parts[1].trim();
        return code.isEmpty() ? null : code;
    }

    /**
     * Handles a specific error code and performs operations based on the provided code
     * and the current state of the players and their turns.
     * @param code the error code to be handled
     * @return true if the error code is "103" and specific conditions about players
     *         and their turns are met, false otherwise
     */
    private boolean handleErrorCode(String code) {
        if (!"103".equals(code)) {
            return false;
        }
        if (localPlayer != null && players.size() == 2) {
            String other = null;
            for (String name : players) {
                if (!name.equals(localPlayer)) {
                    other = name;
                    break;
                }
            }
            if (other != null) {
                players.remove(other);
                playerStates.remove(other);
                if (currentTurn != null && currentTurn.equals(other)) {
                    currentTurn = null;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Formats an error message based on the provided error code.
     * @param code the error code to format the message for; if null, a generic "Server error." message
     *             is returned.
     * @return a formatted error message corresponding to the given code. If the code matches a predefined
     *         error, the error description is included in the message. If the code is unknown, a message
     *         with the error code is returned. If the code is null, a generic server error message is returned.
     */
    private String formatError(String code) {
        if (code == null) {
            return "Server error.";
        }
        String message = switch (code) {
            case "001" -> "Invalid player name";
            case "002" -> "Name already in use";
            case "103" -> "Player disconnected";
            case "204" -> "Invalid command";
            case "205" -> "Command not allowed";
            case "206" -> "Invalid move";
            default -> null;
        };
        if (message == null) {
            return "Error " + code;
        }
        return "Error " + code + " (" + message + ")";
    }

    /**
     * Constructs an announcement string based on the highest scoring player(s) in the game.
     * If there are no players, the announcement indicates the game is over with no winners.
     * If one player has the highest score, the announcement specifies that player as the winner.
     * If multiple players share the highest score, the announcement lists all of them as winners.
     * @return A string representing the winner(s) announcement or a message indicating
     *         the game is over with no winners.
     */
    private String buildWinnerAnnouncement() {
        if (players.isEmpty()) {
            return "Game over.";
        }
        int bestScore = Integer.MIN_VALUE;
        List<String> winners = new ArrayList<>();
        for (String name : players) {
            PlayerState state = playerStates.get(name);
            int score = state != null ? state.score : 0;
            if (score > bestScore) {
                bestScore = score;
                winners.clear();
                winners.add(name);
            } else if (score == bestScore) {
                winners.add(name);
            }
        }
        if (winners.isEmpty()) {
            return "Game over.";
        }
        if (winners.size() == 1) {
            return "Game over. Winner: " + winners.get(0) + " (" + bestScore + ")";
        }
        return "Game over. Winners: " + String.join(", ", winners) + " (" + bestScore + ")";
    }

    /**
     * Builds and returns a string summary of player scores in the format "Scores: name1.score1 name2.score2 ...".
     * If there are no players, the summary will simply be "Scores:".
     * @return A string summarizing the scores of all players. If no players exist, returns "Scores:".
     */
    private String buildScoreSummary() {
        if (players.isEmpty()) {
            return "Scores:";
        }
        StringBuilder sb = new StringBuilder("Scores:");
        for (String name : players) {
            PlayerState state = playerStates.get(name);
            int score = state != null ? state.score : 0;
            sb.append(' ').append(name).append('.').append(score);
        }
        return sb.toString();
    }

    /**
     * Handles the welcome process for a player based on the provided input parts.
     * Adds a player to the players list and initializes their state if they are not already
     * part of the game and the game has not started.
     * Also sets a connection event message if applicable.
     * @param parts the array of input strings where the second element represents the player's name
     * @return the event message indicating the player connection if applicable, or null otherwise
     */
    private String handleWelcome(String[] parts) {
        if (parts.length < 2) {
            return null;
        }
        String name = parts[1].trim();
        if (name.isEmpty()) {
            return null;
        }
        if (!players.contains(name) && !started) {
            players.add(name);
        }
        playerStates.computeIfAbsent(name, PlayerState::new);
        if (!started && !name.equals(localPlayer)) {
            lastEvent = name + " connected.";
            return lastEvent;
        }
        return null;
    }

    /**
     * Processes a chat input to construct a properly formatted message.
     * @param parts An array of strings where the first element is ignored, the second
     *              element represents the name of the sender, and the subsequent elements
     *              represent the message content.
     * @return A formatted string in the format "name: message" if the input is valid;
     *         null if the input is invalid (either the input array is too short, the
     *         name is empty, or the message is blank).
     */
    private String handleChat(String[] parts) {
        if (parts.length < 3) {
            return null;
        }
        String name = parts[1].trim();
        String message = String.join(Command.SEPERATOR, Arrays.copyOfRange(parts, 2, parts.length));
        if (name.isEmpty() || message.isBlank()) {
            return null;
        }
        return name + ": " + message;
    }

    /**
     * Constructs a notice message indicating the current status of players in the queue.
     * If no players are connected, the notice will state that it is waiting for more players.
     * If there are players connected, the notice will include the count of connected players.
     * @return A string message showing the queue status.
     */
    private String buildQueueNotice() {
        if (players.isEmpty()) {
            return "Waiting for more players.";
        }
        return "Waiting for more players (" + players.size() + " connected).";
    }

    /**
     * Constructs an Update object based on the provided parameters and current state.
     * @param changed a boolean indicating whether changes have occurred
     * @param notice a string message or notice associated with the update
     * @return an Update object configured with the determined rendering status and the provided notice
     */
    private Update buildUpdate(boolean changed, String notice) {
        boolean shouldRender = changed && !syncing;
        if (syncing && isSyncReady()) {
            syncing = false;
            shouldRender = true;
        }
        return new Update(shouldRender, notice);
    }

    /**
     * Evaluates if the synchronization process is ready to proceed by checking
     * the sync status of various components and the required stock synchronization.
     * @return true if all synchronization conditions are met, including components
     *         being synced and the stock synchronization count meeting or exceeding
     *         the expected threshold; false otherwise.
     */
    private boolean isSyncReady() {
        if (!syncing) {
            return false;
        }
        if (!handSynced || !turnSynced) {
            return false;
        }
        return stockSynced.size() >= expectedStockSync;
    }
    /** Starting stock amount for each player according to players in the game */
    private int startingStockCount(int playerCount) {
        return Game.getStartingStockCount(playerCount);
    }

    /**
     * Represents an update configuration that combines rendering instructions and a notice message.
     * This class is immutable and defines whether rendering should occur along with providing
     * additional information through a notice.
     */
    public static final class Update {
        public final boolean shouldRender;
        public final String notice;

        /**
         * Constructs an Update instance with specified rendering instructions and notice message.
         *
         * @param shouldRender a boolean indicating whether rendering should occur
         * @param notice a String providing additional information or a message related to the update
         */
        public Update(boolean shouldRender, String notice) {
            this.shouldRender = shouldRender;
            this.notice = notice;
        }
    }

    /**
     * The PlayerView class provides an immutable representation of a player's current state.
     * This class contains information about a player's name, stock pile details, discard pile details,
     * and their current score. It is intended to be used to view a player's game details without
     * modifying the actual game state.
     */
    public static final class PlayerView {
        public final String name;
        public final String stockTop;
        public final Integer stockCount;
        public final String[] discardTops;
        public final Integer score;

        /**
         * Constructs an immutable instance of PlayerView that represents the current state of a player.
         * This includes the player's name, details about their stock pile, details about their discard piles,
         * and their current score.
         * @param name The name of the player.
         * @param stockTop The top card of the player's stock pile.
         * @param stockCount The number of cards remaining in the player's stock pile.
         * @param discardTops An array representing the top cards of each of the player's discard piles.
         * @param score The player's current score.
         */
        private PlayerView(String name, String stockTop, Integer stockCount, String[] discardTops, Integer score) {
            this.name = name;
            this.stockTop = stockTop;
            this.stockCount = stockCount;
            this.discardTops = discardTops;
            this.score = score;
        }
    }

    /**
     * Represents a read-only view of a build pile in a game or card system.
     * This class provides information about the current state of the build pile,
     * such as its size and the token on top of the pile.
     * Instances of this class are immutable and encapsulate the state of
     * the build pile at a specific moment.
     */
    public static final class BuildPileView {
        public final int size;
        public final String topToken;

        /**
         * Constructs a BuildPileView with the specified size and top token.
         *
         * @param size the number of elements in the build pile
         * @param topToken the token at the top of the build pile
         */
        private BuildPileView(int size, String topToken) {
            this.size = size;
            this.topToken = topToken;
        }
    }

    /**
     * Pushes a trimmed and uppercased token onto the discard stack at the specified index
     * in the player's state. The operation is performed only if the state and token are valid
     * and the index is within the valid range of discard piles.
     * @param state the player's current state, which contains the discard stacks
     * @param index the index of the discard stack onto which the token will be pushed
     * @param token the string token to be processed and pushed onto the discard stack
     */
    private void pushDiscard(PlayerState state, int index, String token) {
        if (state == null || index < 0 || index >= DISCARD_PILES) {
            return;
        }
        if (token == null) {
            return;
        }
        String value = token.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("X")) {
            return;
        }
        state.discardStacks[index].push(value);
    }

    /**
     * Removes and returns the top card from the discard stack at the specified index.
     * If the state is null, the index is invalid, or the discard stack at the index is empty, returns null.
     * @param state the current state of the player, containing the discard stacks
     * @param index the index of the discard stack from which to remove the top card
     * @return the removed card from the discard stack, or null if the operation cannot be performed
     */
    private String popDiscard(PlayerState state, int index) {
        if (state == null || index < 0 || index >= DISCARD_PILES) {
            return null;
        }
        Deque<String> stack = state.discardStacks[index];
        return stack.isEmpty() ? null : stack.pop();
    }

    /**
     * Peeks at the top card of the specified discard pile without removing it.
     * @param state the current state of the player, which contains the discard stacks
     * @param index the index of the discard pile to peek at
     * @return the card at the top of the specified discard pile, or null if the state is null,
     *         the index is out of range, the pile is empty, or the pile does not exist
     */
    private String peekDiscard(PlayerState state, int index) {
        if (state == null || index < 0 || index >= DISCARD_PILES) {
            return null;
        }
        Deque<String> stack = state.discardStacks[index];
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Resets all discard stacks for the provided player state by clearing each stack.
     * @param state The PlayerState object containing the discard stacks to be cleared.
     *              If null, the method performs no action.
     */
    private void resetDiscards(PlayerState state) {
        if (state == null) {
            return;
        }
        for (Deque<String> stack : state.discardStacks) {
            stack.clear();
        }
    }

    /**
     * Mutable cache of one player's remote state.
     * The name is fixed, while stock info, score, and discard stacks are updated
     * as server messages arrive.
     */
    private static final class PlayerState {
        private final String name;
        // Null when stock counts are not revealed by the server.
        private Integer stockCount;
        private String stockTop;
        private int score;
        @SuppressWarnings("unchecked")
        // Index 0..DISCARD_PILES-1, top card at the head.
        private final Deque<String>[] discardStacks = new Deque[DISCARD_PILES];

        private PlayerState(String name) {
            this.name = name;
            for (int i = 0; i < DISCARD_PILES; i++) {
                discardStacks[i] = new ArrayDeque<>();
            }
        }
    }
}
