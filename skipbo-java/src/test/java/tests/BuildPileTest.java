package tests;

import model.Card;
import model.exceptions.InvalidPlacementException;
import model.piles.BuildPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests BuildPile placement rules, progression, and copying behavior.
 * @ensures Build piles created in tests are mutated only within test scope.
 */
public class BuildPileTest {
    private BuildPile pile;

    /**
     * Initializes a fresh build pile for each test.
     * @ensures pile is empty and expects value 1.
     */
    @BeforeEach
    void setUp() {
        pile = new BuildPile();
    }

    /**
     * Creates a non-wild card with the given number.
     * @param number card number
     * @return a red card with the specified number
     * @requires number is within the valid range for a build pile.
     * @ensures Returns a new Card instance.
     */
    private Card normalCard(int number) {
        return new Card(number, Card.CardColor.RED);
    }

    /**
     * Creates a wild card for testing.
     * @return a wild card instance
     * @ensures Returns a new wild Card.
     */
    private Card wildCard() {

        return new Card(0, Card.CardColor.WILD);
    }

    /**
     * Verifies a new pile starts empty and expects 1.
     * @requires pile is initialized in #setUp().
     * @ensures Expected value is 1 and pile is not complete.
     */
    @Test
    void newPileStartsEmptyAndExpectsOne() {
        assertEquals(1, pile.expectedNextValue());
        assertFalse(pile.isFull());
        assertFalse(pile.isComplete());
    }

    /**
     * Ensures the expected value increments after placements.
     * @requires pile is empty.
     * @ensures Expected value increases with each placed card.
     */
    @Test
    void expectedValueIncrementsAfterPlacement() {
        pile.placeCard(normalCard(1));
        assertEquals(2, pile.expectedNextValue());

        pile.placeCard(normalCard(2));
        assertEquals(3, pile.expectedNextValue());
    }

    /**
     * Verifies null cards are rejected for placement checks.
     * @requires pile is initialized.
     * @ensures canPlaceCard(null) returns false.
     */
    @Test
    void canPlaceCardRejectsNull() {
        assertFalse(pile.canPlaceCard(null));
    }

    /**
     * Confirms the next sequential number is accepted.
     * @requires pile is empty.
     * @ensures Only the next expected number is accepted.
     */
    @Test
    void canPlaceCardAcceptsNextNumber() {
        assertTrue(pile.canPlaceCard(normalCard(1)));
        pile.placeCard(normalCard(1));

        assertTrue(pile.canPlaceCard(normalCard(2)));
        assertFalse(pile.canPlaceCard(normalCard(3)));
    }

    /**
     * Confirms wild cards are accepted when the pile is not full.
     * @requires pile is not full.
     * @ensures A wild card advances expected value.
     */
    @Test
    void canPlaceCardAcceptsWildWhenNotFull() {
        assertTrue(pile.canPlaceCard(wildCard()));
        pile.placeCard(wildCard());

        assertEquals(2, pile.expectedNextValue());
    }

    /**
     * Ensures placing a null card throws with a helpful message.
     * @requires pile is initialized.
     * @ensures A NullPointerException is thrown.
     */
    @Test
    void placeCardRejectsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> pile.placeCard(null));
        assertTrue(ex.getMessage().contains("card"));
    }

    /**
     * Ensures placing a non-matching number throws an error.
     * @requires pile expects value 1.
     * @ensures An InvalidPlacementException is thrown and expected value is unchanged.
     */
    @Test
    void placeCardRejectsWrongNumber() {
        assertThrows(InvalidPlacementException.class, () -> pile.placeCard(normalCard(2)));

        assertEquals(1, pile.expectedNextValue());
    }

    /**
     * Verifies a pile becomes complete and full after 12 cards.
     * @requires pile is empty.
     * @ensures Pile reports complete/full and rejects further placements.
     */
    @Test
    void pileCompletesAtTwelveCards() {
        for (int i = 1; i <= 12; i++) {
            assertTrue(pile.canPlaceCard(normalCard(i)));
            pile.placeCard(normalCard(i));
        }

        assertTrue(pile.isComplete());
        assertTrue(pile.isFull());

        assertFalse(pile.canPlaceCard(normalCard(12)));
        assertFalse(pile.canPlaceCard(normalCard(1)));
        assertFalse(pile.canPlaceCard(wildCard()));
    }

    /**
     * Ensures placing a card on a full pile throws.
     * @requires pile has 12 cards.
     * @ensures InvalidPlacementException is thrown.
     */
    @Test
    void placeCardRejectsWhenFull() {
        for (int i = 1; i <= 12; i++) {
            pile.placeCard(normalCard(i));
        }

        assertThrows(InvalidPlacementException.class, () -> pile.placeCard(wildCard()));
    }

    /**
     * Verifies deep copy creates an independent pile with shared card references.
     * @requires Original pile has cards.
     * @ensures Copy size is stable when original is mutated.
     */
    @Test
    void deepCopyCreatesIndependentPile() {
        BuildPile original = new BuildPile();
        original.placeCard(normalCard(1));
        original.placeCard(normalCard(2));

        BuildPile copy = original.deepCopy();

        assertEquals(original.expectedNextValue(), copy.expectedNextValue());

        original.placeCard(normalCard(3));
        assertNotEquals(original.expectedNextValue(), copy.expectedNextValue());

        assertTrue(copy.canPlaceCard(normalCard(3)));
    }
}
