package view;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared rendering utilities for the Skip-Bo text user interface (TUI).
 * This class contains only static helper methods used by both the local and remote TUI views to:
 *   Clear the terminal screen using ANSI escape sequences.
 *   Render cards as fixed-size 3-line boxes (empty, draw pile, or value cards).
 *   Render remote "tokens" (like "7", "SB", "SB3", "??", or "X") into card boxes with colors.
 *   Print multiple rendered cards side-by-side (using the 3-line representation).
 *   Print multi-line "blocks" (e.g., other-player panels) in wrapped columns while keeping ANSI-colored text aligned.
 * A key idea used throughout the TUI is that a rendered card is represented as a String[3]:
 * top border, middle content line, bottom border. This makes it easy to print many cards in one row
 * by printing line 0 of each card, then line 1, then line 2.
 */


public final class TuiRenderUtil {

    /**
     * Default width used by the view classes when printing divider lines.
     */

    public static final int DIVIDER_WIDTH = 80;

    /**
     * Utility class: no instances.
     */


    private TuiRenderUtil() { }

    /**
     * Clears the terminal screen and moves the cursor to the top-left corner.
     * Uses ANSI escape sequences. Behavior depends on the terminal/console support.
     * @param out the stream to write the ANSI control codes to (for example System.out)
     */

    public static void clearScreen(PrintStream out) {
        out.print("\033[2J\033[H");
        out.flush(); // ensure it takes effect immediately
    }

    /**
     * Renders an empty card placeholder as a 3-line box.
     * @return a 3-element array representing the empty card box (top/middle/bottom)
     */

    public static String[] renderEmptyCardLines() {
        String[] lines = new String[3];
        String white = AnsiColors.WHITE;
        String reset = AnsiColors.RESET;

        // Fixed 5-character wide card box:
        // ┌───┐
        // │   │
        // └───┘

        lines[0] = white + "┌───┐" + reset;
        lines[1] = white + "│   │" + reset;
        lines[2] = white + "└───┘" + reset;
        return lines;
    }

    /**
     * Renders the draw pile as a 3-line box with a simple "stack" pattern.
     * @return a 3-element array representing the draw pile card box
     */

    public static String[] renderDrawPileLines() {
        String[] lines = new String[3];
        String white = AnsiColors.WHITE;
        String reset = AnsiColors.RESET;
        lines[0] = white + "┌───┐" + reset;
        lines[1] = white + "│###│" + reset;
        lines[2] = white + "└───┘" + reset;
        return lines;

    }

    /**
     * Renders a colored card box containing a given value.
     * The value is centered inside a fixed-width (5 char) card box:
     * ┌───┐
     * │ 7 │
     * └───┘
     * @param value the text to display inside the card (e.g., "SB", "7", "??"); if null an empty card is rendered
     * @param colorCode ANSI color code used for the border/value (e.g., AnsiColors.GREEN)
     * @return a 3-element array representing the card box
     */

    public static String[] renderCardLines(String value, String colorCode) {
        if (value == null) {
            return renderEmptyCardLines();
        }

        String[] lines = new String[3];
        // Total width includes borders. Inside width is (width - 2).
        int width = 5;

        // Top border: ┌───┐ (but with chosen color)

        lines[0] = colorCode +
                AnsiColors.BOX_TOP_LEFT +
                AnsiColors.BOX_HORIZONTAL.repeat(width - 2) +
                AnsiColors.BOX_TOP_RIGHT +
                AnsiColors.RESET;

        // Center the value in the inside area (width - 2).



        int padding = (width - 2 - value.length()) / 2;

        // If value length is odd/even, right padding may differ by 1.

        String spacesBefore = " ".repeat(Math.max(0, padding));
        String spacesAfter = " ".repeat(Math.max(0, width - 2 - padding - value.length()));

        // Middle line: │ 7 │ (colored)

        lines[1] = colorCode + AnsiColors.BOX_VERTICAL +
                spacesBefore +
                colorCode + value +
                spacesAfter +
                AnsiColors.BOX_VERTICAL +
                AnsiColors.RESET;

        // Bottom border: └───┘ (colored)

        lines[2] = colorCode +
                AnsiColors.BOX_BOTTOM_LEFT +
                AnsiColors.BOX_HORIZONTAL.repeat(width - 2) +
                AnsiColors.BOX_BOTTOM_RIGHT +
                AnsiColors.RESET;

        return lines;
    }

