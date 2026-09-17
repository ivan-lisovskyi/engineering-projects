package model.moves;

/**
 * Represents a move that can be applied during a player's turn.
 */
public interface Move {

    /**
     * Indicates whether this move ends the current player's turn.
     *
     * @return true if the turn should end after this move, false otherwise
     */
    boolean endTurn();
}
