package model.players;

import model.Game;
import model.moves.Move;
import model.piles.Hand;
import model.piles.StockPile;

/**
 * Player controlled by a strategy implementation.
 */
public class ComputerPlayer extends Player{
    private Strategy strategy;

    /**
     * Constructs a new ComputerPlayer.
     *
     * @param name       the player's name
     * @param hand       the player's hand
     * @param stockPile  the player's stock pile
     * @param strategy   the strategy used to determine moves
     */
    public ComputerPlayer(String name, Hand hand, StockPile stockPile, Strategy strategy) {
        super(name, hand, stockPile);
        this.strategy = strategy;
    }

    /**
     * Determines the next move for this player using its assigned strategy.
     *
     * @param game the current game state
     * @return the move selected by the strategy
     */
    @Override
    public Move determineMove(Game game) {
        return strategy.determineMove(game, this);
    }

    /**
     * Returns the current strategy used by this player.
     *
     * @return strategy the player's strategy
     */
    public Strategy getStrategy(){
        return strategy;
    }

    /**
     * Replaces the strategy used by this player.
     *
     * @param strategy the new strategy to use
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }
}
