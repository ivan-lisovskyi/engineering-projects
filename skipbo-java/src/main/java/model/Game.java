package model;

import model.moves.CardMove;
import model.moves.Move;
import model.moves.StockMove;
import model.piles.Hand;
import model.players.Player;
import model.exceptions.*;
import model.piles.BuildPile;
import model.piles.DiscardPile;
import model.piles.DrawPile;

import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Core Skip-Bo game engine that manages turns, rounds, and scoring.
 * players != null
 * players.size() >= 2 && players.size() <= 6
 *  scores != null && scores.keySet().containsAll(players)
 *  board != null
 *  currentPlayerIndex >= 0 && currentPlayerIndex < players.size()
 *  completedRounds >= 0
 *  stateVersion >= 0 && progressVersion >= 0
 */
public class Game {
    public static final int WINNING_SCORE = 500; // Winning score
    private static final int ROUND_WIN_POINTS = 25; // Round winner points
    private static final int STOCK_CARD_POINTS = 5; // Point per card in opponents stockpile
    private static final int SMALL_GAME_STOCK = 30;
    private static final int LARGE_GAME_STOCK = 20;
    private static final int SMALL_GAME_THRESHOLD = 4;
    private static final int MAX_ROUNDS = readIntProperty("skipbo.match.rounds", 0); // 0 = no round limit
    // Index of the current player in the list of players
    private int currentPlayerIndex;

    // List of all players participating in the game
    private List<Player> players;

    // A mapping of each player to their respective score
    private Map<Player, Integer> scores;

    // Flag indicating whether the current round's score has been calculated
    private boolean roundScored;

    // The result of the last completed round
    private RoundResult lastRoundResult;

    // The total number of rounds that have been completed so far
    private int completedRounds;

    // The current game board or state of the game
    private Board board;

    // A version number to track changes in the game's state (useful for concurrency or persistence)
    private long stateVersion;
    private long progressVersion; // bumps on visible gameplay changes

    /**
     * Creates a new game with the provided players and starts the first round.
     *
     * @param players the players participating in the game
     * @requires players != null
     * @requires players.size() >= 2 && players.size() <= 6
     * @ensures this.players contains the provided players in the same order
     * @ensures scores contains an entry for each player with score 0
     * @ensures board is initialized for a new round
     * @throws IllegalNumberOfPlayersException if the player count is not between 2 and 6
     */
    public Game(List<Player> players) {
        if (players == null || players.size() < 2 || players.size() > 6) {
            throw new IllegalNumberOfPlayersException("Game needs 2–6 players");
        }

        this.players = new ArrayList<>(players);

        scores = new HashMap<>();
        for (Player player : players) {
            scores.put(player, 0);
        }

        this.completedRounds = 0;
        startNewRound(null);
    }

    /**
     * Returns the build pile at the given index.
     *
     * @param index the 0-based build pile index
     * @return the requested build pile
     */
    public BuildPile getBuildPile(int index) {
        return board.getBuildPile(index);
    }

    /**
     * Determines whether the current round is over.
     *
     * @return true if the current player's stockpile is empty, false otherwise
     */

    public boolean gameOver() {
        return players.get(currentPlayerIndex).getStockPile().isEmpty();
    }

