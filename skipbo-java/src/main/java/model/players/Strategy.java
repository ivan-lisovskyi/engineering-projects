package model.players;

import model.Game;
import model.moves.Move;

/**
 * Strategy for choosing a player's next move.
 */
public interface Strategy {

    /**
     * Returns the display name of the strategy.
     *
     * @return the strategy name
     */
    String getName();

    /**
     * Determines a move for the given player in the current game state.
     *
     * @param game the current game
     * @param player the player to move
     * @return the next move, or null if no move can be selected
     */
    Move determineMove(Game game, Player player);
}
