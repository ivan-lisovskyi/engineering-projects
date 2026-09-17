package tests;

import model.Card;
import model.Card.CardColor;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests StockPile play behavior for empty and non-empty piles.
 * @ensures Only test-local stock piles are mutated.
 */
class StockPileTest {
    private StockPile stockPile;

    /**
     * Creates a fresh stock pile for each test.
     * @ensures stockPile is empty.
     */
    @BeforeEach
    void setUp() {
        stockPile = new StockPile();
    }

    /**
     * Verifies playCard returns the top card and updates size.
     * @requires Stock pile has at least two cards.
     * @ensures The last added card is returned and size decreases by one.
     */
    @Test
    void playCardReturnsTopWhenNotEmpty() {
        Card card1 = new Card(5, CardColor.BLUE);
        Card card2 = new Card(7, CardColor.RED);
        stockPile.draw(card1);
        stockPile.draw(card2);

        Card playedCard = stockPile.playCard();

        assertNotNull(playedCard, "Played card should not be null");
        assertEquals(card2, playedCard, "Played card should be the last card added to the stock pile");
        assertEquals(1, stockPile.getPile().size(), "Stock pile size should decrease by 1 after playing a card");
        assertFalse(stockPile.isCompleted(), "Stock pile should not be empty after playing a card");
    }

    /**
     * Verifies playCard returns null when the pile is empty.
     * @requires Stock pile is empty.
     * @ensures null is returned and pile remains completed.
     */
    @Test
    void playCardReturnsNullWhenEmpty() {
        Card playedCard = stockPile.playCard();

        assertNull(playedCard, "Played card should be null when stock pile is empty");
        assertTrue(stockPile.isCompleted(), "Stock pile should remain empty when no cards are present");
    }
}
