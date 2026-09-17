package model.piles;

import model.Card;
import model.exceptions.InvalidPlacementException;

import java.util.Objects;

/**
 * Build pile that accepts cards in ascending order from 1 to 12 (wilds allowed).
 */
public class BuildPile extends Pile {
    public static final int MAX_SIZE = 12;

    /**
     * Constructs an empty BuildPile.
     */
    public BuildPile() { super(); }

    /**
     * Adds a card to the build pile.
     *
     * @param card the card to place
     *
     */
    @Override
    public void draw(Card card) {
        placeCard(card);
    }

    /**
     * Copy constructor that clones the pile structure.
     * Card objects are not copied.
     *
     * @param other the build pile to copy
     */
    public BuildPile(BuildPile other) {super(other);}

    /**
     * Creates a structural copy of this BuildPile.
     * Card objects are not copied.
     *
     * @return a new BuildPile containing the same card references
     */
    public BuildPile deepCopy() {
        return new BuildPile(this);
    }

    /**
     * Indicates whether this build pile has reached its maximum size.
     *
     * @return true if the pile contains MAX_SIZE cards, false otherwise
     */
    public boolean isFull() {
        return size() >= MAX_SIZE;
    }

    /**
     * Returns the next required card value for this build pile.
     *
     * @return the expected next card number
     */
    public int expectedNextValue() {
        return size() + 1; // build piles start at 1 and grow to 12
    }

    /**
     * Indicates whether this build pile is complete.
     *
     * @return true if the pile is complete, false otherwise
     */
    public boolean isComplete() {
        return size() == 12;
    }

    /**
     * Checks whether the given card can be legally placed on this build pile.
     *
     * @param card the card to check
     * @return true if the card can be placed, false otherwise
     */
    public boolean canPlaceCard(Card card) {
        if (card == null) return false;
        if (isFull()) return false;

        int expected = expectedNextValue();
        return card.isWild() || card.getCardNumber() == expected; // wild = any value
    }

    /**
     * Places a card onto this build pile.
     * @param card the card to place
     * @throws NullPointerException if card is null
     * @throws InvalidPlacementException if the card cannot legally be placed
     */
    public void placeCard(Card card) {
        Objects.requireNonNull(card, "card");
        if (!canPlaceCard(card)) {
            throw new InvalidPlacementException("Card cannot be placed. Expected "
                    + expectedNextValue() + ", got " + card);
        }

        addCardOnTop(card);

    }
}