    /**
     * Renders a remote token into a 3-line colored card box.
     * Remote mode uses string tokens instead of Card objects. Typical tokens:
     *   "X" or null: treated as empty slot
     *   "SB" or "SB3": Skip-Bo / wild representation
     *  "??": unknown/hidden card
     *  "1".."12": numeric card values
     * @param token the token to render; if null or "X" an empty card is rendered
     * @param colorOverride optional ANSI color to force (for example stock piles often force green);
     *if null, #colorForToken(String) is used
     * @return a 3-element array representing the rendered token box
     */

    public static String[] renderTokenLines(String token, String colorOverride) {
        if (token == null || token.equals("X")) {
            return renderEmptyCardLines();
        }

        // Normalize token into what should be displayed inside the box.
        String value = displayToken(token);


        // Choose color either from caller override or computed from token meaning.
        String color = (colorOverride != null) ? colorOverride : colorForToken(token);
        return renderCardLines(value, color);
    }

    /**
     * Converts a remote token into the visible string that should appear inside the card box.
     * <p>
     * Special handling:
     * </p>
     * <ul>
     *   <li>"SB" stays "SB"</li>
     *   <li>"SB3" becomes "3" (so wild cards can visually show a chosen value)</li>
     * </ul>
     *
     * @param token the token from the remote state
     * @return the text that should be displayed inside the card box
     */
    public static String displayToken(String token) {
        String value = token.trim().toUpperCase();
        if (value.startsWith("SB")) {

            // If token includes a suffix (like SB3), show only the suffix inside the box.
            String suffix = value.substring(2);
            if (!suffix.isEmpty()) {
                return suffix;  // e.g. "SB3" displayed as "3"
            }
            return "SB";
        }
        return value;
    }

    /**
     * Chooses an ANSI color for a remote token.
     * <p>
     * Rules used:
     * </p>
     * <ul>
     *   <li>"??" -> white</li>
     *   <li>"SB..." -> magenta</li>
     *   <li>numeric tokens -> color depends on range</li>
     * </ul>
     *
     * @param token the token to evaluate
     * @return an ANSI color code string
     */


    public static String colorForToken(String token) {
        String value = token.trim().toUpperCase();

        if (value.equals("??")) {
            return AnsiColors.WHITE;
        }
        if (value.startsWith("SB")) {
            return AnsiColors.MAGENTA;
        }

        // Numbers are color-coded by range. If parsing fails, default to white.

        try {
            int number = Integer.parseInt(value);
            if (number <= 3) {
                return AnsiColors.RED;
            } else if (number <= 6) {
                return AnsiColors.GREEN;
            } else if (number <= 9) {
                return AnsiColors.BLUE;
            } else {
                return AnsiColors.YELLOW;
            }
        } catch (NumberFormatException e) {
            return AnsiColors.WHITE;
        }
    }

    /**
     * Prints multiple rendered card boxes side-by-side.
     * <p>
     * Each card box must be a String[3] (top, middle, bottom). This method prints:
     * </p>
     * <ol>
     *   <li>All top lines in one row</li>
     *   <li>All middle lines in one row</li>
     *   <li>All bottom lines in one row</li>
     * </ol>
     *
     * @param out the stream to print to
     * @param cardArrays a 2D array where each element is a 3-line rendered card
     */

    public static void printCardsSideBySide(PrintStream out, String[][] cardArrays) {
        int maxLines = 3;

        // For each of the 3 lines in a card box...
        for (int line = 0; line < maxLines; line++) {

            // ...print that line for every card box.
            for (String[] cardLines : cardArrays) {
                if (cardLines == null) {
                    // Keep spacing consistent if a slot is missing.
                    out.print("     ");
                } else if (line < cardLines.length) {
                    out.print(cardLines[line] + " ");
                } else {
                    // Defensive fallback: card array shorter than expected.
                    out.print("     ");
                }
            }
            out.println();
        }
    }

    /**
     * Prints spaces so that subsequent lines align under a given prefix label.
     * Example: if the prefix is "  Stock Pile: ", then printing indent will line up the
     * next line under the card box rather than under the label.
     * @param out the stream to print to
     * @param prefix the label whose length determines indentation; null is treated as length 0
     */

