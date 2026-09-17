package tests;

import model.Board;
import model.Card;
import model.exceptions.BuildPileIndexOutOfRangeException;
import model.piles.DrawPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Board behavior for build piles, drawing, and defensive views.
 * @ensures Board state is confined to test-local instances.
 */
class BoardTest {

    private Board board;

    /**
     * Creates a board populated with sample build piles.
     * @ensures board has four build piles seeded with cards.
     */
    @BeforeEach
    void setUp() {
        this.board = new Board(new DrawPile());
        board.getBuildPile(0).placeCard(new Card(1, Card.CardColor.YELLOW));
        board.getBuildPile(0).placeCard(new Card(2, Card.CardColor.YELLOW));
        board.getBuildPile(0).placeCard(new Card(3, Card.CardColor.YELLOW));

        board.getBuildPile(1).placeCard(new Card(1, Card.CardColor.YELLOW));
        board.getBuildPile(1).placeCard(new Card(2, Card.CardColor.RED));
        board.getBuildPile(1).placeCard(new Card(3, Card.CardColor.YELLOW));

        board.getBuildPile(2).placeCard(new Card(1, Card.CardColor.YELLOW));
        board.getBuildPile(2).placeCard(new Card(2, Card.CardColor.YELLOW));
        board.getBuildPile(2).placeCard(new Card(3, Card.CardColor.BLUE));

        board.getBuildPile(3).placeCard(new Card(1, Card.CardColor.YELLOW));
        board.getBuildPile(3).placeCard(new Card(2, Card.CardColor.YELLOW));
        board.getBuildPile(3).placeCard(new Card(3, Card.CardColor.BLUE));
    }

    /**
     * Verifies deepCopy creates an independent board.
     * @requires board is populated in #setUp().
     * @ensures Mutating the copy does not affect the original.
     */
    @Test
    void deepCopyCreatesIndependentBoard() {
        Board copy = board.deepCopy();

        assertEquals(board.getBuildPile(0).peekTopCard(), copy.getBuildPile(0).peekTopCard());
        assertEquals(board.getBuildPile(1).peekTopCard(), copy.getBuildPile(1).peekTopCard());
        assertEquals(board.getBuildPile(2).peekTopCard(), copy.getBuildPile(2).peekTopCard());
        assertEquals(board.getBuildPile(3).peekTopCard(), copy.getBuildPile(3).peekTopCard());

        copy.getBuildPile(1).placeCard(new Card(4, Card.CardColor.YELLOW));

        assertTrue(copy.getBuildPile(1).size() > board.getBuildPile(1).size());

    }

    /**
     * Ensures legal placements succeed and update the build pile.
     * @requires A fresh board is used.
     * @ensures The card is placed and the pile size increments.
     */
    @Test
    void putCardPlacesCardWhenLegal() {
        Board fresh = new Board(new DrawPile());
        Card card = new Card(1, Card.CardColor.RED);

        assertTrue(fresh.putCard(0, card));
        assertEquals(1, fresh.getBuildPile(0).size());
        assertEquals(1, fresh.getBuildPile(0).peekTopCard().getCardNumber());
    }

    /**
     * Ensures illegal placements are rejected without mutating the pile.
     * @requires A fresh board is used.
     * @ensures The build pile remains empty.
     */
    @Test
    void putCardRejectsIllegalCard() {
        Board fresh = new Board(new DrawPile());

        assertFalse(fresh.putCard(0, new Card(2, Card.CardColor.RED)));
        assertEquals(0, fresh.getBuildPile(0).size());
    }

    /**
     * Ensures out-of-range build pile indices throw an exception.
     * @requires A fresh board is used.
     * @ensures BuildPileIndexOutOfRangeException is thrown for invalid indices.
     */
    @Test
    void putCardThrowsForInvalidIndex() {
        Board fresh = new Board(new DrawPile());

        assertThrows(BuildPileIndexOutOfRangeException.class,
                () -> fresh.putCard(-1, new Card(1, Card.CardColor.RED)));
        assertThrows(BuildPileIndexOutOfRangeException.class,
                () -> fresh.putCard(Board.BUILD_PILE_COUNT, new Card(1, Card.CardColor.RED)));
    }

    /**
     * Verifies drawing returns null when no cards remain.
     * @requires The draw pile is empty.
     * @ensures null is returned.
     */
    @Test
    void drawReturnsNullWhenNoCardsAvailable() {
        Board fresh = new Board(new DrawPile());
        fresh.clearDrawPile();

        assertNull(fresh.draw());
    }

    /**
     * Ensures the draw pile view cannot be modified externally.
     * @requires A fresh board is used.
     * @ensures Modifying the draw pile view throws an exception.
     */
    @Test
    void drawPileViewIsUnmodifiable() {
        Board fresh = new Board(new DrawPile());
        assertThrows(UnsupportedOperationException.class,
                () -> fresh.getDrawPile().add(new Card(1, Card.CardColor.GREEN)));
    }
}
