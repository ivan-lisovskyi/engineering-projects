package model.piles;

import model.Card;
import model.exceptions.NegativeCountOfCardsException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for card piles with stack-like behavior.
 */
public abstract class Pile {
    protected final ArrayList<Card> pile;

    /**
     * Constructs an empty pile.
     */
    public Pile() {
        this.pile = new ArrayList<>();
    }

    /**
     * Copy constructor that performs a shallow element copy of the underlying card list.
     *
     * @param other the pile to copy
     * @throws NullPointerException if other is null
     */
    protected Pile(Pile other) {
        Objects.requireNonNull(other, "other");
        this.pile = new ArrayList<>(other.pile);
    }

    /**
     * Convenience method that draws cards from this pile into a hand-like list until it has 5 cards.
     *
     * @param handDeck the target list representing a player's hand
     * @throws NullPointerException if handDeck is null
     */
    public void cardDrawToHand(ArrayList<Card> handDeck) {
        drawCardsToList(handDeck, 5);
    }

    /**
     * Returns the internal card list (top card is the last element).
     * Callers should treat the list as read-only.
     *
     * @return the backing list for this pile
     */
    public List<Card> getPile() {
        return pile; // top card is at end of list
    }

    /**
     * Returns the number of cards in the pile.
     *
     * @return the pile size
     */
    public int size() {
        return pile.size();
    }

    /**
     * Indicates whether this pile contains no cards.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return pile.isEmpty();
    }

    /**
     * Returns the top card without removing it.
     *
     * @return the top card, or null if the pile is empty
     */
    public Card peekTopCard() {
        if (pile.isEmpty()) {
            return null;
        }
        return pile.get(pile.size() - 1);
    }

    /**
     * Adds the given card to the top of this pile.
     *
     * @param card the card to place on top
     */
    public void addCardOnTop(Card card) {
        pile.add(card);
    }

    /**
     * Removes and returns the top card of this pile.
     *
     * @return the removed top card, or null if the pile is empty
     */
    protected Card drawTopCard() {
        if (pile.isEmpty()) {
            return null;
        }
        return pile.remove(pile.size() - 1); // treat list as stack
    }

    /**
     * Randomly shuffles the cards in the pile.
     */
    protected void shuffle() {
        Collections.shuffle(pile, ThreadLocalRandom.current());
    }

    /**
     * Draws up to count cards from this pile into the target list.
     *
     * @param target the list to receive drawn cards
     * @param count  the maximum number of cards to draw (must be >= 0)
     * @throws NullPointerException if target is null
     * @throws NegativeCountOfCardsException if count < 0
     */
    public void drawCardsToList(List<Card> target, int count) {
        Objects.requireNonNull(target, "target");
        if (count < 0) throw new NegativeCountOfCardsException("count must be >= 0");

        int drawn = 0;
        while (drawn < count && !pile.isEmpty()) {
            target.add(drawTopCard());
            drawn++;
        }
    }

    /**
     * Moves all cards from this pile into another pile, transferring from top to top.
     *
     * @param target the pile receiving the cards
     * @throws NullPointerException if target is null
     */
    public void moveAllTo(Pile target) {
        Objects.requireNonNull(target, "target");
        while (!pile.isEmpty()) {
            target.addCardOnTop(drawTopCard()); // move top-to-top
        }
    }

    /**
     * Removes all cards from this pile.
     */
    public void clear() {
        pile.clear();
    }

    /**
     * Adds a card into this pile according to the pile's rules.
     *
     * @param card the card to add
     */
    public abstract void draw(Card card);
}
