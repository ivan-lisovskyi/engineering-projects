package tests;

import model.Card;
import model.exceptions.NegativeCountOfCardsException;
import model.piles.Pile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the base Pile behaviors such as stacking, drawing, and copying.
 * @ensures Only test-local piles are mutated.
 */
public class PileTest {
    private TestPile pile;

    /**
     * Creates a fresh test pile for each test.
     * @ensures pile is empty.
     */
    @BeforeEach
    void setUp() {
        pile = new TestPile();
    }

    /**
     * Test-specific pile exposing protected behaviors.
     * @ensures Wraps Pile functionality for testing.
     */
    private static class TestPile extends Pile {
        /**
         * Creates an empty test pile.
         * @ensures Pile is empty.
         */
        public TestPile() {
            super();
        }

        /**
         * Creates a copy of another pile.
         * @param other pile to copy
         * @requires other is not null.
         * @ensures This pile contains copies of the other pile's cards.
         */
        public TestPile(Pile other) {
            super(other);
        }

        /**
         * Adds the card to the top of the pile.
         * @param card card to add
         * @requires card is not null.
         * @ensures card becomes the top card.
         */
        @Override
        public void draw(Card card) {
            addCardOnTop(card);
        }

        /**
         * Exposes #drawTopCard() for test assertions.
         * @return the removed top card or null
         * @ensures Top card is removed if present.
         */
        public Card publicDrawTopCard() {
            return drawTopCard();
        }

        /**
         * Exposes #shuffle() for test assertions.
         * @ensures Pile order may change, but contents remain the same.
         */
        public void publicShuffle() {
            shuffle();
        }
    }

    /**
     * Creates a red card with the given number.
     * @param number card number
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
     * @ensures Size is zero and underlying list is empty.
     */
    @Test
    void newPileShouldStartEmpty() {
        assertEquals(0, pile.size());
        assertTrue(pile.isEmpty());
        assertNotNull(pile.getPile());
        assertTrue(pile.getPile().isEmpty());
    }

    /**
     * Ensures adding cards updates the top card and size.
     * @requires pile is empty.
     * @ensures Top card reflects the last added card.
     */
    @Test
    void addCardOnTopUpdatesPeek() {
        Card c1 = card(1);
        Card c2 = card(2);

        pile.addCardOnTop(c1);
        assertSame(c1, pile.peekTopCard());

        pile.addCardOnTop(c2);
        assertSame(c2, pile.peekTopCard());
        assertEquals(2, pile.size());
    }

    /**
     * Verifies peeking an empty pile returns null.
     * @requires pile is empty.
     * @ensures null is returned.
     */
    @Test
    void peekTopCardShouldReturnNullWhenEmpty() {
        assertNull(pile.peekTopCard());
    }

    /**
     * Verifies drawing from an empty pile returns null.
     * @requires pile is empty.
     * @ensures null is returned.
     */
    @Test
    void drawTopCardShouldReturnNullWhenEmpty() {
        assertNull(pile.publicDrawTopCard());
    }

    /**
     * Confirms drawing removes cards in LIFO order.
     * @requires pile contains multiple cards.
     * @ensures Cards are removed from top to bottom.
     */
    @Test
    void drawTopCardReturnsLifo() {
        Card c1 = card(10);
        Card c2 = card(11);

        pile.addCardOnTop(c1);
        pile.addCardOnTop(c2);

        assertSame(c2, pile.publicDrawTopCard());
        assertEquals(1, pile.size());

        assertSame(c1, pile.publicDrawTopCard());
        assertEquals(0, pile.size());
    }

    /**
     * Ensures drawCardsToList transfers up to the requested count.
     * @requires pile contains at least three cards.
     * @ensures Target list contains the requested number of cards.
     */
    @Test
    void drawCardsToListShouldDrawUpToCount() {
        pile.addCardOnTop(card(1));
        pile.addCardOnTop(card(2));
        pile.addCardOnTop(card(3));

        List<Card> target = new ArrayList<>();
        pile.drawCardsToList(target, 2);

        assertEquals(2, target.size());
        assertEquals(1, pile.size());
    }

    /**
     * Ensures drawCardsToList draws all cards when count exceeds size.
     * @requires pile contains fewer cards than requested.
     * @ensures Target list receives all cards and pile is empty.
     */
    @Test
    void drawCardsToListDrawsAllWhenCountTooLarge() {
        pile.addCardOnTop(card(1));
        pile.addCardOnTop(card(2));

        List<Card> target = new ArrayList<>();
        pile.drawCardsToList(target, 10);

        assertEquals(2, target.size());
        assertTrue(pile.isEmpty());
    }

