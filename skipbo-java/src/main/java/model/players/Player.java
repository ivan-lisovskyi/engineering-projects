package model.players;

import model.Game;
import model.moves.Move;
import model.piles.DiscardPile;
import model.piles.Hand;
import model.piles.StockPile;
import view.LocalTuiView;


import java.util.ArrayList;
import java.util.List;

/**
 * Base class for human and computer players.
 * name != null
 * hand != null
 * stockPile != null
 * discardPiles != null && discardPiles.size() == SIZE
 */
public abstract class  Player {
    public static final int DISCARD_PILE_COUNT = 4;// size of discard pile size
    private final String name;
    private final Hand hand;
    private List<DiscardPile> discardPiles = new ArrayList<>();
    private StockPile stockPile;


    /**
     * Constructs a new Player with a hand and stock pile.
     *
     * @param name      the player's name
     * @param hand      the player's hand
     * @param stockPile the player's stockpile
     * @requires name != null
     * @requires hand != null
     * @requires stockPile != null
     * @ensures discardPiles.size() == SIZE
     * @ensures all discard piles are empty
     */
    public Player(String name, Hand hand, StockPile stockPile) {
        this.name = name;
        this.hand = hand;
        this.stockPile = stockPile;
        for (int i = 0; i < DISCARD_PILE_COUNT; i++) discardPiles.add(new DiscardPile());
    }


    /**
     * Returns the player's name.
     *
     * @return the name
     */
    public String getName(){
        return name;
    }

    /**
     * Returns the player's hand.
     *
     * @return the hand
     */
    public Hand getHand(){
        return hand;
    }

    /**
     * Determines the next move for this player.
     *
     * @param game the current game state
     * @return the next move to attempt
     */
    public abstract Move determineMove(Game game);

    /**
     * Executes this player's move.
     *
     * @param game the current game state
     */
    public void makeMove(Game game){
        while (true){
            Move move = determineMove(game);

            Game.MoveResult result = game.applyMove(move, this);
            if (!result.isApplied()) { // illegal or end-turn
                if (result.isEndTurn()) {
                    game.refillHand(this); // draw back to 5
                    renderBoardIfHuman(game);
                    break;
                }
                if (this instanceof HumanPlayer) {
                    // players get another try if move was illegal
                    String message = result.getMessage();
                    if (message != null && !message.isBlank()) {
                        System.out.println(message);
                    } else {
                        System.out.println("Illegal move. Try again.");
                    }
                    continue;
                }

                break;
            }

            if (result.isEndTurn()) {
                game.refillHand(this); // end turn: refill then stop
                renderBoardIfHuman(game);
                break;
            }

            if (game.gameOver()) {
                renderBoardIfHuman(game);
                break;
            }

            if (getHand().isEmpty()) {
                game.refillHand(this); // auto-refill if you used all hand cards
            }

            renderBoardIfHuman(game);
        }
    }

    /**
     * Renders the board if this player is a human player.
     *
     * @param game the current game state
     */
    private void renderBoardIfHuman(Game game) {
        if (this instanceof HumanPlayer) {
            renderBoard(game);
        }
    }

    /**
     * Returns the discard pile at the given index (1-based).
     *
     * @param index the discard pile index (1..SIZE)
     * @return the requested discard pile
     * @requires index >= 1 && index <= SIZE
     * @ensures result != null
     */
    public DiscardPile getDiscardPile(int index) {
        return discardPiles.get(index-1);
    }

    /**
     * Returns the player's stock pile.
     *
     * @return stockPile the stock pile
     */
    public StockPile getStockPile() {
        return stockPile;
    }

    /**
     * Clears this player's piles in preparation for a new round.
     *
     * @ensures hand.isEmpty()
     * @ensures stockPile.isEmpty()
     * @ensures all discard piles are empty
     */
    public void resetForNewRound() {
        hand.clear();
        stockPile.clear();
        for (DiscardPile pile : discardPiles) {
            pile.clear();
        }
    }

    /**
     * Renders the game state for this player.
     *
     * @param game the current game state
     */
    protected void renderBoard(Game game) {
        LocalTuiView.printBoard(System.out, game);
    }
}
