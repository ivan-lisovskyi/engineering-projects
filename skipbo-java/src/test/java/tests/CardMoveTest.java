package tests;

import model.moves.CardMove;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests CardMove field access and end-turn semantics.
 * @ensures Only test-local move instances are created and asserted.
 */
public class CardMoveTest {
    private char fromPile;
    private int fromIndex;
    private char toPile;
    private int toIndex;
    private CardMove move;

    /**
     * Initializes a representative CardMove for accessor tests.
     * @ensures move contains the configured from/to fields.
     */
    @BeforeEach
    void setUp() {
        fromPile = 'h';
        fromIndex = 2;
        toPile = 'b';
        toIndex = 1;
        move = new CardMove(fromPile, fromIndex, toPile, toIndex);
    }

    /**
     * Verifies constructor values are returned by getters.
     * @requires move is initialized in #setUp().
     * @ensures Getter values match the constructor arguments.
     */
    @Test
    void constructorSetsFields() {
        assertEquals(fromPile, move.getFromPile());
        assertEquals(fromIndex, move.getFromIndex());
        assertEquals(toPile, move.getToPile());
        assertEquals(toIndex, move.getToIndex());
    }

    /**
     * Confirms discard moves from hand end the turn.
     * @ensures endTurn() returns true.
     */
    @Test
    void endTurnTrueForHandToDiscard() {
        CardMove move = new CardMove('h', 0, 'd', 0);

        assertTrue(move.endTurn());
    }

    /**
     * Confirms hand-to-build moves do not end the turn.
     * @ensures endTurn() returns false.
     */
    @Test
    void endTurnFalseForHandToBuild() {
        CardMove move = new CardMove('h', 1, 'b', 0);

        assertFalse(move.endTurn());
    }

    /**
     * Confirms stock-to-build moves do not end the turn.
     * @ensures endTurn() returns false.
     */
    @Test
    void endTurnFalseForStockToBuild() {
        CardMove move = new CardMove('s', 0, 'b', 2);

        assertFalse(move.endTurn());
    }

    /**
     * Confirms discard-to-build moves do not end the turn.
     * @ensures endTurn() returns false.
     */
    @Test
    void endTurnFalseForDiscardToBuild() {
        CardMove move = new CardMove('d', 3, 'b', 1);

        assertFalse(move.endTurn());
    }
}