    /**
     * Retrieves the player whose turn it currently is in the game.
     *
     * @return the player object representing the current player.
     */
    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    /**
     * Applies a single move to the game state and returns the result.
     *
     * @param move the move to apply (null ends the turn)
     * @param p the player making the move
     * @requires p != null
     * @requires players.contains(p)
     * @requires p == getCurrentPlayer()
     * @ensures result.isApplied() implies the move was applied to the board and player piles
     * @ensures result.isEndTurn() implies the turn should end after this call
     * @return the result of applying the move
     */
    public MoveResult applyMove(Move move, Player p) {
        Objects.requireNonNull(p, "player");

        if (move == null) {
            return MoveResult.endTurn("Turn ended."); // no move chosen (ends turn)
        }

        try {
            // ---------- Stock -> Build ----------
            if (move instanceof StockMove sm) {
                int b = sm.getBuildIndex();
                BuildPile buildPile = getBuildPileChecked(b);

                Card top = p.getStockPile().peekTopCard();
                if (top == null) {
                    throw new IllegalMoveException("Stock pile is empty.");
                }

                if (!buildPile.canPlaceCard(top)) {
                    throw new IllegalMoveException("Cannot place stock card on build pile " + (b + 1) + ".");
                }

                if (!board.putCard(b, top)) { // board handles completion/recycle
                    throw new IllegalMoveException("Move rejected by build pile.");
                }
                p.getStockPile().playCard();
                markProgress();
                return MoveResult.applied(false);
            }

            // ---------- Hand/Discard moves ----------
            if (move instanceof CardMove cm) {
                char from = cm.getFromPile();
                char to = cm.getToPile();

                switch (from) {
                    case 'h': {
                        validateHandIndex(p, cm.getFromIndex());
                        if (to == 'd') {
                            validateDiscardIndex(p, cm.getToIndex());
                            Card c = p.getHand().takeCard(cm.getFromIndex());
                            p.getDiscardPile(cm.getToIndex()).discard(c);
                            markProgress();
                            return MoveResult.applied(true);
                        }

                        if (to == 'b') {
                            int buildIndex = cm.getToIndex();
                            BuildPile buildPile = getBuildPileChecked(buildIndex);
                            Card peek = p.getHand().getCard(cm.getFromIndex());

                            if (!buildPile.canPlaceCard(peek)) {
                                throw new IllegalMoveException("Cannot place that card on build pile " + (buildIndex + 1) + ".");
                            }

                            if (!board.putCard(buildIndex, peek)) { // validate + auto-recycle
                                throw new IllegalMoveException("Move rejected by build pile.");
                            }
                            p.getHand().takeCard(cm.getFromIndex());
                            markProgress();
                            return MoveResult.applied(false);
                        }

                        throw new InvalidMoveException("Unknown target pile: " + to);
                    }
                    case 'd': {
                        validateDiscardIndex(p, cm.getFromIndex());
                        DiscardPile dp = p.getDiscardPile(cm.getFromIndex());
                        if (to != 'b') {
                            throw new InvalidMoveException("Unknown target pile: " + to);
                        }
                        int buildIndex = cm.getToIndex();
                        BuildPile buildPile = getBuildPileChecked(buildIndex);

                        Card top = dp.peekTopCard();
                        if (top == null) {
                            throw new IllegalMoveException("Discard pile " + cm.getFromIndex() + " is empty.");
                        }

                        if (!buildPile.canPlaceCard(top)) {
                            throw new IllegalMoveException("Cannot place discard card on build pile " + (buildIndex + 1) + ".");
                        }

                        if (!board.putCard(buildIndex, top)) { // validate + auto-recycle
                            throw new IllegalMoveException("Move rejected by build pile.");
                        }
                        dp.takeTop();
                        markProgress();
                        return MoveResult.applied(false);
                    }
                    default:
                        throw new InvalidMoveException("Unknown source pile: " + from);
                }
            }
        } catch (SkipBoException e) {
            return MoveResult.illegal(e.getMessage());
        } catch (RuntimeException e) {
            return MoveResult.illegal("Move failed: " + e.getMessage());
        }

        return MoveResult.illegal("Unknown move.");
    }

    /**
     * Returns the current board state.
     * @return the board
     */
    public Board getBoard() {
        return this.board;
    }

    /**
     * Returns the list of players in turn order.
     * @return the players list
     */
    public List<Player> getPlayers() {
        return players;
    }

