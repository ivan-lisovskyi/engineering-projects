package model.players;

import model.Board;
import model.Game;
import model.moves.CardMove;
import model.moves.Move;
import model.moves.StockMove;
import model.piles.Hand;
import model.piles.StockPile;

import java.util.Locale;
import java.util.Scanner;

/**
 * Human-controlled player that reads moves from standard input.
 */
public class HumanPlayer extends Player{

    private static final Scanner INPUT = new Scanner(System.in); // shared stdin scanner

    /**
     * Constructs a new HumanPlayer.
     *
     * @param name      the player's name
     * @param hand      the player's hand
     * @param stockPile the player's stock pile
     */
    public HumanPlayer(String name, Hand hand, StockPile stockPile) {
        super(name, hand, stockPile);
    }

    /**
     * Prompts the user for input until a valid move command is entered.
     *
     * @param game the current game state
     * @return a parsed Moves object, or null if input is closed
     */
    @Override
    public Move determineMove(Game game) {
        while (true) {
            System.out.print("> Enter move (type 'help' for options): ");
            String line = INPUT.nextLine();
            if (line == null) {
                return null;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.equalsIgnoreCase("help")) {
                printHelp();
                continue;
            }

            Move move = parseMove(line, game.getBoard());
            if (move != null) {
                return move;
            }

            System.out.println("Could not understand move. Example: 's 1' or 'h 2 b 3'.");
        }
    }

    /**
     * Indicates that this player is controlled by a human.
     *
     * @return true always
     */
    public boolean isHuman(){
        return true;
    }

    /**
     * Parses a user command into a Moves object.
     *
     * @param line  the raw user command line
     * @param board the game board
     * @return a Moves instance if parsing succeeded, otherwise null
     */
    private Move parseMove(String line, Board board) {
        String[] parts = line.toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        // stock <build>
        if (parts[0].equals("s") || parts[0].equals("stock")) {
            if (parts.length < 2) return null;
            int buildIdx = parseIndex(parts[1], 1, Board.BUILD_PILE_COUNT);
            if (buildIdx < 0) return null;
            return new StockMove('s', buildIdx - 1); // convert to 0-based build index
        }

        // hand <index> build <build>
        if (parts[0].equals("h") || parts[0].equals("hand")) {
            if (parts.length < 3) return null;
            int handIdx = parseIndex(parts[1], 1, getHand().size());
            if (handIdx < 0) return null;

            if (parts[2].equals("b") || parts[2].equals("build")) {
                if (parts.length < 4) return null;
                int buildIdx = parseIndex(parts[3], 1, Board.BUILD_PILE_COUNT);
                if (buildIdx < 0) return null;
                return new CardMove('h', handIdx, 'b', buildIdx - 1); // UI is 1-based
            }

            if (parts[2].equals("d") || parts[2].equals("discard")) {
                if (parts.length < 4) return null;
                int discardIdx = parseIndex(parts[3], 1, DISCARD_PILE_COUNT);
                if (discardIdx < 0) return null;
                return new CardMove('h', handIdx, 'd', discardIdx); // discard piles stay 1-based
            }
        }

        // discard <index> build <build>
        if (parts[0].equals("d") || parts[0].equals("discard")) {
            if (parts.length < 3) return null;
            int discardIdx = parseIndex(parts[1], 1, DISCARD_PILE_COUNT);
            if (discardIdx < 0) return null;

            String destination = parts[2];
            if (destination.equals("b") || destination.equals("build")) {
                if (parts.length < 4) return null;
                int buildIdx = parseIndex(parts[3], 1, Board.BUILD_PILE_COUNT);
                if (buildIdx < 0) return null;
                return new CardMove('d', discardIdx, 'b', buildIdx - 1); // build is 0-based
            }
        }

        return null;
    }

    /**
     * Parses and validates an integer index token within bounds.
     *
     * @param token the user input token to parse
     * @param min   the minimum allowed value (inclusive)
     * @param max   the maximum allowed value (inclusive)
     * @return the parsed index if valid; -1 otherwise
     */
    private int parseIndex(String token, int min, int max) {
        try {
            int value = Integer.parseInt(token);
            if (value < min || value > max) {
                System.out.printf("Index must be between %d and %d.%n", min, max);
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("Not a number: " + token);
            return -1;
        }
    }

    /**
     * Prints available commands for the user.
     */
    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  s <build#>      - play stock top to build pile (1-4)");
        System.out.println("  h <idx> b <#>   - play hand card to build");
        System.out.println("  h <idx> d <#>   - discard hand card to a discard pile (1-4) and end turn");
        System.out.println("  d <idx> b <#>   - play top of discard pile to build");
    }
}
