package tests;

import model.Card;
import model.piles.DrawPile;
import model.piles.CompletedPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests DrawPile deck composition, copy behavior, and refill logic.
 * @ensures Only test-local piles are mutated.
 */
class DrawPileTest {

    private DrawPile drawPile;

    /**
     * Creates a fresh draw pile for each test.
     * @ensures drawPile is empty and ready for setup.
     */
    @BeforeEach
    void setUp() {
        drawPile = new DrawPile();
    }

    /**
     * Verifies the main deck size is correct after initialization.
     * @ensures The deck contains 162 cards.
     */
    @Test
    void createMainDeckInitializesCardCount() {
        drawPile.createMainDeck(); // Reinitialize to ensure consistency
        int totalCards = drawPile.getPile().size();
        assertEquals(162, totalCards, "The total number of cards should be 162.");
    }

    /**
     * Ensures the main deck contains the expected color distribution.
     * @ensures Each color appears the expected number of times.
     */
    @Test
    void createMainDeckContainsExpectedDistribution() {
        drawPile.createMainDeck(); // Reinitialize to ensure consistency

        long blueCards = drawPile.getPile().stream()
                .filter(card -> card.getCardColor() == Card.CardColor.BLUE)
                .count();
        long redCards = drawPile.getPile().stream()
                .filter(card -> card.getCardColor() == Card.CardColor.RED)
                .count();
        long yellowCards = drawPile.getPile().stream()
                .filter(card -> card.getCardColor() == Card.CardColor.YELLOW)
                .count();
        long greenCards = drawPile.getPile().stream()
                .filter(card -> card.getCardColor() == Card.CardColor.GREEN)
                .count();
        long wildCards = drawPile.getPile().stream()
                .filter(card -> card.getCardColor() == Card.CardColor.WILD)
                .count();

        assertEquals(36, blueCards, "There should be 36 blue cards.");
        assertEquals(36, redCards, "There should be 36 red cards.");
        assertEquals(36, yellowCards, "There should be 36 yellow cards.");
        assertEquals(36, greenCards, "There should be 36 green cards.");
        assertEquals(18, wildCards, "There should be 18 wild cards.");

        // Extra: all wilds should have number -1
        assertTrue(drawPile.getPile().stream()
                        .filter(Card::isWild)
                        .allMatch(c -> c.getCardNumber() == -1),
                "All wild cards should have cardNumber = -1");
    }

    /**
     * Ensures non-wild cards have numbers within the allowed range.
     * @ensures All non-wild cards have numbers in [1..12].
     */
    @Test
    void createMainDeckNonWildNumbersInRange() {
        drawPile.createMainDeck();

        assertTrue(drawPile.getPile().stream()
                        .filter(c -> !c.isWild())
                        .allMatch(c -> c.getCardNumber() >= 1 && c.getCardNumber() <= 12),
                "All non-wild cards must have cardNumber in [1..12]");
    }

    /**
     * Confirms deep copy preserves size and order.
     * @requires drawPile is populated in a known order.
     * @ensures Copy size and order match the original.
     */
    @Test
    void deepCopyPreservesOrderAndSize() {
        drawPile.clear();

        // Build known order (bottom -> top)
        drawPile.addCardOnTop(new Card(1, Card.CardColor.BLUE));
        drawPile.addCardOnTop(new Card(2, Card.CardColor.RED));
        drawPile.addCardOnTop(new Card(-1, Card.CardColor.WILD));
        drawPile.addCardOnTop(new Card(12, Card.CardColor.GREEN)); // top

        DrawPile copy = drawPile.deepCopy();

        assertEquals(drawPile.size(), copy.size(), "Copy must have same size");

        List<Card> originalList = drawPile.getPile();
        List<Card> copyList = copy.getPile();

        for (int i = 0; i < originalList.size(); i++) {
            Card o = originalList.get(i);
            Card c = copyList.get(i);

            assertEquals(o.getCardNumber(), c.getCardNumber(), "Card number differs at index " + i);
            assertEquals(o.getCardColor(), c.getCardColor(), "Card color differs at index " + i);
        }
    }

    /**
     * Ensures the copied list is independent of the original.
     * @requires drawPile has cards.
     * @ensures Adding to the original does not change the copy size.
     */
    @Test
    void deepCopyUsesIndependentList() {
        drawPile.clear();

        drawPile.addCardOnTop(new Card(1, Card.CardColor.BLUE));
        drawPile.addCardOnTop(new Card(2, Card.CardColor.RED));
        drawPile.addCardOnTop(new Card(3, Card.CardColor.GREEN));

        DrawPile copy = drawPile.deepCopy();

        // Modify original pile (list) and ensure copy doesn't change
        drawPile.addCardOnTop(new Card(-1, Card.CardColor.WILD));

        assertEquals(4, drawPile.size(), "Original should have 4 cards after adding one");
        assertEquals(3, copy.size(), "Copy should remain 3 cards (independent list)");
    }

    /**
     * Documents the current shallow-copy behavior for card references.
     * @requires drawPile contains a shared card instance.
     * @ensures The copied pile references the same card object.
     */
    @Test
    void deepCopyUsesShallowCardReferences() {
        drawPile.clear();

        Card shared = new Card(7, Card.CardColor.YELLOW);
        drawPile.addCardOnTop(shared);

        DrawPile copy = drawPile.deepCopy();

        // Your deepCopy uses addAll -> it copies references (shallow copy of cards)
        assertSame(drawPile.getPile().get(0), copy.getPile().get(0),
                "Current deepCopy copies Card references (shallow copy).");
    }

    /**
     * Verifies refill moves all cards from a completed pile.
     * @requires Source completed pile contains cards; draw pile is empty.
     * @ensures Source is empty and draw pile contains those cards.
     */
    @Test
    void refillFromMovesAllCards() {
        drawPile.clear();
        CompletedPile source = new CompletedPile();
        Card c1 = new Card(1, Card.CardColor.BLUE);
        Card c2 = new Card(2, Card.CardColor.RED);
        Card c3 = new Card(-1, Card.CardColor.WILD);
        source.draw(c1);
        source.draw(c2);
        source.draw(c3);

        drawPile.refillFrom(source);

        assertEquals(0, source.size());
        assertEquals(3, drawPile.size());
        assertTrue(drawPile.getPile().contains(c1));
        assertTrue(drawPile.getPile().contains(c2));
        assertTrue(drawPile.getPile().contains(c3));
    }

    /**
     * Confirms taking the top card returns the most recently added card.
     * @requires Draw pile has a known order.
     * @ensures Cards are returned in LIFO order.
     */
    @Test
    void takeTopCardReturnsLastAdded() {
        drawPile.clear();
        Card c1 = new Card(1, Card.CardColor.GREEN);
        Card c2 = new Card(2, Card.CardColor.YELLOW);
        drawPile.addCardOnTop(c1);
        drawPile.addCardOnTop(c2);

        assertSame(c2, drawPile.takeTopCard());
        assertEquals(1, drawPile.size());
        assertSame(c1, drawPile.takeTopCard());
        assertTrue(drawPile.isEmpty());
    }
}
