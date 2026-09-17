package tests;

import model.Card;
import model.piles.CompletedPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests CompletedPile stacking behavior and copy semantics.
 * @ensures Only test-local pile instances are mutated.
 */
public class CompletedPileTest {
    private CompletedPile pile;

    /**
     * Creates a fresh completed pile for each test.
     * @ensures pile is empty.
     */
    @BeforeEach
    void setUp() {
        pile = new CompletedPile();
    }

    /**
     * Creates a colored test card.
     * @param number card number to use
     * @return green card with the given number
     * @requires number is in a valid range for the test case.
     * @ensures Returns a new Card instance.
     */
    private Card card(int number) {
        return new Card(number, Card.CardColor.GREEN);
    }

    /**
     * Verifies draw adds cards on top of the pile.
     * @requires pile is empty.
     * @ensures The last drawn card is the top card.
     */
    @Test
    void drawAddsCardOnTop() {
        pile.draw(card(1));
        pile.draw(card(2));

        assertEquals(2, pile.size());
        assertEquals(2, pile.peekTopCard().getCardNumber());
    }

    /**
     * Ensures drawing a null card throws an error.
     * @requires pile is initialized.
     * @ensures A NullPointerException is thrown.
     */
    @Test
    void drawRejectsNullCard() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> pile.draw(null));
        assertTrue(ex.getMessage().contains("card"));
    }

    /**
     * Confirms deep copy uses an independent list with shared card references.
     * @requires pile contains cards.
     * @ensures Mutating the original does not change the copy's size.
     */
    @Test
    void deepCopyKeepsCardReferences() {
        Card c1 = card(3);
        Card c2 = card(4);
        pile.draw(c1);
        pile.draw(c2);

        CompletedPile copy = pile.deepCopy();

        assertEquals(pile.size(), copy.size());
        assertNotSame(pile.getPile(), copy.getPile());
        assertSame(c1, copy.getPile().get(0));
        assertSame(c2, copy.getPile().get(1));

        pile.draw(card(5));
        assertEquals(3, pile.size());
        assertEquals(2, copy.size());
    }
}
