package model.piles;

import model.Card;

/**
 * Stock pile used to determine when a player completes a round.
 */
public class StockPile extends Pile{

    /**
     * Constructs an empty StockPile.
     */
    public StockPile() {
        super();
    }

    /**
     * Adds a card to the stock pile.
     *
     * @param card the card to add
     */
    @Override
    public void draw(Card card) {
        pile.add(card);
    }

    /**
     * Indicates whether this stock pile has been completely emptied.
     *
     * @return true if the stock pile is empty, false otherwise
     */
    public boolean isCompleted() {
        return isEmpty(); // round ends when stock pile hits 0
    }

    /**
     * Removes and returns the top card of the stock pile.
     *
     * @return the top card, or null if the stock pile is empty
     */
    public Card playCard() {
        return drawTopCard();
    }

}
