package view;

import model.Board;
import model.Card;
import model.Game;
import model.players.Player;
import model.piles.BuildPile;
import model.piles.DiscardPile;
import model.piles.Hand;
import model.piles.StockPile;

import java.io.PrintStream;

import static view.TuiRenderUtil.*;


/**
 * Text-based user interface for the local Skip-Bo game.
 * This class prints a full "frame" of the game state to a PrintStream using:
 *ANSI escape codes (colors + clear screen)
 * 3-line "card boxes" that can be printed side-by-side
 * >Sections for other players, building piles, and the current player</li>
 */

public final class LocalTuiView {


    /**
     * Utility class: no instances.
     */

    private LocalTuiView() { }


    /**
     * Prints the full local game board to the given output stream.

     * Layout:
     * Title
     * Other players (stock + discard piles)
     * Divider
     *  Building piles + draw pile
     *  Divider<
     *  Current player (stock + discard piles + hand)
     * @param out the stream to print to
     * @param game the local game state to render
     * @throws NullPointerException if out or game is null
     */

    public static void printBoard(PrintStream out, Game game) {
        clearScreen(out);

        Player currentPlayer = game.getPlayers().get(game.getCurrentPlayerIndex());

        out.println(AnsiColors.BOLD_WHITE + "SKIP-BO MULTIPLAYER" + AnsiColors.RESET);
        out.println();

        printOtherPlayers(out, game, currentPlayer);

        out.println(AnsiColors.divider(DIVIDER_WIDTH));

        printBuildingPiles(out, game);

        out.println(AnsiColors.divider(DIVIDER_WIDTH));

        printCurrentPlayer(out, currentPlayer);

        out.println();
    }

    /**
     * Prints all players except the current player.
     * Each player block shows:
     * Stock pile (top card + stock size)
     * Discard piles (4 piles printed side-by-side, indexed 1..4)
     * @param out the stream to print to
     * @param game the game state
     * @param currentPlayer the player whose "You" section will be printed later
     */

    private static void printOtherPlayers(PrintStream out, Game game, Player currentPlayer) {
        for (Player player : game.getPlayers()) {
            if (player == currentPlayer) continue;

            out.println(AnsiColors.BOLD_WHITE + "Player (" + player.getName() + ")" + AnsiColors.RESET);

            // Stock pile
            StockPile stock = player.getStockPile();
            Card stockTop = stock.peekTopCard();

            String prefix = "  Stock Pile: ";
            out.print(prefix);

            // Render stock top as a 3-line box, or an empty box if there is no top card.
            String[] stockLines = (stockTop != null) ? renderStockCardLines(stockTop) : renderEmptyCardLines();
            // Print the 3 lines. Lines 2 and 3 are indented to align under the card box.
            out.println(stockLines[0]);

            indent(out, prefix);
            out.print(stockLines[1]);
            out.println(" Stock: " + stock.size());

            indent(out, prefix);
            out.println(stockLines[2]);

            // --- Discard piles ---
            // Each discard pile is rendered as 3 lines. We store them in a 2D array:
            // discardCardLines[pileIndex][lineIndex]
            String[][] discardCardLines = new String[player.DISCARD_PILE_COUNT][];
            for (int d = 1; d <= player.DISCARD_PILE_COUNT; d++) {
                DiscardPile dp = player.getDiscardPile(d);
                Card top = dp.peekTopCard();

                discardCardLines[d - 1] = (top != null) ? renderCardLines(top) : renderEmptyCardLines();

                // Put the discard pile index into the top border, so the user can refer to pile 1..4.

                String idx = String.valueOf(d);
                discardCardLines[d - 1][0] = discardCardLines[d - 1][0].replace("┌───┐", "┌─" + idx + "─┐");
            }
            // Print all discard piles side-by-side by printing line 0 for all piles, then line 1, then line 2.

            out.println("  Discard Piles:");
            for (int line = 0; line < 3; line++) {
                out.print("                 ");
                for (int d = 0; d < player.DISCARD_PILE_COUNT; d++) {
                    out.print(discardCardLines[d][line] + " ");
                }
                out.println();
            }

            out.println();
        }
    }

    /**
     * Prints the draw pile and all build piles in the center "board" section.
     * @param out the stream to print to
     * @param game the local game state
     */