    /**
     * Returns the current player's index.
     * @return the current player index (0-based)
     */
    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    /**
     * Advances the turn to the next player.
     */
    public void advanceTurn() {
        if (players.isEmpty()) {
            return;
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        markStateChange();
    }

    /**
     * Starts a new round, dealing fresh cards and optionally setting the starting player.
     * @param startingPlayer the player to start, or null to start with index 0
     * @requires players != null && !players.isEmpty()
     * @ensures board is reset with a fresh draw pile and empty build piles
     * @ensures each player's hand, stock, and discard piles are reset and refilled
     * @ensures currentPlayerIndex corresponds to startingPlayer if present, otherwise 0
     */
    public void startNewRound(Player startingPlayer) {
        this.board = new Board(new DrawPile()); // fresh shuffled deck every round
        for (Player player : players) {
            player.resetForNewRound();
        }
        dealInitialCards();

        int idx = startingPlayer == null ? -1 : players.indexOf(startingPlayer);
        currentPlayerIndex = Math.max(idx, 0);

        roundScored = false;
        lastRoundResult = null;
        markProgress();
    }

    /**
     * Executes a single player's turn.
     */
    public void playOneTurn() {
        if (gameOver()) {
            return;
        }
        Player currentPlayer = getCurrentPlayer();
        currentPlayer.makeMove(this);
        if (!gameOver()) {
            advanceTurn();
        }
    }

    /**
     * Plays a full round and returns the scoring result.
     * @return the round result
     */
    public RoundResult playRound() {
        while (!gameOver()) {
            playOneTurn();
        }
        return finishRound();
    }

    /**
     * Plays rounds until the match ends.
     * The match ends when a player reaches the winning score or a configured
     * round limit is reached.
     */
    public Player playMatch() {
        while (!isMatchOver()) {
            RoundResult result = playRound();
            if (!isMatchOver()) {
                startNewRound(result.getWinner());
            }
        }
        return getMatchWinner();
    }

    /**
     * Scores the round and returns its result.
     * @return the round result
     * @requires gameOver()
     * @ensures roundScored == true
     * @ensures lastRoundResult != null
     * @throws RoundNotOverException if the round is not finished
     */
    public RoundResult finishRound() {
        if (!gameOver()) {
            throw new RoundNotOverException("Round is not over yet");
        }
        return scoreRound(getCurrentPlayer());
    }

    /**
     * Scores a round that ended in a stalemate.
     * @return the round result based on a stalemate winner
     */
    public RoundResult resolveStalemate() {
        return scoreRound(pickStalemateWinner());
    }

    /**
     * Indicates whether the overall match has ended.
     * @return true if a player reached the winning score or a round limit was reached
     */
    public boolean isMatchOver() {
        if (isRoundLimitReached()) {
            return true;
        }
        return getMatchWinner() != null;
    }

    /**
     * Returns the match winner, if any.
     * When a round limit is reached, the highest-score player is returned.
     * @return the winning player, or null if the match is still ongoing
     */
    public Player getMatchWinner() {
        if (isRoundLimitReached()) {
            return pickBestScoreWinner();
        }
        Player winner = null;
        int bestScore = WINNING_SCORE;
        for (Map.Entry<Player, Integer> entry : scores.entrySet()) {
            int score = entry.getValue();
            if (score >= WINNING_SCORE && (winner == null || score > bestScore)) {
                winner = entry.getKey();
                bestScore = score;
            }
        }
        return winner;
    }

    /**
     * Returns the current score for the given player.
     * @param player the player to look up
     * @return the player's score
     */
    public int getScore(Player player) {
        return scores.getOrDefault(player, 0);
    }

    /**
     * Returns the progress version, incremented when visible state changes.
     * @return the progress version
     */
    public long getProgressVersion() {
        return progressVersion;
    }

    /**
     * Draws a card from the board or throws if none are available.
     * @return the drawn card
     * @throws NoCardsLeftException if no cards remain
     */
    private Card drawCardOrThrow() {
        Card card = board.draw();
        if (card == null) {
            throw new NoCardsLeftException("No cards left in the draw pile");
        }
        return card;
    }

    /**
     * Refills the player's hand up to five cards.
     * @param player the player to refill
     * @requires player != null
     * @ensures player.getHand().size() <= Hand.MAX_SIZE
     * @ensures player.getHand().size() >= 0
     */
    public void refillHand(Player player) {
        int drawnCount = 0;
        while (player.getHand().size() < Hand.MAX_SIZE) {
            Card drawn = board.draw();
            if (drawn == null) {
                break;
            }
            player.getHand().draw(drawn);
            drawnCount++;
        }
        if (drawnCount > 0) {
            markProgress();
        }
    }

    /**
     * Determines the starting stock count based on the number of players in the game.
     * @param playerCount the number of players participating in the game
     * @return the initial stock count, either for a small game or a large game,
     *         depending on the number of players
     */
    public static int getStartingStockCount(int playerCount) {
        return playerCount <= SMALL_GAME_THRESHOLD ? SMALL_GAME_STOCK : LARGE_GAME_STOCK;
    }

    /**
     * Deals initial stock and hand cards for a new round.
     */
    private void dealInitialCards() {
        int cardsToHandout = getStartingStockCount(players.size()); // official Skip-Bo setup rule
        for (int i = 0; i < cardsToHandout; i++) {
            for (Player player : players) {
                player.getStockPile().draw(drawCardOrThrow());
            }
        }
        for (int i = 0; i < Hand.MAX_SIZE; i++) {
            for (Player player : players) {
                player.getHand().draw(drawCardOrThrow());
            }
        }
    }

    /**
     * Computes the round score awarded to the winner.
     * @param winner the round winner
     * @return the points awarded
     */
    private int calculateRoundScore(Player winner) {
        int points = ROUND_WIN_POINTS; // base points for round winner
        for (Player player : players) {
            if (player == winner) {
                continue;
            }
            points += player.getStockPile().size() * STOCK_CARD_POINTS; // penalty for others
        }
        return points;
    }

    /**
     * Selects a winner for a stalemated round.
     * @return the player with the smallest stock pile
     */
    private Player pickStalemateWinner() {
        Player best = null;
        int bestStock = Integer.MAX_VALUE;
        for (Player player : players) {
            int stockSize = player.getStockPile().size();
            if (stockSize < bestStock) {
                bestStock = stockSize;
                best = player;
            }
        }
        if (best == null && !players.isEmpty()) { // fallback if all piles empty
            return players.getFirst();
        }
        return best;
    }

    /**
     * Scores the round for the given winner, caching the result.
     * @param winner the round winner
     * @return the round result
     */
    private RoundResult scoreRound(Player winner) {
        if (roundScored && lastRoundResult != null) { // avoid double scoring
            return lastRoundResult;
        }
        int points = calculateRoundScore(winner);
        addScore(winner, points);
        if (!roundScored) { // only count once per round
            completedRounds++;
        }
        roundScored = true;
        lastRoundResult = new RoundResult(winner, points, getScore(winner));
        return lastRoundResult;
    }

    /**
     * Indicates whether the configured round limit has been reached.
     * @return true if a round limit is configured and reached
     */
    private boolean isRoundLimitReached() {
        return MAX_ROUNDS > 0 && completedRounds >= MAX_ROUNDS;
    }

    /**
     * Picks the highest-score player.
     * @return the player with the best score, or null if no players exist
     */
    private Player pickBestScoreWinner() {
        Player winner = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<Player, Integer> entry : scores.entrySet()) {
            int score = entry.getValue();
            if (winner == null || score > bestScore) {
                winner = entry.getKey();
                bestScore = score;
            }
        }
        return winner;
    }

