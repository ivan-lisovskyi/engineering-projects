package networking;

import model.Card;
import model.Game;
import model.moves.CardMove;
import model.moves.Move;
import model.moves.StockMove;
import model.players.ComputerPlayer;
import model.players.NaiveStrategy;
import model.players.Player;
import model.piles.BuildPile;
import model.piles.Hand;
import model.piles.StockPile;

import protocol.ProtocolCodec;
import protocol.ProtocolException;
import protocol.common.ErrorCode;
import protocol.common.position.HandPosition;
import protocol.common.position.NumberedPilePosition;
import protocol.common.position.StockPilePosition;
import protocol.server.Round;
import protocol.server.Start;
import protocol.server.Stock;
import protocol.server.Turn;
import protocol.server.Winner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;



/**
 * Server-side manager for a single online Skip-Bo match ("room").
 * A GameRoom owns a local Game instance and coordinates:
 *   Connected clients via ClientHandler
 *   Bot players via ComputerPlayer
 *   Turn order, active players, and disconnect handling
 *   Validation + application of incoming play/end commands
 *   Broadcasting protocol messages (START, TURN, TABLE, STOCK, PLAY, ROUND, WINNER, ERROR)
 * The game rules are enforced by the local  Game model. This class is responsible for
 * translating between protocol tokens/positions and the model's  Moves types.
 */

public class GameRoom {

    // Reference back to the server coordinator that created this room.
    private final ServerState state;

    // Connected client handlers by player name (only humans; bots have no handler).
    private final Map<String, ClientHandler> handlersByName = new HashMap<>();

    // All players by name (humans + bots).
    private final Map<String, Player> playersByName = new HashMap<>();

    // Bot players by name
    private final Map<String, ComputerPlayer> botPlayers = new HashMap<>();

    // Bot names for fast membership checks
    private final Set<String> botNames = new HashSet<>();

    // Ordered list of player names in seat/turn order
    private final List<String> playerOrder = new ArrayList<>();

    //Set of player names currently active in the game.
    // When a player disconnects, they are removed from this set and skipped in turn advancement.
    private final Set<String> activePlayers = new HashSet<>();

    //The local game model that contains the authoritative state for this room
    private final Game game;

// True if the current player has made a move that ends their turn, and the server is now waiting for an END command.
    private boolean awaitingEnd;
    // Name of the player who is allowed to send END while awaitingEnd is true.
    private String awaitingEndPlayer;
    //True once the room is finished (match ended or not enough players remain).
    private boolean roomEnded;

    /**
     * Creates a new game room with the provided human handlers and a number of bot players.
     * For each human handler, a ClientPlayer is created and added to the model.
     * Each computer player,is created and added too.
     * @param state server coordinator / owner
     * @param handlers connected clients (humans)
     * @param botCount number of bots to add
     */

    public GameRoom(ServerState state, List<ClientHandler> handlers, int botCount) {
        this.state = state;
        List<Player> players = new ArrayList<>();
        // Create one "ClientPlayer" per connected handler and register mappings.
        for (ClientHandler handler : handlers) {
            String name = handler.getPlayerName();
            ClientPlayer player = new ClientPlayer(name);
            players.add(player);
            playersByName.put(name, player);
            handlersByName.put(name, handler);
            playerOrder.add(name);
            activePlayers.add(name);
        }

        // Create bot players and register them.
        for (int i = 1; i <= botCount; i++) {
            NaiveStrategy strategy = new NaiveStrategy();
            String name = uniqueBotName(i);
            ComputerPlayer bot = new ComputerPlayer(name, new Hand(), new StockPile(), strategy);
            players.add(bot);
            playersByName.put(name, bot);
            botPlayers.put(name, bot);
            botNames.add(name);
            playerOrder.add(name);
            activePlayers.add(name);
        }
        // The game model is built from the combined player list.
        this.game = new Game(players);
    }
    /**
     * Starts the room by broadcasting the initial state and then letting bots play if it is a bot's turn.
     */

    public void start() {
        broadcastStartState();
        playBotsIfNeeded();
    }

    /**
     * Handles an incoming command from a connected client.
     * This validates basic permission rules (player exists, still active), then dispatches to the
     * correct handler based on the command name and argument count.
     * @param handler client connection that sent the command
     * @param command raw command name (case-insensitive)
     * @param parts tokenized command parts (command + args)
     */

