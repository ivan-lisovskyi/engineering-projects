package tests;

import model.Board;
import model.Card;
import model.piles.DrawPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the board refills its draw pile from completed piles.
 * @ensures Only test-local board state is mutated.
 */
public class BoardCompletedPileTest {
    private Board board;

    /**
     * Initializes a board with an empty draw pile for refill tests.
     * @ensures board has an empty draw pile.
     */
    @BeforeEach
    void setUp() {
        board = new Board(new DrawPile());
        board.clearDrawPile();
    }

    /**
     * Verifies drawing triggers refill from completed pile when the draw pile is empty.
     * @requires Draw pile is empty and a completed pile exists.
     * @ensures Draw pile is refilled and completed pile is cleared.
     */
    @Test
    void drawRefillsFromCompletedWhenEmpty() {
        for (int i = 1; i <= 12; i++) {
            assertTrue(board.putCard(0, new Card(i, Card.CardColor.BLUE)));
        }

        assertEquals(12, board.getCompletedPileSize());
        assertEquals(0, board.getBuildPile(0).size());

        Card drawn = board.draw();

        assertNotNull(drawn);
        assertEquals(0, board.getCompletedPileSize());
        assertEquals(11, board.getDrawPile().size());
    }
}
