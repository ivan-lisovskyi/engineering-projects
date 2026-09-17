package model.moves;

/**
 * Represents a single card move between hand/discard piles and build/discard piles.
 * Hand and discard pile indices are 1-based; build pile indices are 0-based.
 */
public class CardMove implements Move {
    private char fromPile;
    private int fromIndex;
    private char toPile;
    private int toIndex;

    /**
     * Constructs a new CardMoves instance describing a card transfer.
     *
     * @param fromPile  the source pile type ('h' for hand, 'd' for discard)
     * @param fromIndex the 1-based index of the source pile/card
     * @param toPile    the destination pile type ('b' for build, 'd' for discard)
     * @param toIndex   the destination index (0-based for build, 1-based for discard)
     */
    public CardMove(char fromPile, int fromIndex, char toPile, int toIndex){
        this.fromPile = fromPile;
        this.fromIndex = fromIndex;
        this.toPile = toPile;
        this.toIndex = toIndex;
    }

    /**
     * Returns the source pile identifier.
     *
     * @return fromPile, the source pile type
     */
    public char getFromPile() {
        return fromPile;
    }

    /**
     * Returns the source pile index.
     *
     * @return fromIndex, the source pile index
     */
    public int getFromIndex() {
        return fromIndex;
    }

    /**
     * Returns the destination pile identifier.
     *
     * @return toPile, the destination pile type
     */
    public char getToPile() {
        return toPile;
    }

    /**
     * Returns the destination pile index.
     *
     * @return toIndex, the destination pile index
     */
    public int getToIndex() {
        return toIndex;
    }


    /**
     * Indicates whether this move ends the current player's turn.
     *
     * According to the game rules, a move from the hand to a discard pile
     * always ends the turn.
     *
     * @return true if the move ends the turn, false otherwise
     */
    @Override
    public boolean endTurn() {
        return fromPile == 'h' && toPile == 'd'; // discard from hand always ends turn
    }

}
