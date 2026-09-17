package view;

import networking.RemoteGameState;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static view.TuiRenderUtil.*;


/**
 * Text-based user interface (TUI) renderer for the remote/online Skip-Bo game.
 * This class renders a RemoteGameState snapshot received from the server
 * as a complete console "frame" using ANSI colors and 3-line card boxes.
 * Rendering is stateless: every call to printBoard clears the screen and redraws the full view:
 * Other players
 * Building piles and draw pile
 * Local player area (stock, discards, and hand)
 * Scores
 *  Turn / state / last move / info footer
 * The remote renderer differs from the local renderer because it does not have real Card
 * objects. Remote renderer uses token strings from RemoteGamesTate  e.g. "7", "SB", "X", "??")
 * and converts them into colored card boxes via TuiRenderUtil renderTokenLines(String, String)} method.
 */

public final class RemoteTuiView {

    /**
     * Utility class: no instances.
     */
    private RemoteTuiView() { }

    /**
     * Prints the full remote game board to the given output stream.
     * If the remote game has not started yet, this prints a waiting message and returns.
     * Otherwise, the method clears the screen and prints a complete board layout.
     * @param out the stream to print to
     * @param state the remote snapshot to render
     * @param localPlayer the local player's name (used to label "You" and filter other players)
     */

    public static void printBoard(PrintStream out, RemoteGameState state, String localPlayer) {
        clearScreen(out);

        // Client gets notified when the game is about to start

        if (state == null || !state.isStarted()) {
            out.println("Waiting for game to start...");
            return;
        }

        out.println(AnsiColors.BOLD_WHITE + "SKIP-BO ONLINE" + AnsiColors.RESET);
        out.println();

        //  Other players (wrapped blocks)
        printOtherPlayers(out, state, localPlayer);

        out.println(AnsiColors.divider(DIVIDER_WIDTH));

        //  Building piles and draw piles
        printBuildingPiles(out, state);

        out.println(AnsiColors.divider(DIVIDER_WIDTH));

        //  Current player and its stock + discard + hand
        printCurrentPlayer(out, state, localPlayer);

        // Scores
        printScores(out, state);

        // Turn/status/last move/event footer (ONLY ONCE)
        out.println();

        // If it's your turn, shows "You" instead of your username.

        String currentTurn = state.getCurrentTurn();
        if (currentTurn != null && !currentTurn.isBlank()) {
            String label = currentTurn.equals(localPlayer) ? "You" : formatRemoteName(currentTurn);
            out.println("Turn: " + label);
        }



        String statusLine = state.isYourTurn() ? "Your turn." : statusForTurn(currentTurn);
        out.println("State: " + statusLine);

        String lastMove = state.getLastMove();
        if (lastMove != null) {
            out.println("Last move: " + lastMove);
        }

        // Print lastEvent only if it is not duplicating the move or the status line.

        String lastEvent = state.getLastEvent();
        if (lastEvent != null
                && (!lastEvent.equals(lastMove))
                && (!lastEvent.equals(statusLine))) {
            out.println("Info: " + lastEvent);
        }

        out.println();
    }


    //Other players



    /**
     * Prints all players except the local player as horizontally wrapped blocks.
     * Each block contains the player's stock pile and discard piles.
     * Blocks are then printed in columns
     * using TuiRenderUtil method printBlocksWrapped(PrintStream, List, int, String)}.
     * @param out the stream to print to
     * @param state the remote snapshot
     * @param localPlayer local player name (filtered out of the "other players" list)
     */


    private static void printOtherPlayers(PrintStream out, RemoteGameState state, String localPlayer) {
        List<String[]> blocks = new ArrayList<>();

        for (String name : state.getPlayers()) {
            if (name == null) continue;
            if (name.equals(localPlayer)) continue;

            RemoteGameState.PlayerView view = state.getPlayerView(name);
            if (view == null) continue;

            // Build one multi-line "panel" per player.
            blocks.add(buildOtherPlayerBlock(view));
        }

        // Print up to 3 player blocks per row.
        // Separator includes a colored vertical bar to visually separate the panels.
        printBlocksWrapped(out, blocks, 3, "   " + AnsiColors.WHITE + "│" + AnsiColors.RESET + "   ");
    }