    public synchronized void handleCommand(ClientHandler handler, String command, String[] parts) {
        String playerName = handler.getPlayerName();
        // Must be a known player in this room.
        if (playerName == null || !playersByName.containsKey(playerName)) {
            handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }
        // Disconnected or removed players cannot send commands.

        if (!activePlayers.contains(playerName)) {
            handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }
        // Dispatch based on protocol command name.
        // Play <from> <to>

        switch (command.toUpperCase(Locale.ROOT)) {
            case protocol.client.Play.COMMAND -> {
                if (parts.length != 3) {
                    handler.sendError(ErrorCode.INVALID_COMMAND);
                    return;
                }
                handlePlay(handler, parts);
            }
            //END
            case protocol.client.End.COMMAND -> {
                if (parts.length != 1) {
                    handler.sendError(ErrorCode.INVALID_COMMAND);
                    return;
                }
                handleEnd(handler);
            }
            // HAND
            case protocol.client.Hand.COMMAND -> {
                if (parts.length != 1) {
                    handler.sendError(ErrorCode.INVALID_COMMAND);
                    return;
                }
                sendHandTo(playerName);
            }
            // TABLE
            case protocol.client.Table.COMMAND -> {
                if (parts.length != 1) {
                    handler.sendError(ErrorCode.INVALID_COMMAND);
                    return;
                }
                sendTableTo(playerName);
            }
            default -> handler.sendError(ErrorCode.INVALID_COMMAND);
        }
    }
    /**
     * Called when a human player disconnects.
     * This removes the player from the active/handler lists, broadcasts an error to remaining players,
     * and either ends the room (if not enough players remain) or advances the turn if needed.
     * @param name disconnected player's name
     */

    public synchronized void playerDisconnected(String name) {
        // Only process once; if player was already removed, do nothing.
        if (!activePlayers.remove(name)) {
            return;
        }
        // Remove handler and order entry so future broadcasts/turn display match remaining humans.
        handlersByName.remove(name);
        playerOrder.remove(name);

        // Inform clients that a player disconnected.

        broadcastError(ErrorCode.PLAYER_DISCONNECTED);

        // End the room if there are no human handlers OR fewer than 2 active players remain.
        if (handlersByName.isEmpty() || activePlayers.size() < 2) {
            // Mark remaining handlers so they can show a "game ended by disconnect" message if needed.
            for (ClientHandler handler : handlersByName.values()) {
                handler.markGameEndedByDisconnect();
            }
            roomEnded = true;
            state.endRoom();
            return;
        }
        // If the disconnected player was making turn, skip them and continue.

        if (name.equals(getCurrentPlayerName())) {
            awaitingEnd = false;
            awaitingEndPlayer = null;
            advanceToNextActive();
            broadcastTurn();
            playBotsIfNeeded();
        }
    }

    /**
     * Handles the PLAY command.
     * This verifies turn ownership and state rules, parses the protocol positions, translates them
     * into a model Moves object, applies the move via GameapplyMove(Moves, Player)
     * and broadcasts the resulting protocol updates.
     * @param handler the client sending the command
     * @param parts command tokens: PLAY from to
     */

