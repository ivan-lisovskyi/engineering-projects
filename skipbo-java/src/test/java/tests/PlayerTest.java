package tests;

import model.Board;
import model.Game;
import model.moves.Move;
import model.players.Player;
import model.piles.DiscardPile;
import model.piles.Hand;
import model.piles.StockPile;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Player initialization, move loop behavior, and round resets.
 * @ensures Only test-local player/game state is mutated.
 */
public class PlayerTest {
    private Hand hand;
    private StockPile stock;

    /**
     * Creates fresh piles for each test.
     * @ensures hand and stock are empty.
     */
    @BeforeEach
    void setUp() {
        hand = new Hand();
        stock = new StockPile();
    }

    /**
     * Simple move implementation with configurable end-turn behavior.
     * @ensures #endTurn() returns the configured value.
     */
    private static class DummyMove implements Move {
        private final boolean endTurn;

        /**
         * Creates a move with the given end-turn flag.
         * @param endTurn whether the move ends the turn
         * @ensures endTurn() returns the provided value.
         */
        DummyMove(boolean endTurn) {
            this.endTurn = endTurn;
        }

        /**
         * Indicates whether the move ends the turn.
         * @return true if the move ends the turn
         * @ensures Returns the value provided at construction.
         */
        @Override
        public boolean endTurn() {
            return endTurn;
        }
    }

    /**
     * Player stub that returns a scripted list of moves.
     * @ensures #determineMove(Game) returns scripted moves in order.
     */
    private static class TestPlayer extends Player {
        private final List<Move> scriptedMoves;
        private int idx = 0;

        /**
         * Creates a test player with scripted moves.
         * @param name player name
         * @param hand player hand
         * @param stockPile player stock pile
         * @param scriptedMoves moves to return in sequence
         * @requires Parameters are non-null.
         * @ensures Scripted moves are stored for later use.
         */
        TestPlayer(String name, Hand hand, StockPile stockPile, List<Move> scriptedMoves) {
            super(name, hand, stockPile);
            this.scriptedMoves = scriptedMoves;
        }

        /**
         * Returns the next scripted move or an end-turn move when exhausted.
         * @param game game context
         * @return next scripted move
         * @ensures Returns a move; returns end-turn when scripts are exhausted.
         */
        @Override
        public Move determineMove(Game game) {
            if (idx >= scriptedMoves.size()) {
                return new DummyMove(true);
            }
            return scriptedMoves.get(idx++);
        }
    }


    /**
     * Game stub that records move application and refill calls.
     * @ensures Call counters and applied move lists are updated on use.
     */
    private static class FakeGame extends Game {
        private final Board board;
        int applyMoveCallCount = 0;
        int refillHandCallCount = 0;
        final List<Move> appliedMoves = new ArrayList<>();
        final List<Player> appliedPlayers = new ArrayList<>();


        /**
         * Creates a fake game backed by the provided board.
         * @param board board instance to return
         * @ensures The game is initialized with two dummy players.
         */
        FakeGame(Board board) {
            super(buildPlayers());
            this.board = board;
        }

        /**
         * Builds two dummy players for the fake game.
         * @return list of two players
         * @ensures Returned list contains two players.
         */
        private static List<Player> buildPlayers() {
            Player p1 = new TestPlayer("Dummy", new Hand(), new StockPile(), List.of(new DummyMove(true)));
            Player p2 = new TestPlayer("Dummy2", new Hand(), new StockPile(), List.of(new DummyMove(true)));
            return List.of(p1, p2);
        }

        /**
         * Returns the configured board.
         * @return board instance
         * @ensures Returns the board passed to the constructor.
         */
        @Override
        public Board getBoard() {
            return board;
        }

        /**
         * Records applied moves and returns an appropriate MoveResult.
         * @param move move to apply
         * @param player player applying the move
         * @return applied or end-turn result
         * @ensures Call counters and applied lists are updated.
         */
        @Override
        public MoveResult applyMove(Move move, Player player) {
            applyMoveCallCount++;
            appliedMoves.add(move);
            appliedPlayers.add(player);
            if (move == null) {
                return MoveResult.endTurn("Turn ended.");
            }
            return MoveResult.applied(move.endTurn());
        }

        /**
         * Records a hand refill call.
         * @param player player to refill
         * @ensures refillHandCallCount is incremented.
         */
        @Override
        public void refillHand(Player player) {
            refillHandCallCount++;
        }
    }

