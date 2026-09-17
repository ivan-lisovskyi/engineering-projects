package tests;

import model.Board;
import model.Card;
import model.players.Player;
import model.piles.StockPile;

/**
 * Shared helpers for test setup and common game state mutations.
 * @requires Callers provide non-null model objects.
 * @ensures The provided objects are mutated to the requested state for testing.
 */
public final class TestSupport {
    /**
     * Prevents instantiation of utility class.
     * @ensures An instance cannot be created.
     */
    private TestSupport() {
    }

    /**
     * Clears the player's hand, stock pile, and all discard piles.
     * @param player player to reset
     * @requires player is not null.
     * @ensures All piles owned by player are empty.
     */
    public static void clearPlayer(Player player) {
        player.getHand().clear();
        player.getStockPile().clear();
        for (int i = 1; i <= player.DISCARD_PILE_COUNT; i++) {
            player.getDiscardPile(i).clear();
        }
    }

    /**
     * Sets the stock pile to the requested size using red cards.
     * @param pile  stock pile to populate
     * @param count number of cards to place in the pile
     * @requires pile is not null and count >= 0.
     * @ensures pile contains count red cards with number 1.
     */
    public static void setStockSize(StockPile pile, int count) {
        setStockSize(pile, count, Card.CardColor.RED);
    }

    /**
     * Sets the stock pile to the requested size using cards of the given color.
     * @param pile  stock pile to populate
     * @param count number of cards to place in the pile
     * @param color card color to use
     * @requires pile and color are not null, and count >= 0.
     * @ensures pile contains count cards of the requested color.
     */
    public static void setStockSize(StockPile pile, int count, Card.CardColor color) {
        pile.clear();
        for (int i = 0; i < count; i++) {
            pile.draw(new Card(1, color));
        }
    }

    /**
     * Builds a build pile up to (but not including) the expected value.
     * @param board       board containing build piles
     * @param pileIndex   index of the build pile to fill
     * @param expectedValue next expected card value (1..12)
     * @requires board is not null and expectedValue is in 1..12.
     * @ensures The build pile at pileIndex contains values 1..expectedValue - 1.
     */
    public static void buildToExpected(Board board, int pileIndex, int expectedValue) {
        if (expectedValue < 1 || expectedValue > 12) {
            throw new IllegalArgumentException("expectedValue must be in 1..12");
        }
        for (int i = 1; i < expectedValue; i++) {
            board.putCard(pileIndex, new Card(i, Card.CardColor.BLUE));
        }
    }
}