    /**
     * Builds a multi-line "panel" for one non-local player.
     * This returns an array of strings where each element is one output line.
     * The block is later column-aligned and printed by printOtherPlayers(PrintStream, RemoteGameState, String)}.
     * @param view remote view of one player
     * @return a string array representing the player's block (one string per line)
     */

    private static String[] buildOtherPlayerBlock(RemoteGameState.PlayerView view) {
        List<String> lines = new ArrayList<>();

        lines.add(AnsiColors.BOLD_WHITE + "Player (" + formatRemoteName(view.name) + ")" + AnsiColors.RESET);

        // Stock pile
        String prefix = "  Stock Pile: ";
        String[] stockLines = renderRemoteStockLines(view);

        // Stock pile is rendered as 3 lines.

        lines.add(prefix + stockLines[0]);
        lines.add(indentToAfter(prefix) + stockLines[1] + " Stock: " + formatCount(view.stockCount));
        lines.add(indentToAfter(prefix) + stockLines[2]);

        // Discard piles
        lines.add("  Discard Piles:");

        String[][] discardCardLines = new String[RemoteGameState.DISCARD_PILES][];
        for (int d = 0; d < RemoteGameState.DISCARD_PILES; d++) {
            discardCardLines[d] = renderTokenLines(view.discardTops[d], null);

            // Put index number into the top border of a card
            String idx = String.valueOf(d + 1);
            discardCardLines[d][0] = discardCardLines[d][0].replace("┌───┐", "┌─" + idx + "─┐");
        }

        // Print discard piles side-by-side

        for (int line = 0; line < 3; line++) {
            StringBuilder sb = new StringBuilder("                 ");
            for (int d = 0; d < RemoteGameState.DISCARD_PILES; d++) {
                sb.append(discardCardLines[d][line]).append(" ");
            }
            lines.add(sb.toString().stripTrailing());
        }

        return lines.toArray(new String[0]);
    }


    // Building piles

    /**
     * Prints the shared board section: draw pile + building piles.
     * @param out the stream to print to
     * @param state the remote snapshot
     */


    private static void printBuildingPiles(PrintStream out, RemoteGameState state) {
        out.println(AnsiColors.BOLD_WHITE + "BUILDING PILES" + AnsiColors.RESET);
        out.println();

        String prefix = "Draw: ";
        out.print(prefix);

        // Remote view always draws the draw pile as a stack pattern.
        String[] drawLines = renderDrawPileLines();
        out.println(drawLines[0]);
        indent(out, prefix);
        out.println(drawLines[1]);
        indent(out, prefix);
        out.println(drawLines[2]);
        out.println();

        // Building pile labels

        out.print("     ");
        for (int i = 0; i < RemoteGameState.BUILD_PILES; i++) {
            out.print((i + 1) + "       ");
        }
        out.println();
        // --- Building pile top cards ---

        RemoteGameState.BuildPileView[] piles = state.getBuildPiles();

        String[][] buildCardLines = new String[RemoteGameState.BUILD_PILES][];

        // Print piles side-by-side by printing 3 lines (top/middle/bottom).
        for (int i = 0; i < RemoteGameState.BUILD_PILES; i++) {
            String topToken = (piles != null && piles[i] != null) ? piles[i].topToken : "X";
            buildCardLines[i] = renderTokenLines(topToken, null);
        }

        for (int line = 0; line < 3; line++) {
            out.print("     ");
            for (int i = 0; i < RemoteGameState.BUILD_PILES; i++) {
                out.print(buildCardLines[i][line] + "   ");
            }
            out.println();
        }
    }


    // Current player


    /**
     * Prints the local player's section (stock pile, discard piles, and hand) based on the remote snapshot.
     * @param out the stream to print to
     * @param state the remote snapshot
     * @param localPlayer the local player's name
     */