    private void handlePlay(ClientHandler handler, String[] parts) {
        String playerName = handler.getPlayerName();
        // Only the current player may play.
        if (!playerName.equals(getCurrentPlayerName())) {
            handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }
        // If the server is waiting for END, no further plays are allowed.

        if (awaitingEnd) {
            handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }
        // Parse protocol positions (from/to).

        ProtocolCodec.PositionToken from = ProtocolCodec.parsePositionToken(parts[1]);
        ProtocolCodec.PositionToken to = ProtocolCodec.parsePositionToken(parts[2]);
        if (from == null || to == null) {
            handler.sendError(ErrorCode.INVALID_COMMAND);
            return;
        }
        // Validate the general "shape" of moves allowed by the protocol.

        if (!isMoveShapeValid(from, to)) {
            handler.sendError(ErrorCode.INVALID_MOVE);
            return;
        }

        Player player = playersByName.get(playerName);

        // Model move to apply, and protocol positions to include in the PLAY broadcast.
        Move move = null;
        protocol.common.position.Position fromPosition = null;
        protocol.common.position.Position toPosition = null;

        //Translate protocol move -> model move

        // STOCK to BUILD
        if (from.kind == ProtocolCodec.PositionKind.STOCK && to.kind == ProtocolCodec.PositionKind.BUILD) {
            move = new StockMove('s', to.index);

            // For protocol broadcast (what clients see):

            fromPosition = new StockPilePosition();
            toPosition = new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, to.index);
            // HAND to (BUILD or DISCARD)
        } else if (from.kind == ProtocolCodec.PositionKind.HAND) {
            // The protocol identifies a hand card by token
            ProtocolCodec.CardToken cardSpec = ProtocolCodec.parseCardToken(from.cardToken, false);
            if (cardSpec == null) {
                handler.sendError(ErrorCode.INVALID_COMMAND);
                return;
            }
            // Find which index in the *server-side hand* matches that token.
            int handIndex = findHandIndex(player.getHand(), cardSpec);
            if (handIndex < 1) {
                handler.sendError(ErrorCode.INVALID_MOVE);
                return;
            }
            // Translate the actual model card into a protocol card.
            Card card = player.getHand().getCard(handIndex);
            protocol.common.Card protocolCard = toProtocolCard(card);
            if (protocolCard == null) {
                handler.sendError(ErrorCode.INVALID_MOVE);
                return;
            }
            fromPosition = new HandPosition(protocolCard);
            if (to.kind == ProtocolCodec.PositionKind.BUILD) {
                // Note the model uses discard piles 1..4, while protocol positions appear 0-based in this code.

                move = new CardMove('h', handIndex, 'b', to.index);
                toPosition = new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, to.index);
            } else if (to.kind == ProtocolCodec.PositionKind.DISCARD) {
                move = new CardMove('h', handIndex, 'd', to.index + 1);
                toPosition = new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, to.index);
            }

            // DISCARD -> BUILD

        } else if (from.kind == ProtocolCodec.PositionKind.DISCARD && to.kind == ProtocolCodec.PositionKind.BUILD) {
            // Model expects discard pile index 1..4.
            move = new CardMove('d', from.index + 1, 'b', to.index);
            fromPosition = new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, from.index);
            toPosition = new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, to.index);
        }
        // If translation failed for any reason, reject the move.

        if (move == null) {
            handler.sendError(ErrorCode.INVALID_MOVE);
            return;
        }
        // Apply move to the model.

        Game.MoveResult result = game.applyMove(move, player);
        if (!result.isApplied()) {
            handler.sendError(ErrorCode.INVALID_MOVE);
            return;
        }
        // Broadcast PLAY to everyone.

        broadcast(buildPlayMessage(playerName, fromPosition, toPosition));

        // If stock was used, stock top changed; broadcast stock update to all.

        if (from.kind == ProtocolCodec.PositionKind.STOCK) {
            broadcastStock(playerName);
        }

        // If a hand card was used, the player's HAND view must be refreshed.

        if (from.kind == ProtocolCodec.PositionKind.HAND) {
            sendHandTo(playerName);
        }

        // If the model says turn ended, require explicit END command.

        if (result.isEndTurn()) {
            awaitingEnd = true;
            awaitingEndPlayer = playerName;
        }

        // If the move ended the round, finish round and possibly match.

        if (game.gameOver()) {
            handleRoundEnd();
            playBotsIfNeeded();
            return;
        }

        // If player's hand is empty mid-turn, refill and send updated hand.

        if (player.getHand().isEmpty()) {
            game.refillHand(player);
            sendHandTo(playerName);
        }
    }


    /**
     * Handles the END command from the current player.
     * END is only allowed after a move that ends the turn (awaitingEnd).
     * This refills the hand, clears awaiting state, advances to the next active player, and broadcasts TURN.
     * @param handler the client sending END
     */

    private void handleEnd(ClientHandler handler) {
        String playerName = handler.getPlayerName();
        // Only current player may end the turn.
        if (!playerName.equals(getCurrentPlayerName())) {
            handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }
        // END only allowed if the server is expecting it from this specific player.

        if (!awaitingEnd || !playerName.equals(awaitingEndPlayer)) {
            handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }
        Player player = playersByName.get(playerName);

        // End of turn: refill and send updated hand to that player.
        game.refillHand(player);
        sendHandTo(playerName);
        awaitingEnd = false;
        awaitingEndPlayer = null;

        advanceToNextActive();
        broadcastTurn();
        playBotsIfNeeded();
    }

    /**
     * Finishes the current round (and possibly the entire match) and broadcasts the messages.
     * If the match is over, broadcasts WINNER and ends the room.
     * Otherwise starts a new round and broadcasts the new START state.
     */

    private synchronized void handleRoundEnd() {
        Game.RoundResult result = game.finishRound();

        // Broadcast round scores after a round is finished.
        broadcast(buildRoundMessage());

        if (game.isMatchOver()) {
            String winnerMessage = buildWinnerMessage();
            broadcast(winnerMessage);
            // If no handlers remain, log winner server-side for debugging/visibility.
            if (handlersByName.isEmpty()) {
                System.out.println(winnerMessage);
                Player winner = game.getMatchWinner();
                if (winner != null) {
                    System.out.println("Winner: " + winner.getName() + " (" + game.getScore(winner) + ")");
                }
            }
            roomEnded = true;
            state.endRoom();
            return;
        }
        // Start a new round with the winner of the previous round.

        game.startNewRound(result.getWinner());
        awaitingEnd = false;
        awaitingEndPlayer = null;
        broadcastStartState();
    }

    /**
     * Broadcasts a full "start state" snapshot:
     *   START with player order
     *   HAND to each player
     *   STOCK (top card) for each player to everyone
     *   TURN indicating whose turn it is
     */

    private synchronized void broadcastStartState() {
        broadcast(new Start(playerOrder.toArray(new String[0])).transformToProtocolString());

        // Each player receives their own HAND snapshot.
        for (String name : playerOrder) {
            sendHandTo(name);
        }
        // Broadcast each player's stock top to all clients.
        for (String name : playerOrder) {
            broadcastStock(name);
        }
        broadcastTurn();
    }

    /**
     * Broadcasts the current player's turn to everyone.
     */

    private void broadcastTurn() {
        broadcast(new Turn(getCurrentPlayerName()).transformToProtocolString());
    }


    /**
     * Sends the current hand snapshot to a specific player.
     * If the hand is empty and the round isn't over, this refills the hand before sending.
     * @param playerName target player
     */

    private void sendHandTo(String playerName) {
        Player player = playersByName.get(playerName);
        ClientHandler handler = handlersByName.get(playerName);
        if (player == null || handler == null) {
            return;
        }
        // Make sure players always have a hand unless the round is over.
        if (player.getHand().isEmpty() && !game.gameOver()) {
            game.refillHand(player);
        }
        String[] cards = handToStrings(player.getHand());
        handler.send(new protocol.server.Hand(cards).transformToProtocolString());
    }

    /**
     * Sends the current table snapshot to a specific player.
     * @param playerName target player
     */

    private void sendTableTo(String playerName) {
        ClientHandler handler = handlersByName.get(playerName);
        if (handler == null) {
            return;
        }
        handler.send(buildTableMessage());
    }

    /**
     * Broadcasts a STOCK update for the given player to all clients.
     * This sends the top stock card token ("X" if unknown/empty).
     * @param playerName whose stock top should be broadcast
     */

    private void broadcastStock(String playerName) {
        Player player = playersByName.get(playerName);
        if (player == null) {
            return;
        }
        StockPile stockPile = player.getStockPile();
        String top = cardToProtocol(stockPile.peekTopCard());
        broadcast(new Stock(playerName, top).transformToProtocolString());
    }

    /**
     * Builds the protocol PLAY message string from raw token strings.
     * @param playerName player who made the move
     * @param fromToken protocol "from" token (position)
     * @param toToken protocol "to" token (position)
     * @return encoded protocol message
     */

    private String buildPlayMessage(String playerName, String fromToken, String toToken) {
        return new protocol.server.Play(fromToken, toToken, playerName).transformToProtocolString();
    }

    /**
     * Builds the protocol PLAY message string from typed protocol positions.
     * @param playerName player who made the move
     * @param from from position
     * @param to to position
     * @return encoded protocol message
     */

    private String buildPlayMessage(String playerName, protocol.common.position.Position from,
                                    protocol.common.position.Position to) {
        return buildPlayMessage(playerName, from.toString(), to.toString());
    }


    /**
     * Builds a protocol TABLE message containing the visible table state:
     *   Each player's stock top + discard pile tops
     *   Each building pile top
     * @return encoded protocol TABLE message
     */

    private String buildTableMessage() {
        protocol.server.Table.PlayerTable[] playerDetails =
                new protocol.server.Table.PlayerTable[playerOrder.size()];
        // For each player in turn order, include their visible pile tops.
        for (int i = 0; i < playerOrder.size(); i++) {
            String name = playerOrder.get(i);
            Player player = playersByName.get(name);
            // Default to "X" (unknown/empty) when not available.
            String discard1 = "X";
            String discard2 = "X";
            String discard3 = "X";
            String discard4 = "X";
            String stockToken = "X";
            if (player != null) {
                discard1 = cardToProtocol(player.getDiscardPile(1).peekTopCard());
                discard2 = cardToProtocol(player.getDiscardPile(2).peekTopCard());
                discard3 = cardToProtocol(player.getDiscardPile(3).peekTopCard());
                discard4 = cardToProtocol(player.getDiscardPile(4).peekTopCard());
                stockToken = cardToProtocol(player.getStockPile().peekTopCard());
            }
            playerDetails[i] = new protocol.server.Table.PlayerTable(
                    name, stockToken, discard1, discard2, discard3, discard4);
        }
        // Build piles are sent as 4 separate tokens.
        return new protocol.server.Table(
                playerDetails,
                buildPileTopToProtocol(game.getBuildPile(0)),
                buildPileTopToProtocol(game.getBuildPile(1)),
                buildPileTopToProtocol(game.getBuildPile(2)),
                buildPileTopToProtocol(game.getBuildPile(3))
        ).transformToProtocolString();
    }

    /**
     * Builds a protocol ROUND message containing scores after a round finishes.
     * @return encoded protocol ROUND message
     */

    private String buildRoundMessage() {
        Round.Score[] scores = new Round.Score[playerOrder.size()];
        for (int i = 0; i < playerOrder.size(); i++) {
            String name = playerOrder.get(i);
            Player player = playersByName.get(name);
            scores[i] = new Round.Score(name, game.getScore(player));
        }
        return new Round(scores).transformToProtocolString();
    }


    /**
     * Builds a protocol WINNER message containing final match scores.
     * @return encoded protocol WINNER message
     */

    private String buildWinnerMessage() {
        Winner.Score[] scores = new Winner.Score[playerOrder.size()];
        for (int i = 0; i < playerOrder.size(); i++) {
            String name = playerOrder.get(i);
            Player player = playersByName.get(name);
            scores[i] = new Winner.Score(name, game.getScore(player));
        }
        return new Winner(scores).transformToProtocolString();
    }
    /**
     * Converts a server-side Hand into protocol card tokens.
     * @param hand hand to convert
     * @return array of card tokens (e.g. "SB", "7", etc.)
     */

    private String[] handToStrings(Hand hand) {
        String[] cards = new String[hand.size()];
        for (int i = 0; i < hand.size(); i++) {
            // Hand uses 1-based indexing in this model.
            cards[i] = cardToProtocol(hand.getCard(i + 1));
        }
        return cards;
    }

    /**
     * Converts a model Card into a protocol token string.
     * @param card model card (may be null)
     * @return "X" for null, "SB" for wild, otherwise the number as string
     */

    private String cardToProtocol(Card card) {
        if (card == null) {
            return "X";
        }
        return card.isWild() ? "SB" : String.valueOf(card.getCardNumber());
    }

    /**
     * Converts the top card of a build pile into a protocol token.
     * For wild cards on build piles, the protocol encodes "SB" + pile size (e.g. "SB3").
     * @param pile build pile
     * @return token for the build pile top
     */

    private String buildPileTopToProtocol(BuildPile pile) {
        Card top = pile.peekTopCard();
        if (top == null) {
            return "X";
        }
        if (top.isWild()) {
            // Encode wild-on-build as SB + current pile size.
            return "SB" + pile.size();
        }
        return String.valueOf(top.getCardNumber());
    }

    /**
     * Checks whether a move is valid at the protocol "shape" level.
     * This does not check Skip-Bo rules; it only checks allowed source/target categories:
     *  STOCK -> BUILD
     *  HAND -> BUILD or DISCARD
     *  DISCARD -> BUILD
     * @param from parsed from-position token
     * @param to parsed to-position token
     * @return true if this move category is allowed, false otherwise
     */

    private boolean isMoveShapeValid(ProtocolCodec.PositionToken from, ProtocolCodec.PositionToken to) {
        return switch (from.kind) {
            case STOCK -> to.kind == ProtocolCodec.PositionKind.BUILD;
            case HAND -> to.kind == ProtocolCodec.PositionKind.BUILD
                    || to.kind == ProtocolCodec.PositionKind.DISCARD;
            case DISCARD -> to.kind == ProtocolCodec.PositionKind.BUILD;
            default -> false;
        };
    }
    /**
     * Finds a hand index (1-based) that matches a protocol card spec.
     * The protocol selects hand cards by value (e.g. "7" or "SB"), not by index.
     * This method searches the hand for the first matching card.
     * @param hand player's hand
     * @param spec protocol card token spec
     * @return matching 1-based index, or -1 if not found
     */

    private int findHandIndex(Hand hand, ProtocolCodec.CardToken spec) {
        for (int i = 1; i <= hand.size(); i++) {
            Card card = hand.getCard(i);
            if (spec.skipBo && card.isWild()) {
                return i;
            }
            if (!spec.skipBo && !card.isWild() && card.getCardNumber() == spec.number) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Advances the model's current player pointer until it lands on an active player.
     * This is used after disconnects and after END to skip inactive players.
     * A safety counter prevents infinite loops.
     */

    private void advanceToNextActive() {
        if (activePlayers.isEmpty()) {
            return;
        }
        int attempts = 0;
        do {
            game.advanceTurn();
            attempts++;
        } while (!activePlayers.contains(getCurrentPlayerName()) && attempts <= game.getPlayers().size());
    }

    /**
     * Returns the name of the current player according to the model's turn pointer.
     * @return current player's name
     */

    private String getCurrentPlayerName() {
        return game.getCurrentPlayer().getName();
    }
    /**
     * Broadcasts a raw protocol message to all connected handlers.
     * @param message encoded protocol message (may be null)
     */

    private void broadcast(String message) {
        if (message == null) {
            return;
        }
        // Copy to avoid concurrent modification issues if handlers change during send
        for (ClientHandler handler : new ArrayList<>(handlersByName.values())) {
            handler.send(message);
        }
    }

    /**
     * Broadcasts a protocol ERROR message to all connected handlers.
     * @param code error code to broadcast
     */

    private void broadcastError(ErrorCode code) {
        String message = new protocol.server.Error(code).transformToProtocolString();
        for (ClientHandler handler : new ArrayList<>(handlersByName.values())) {
            handler.send(message);
        }
    }

    /**
     * Lets bots play automatically if the current turn belongs to a bot.
     * This repeatedly executes bot turns until the current player is human,
     * the round/match ends, or the room ends.
     */

    private synchronized void playBotsIfNeeded() {
        if (roomEnded) {
            return;
        }
        // If multiple bots are in a row in turn order, this loop will play them back-to-back.
        while (isCurrentPlayerBot()) {
            ComputerPlayer bot = botPlayers.get(getCurrentPlayerName());
            if (bot == null) {
                return;
            }
            playBotTurn(bot);
            // If the bot ended the round, handle round end before continuing.
            if (game.gameOver()) {
                handleRoundEnd();
                if (roomEnded) {
                    return;
                }
                continue;
            }
            // Move to next active player and announce turn.
            advanceToNextActive();
            broadcastTurn();
        }
    }
    /**
     * Executes a full bot turn until:
     *   the bot has no valid move
     *   the bot ends its turn
     *   the round ends
     * @param bot bot player whose turn should be played
     */

    private synchronized void playBotTurn(ComputerPlayer bot) {
        while (!game.gameOver()) {
            Move move = bot.determineMove(game);
            if (move == null) {
                break;
            }
            // Convert the model move to protocol tokens for broadcasting PLAY.
            MoveTokens tokens = buildMoveTokens(bot, move);
            if (tokens == null) {
                break;
            }
            Game.MoveResult result = game.applyMove(move, bot);
            if (!result.isApplied()) {
                break;
            }

            String playMessage = buildPlayMessage(bot.getName(), tokens.from, tokens.to);
            logBotLine(playMessage);
            broadcast(playMessage);

            // If stock was played, update stock top for all clients.
            if (move instanceof StockMove) {
                String top = cardToProtocol(bot.getStockPile().peekTopCard());
                logBotLine(new Stock(bot.getName(), top).transformToProtocolString());
                broadcastStock(bot.getName());
            }
            // If bot ended the turn, refill and stop.

            if (result.isEndTurn()) {
                game.refillHand(bot);
                break;
            }
            // If bot emptied its hand mid-turn, refill so it can continue (model rule).

            if (bot.getHand().isEmpty()) {
                game.refillHand(bot);
            }
        }
    }
    /**
     * Checks if the current model turn belongs to a bot player.
     * @return true if current player is a bot, false otherwise
     */

    private boolean isCurrentPlayerBot() {
        return botNames.contains(getCurrentPlayerName());
    }

    /**
     * Generates a unique bot name that does not collide with existing player names.
     * @param index bot index (for default naming)
     * @return unique bot name
     */

    private String uniqueBotName(int index) {
        String base = "BOT" + index;
        String name = base;
        int suffix = 1;
        while (playersByName.containsKey(name)) {
            name = base + "_" + suffix;
            suffix++;
        }
        return name;
    }
    /**
     * Logs bot actions if server logging is enabled.
     * @param message protocol message to print (may be null)
     */

    private void logBotLine(String message) {
        if (!Boolean.getBoolean("skipbo.server.log") || message == null) {
            return;
        }
        System.out.println("BOT " + message);
    }

    /**
     * Builds protocol position tokens for a model Moves instance.
     * This is used when broadcasting bot PLAY messages so clients can interpret the move in protocol terms.
     * @param player bot player (needed for looking up actual hand card values)
     * @param move model move
     * @return  MoveTokens containing protocol from/to positions, or null if move cannot be encoded
     */

    private MoveTokens buildMoveTokens(Player player, Move move) {
        // STOCK -> BUILD
        if (move instanceof StockMove stockMoves) {
            return new MoveTokens(
                    new StockPilePosition(),
                    new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, stockMoves.getBuildIndex())
            );
        }

        // HAND/DISCARD -> BUILD/DISCARD
        if (move instanceof CardMove cardMoves) {
            protocol.common.position.Position fromPosition;
            if (cardMoves.getFromPile() == 'h') {

                // For HAND moves, protocol "from" includes the card value (HandPosition).
                Card card = player.getHand().getCard(cardMoves.getFromIndex());
                protocol.common.Card protocolCard = toProtocolCard(card);
                if (protocolCard == null) {
                    return null;
                }
                fromPosition = new HandPosition(protocolCard);

            } else if (cardMoves.getFromPile() == 'd') {
                // For DISCARD moves, protocol uses numbered discard pile position (0-based).
                fromPosition = new NumberedPilePosition(
                        NumberedPilePosition.Pile.DISCARD_PILE,
                        cardMoves.getFromIndex() - 1
                );
            } else {
                return null;
            }

            protocol.common.position.Position toPosition;
            if (cardMoves.getToPile() == 'b') {
                toPosition = new NumberedPilePosition(
                        NumberedPilePosition.Pile.BUILDING_PILE,
                        cardMoves.getToIndex()
                );
            } else if (cardMoves.getToPile() == 'd') {
                toPosition = new NumberedPilePosition(
                        NumberedPilePosition.Pile.DISCARD_PILE,
                        cardMoves.getToIndex() - 1
                );
            } else {
                return null;
            }
            return new MoveTokens(fromPosition, toPosition);
        }
        return null;
    }

    /**
     * Simple holder for protocol positions used in a PLAY message.
     * This is used primarily for bot broadcasting (convert model move -> protocol from/to positions).
     */

    private static final class MoveTokens {
        private final protocol.common.position.Position from;
        private final protocol.common.position.Position to;

        private MoveTokens(protocol.common.position.Position from, protocol.common.position.Position to) {
            this.from = from;
            this.to = to;
        }
    }

    /**
     * Lightweight server-side player object representing a connected human client.
     * Its determineMove(Game) returns null because moves are driven by client commands.
     */

    private static final class ClientPlayer extends Player {
        private ClientPlayer(String name) {
            super(name, new Hand(), new StockPile());
        }

        @Override
        public Move determineMove(Game game) {
            return null;
        }
    }

    /**
     * Converts a model Card} into a protocol.
     * Wild cards are encoded as a protocol card with a null number.
     * @param card model card
     * @return protocol card, or null if conversion fails
     */

    private protocol.common.Card toProtocolCard(Card card) {
        if (card == null) {
            return null;
        }
        if (card.isWild()) {
            // Wild card in protocol is represented by a Card with null value.
            return new protocol.common.Card( null);
        }
        try {
            return new protocol.common.Card(card.getCardNumber());
        } catch (ProtocolException e) {
            return null;
        }
    }
}