    /**
     * Verifies zero-count draws do not mutate the pile.
     * @requires pile contains cards.
     * @ensures Target list remains empty and pile size is unchanged.
     */
    @Test
    void drawCardsToListShouldAllowZeroCount() {
        pile.addCardOnTop(card(1));

        List<Card> target = new ArrayList<>();
        pile.drawCardsToList(target, 0);

        assertTrue(target.isEmpty());
        assertEquals(1, pile.size());
    }

    /**
     * Ensures a null target list triggers a NullPointerException.
     * @ensures Exception is thrown.
     */
    @Test
    void drawCardsToListShouldThrowForNullTarget() {
        assertThrows(NullPointerException.class,
                () -> pile.drawCardsToList(null, 1));
    }

    /**
     * Ensures negative counts are rejected.
     * @requires Target list is non-null.
     * @ensures NegativeCountOfCardsException is thrown.
     */
    @Test
    void drawCardsToListShouldThrowForNegativeCount() {
        List<Card> target = new ArrayList<>();

        assertThrows(NegativeCountOfCardsException.class,
                () -> pile.drawCardsToList(target, -1));
    }

    /**
     * Verifies cardDrawToHand draws exactly five cards.
     * @requires pile contains at least five cards.
     * @ensures Hand receives five cards and pile size decreases accordingly.
     */
    @Test
    void cardDrawToHandDrawsFiveCards() {
        for (int i = 1; i <= 7; i++) {
            pile.addCardOnTop(card(i));
        }

        ArrayList<Card> hand = new ArrayList<>();
        pile.cardDrawToHand(hand);

        assertEquals(5, hand.size());
        assertEquals(2, pile.size());
    }

    /**
     * Ensures clear removes all cards.
     * @requires pile contains cards.
     * @ensures Pile is empty and top card is null.
     */
    @Test
    void clearShouldRemoveAllCards() {
        pile.addCardOnTop(card(1));
        pile.addCardOnTop(card(2));

        pile.clear();

        assertTrue(pile.isEmpty());
        assertEquals(0, pile.size());
        assertNull(pile.peekTopCard());
    }

    /**
     * Verifies the copy constructor produces an independent list.
     * @requires Original pile has cards.
     * @ensures Mutating the original does not change the copy size.
     */
    @Test
    void copyConstructorCreatesIndependentPile() {
        TestPile original = new TestPile();
        original.addCardOnTop(card(1));
        original.addCardOnTop(card(2));

        TestPile copy = new TestPile(original);

        assertEquals(original.size(), copy.size());
        assertNotSame(original.getPile(), copy.getPile());

        original.addCardOnTop(card(3));

        assertEquals(3, original.size());
        assertEquals(2, copy.size());
    }

    /**
     * Ensures shuffle retains the same cards and size.
     * @requires pile contains cards.
     * @ensures The multiset of cards is unchanged.
     */
    @Test
    void shuffleShouldKeepSameCardsAndSize() {
        for (int i = 1; i <= 10; i++) {
            pile.addCardOnTop(card(i));
        }

        Map<Integer, Integer> before = countByNumber(pile.getPile());

        pile.publicShuffle();

        Map<Integer, Integer> after = countByNumber(pile.getPile());
        assertEquals(before, after);
        assertEquals(10, pile.size());
    }

    /**
     * Verifies moveAllTo transfers all cards and empties the source.
     * @requires Source pile contains cards; target pile is empty.
     * @ensures Source is empty and target contains all cards.
     */
    @Test
    void moveAllToTransfersAllCards() {
        TestPile target = new TestPile();
        Card c1 = card(1);
        Card c2 = card(2);
        Card c3 = card(3);
        pile.addCardOnTop(c1);
        pile.addCardOnTop(c2);
        pile.addCardOnTop(c3);

        pile.moveAllTo(target);

        assertTrue(pile.isEmpty());
        assertEquals(3, target.size());
        assertSame(c1, target.peekTopCard(), "Top card should be original bottom due to top-to-top transfer.");
    }

    /**
     * Ensures moveAllTo rejects a null target.
     * @ensures NullPointerException is thrown.
     */
    @Test
    void moveAllToRejectsNullTarget() {
        assertThrows(NullPointerException.class, () -> pile.moveAllTo(null));
    }

    /**
     * Counts cards by number for shuffle verification.
     * @param cards list of cards to count
     * @return map of card number to count
     * @requires cards is not null.
     * @ensures Returned map reflects the card distribution.
     */
    private Map<Integer, Integer> countByNumber(List<Card> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Card c : cards) {
            counts.merge(c.getCardNumber(), 1, Integer::sum);
        }
        return counts;
    }

}
