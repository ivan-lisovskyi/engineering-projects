package tests;

import model.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the Card model for value storage and wild-card detection.
 * @ensures Only test-local card instances are created and asserted.
 */
public class CardTest {
    private Card card;

    /**
     * Initializes a baseline card for mutation tests.
     * @ensures card is a blue card with number 1.
     */
    @BeforeEach
    void setUp() {
        card = new Card(1, Card.CardColor.BLUE);
    }

    /**
     * Verifies the constructor preserves number and color.
     * @ensures The constructed card exposes the provided fields.
     */
    @Test
    void constructorSetsNumberAndColor() {
        Card card = new Card(5, Card.CardColor.RED);

        assertEquals(5, card.getCardNumber());
        assertEquals(Card.CardColor.RED, card.getCardColor());
    }

    /**
     * Ensures setters update the card state.
     * @requires card is initialized in #setUp().
     * @ensures Getter values reflect the new number and color.
     */
    @Test
    void settersUpdateNumberAndColor() {
        card.setCardNumber(12);
        card.setCardColor(Card.CardColor.GREEN);

        assertEquals(12, card.getCardNumber());
        assertEquals(Card.CardColor.GREEN, card.getCardColor());
    }

    /**
     * Confirms wild cards are detected.
     * @ensures isWild() returns true for wild color.
     */
    @Test
    void isWildReturnsTrueForWild() {
        Card wildCard = new Card(0, Card.CardColor.WILD);

        assertTrue(wildCard.isWild());
    }

    /**
     * Confirms non-wild cards are not misclassified.
     * @ensures isWild() returns false for colored cards.
     */
    @Test
    void isWildReturnsFalseForNonWild() {
        Card redCard = new Card(3, Card.CardColor.RED);
        Card blueCard = new Card(7, Card.CardColor.BLUE);
        Card greenCard = new Card(9, Card.CardColor.GREEN);
        Card yellowCard = new Card(11, Card.CardColor.YELLOW);

        assertFalse(redCard.isWild());
        assertFalse(blueCard.isWild());
        assertFalse(greenCard.isWild());
        assertFalse(yellowCard.isWild());
    }

    /**
     * Verifies wild detection depends on color rather than number.
     * @ensures Wild cards report true regardless of number.
     */
    @Test
    void wildCardsIgnoreNumber() {
        Card wildWithZero = new Card(0, Card.CardColor.WILD);
        Card wildWithNumber = new Card(10, Card.CardColor.WILD);

        assertTrue(wildWithZero.isWild());
        assertTrue(wildWithNumber.isWild());
    }
}