    /**
     * Adds points to the given player's score.
     * @param player the player to award points to
     * @param points the points to add
     */
    private void addScore(Player player, int points) {
        scores.put(player, getScore(player) + points);
    }

    /**
     * Marks progress and state versions as changed.
     */
    private void markProgress() {
        progressVersion++; // used by UI/network to refresh
        stateVersion++;
    }

    /**
     * Marks a state change without progress.
     */
    private void markStateChange() {
        stateVersion++; // state changed but no "move progress"
    }

    /**
     * Returns the build pile at the given index or throws if out of range.
     *
     * @param index the 0-based build pile index
     * @return the build pile
     * @throws InvalidMoveException if the index is out of range
     */
    private BuildPile getBuildPileChecked(int index) {
        if (index < 0 || index >= Board.BUILD_PILE_COUNT) {
            throw new InvalidMoveException("Build pile index out of range: " + (index + 1));
        }
        return getBuildPile(index);
    }

    /**
     * Validates a 1-based hand index for the given player.
     * @param player the player to check
     * @param index the 1-based hand index
     * @throws IllegalMoveException if the hand is empty
     * @throws InvalidMoveException if the index is out of range
     */
    private void validateHandIndex(Player player, int index) {
        if (player.getHand().isEmpty()) {
            throw new IllegalMoveException("Hand is empty.");
        }
        if (index < 1 || index > player.getHand().size()) { // UI uses 1-based indices
            throw new InvalidMoveException("Hand index out of range: " + index);
        }
    }