    private static void printCurrentPlayer(PrintStream out, RemoteGameState state, String localPlayer) {
        RemoteGameState.PlayerView view = state.getPlayerView(localPlayer);
        if (view == null) {
            out.println(AnsiColors.BOLD_WHITE + "You (" + formatRemoteName(localPlayer) + ")" + AnsiColors.RESET);
            out.println("  (No player data)");
            return;
        }

        out.println(AnsiColors.BOLD_WHITE + "You (" + formatRemoteName(view.name) + ")" + AnsiColors.RESET);

        // Stock
        String prefix = "  Stock Pile: ";
        out.print(prefix);

        String[] stockLines = renderRemoteStockLines(view);
        out.println(stockLines[0]);

        // Indent lines 2 and 3 to align under the card box rather than under the label.

        indent(out, prefix);
        out.print(stockLines[1]);
        out.println(" Stock: " + formatCount(view.stockCount));

        indent(out, prefix);
        out.println(stockLines[2]);

        // Discards
        String[][] discardCardLines = new String[RemoteGameState.DISCARD_PILES][];
        for (int d = 0; d < RemoteGameState.DISCARD_PILES; d++) {
            discardCardLines[d] = renderTokenLines(view.discardTops[d], null);

            // Index the piles in the border so a user can refer to pile
            String idx = String.valueOf(d + 1);
            discardCardLines[d][0] = discardCardLines[d][0].replace("┌───┐", "┌─" + idx + "─┐");
        }

        out.println("  Discard Piles:");
        for (int line = 0; line < 3; line++) {
            out.print("                 ");
            for (int d = 0; d < RemoteGameState.DISCARD_PILES; d++) {
                out.print(discardCardLines[d][line] + " ");
            }
            out.println();
        }

        // Hand
        out.println();
        out.println(AnsiColors.BOLD_WHITE + "Your Hand:" + AnsiColors.RESET);

        List<String> handSnapshot = state.getHand();
        if (handSnapshot == null || handSnapshot.isEmpty()) {
            out.println("  (empty)");
            return;
        }

        // Render each hand token into a 3-line card, index it in the border, then print side-by-side.

        String[][] handLines = new String[handSnapshot.size()][];
        for (int i = 0; i < handSnapshot.size(); i++) {
            String token = handSnapshot.get(i);
            handLines[i] = renderTokenLines(token, null);

            String idx = String.valueOf(i + 1);
            handLines[i][0] = handLines[i][0].replace("┌───┐", "┌─" + idx + "─┐");
        }

        printCardsSideBySide(out, handLines);
    }


    // Scores + footer helpers

    /**
     * Prints a single score line containing every player's score.
     * Output format:
     * Scores: Playername1.10 Playername2.7 Playername3.0
     * @param out the stream to print to
     * @param state the remote snapshot
     */

    private static void printScores(PrintStream out, RemoteGameState state) {
        if (state.getPlayers() == null || state.getPlayers().isEmpty()) return;

        StringBuilder sb = new StringBuilder("Scores:");
        String separator = "  ";

        for (String name : state.getPlayers()) {
            RemoteGameState.PlayerView view = state.getPlayerView(name);
            int score = (view != null && view.score != null) ? view.score : 0;

            if (sb.length() > "Scores:".length()) sb.append(separator);
            else sb.append(' ');

            sb.append(formatRemoteName(name)).append('.').append(score);
        }

        out.println();
        out.println(sb);
    }

    /**
     * Formats a remote player name for display.
     * @param name the raw player name
     * @return a safe display string (never null)
     */

    private static String formatRemoteName(String name) {
        if (name == null || name.isBlank()) return "";
        return name;
    }

    /**
     * Builds a default waiting status message for the footer when it is not the local player's turn.
     * @param currentTurn the player name whose turn it currently is (may be null/blank)
     * @return a status message for display (never null)
     */

    private static String statusForTurn(String currentTurn) {
        if (currentTurn == null || currentTurn.isBlank()) {
            return "Waiting for turn.";
        }
        return "Waiting for " + formatRemoteName(currentTurn) + ".";
    }

    /**
     * Formats an integer count that may be unknown.
     * @param count the count value, or null if unknown
     * @return the count as a string, or "?" if unknown
     */

    private static String formatCount(Integer count) {
        return count == null ? "?" : count.toString();
    }

    /**
     * Renders the stock pile top card for a remote player
     * If the top token is unknown/missing but the stock count is positive, this renders "??"
     * to indicate there are cards but the top value is hidden.
     * @param view remote view of the player whose stock should be rendered
     * @return a 3-element array representing the stock card box
     */

    private static String[] renderRemoteStockLines(RemoteGameState.PlayerView view) {
        String token = view.stockTop;

        if ((token == null || token.equals("X")) && view.stockCount != null && view.stockCount > 0) {
            return TuiRenderUtil.renderCardLines("??", AnsiColors.GREEN);
        }
        return renderTokenLines(token, AnsiColors.GREEN);
    }

}

