package model.piles;

import model.Card;

import java.util.Objects;

/**
 * Pile that collects cards from completed build piles for recycling into the draw pile.
 */
public class CompletedPile extends Pile {

    /**
     * Constructs an empty CompletedPile.
     */
    public CompletedPile() {
        super();
    }

    /**
     * Copy constructor that clones the pile structure.
     * Card objects are not copied.
     *
     * @param other the pile to copy
     */
    public CompletedPile(CompletedPile other) {
        super(other);
    }

    /**
     * Adds a card on top of this completed pile.
     * The card must not be null.
     * @param card the card to add
     *
     */
    @Override
    public void draw(Card card) {
        addCardOnTop(Objects.requireNonNull(card, "card")); // no ordering rules here
    }

    /**
     * Creates a structural copy of this CompletedPile.
     * Card objects are not copied.
     *
     * @return a new CompletedPile containing the same card references
     */
    public CompletedPile deepCopy() {
        return new CompletedPile(this);
    }
}
