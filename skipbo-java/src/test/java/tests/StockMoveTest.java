package tests;

import model.moves.StockMove;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests StockMove accessors and end-turn behavior.
 * @ensures Only test-local move instances are created and asserted.
 */
public class StockMoveTest {
    private char action;
    private int buildIndex;
    private StockMove move;

    /**
     * Initializes a representative StockMove for accessor tests.
     * @ensures move contains the configured action and build index.
     */
    @BeforeEach
    void setUp() {
        action = 'b';
        buildIndex = 2;
        move = new StockMove(action, buildIndex);
    }

    /**
     * Verifies constructor values are returned by getters.
     * @requires move is initialized in #setUp().
     * @ensures Getter values match the constructor arguments.
     */
    @Test
    void constructorSetsFields() {
        assertEquals(action, move.getAction());
        assertEquals(buildIndex, move.getBuildIndex());
    }

    /**
     * Confirms stock moves never end a turn directly.
     * @ensures endTurn() returns false for stock moves.
     */
    @Test
    void endTurnShouldAlwaysReturnFalse() {
        StockMove move1 = new StockMove('b', 0);
        StockMove move2 = new StockMove('b', 3);

        assertFalse(move1.endTurn());
        assertFalse(move2.endTurn());
    }
}