    /**
     * Verifies constructor initializes name, piles, and discard piles.
     * @requires Piles are provided at construction.
     * @ensures Name and piles are set and four discard piles are created.
     */
    @Test
    void constructorInitializesPilesAndName() {
        Player p = new TestPlayer("Alice", hand, stock, List.of(new DummyMove(true)));

        assertEquals("Alice", p.getName());
        assertSame(hand, p.getHand());
        assertSame(stock, p.getStockPile());

        DiscardPile d1 = p.getDiscardPile(1);
        DiscardPile d2 = p.getDiscardPile(2);
        DiscardPile d3 = p.getDiscardPile(3);
        DiscardPile d4 = p.getDiscardPile(4);

        assertNotNull(d1);
        assertNotNull(d2);
        assertNotNull(d3);
        assertNotNull(d4);

        assertNotSame(d1, d2);
        assertNotSame(d2, d3);
        assertNotSame(d3, d4);

        assertSame(d1, p.getDiscardPile(1));
        assertSame(d4, p.getDiscardPile(4));
    }

    /**
     * Verifies stock pile reference is preserved from construction.
     * @requires A stock pile is provided at construction.
     * @ensures Player#getStockPile() returns the same instance.
     */
    @Test
    void stockPileFromConstructor() {
        Player p = new TestPlayer("Bob", hand, stock, List.of(new DummyMove(true)));

        assertSame(stock, p.getStockPile());
    }

    /**
     * Ensures makeMove stops after a move that ends the turn.
     * @requires Scripted moves include an end-turn move.
     * @ensures Only moves up to the end-turn move are applied.
     */
    @Test
    void makeMoveStopsOnEndTurn() {
        Board board = null;
        FakeGame game = new FakeGame(board);

        Move m1 = new DummyMove(false);
        Move m2 = new DummyMove(true); // ends turn
        Player p = new TestPlayer("CPU", hand, stock, List.of(m1, m2));

        p.makeMove(game);

        assertEquals(2, game.applyMoveCallCount);
        assertSame(p, game.appliedPlayers.get(0));
        assertSame(p, game.appliedPlayers.get(1));

        assertSame(m1, game.appliedMoves.get(0));

        assertSame(m2, game.appliedMoves.get(1));
    }

    /**
     * Ensures makeMove continues until a move ends the turn.
     * @requires Scripted moves eventually include an end-turn move.
     * @ensures All moves up to the end-turn move are applied.
     */
    @Test
    void makeMoveContinuesUntilEndTurn() {
        Board board = null;
        FakeGame game = new FakeGame(board);

        Move m1 = new DummyMove(false);
        Move m2 = new DummyMove(false);
        Move m3 = new DummyMove(true);
        Player p = new TestPlayer("CPU", hand, stock, List.of(m1, m2, m3));

        p.makeMove(game);

        assertEquals(3, game.applyMoveCallCount);
        assertSame(m1, game.appliedMoves.get(0));
        assertSame(m2, game.appliedMoves.get(1));
        assertSame(m3, game.appliedMoves.get(2));
    }


    /**
     * Ensures makeMove handles a null move without throwing.
     * @requires Player#determineMove(Game) returns null.
     * @ensures The game applies a null move and refills the hand.
     */
    @Test
    void makeMoveHandlesNullMove() {
        FakeGame game = new FakeGame(null);

        List<Move> scripted = new ArrayList<>();
        scripted.add(null);

        Player p = new TestPlayer("CPU", hand, stock, scripted);

        assertDoesNotThrow(() -> p.makeMove(game));

        assertEquals(1, game.applyMoveCallCount);
        assertNull(game.appliedMoves.get(0));

        assertEquals(1, game.refillHandCallCount);
    }

    /**
     * Verifies resetForNewRound clears all player piles.
     * @requires Player piles contain cards.
     * @ensures Hand, stock, and discard piles are empty.
     */
    @Test
    void resetForNewRoundClearsPiles() {
        Player p = new TestPlayer("Alice", hand, stock, List.of(new DummyMove(true)));
        hand.draw(new model.Card(1, model.Card.CardColor.BLUE));
        stock.draw(new model.Card(2, model.Card.CardColor.RED));
        p.getDiscardPile(1).discard(new model.Card(3, model.Card.CardColor.GREEN));

        p.resetForNewRound();

        assertTrue(hand.isEmpty());
        assertTrue(stock.isEmpty());
        for (int i = 1; i <= Player.DISCARD_PILE_COUNT; i++) {
            assertTrue(p.getDiscardPile(i).isEmpty());
        }
    }
}
