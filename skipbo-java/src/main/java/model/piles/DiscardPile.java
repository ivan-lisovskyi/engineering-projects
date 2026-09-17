package model.piles;

import model.Card;
import java.util.Objects;

/**
 * Discard pile for a single player; only the top card is playable.
 */
public class DiscardPile extends Pile {

    /**
     * Constructs an empty DiscardPile.
     */
    public DiscardPile() {
        super();
    }

    /**
     * Adds a card to the discard pile.
     *
     * This method is the Pile abstraction hook used when dealing with piles
     * generically. For discard piles, drawing means placing on top.
     *
     * @param card the card to discard
     *
     */
    @Override
    public void draw(Card card) {
        discard(card);
    }

    /**
     * Copy constructor that clones the pile structure.
     * Card objects are not copied.
     *
     * @param other the discard pile to copy
     */
    public DiscardPile(DiscardPile other) {
        super(other);
    }

    /**
     * Places a card on top of this discard pile.
     *
     * @param card the card to place on top
     *
     */
    public void discard(Card card) {
        addCardOnTop(Objects.requireNonNull(card, "card")); // top card is the only playable one
    }

    /**
     * Removes and returns the current top card of this discard pile.
     *
     * This operation assumes the caller has already checked that taking from this pile
     * is legal and that the pile is not empty.
     *
     * @return the previous top card
     *
     */
    public Card takeTop() {
        return drawTopCard();
    }
}
