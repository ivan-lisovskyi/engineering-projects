package tests;

import model.Card;
import model.exceptions.FullHandException;
import model.exceptions.HandIndexOutOfRangeException;
import model.piles.Hand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Hand capacity rules, indexing, and copy behavior.
 * @ensures Only test-local hand instances are mutated.
 */
public class HandTest {
    private Hand hand;

    /**
     * Creates a fresh hand before each test.
     * @ensures hand is empty.
     */
    @BeforeEach
    void setUp() {
        hand = new Hand();
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
     * Verifies a new hand starts empty and not full.
     * @requires hand is initialized.
     * @ensures Size is zero and isFull() is false.
     */
    @Test
    void newHandStartsEmptyAndNotFull() {
        assertTrue(hand.isEmpty());
        assertEquals(0, hand.size());
        assertFalse(hand.isFull());
    }

    /**
     * Ensures addToHand fills the hand to capacity.
     * @requires hand is empty.
     * @ensures Size is five and isFull() is true.
     */
    @Test
    void addToHandFillsHand() {
        hand.addToHand(card(1));
        hand.addToHand(card(2));
        hand.addToHand(card(3));
        hand.addToHand(card(4));
        hand.addToHand(card(5));

        assertEquals(5, hand.size());
        assertTrue(hand.isFull());
        assertFalse(hand.isEmpty());
    }

    /**
     * Ensures addToHand rejects additions when full.
     * @requires hand contains five cards.
     * @ensures A FullHandException is thrown and size remains five.
     */
    @Test
    void addToHandRejectsWhenFull() {
        for (int i = 1; i <= 5; i++) {
            hand.addToHand(card(i));
        }

        FullHandException ex = assertThrows(FullHandException.class,
                () -> hand.addToHand(card(6)));

        assertTrue(ex.getMessage().toLowerCase().contains("full"));
        assertEquals(5, hand.size());
    }

    /**
     * Verifies Hand#getCard(int) uses 1-based indexing.
     * @requires hand contains three cards.
     * @ensures Returned cards match insertion order using 1-based indices.
     */
    @Test
    void getCardUsesOneBasedIndex() {
        Card c1 = card(10);
        Card c2 = card(11);
        Card c3 = card(12);

        hand.addToHand(c1);
        hand.addToHand(c2);
        hand.addToHand(c3);

        assertSame(c1, hand.getCard(1));
        assertSame(c2, hand.getCard(2));
        assertSame(c3, hand.getCard(3));
    }

    /**
     * Ensures out-of-range indices throw an exception.
     * @requires hand contains one card.
     * @ensures HandIndexOutOfRangeException is thrown for invalid indices.
     */
    @Test
    void getCardRejectsOutOfRange() {
        hand.addToHand(card(1));

        assertThrows(HandIndexOutOfRangeException.class, () -> hand.getCard(0));
        assertThrows(HandIndexOutOfRangeException.class, () -> hand.getCard(2));
        assertThrows(HandIndexOutOfRangeException.class, () -> hand.getCard(-5));
    }

    /**
     * Verifies Hand#takeCard(int) uses 1-based indexing and removes the card.
     * @requires hand contains three cards.
     * @ensures The correct card is returned and remaining cards shift.
     */
    @Test
    void takeCardUsesOneBasedIndex() {
        Card c1 = card(1);
        Card c2 = card(2);
        Card c3 = card(3);

        hand.addToHand(c1);
        hand.addToHand(c2);
        hand.addToHand(c3);

        Card taken = hand.takeCard(2);

        assertSame(c2, taken);
        assertEquals(2, hand.size());

        assertSame(c1, hand.getCard(1));
        assertSame(c3, hand.getCard(2));
    }

    /**
     * Ensures taking with an invalid index throws an exception.
     * @requires hand contains one card.
     * @ensures HandIndexOutOfRangeException is thrown.
     */
    @Test
    void takeCardRejectsOutOfRange() {
        hand.addToHand(card(1));

        assertThrows(HandIndexOutOfRangeException.class, () -> hand.takeCard(0));
        assertThrows(HandIndexOutOfRangeException.class, () -> hand.takeCard(2));
    }

    /**
     * Documents current behavior of Hand#draw(Card) allowing overfill.
     * @requires hand is empty.
     * @ensures Size can exceed five and isFull() is true.
     */
    @Test
    void drawAllowsOverfillCurrentImpl() {
        for (int i = 1; i <= 6; i++) {
            hand.draw(card(i));
        }

        assertEquals(6, hand.size());
        assertTrue(hand.isFull(), "Hand reports full when size >= 5.");
    }

    /**
     * Ensures deep copy uses an independent list with shared card references.
     * @requires Original hand contains cards.
     * @ensures Copy size is stable when original is mutated.
     */
    @Test
    void deepCopyCreatesIndependentHand() {
        Hand original = new Hand();
        original.addToHand(card(1));
        original.addToHand(card(2));
        original.addToHand(card(3));

        Hand copy = original.deepCopy();

        assertEquals(original.size(), copy.size());
        assertSame(original.getCard(1), copy.getCard(1));
        assertSame(original.getCard(2), copy.getCard(2));
        assertSame(original.getCard(3), copy.getCard(3));


        original.takeCard(1);

        assertNotEquals(original.size(), copy.size());
        assertEquals(3, copy.size());
    }

}
