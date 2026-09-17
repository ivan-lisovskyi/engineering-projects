package tests;

import model.Game;
import model.moves.Move;
import model.players.ComputerPlayer;
import model.players.Player;
import model.players.Strategy;

import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ComputerPlayer delegation to strategies and strategy updates.
 * @ensures Only test-local player instances are mutated.
 */
public class ComputerPlayerTest {
    private Hand hand;
    private StockPile stockPile;

    /**
     * Initializes empty hand and stock pile for each test.
     * @ensures hand and stockPile are empty.
     */
    @BeforeEach
    void setUp() {
        hand = new Hand();
        stockPile = new StockPile();
    }

    /**
     * Minimal move implementation that never ends a turn.
     * @ensures #endTurn() always returns false.
     */
    private static class DummyMove implements Move {
        /**
         * Indicates that the turn should continue.
         * @return false
         * @ensures Always returns false.
         */
        @Override
        public boolean endTurn() {
            return false;
        }
    }

    /**
     * Strategy that records the last game/player and returns a predefined move.
     * @ensures #determineMove(Game, Player) captures its inputs.
     */
    private static class RecordingStrategy implements Strategy {
        Game lastGame;
        Player lastPlayer;
        Move toReturn;

        /**
         * Creates a recording strategy that returns the given move.
         * @param toReturn move to return from #determineMove(Game, Player)
         * @ensures toReturn is stored for later use.
         */
        RecordingStrategy(Move toReturn) {
            this.toReturn = toReturn;
        }

        /**
         * Returns the strategy name.
         * @return strategy name
         * @ensures Returns a non-null name.
         */
        @Override
        public String getName() {
            return "RecordingStrategy";
        }

        /**
         * Records inputs and returns the preset move.
         * @param game   game context
         * @param player player making the move
         * @return the move configured at construction time
         * @ensures lastGame and lastPlayer are updated.
         */
        @Override
        public Move determineMove(Game game, Player player) {
            this.lastGame = game;
            this.lastPlayer = player;
            return toReturn;
        }
    }

    /**
     * Verifies determineMove delegates to the strategy.
     * @requires A RecordingStrategy is installed.
     * @ensures Returned move and captured inputs match the call.
     */
    @Test
    void determineMoveDelegatesToStrategy() {
        Move expectedMove = new DummyMove();
        RecordingStrategy strategy = new RecordingStrategy(expectedMove);

        ComputerPlayer cpu = new ComputerPlayer("CPU", hand, stockPile, strategy);

        Game game = null;

        Move actualMove = cpu.determineMove(game);

        assertSame(expectedMove, actualMove);

        assertSame(game, strategy.lastGame);
        assertSame(cpu, strategy.lastPlayer);
    }

    /**
     * Ensures getStrategy returns the currently configured strategy.
     *
     * @requires A strategy is set in the constructor.
     * @ensures Returned strategy is the same instance.
     */
    @Test
    void getStrategyReturnsCurrentStrategy() {
        Strategy strategy = new RecordingStrategy(new DummyMove());

        ComputerPlayer cpu = new ComputerPlayer("CPU", hand, stockPile, strategy);

        assertSame(strategy, cpu.getStrategy());
    }

    /**
     * Ensures setStrategy swaps the strategy used by determineMove.
     * @requires A new strategy instance is provided.
     * @ensures determineMove delegates to the new strategy only.
     */
    @Test
    void setStrategyReplacesDelegate() {
        RecordingStrategy strategy1 = new RecordingStrategy(new DummyMove());
        Move expectedMove2 = new DummyMove();
        RecordingStrategy strategy2 = new RecordingStrategy(expectedMove2);

        ComputerPlayer cpu = new ComputerPlayer("CPU", hand, stockPile, strategy1);

        cpu.setStrategy(strategy2);

        Move actualMove = cpu.determineMove(null);

        assertSame(expectedMove2, actualMove);
        assertSame(cpu, strategy2.lastPlayer);

        assertNull(strategy1.lastPlayer);
        assertNull(strategy1.lastGame);
    }
}
