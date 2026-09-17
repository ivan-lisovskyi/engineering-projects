package view;
import model.Card;

public class AnsiColors {
    public static final String RESET = "\033[0m";

    // Regular colors

    public static final String RED = "\033[0;31m";
    public static final String GREEN = "\033[0;32m";
    public static final String YELLOW = "\033[0;33m";
    public static final String BLUE = "\033[0;34m";
    public static final String MAGENTA = "\033[0;35m";

    public static final String WHITE = "\033[0;37m";

    // Bold colors

    public static final String BOLD_WHITE = "\033[1;37m";

    // Box drawing characters
    public static final String BOX_HORIZONTAL = "─";
    public static final String BOX_VERTICAL = "│";
    public static final String BOX_TOP_LEFT = "┌";
    public static final String BOX_TOP_RIGHT = "┐";
    public static final String BOX_BOTTOM_LEFT = "└";
    public static final String BOX_BOTTOM_RIGHT = "┘";

    // Utility methods

    /**
     * Get the ANSI color code for a card color
     */
    public static String getCardColor(Card.CardColor cardColor) {
        return switch (cardColor) {
            case RED -> RED;
            case BLUE -> BLUE;
            case GREEN -> GREEN;
            case YELLOW -> YELLOW;
            case WILD -> MAGENTA; // Purple/Magenta for Skip-Bo wild cards
        };
    }

    /**
     * Create a horizontal divider line
     */
    public static String divider(int width) {
        return WHITE + BOX_HORIZONTAL.repeat(Math.max(0, width)) + RESET;
    }
}


