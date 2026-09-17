package model.players;

import model.Board;
import model.Card;
import model.Game;
import model.moves.CardMove;
import model.moves.Move;
import model.moves.StockMove;
import model.piles.BuildPile;
import model.piles.DiscardPile;
import model.piles.Hand;

/**
 * Simple heuristic strategy that prioritizes stock, then discard, then hand plays.
 */
public class NaiveStrategy implements Strategy {

    private static final String NAME = "Naive";

    /**
     * Returns the strategy name.
     *
     * @return NAME the name of this strategy
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Determines the next move for the given player in the current game state.
     *
     * @param game   the current game state
     * @param player the player for whom a move is being selected
     * @return the selected Moves, or null if no move could be selected
     */
    @Override
    public Move determineMove(Game game, Player player) {
        Board board = game.getBoard();

        // -----------------------------
        // 1) STOCK -> BUILD (priority 1)
        // -----------------------------
        Card stockTop = player.getStockPile().peekTopCard();
        if (stockTop != null) {
            for (int b = 0; b < Board.BUILD_PILE_COUNT; b++) {
                if (board.getBuildPile(b).canPlaceCard(stockTop)) {
                    return new StockMove('s', b);
                }
            }
        }

        // -------------------------------
        // 2) DISCARD -> BUILD (priority 2)
        // -------------------------------
        for (int d = 1; d <= player.DISCARD_PILE_COUNT; d++) {
            DiscardPile dp = player.getDiscardPile(d);
            Card top = dp.peekTopCard();
            if (top == null) continue;

            for (int b = 0; b < Board.BUILD_PILE_COUNT; b++) {
                if (board.getBuildPile(b).canPlaceCard(top)) {
                    // from discard pile #d (1-based) to build pile b (0-based)
                    return new CardMove('d', d, 'b', b);
                }
            }
        }

        // ---------------------------
        // 3) HAND -> BUILD (priority 3)
        // ---------------------------
        Hand hand = player.getHand();
        if (!hand.isEmpty()) {
            for (int h = 1; h <= hand.size(); h++) { // hand indices are 1-based
                Card c = hand.getCard(h);

                for (int b = 0; b < Board.BUILD_PILE_COUNT; b++) {
                    BuildPile bp = board.getBuildPile(b);
                    if (bp.canPlaceCard(c)) {
                        return new CardMove('h', h, 'b', b);
                    }
                }
            }
        } else {
            // If hand is empty, strategy has no legal CardMoves to generate.
            return null;
        }

        // ---------------------------------------
        // 4) END TURN: HAND -> DISCARD (priority 4)
        // ---------------------------------------
        int handIndexToDiscard = pickDiscardCardIndex(hand);
        int discardPileIndex = pickDiscardPileIndex(player);

        return new CardMove('h', handIndexToDiscard, 'd', discardPileIndex);
    }

    /**
     * Selects which hand card to discard when no build move is available.
     *
     * @param hand the player's hand
     * @return the 1-based index of the chosen card
     */
    private int pickDiscardCardIndex(Hand hand) {
        int bestIdx = 1;
        int bestVal = -1;

        for (int i = 1; i <= hand.size(); i++) {
            Card c = hand.getCard(i);

            int val = c.isWild() ? -100 : c.getCardNumber(); // avoid discarding wild

            if (val > bestVal) {
                bestVal = val;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Selects which discard pile should receive a discarded card.
     *
     * @param player the player who owns the discard piles
     * @return the 1-based discard pile index (1..SIZE)
     */
    private int pickDiscardPileIndex(Player player) {
        int bestPile = 1;
        int bestSize = player.getDiscardPile(1).size(); // start with first pile

        for (int i = 2; i <= player.DISCARD_PILE_COUNT; i++) {
            int s = player.getDiscardPile(i).size();
            if (s < bestSize) { // keep discard piles balanced
                bestSize = s;
                bestPile = i;
            }
        }
        return bestPile;
    }
}