    private static void printBuildingPiles(PrintStream out, Game game) {
        out.println(AnsiColors.BOLD_WHITE + "BUILDING PILES" + AnsiColors.RESET);
        out.println();

        // Draw pile
        int drawPileSize = game.getBoard().getDrawPile().size();
        String prefix = "Draw: ";
        out.print(prefix);
        // Render as a stack pattern if there are cards, otherwise show an empty placeholder box.

        String[] drawLines = (drawPileSize > 0) ? renderDrawPileLines() : renderEmptyCardLines();
        out.println(drawLines[0]);
        indent(out, prefix);
        out.println(drawLines[1]);
        indent(out, prefix);
        out.println(drawLines[2]);
        out.println();

        // --- Build pile labels (1..N) ---
        out.print("     ");
        for (int i = 0; i < Board.BUILD_PILE_COUNT; i++) {
            out.print((i + 1) + "       ");
        }
        out.println();

        // --- Build pile top cards ---
        // Each build pile top is rendered into 3 lines so they can be printed in a grid.
        String[][] buildCardLines = new String[Board.BUILD_PILE_COUNT][];
        for (int i = 0; i < Board.BUILD_PILE_COUNT; i++) {
            BuildPile pile = game.getBuildPile(i);
            Card top = pile.peekTopCard();
            buildCardLines[i] = (top != null) ? renderCardLines(top) : renderEmptyCardLines();
        }

        for (int line = 0; line < 3; line++) {
            out.print("     ");
            for (int i = 0; i < Board.BUILD_PILE_COUNT; i++) {
                out.print(buildCardLines[i][line] + "   ");
            }
            out.println();
        }
    }

    /**
     * Prints the "You" section for the current local player, including stock, discard piles, and hand.
     * @param out the stream to print to
     * @param currentPlayer the player whose hand is visible locally
     */

    private static void printCurrentPlayer(PrintStream out, Player currentPlayer) {
        out.println(AnsiColors.BOLD_WHITE + "You (" + currentPlayer.getName() + ")" + AnsiColors.RESET);

        // Stock pile
        StockPile stock = currentPlayer.getStockPile();
        Card stockTop = stock.peekTopCard();

        String prefix = "  Stock Pile: ";
        out.print(prefix);

        String[] stockLines = (stockTop != null) ? renderStockCardLines(stockTop) : renderEmptyCardLines();
        out.println(stockLines[0]);

        indent(out, prefix);
        out.print(stockLines[1]);
        out.println(" Stock: " + stock.size());

        indent(out, prefix);
        out.println(stockLines[2]);

        // Discard piles
        String[][] discardCardLines = new String[currentPlayer.DISCARD_PILE_COUNT][];
        for (int d = 1; d <= currentPlayer.DISCARD_PILE_COUNT; d++) {
            DiscardPile dp = currentPlayer.getDiscardPile(d);
            Card top = dp.peekTopCard();

            discardCardLines[d - 1] = (top != null) ? renderCardLines(top) : renderEmptyCardLines();

            // Index labels inside the top border for user input (pile 1..4).

            String idx = String.valueOf(d);
            discardCardLines[d - 1][0] = discardCardLines[d - 1][0].replace("┌───┐", "┌─" + idx + "─┐");
        }

        out.println("  Discard Piles:");
        for (int line = 0; line < 3; line++) {
            out.print("                 ");
            for (int d = 0; d < currentPlayer.DISCARD_PILE_COUNT; d++) {
                out.print(discardCardLines[d][line] + " ");
            }
            out.println();
        }

        // Hand
        out.println();
        out.println(AnsiColors.BOLD_WHITE + "Your Hand:" + AnsiColors.RESET);

        Hand hand = currentPlayer.getHand();
        if (hand.isEmpty()) {
            out.println("  (empty)");
            return;
        }

        // Render each hand card as 3 lines, then print them side-by-side.

        String[][] handLines = new String[hand.size()][];
        for (int i = 1; i <= hand.size(); i++) {
            Card card = hand.getCard(i);
            handLines[i - 1] = renderCardLines(card);

            // Index labels inside the top border for user input (card 1...handSize).

            String idx = String.valueOf(i);
            handLines[i - 1][0] = handLines[i - 1][0].replace("┌───┐", "┌─" + idx + "─┐");
        }
        printCardsSideBySide(out, handLines);
    }


    // Local-only card rendering

    /**
     * Renders the top card of a stockpile as a 3-line card box with a green border.
     * Stockpiles are special in the UI because the border color stays green, independent of card color.
     * @param card the stock top card
     * @return a 3-element array representing the rendered card lines
     */


    private static String[] renderStockCardLines(Card card) {
        if (card == null) return renderEmptyCardLines();
        String value = card.isWild() ? "SB" : String.valueOf(card.getCardNumber());
        return renderCardLines(value, AnsiColors.GREEN);
    }

    private static String[] renderCardLines(Card card) {
        if (card == null) return renderEmptyCardLines();

        // Wild cards are always magenta; other cards use their color property.

        String colorCode = card.isWild()
                ? AnsiColors.MAGENTA
                : AnsiColors.getCardColor(card.getCardColor());
// Wild cards display "SB"; normal cards display their number.
        String value = card.isWild() ? "SB" : String.valueOf(card.getCardNumber());
        return renderCardLines(value, colorCode);
    }

    /**
     * Uses to TuiRenderUtil to draw a colored 3-line card box.
     * This method exists to keep local rendering code readable while sharing the common box-drawing
     * @param value the text displayed inside the card (for example "SB" or "7")
     * @param colorCode the ANSI color code for the card border/value
     * @return a 3-element array representing the rendered card lines
     */
    private static String[] renderCardLines(String value, String colorCode) {
        return TuiRenderUtil.renderCardLines(value, colorCode);
    }
}
