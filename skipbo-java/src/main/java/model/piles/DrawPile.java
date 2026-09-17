package model.piles;

import model.Card;

/**
 * Main draw pile that starts with a full Skip-Bo deck.
 */
public class DrawPile extends Pile {
    public DrawPile() {
        this(true);
    }

    /**
     * Constructs a new DrawPile by creating the full main deck and shuffling it.
     */
    private DrawPile(boolean initDeck) {
        super();
        if (initDeck) {
            createMainDeck();
        }
    }

    /**
     * Adds a card on top of this draw pile.
     *
     * @param card the card to place on top
     *
     */
    @Override
    public void draw(Card card) {
        addCardOnTop(card);
    }

    private static final int CARD_NUMBERS = 12;
    private static final int WILD_CARDS = 18;
    private static final int NUMBER_SETS = 3;

    /**
     * Creates a fresh full Skip-Bo deck in this pile and shuffles it.
     *
     */
    public void createMainDeck(){
        pile.clear();

        for (int set = 0; set < NUMBER_SETS; set++) { // 3 copies of 1..12 in 4 colors
            for (int i = 1; i <= CARD_NUMBERS; i++){
                pile.add(new Card(i, Card.CardColor.BLUE));
                pile.add(new Card(i, Card.CardColor.RED));
                pile.add(new Card(i, Card.CardColor.YELLOW));
                pile.add(new Card(i, Card.CardColor.GREEN));
            }
        }

        for (int i = 0; i < WILD_CARDS; i++){ // extra wilds in the deck
            pile.add(new Card(-1, Card.CardColor.WILD));
        }

        shuffle();
    }

    /**
     * Creates a structural copy of this DrawPile keeping the current card order.
     * Card objects are not copied.
     *
     * @return a new DrawPile with the same card references in the same order
     */
    public DrawPile deepCopy() {
        DrawPile copy = new DrawPile(false);
        copy.pile.addAll(this.pile);
        return copy;
    }

    /**
     * Refills this draw pile by moving all cards from a source pile into this pile,
     * then shuffles.
     *
     * @param source the pile to take cards from
     */
    public void refillFrom(Pile source) {
        while (!source.isEmpty()) {
            addCardOnTop(source.drawTopCard());
        }
        shuffle(); // keep random order after refill
    }

    /**
     * Removes and returns the top card of this draw pile.
     *
     * @return the top card
     */
    public Card takeTopCard() {
        return drawTopCard();
    }

}
