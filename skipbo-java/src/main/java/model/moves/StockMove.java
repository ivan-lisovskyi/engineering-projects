package model.moves;

/**
 * Represents a move from stock to a build pile.
 * Build pile index is 0-based.
 */
public class StockMove implements Move {
    private char action;
    private int buildIndex;

    /**
     * Constructs a new StockMoves instance.
     *
     * @param action a character identifying the stock action (typically 's')
     * @param buildIndex the target build pile index (0-based)
     */
    //TODO: also remove char action, not really needed (but check before)
    public StockMove(char action, int buildIndex){
        this.action = action;
        this.buildIndex = buildIndex;
    }

    /**
     * Returns the encoded stock action.
     *
     * @return action, the stock action character
     */
    // TODO: remove, only used in tests
    public char getAction() {
        return action;
    }


    /**
     * Returns the target build pile index.
     *
     * @return buildIndex, the build pile index (0-based)
     */
    public int getBuildIndex() {
        return buildIndex;
    }

    /**
     * Indicates whether this move ends the current player's turn.
     *
     * @return false always
     */
    @Override
    public boolean endTurn(){
        return false;
    }
}