    public static void indent(PrintStream out, String prefix) {
        out.print(" ".repeat(prefix == null ? 0 : prefix.length()));
    }
    /**
     * Produces a string of spaces to align text after a prefix label.
     * @param prefix the label whose length determines indentation; null is treated as length 0
     * @return spaces equal to prefix.length()
     */
    public static String indentToAfter(String prefix) {
        return " ".repeat(prefix == null ? 0 : prefix.length());
    }

    /**
     * Prints multi-line "blocks" (panels) in columns, wrapping to new rows after maxCols.
     * <p>
     * This is used mainly for remote rendering where multiple "other players" are shown as panels
     * next to each other instead of vertically.

     * ANSI color codes do not count toward visible width, so this method strips ANSI sequences when
     * computing widths in order to keep columns aligned.
     * </p>
     *
     * @param out the stream to print to
     * @param blocks list of blocks; each block is a string array (one entry per output line)
     * @param maxCols maximum number of blocks to print per row (minimum 1)
     * @param separator string placed between blocks (if null, a default is used)
     */

    public static void printBlocksWrapped(PrintStream out, List<String[]> blocks, int maxCols, String separator) {
        if (blocks == null || blocks.isEmpty()) return;

        maxCols = Math.max(1, maxCols);
        separator = (separator == null) ? "   |   " : separator;

        for (int start = 0; start < blocks.size(); start += maxCols) {
            int end = Math.min(start + maxCols, blocks.size());
            List<String[]> row = blocks.subList(start, end);

            // Find the maximum visible width among all lines in this row.
            // "Visible width" ignores ANSI codes so columns align even when colored.
            int maxHeight = 0;
            for (String[] b : row) maxHeight = Math.max(maxHeight, b.length);

            // max visible width for blocks in this row
            int blockWidth = 0;
            for (String[] b : row) {
                for (String line : b) blockWidth = Math.max(blockWidth, visibleLen(line));
            }

            // Pad each line of every block to the same visible width.
            List<String[]> normalized = new ArrayList<>();
            for (String[] b : row) normalized.add(normalizeBlockWidth(b, blockWidth));

            // Print the row line-by-line:
            // line 0 of all blocks, then line 1, etc.
            for (int line = 0; line < maxHeight; line++) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < normalized.size(); i++) {
                    String[] block = normalized.get(i);

                    // If a block has fewer lines than maxHeight, pad with spaces.
                    String part = (line < block.length) ? block[line] : " ".repeat(blockWidth);
                    sb.append(part);
                    if (i < normalized.size() - 1) sb.append(separator);
                }
                out.println(sb.toString().stripTrailing());
            }
            // Blank line between rows of blocks for readability.
            out.println();
        }
    }

    /**
     * Pattern that matches ANSI SGR (color/style) escape sequences such as "\u001B[31m".
     * <p>
     * Used to compute the visible width of colored strings for column alignment.
     * </p>
     */

    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    /**
     * Removes ANSI color/style codes from a string.
     *
     * @param s the input string (possibly containing ANSI escape sequences)
     * @return the same string without ANSI sequences
     */

    private static String stripAnsi(String s) {
        if (s == null) return "";
        return ANSI_PATTERN.matcher(s).replaceAll("");
    }

    /**
     * Computes the visible length of a string by ignoring ANSI escape sequences.
     *
     * @param s the input string (possibly colored)
     * @return the displayed character count (ANSI codes excluded)
     */

    private static int visibleLen(String s) {
        return stripAnsi(s).length();
    }

    /**
     * Pads a string on the right with spaces until it reaches a given visible width.
     * ANSI codes are not counted toward width.
     * @param s the string to pad
     * @param width the target visible width
     * @return padded string whose visible length is at least width
     */


    private static String padRightVisible(String s, int width) {
        int len = visibleLen(s);
        if (len >= width) return s;
        return s + " ".repeat(width - len);
    }

    /**
     * Pads every line of a block to a fixed visible width, so multiple blocks can be aligned in columns.
     * @param block the block lines to normalize
     * @param width the target visible width for each line
     * @return a new string array with padded lines
     */
    private static String[] normalizeBlockWidth(String[] block, int width) {
        String[] out = new String[block.length];
        for (int i = 0; i < block.length; i++) {
            out[i] = padRightVisible(block[i], width);
        }
        return out;
    }
}
