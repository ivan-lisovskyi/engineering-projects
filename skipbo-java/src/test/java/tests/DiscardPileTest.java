package tests;

import model.Card;
import model.piles.DiscardPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests DiscardPile push/pop behavior and copying.
 * @ensures Only test-local discard piles are mutated.
 */
public class DiscardPileTest {
    private DiscardPile pile;

    /**
     * Creates an empty discard pile for each test.
     * @ensures pile is empty.
     */
    @BeforeEach
    void setUp() {
        pile = new DiscardPile();
    }

    /**
     * Creates a red card with the given number.
     * @param number card number to assign
     * @return a red card instance
     * @requires number is valid for the test case.
     * @ensures Returns a new Card.
     */
    private Card card(int number) {
        return new Card(number, Card.CardColor.RED);
    }

    /**
     * Verifies a new pile starts empty.
     * @requires pile is initialized.
     * @ensures Size is zero.
     */
    @Test
    void newPileStartsEmpty() {
        assertEquals(0, pile.size());
    }

    /**
     * Verifies discard adds a card and increments size.
     * @requires pile is empty.
     * @ensures Size increases by one.
     */
    @Test
    void discardAddsCardOnTop() {
        pile.discard(card(5));

        assertEquals(1, pile.size());
    }

    /**
     * Ensures discarding a null card throws.
     * @requires pile is initialized.
     * @ensures A NullPointerException is thrown.
     */
    @Test
    void discardRejectsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> pile.discard(null));
        assertTrue(ex.getMessage().contains("card"));
    }

    /**
     * Confirms top removal follows LIFO ordering.
     * @requires pile contains multiple cards.
     * @ensures Cards are returned in reverse insertion order.
     */
    @Test
    void takeTopReturnsLastDiscarded() {
        Card c1 = card(1);
        Card c2 = card(2);
        Card c3 = card(3);

        pile.discard(c1);
        pile.discard(c2);
        pile.discard(c3);

        assertEquals(3, pile.size());

        assertSame(c3, pile.takeTop());
        assertEquals(2, pile.size());

        assertSame(c2, pile.takeTop());
        assertEquals(1, pile.size());

        assertSame(c1, pile.takeTop());
        assertEquals(0, pile.size());
    }

    /**
     * Verifies taking from an empty pile returns null.
     * @requires pile is empty.
     * @ensures null is returned.
     */
    @Test
    void takeTopReturnsNullWhenEmpty() {
        Card result = pile.takeTop();
        assertNull(result, "Empty discard pile should return null.");
    }

    /**
     * Ensures the copy constructor creates an independent pile.
     * @requires Original pile has cards.
     * @ensures Mutating the original does not change the copy.
     */
    @Test
    void copyConstructorCreatesIndependentCopy() {
        DiscardPile original = new DiscardPile();
        original.discard(card(7));
        original.discard(card(8));

        DiscardPile copy = new DiscardPile(original);

        assertEquals(original.size(), copy.size());

        original.takeTop();

        assertNotEquals(original.size(), copy.size(),
                "Copy should be independent: changing original must not change copy.");
    }
}