    /**
     * Validates a 1-based discard pile index for the given player.
     * @param player the player to check
     * @param index the 1-based discard pile index
     * @throws InvalidMoveException if the index is out of range
     */
    private void validateDiscardIndex(Player player, int index) {
        if (index < 1 || index > player.DISCARD_PILE_COUNT) { // discard piles are 1..4
            throw new InvalidMoveException("Discard pile index out of range: " + index);
        }
    }

    /**
     * Reads a non-negative integer system property.
     * @param key the system property key
     * @param defaultValue the fallback value
     * @return the parsed value or the default
     */
    private static int readIntProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) { // missing property -> default
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim())); // negative treated as 0
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Captures the scoring result of a completed round.
     */
    public static final class RoundResult {
        private final Player winner;
        private final int pointsAwarded;
        private final int totalScore;

        /**
         * Creates a round result snapshot.
         * @param winner the round winner
         * @param pointsAwarded the points awarded in the round
         * @param totalScore the winner's total score after the round
         */
        private RoundResult(Player winner, int pointsAwarded, int totalScore) {
            this.winner = winner;
            this.pointsAwarded = pointsAwarded;
            this.totalScore = totalScore;
        }

        /**
         * Returns the round winner.
         * @return the winner
         */
        public Player getWinner() {
            return winner;
        }

        /**
         * Returns the points awarded in the round.
         * @return the points awarded
         */
        public int getPointsAwarded() {
            return pointsAwarded;
        }

        /**
         * Returns the winner's total score after the round.
         * @return the total score
         */
        public int getTotalScore() {
            return totalScore;
        }
    }

    /**
     * Encapsulates the outcome of applying a move to the game.
     */
    public static final class MoveResult {
        private final boolean applied;
        private final boolean endTurn;
        private final String message;

        /**
         * Creates a move result.
         * @param applied whether the move was applied
         * @param endTurn whether the move ends the turn
         * @param message optional message describing the result
         */
        private MoveResult(boolean applied, boolean endTurn, String message) {
            this.applied = applied;
            this.endTurn = endTurn;
            this.message = message;
        }

        /**
         * Creates a successful move result.
         * @param endTurn true if the move ends the turn
         * @return the move result
         */
        public static MoveResult applied(boolean endTurn) {
            return new MoveResult(true, endTurn, null);
        }

        /**
         * Creates an illegal move result.
         * @param message the error message
         * @return the move result
         */
        public static MoveResult illegal(String message) {
            return new MoveResult(false, false, message);
        }

        /**
         * Creates a result that ends the turn without applying a move.
         * @param message the end-turn message
         * @return the move result
         */
        public static MoveResult endTurn(String message) {
            return new MoveResult(false, true, message);
        }

        /**
         * Indicates whether the move was applied.
         * @return true if applied
         */
        public boolean isApplied() {
            return applied;
        }

        /**
         * Indicates whether the move ended the turn.
         * @return true if the turn ended
         */
        public boolean isEndTurn() {
            return endTurn;
        }

        /**
         * Returns the associated message, if any.
         * @return the message or null
         */
        public String getMessage() {
            return message;
        }
    }
}
