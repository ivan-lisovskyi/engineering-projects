package model.players;

import model.Game;
import model.piles.Hand;
import model.piles.StockPile;
import view.LocalTuiView;

/**
 * Human player that renders the advanced TUI view.
 */
public class HumanPlayerAdvanced extends HumanPlayer {

    /**
     * Constructs a new HumanPlayerAdvanced.
     * @param name      the player's name
     * @param hand      the player's hand
     * @param stockPile the player's stock pile
     */
    public HumanPlayerAdvanced(String name, Hand hand, StockPile stockPile) {
        super(name, hand, stockPile);
    }

    /**
     * Renders the current game state using the advanced TUI.
     *
     * @param game the current game state
     */
    @Override
    protected void renderBoard(Game game) {
        LocalTuiView.printBoard(System.out, game);
    }
}
