package model.piles;

import model.Card;
import model.exceptions.FullHandException;
import model.exceptions.HandIndexOutOfRangeException;

/**
 * Hand of up to five cards for a player.
 */
public class Hand extends Pile {
    public static final int MAX_SIZE = 5; // Skip-Bo hand size

    /**
     * Constructs an empty Hand.
     */
    public Hand(){
        super();
    }

    /**
     * Creates a structural copy of the hand.
     * Card objects are not copied.
     *
     * @return a new Hand containing the same card references in the same order
     */
    public Hand deepCopy() { return new Hand(this); }

    /**
     * Copy constructor that clones the hand structure.
     * Card objects are not copied.
     *
     * @param other the hand to copy
     */
    public Hand(Hand other) { super(other); }

    /**
     * Adds a card to this hand.
     *
     * @param card the card to add
     * @throws FullHandException if the hand already contains the maximum number of cards
     */
    public void addToHand(Card card) {
        if (isFull()) throw new FullHandException("Hand is full");
        addCardOnTop(card);
    }

    /**
     * Indicates whether this hand is empty.
     *
     * @return true if the hand contains no cards, false otherwise
     */
    @Override
    public boolean isEmpty() {
        return super.isEmpty();
    }

    /**
     * Indicates whether this hand has reached its maximum capacity.
     *
     * @return true if the hand contains MAX_SIZE cards, false otherwise
     */
    public boolean isFull() {
        return size() >= MAX_SIZE;
    }

    /**
     * Removes and returns the card at the given hand position.
     *
     * @param index1based the position of the card to remove (1-based)
     * @return the removed card
     * @throws HandIndexOutOfRangeException if the index is not within the hand bounds
     */
    public Card takeCard(int index1based) {
        if (index1based < 1 || index1based > pile.size()) {
            throw new HandIndexOutOfRangeException("Hand index out of range: " + index1based);
        }
        return pile.remove(index1based - 1); // UI uses 1-based indices
    }

    /**
     * Returns (without removing) the card at the given hand position.
     *
     * @param index1based the position of the card to retrieve (1-based)
     * @return the requested card
     * @throws HandIndexOutOfRangeException if the index is not within the hand bounds
     */
    public Card getCard(int index1based) {
        if (index1based < 1 || index1based > pile.size()) {
            throw new HandIndexOutOfRangeException("Hand index out of range: " + index1based);
        }
        return pile.get(index1based - 1); // UI uses 1-based indices
    }

    /**
     * Adds a card to this hand as part of a generic pile operation.
     *
     * @param card the card to add
     */
    @Override
    public void draw(Card card) {
        pile.add(card); // no size check here (caller enforces)
    }
}
