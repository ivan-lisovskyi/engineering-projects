package tests;

import model.Board;
import model.Card;
import model.Game;
import model.moves.CardMove;
import model.moves.Move;
import model.moves.StockMove;
import model.players.NaiveStrategy;
import model.players.Player;
import model.piles.BuildPile;
import model.piles.DrawPile;
import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;



/**
 * Tests NaiveStrategy decision ordering and discard behavior.
 * @ensures Only test-local game state is mutated.
 */
public class NaiveStrategyTest {
    private NaiveStrategy strategy;


    /**
     * Creates a red card with the given number.
     * @param number card number
     * @return a red card instance
     * @requires number is valid for the test case.
     * @ensures Returns a new Card.
     */
    private Card card(int number) {
        return new Card(number, Card.CardColor.RED);
    }

    /**
     * Creates a wild card instance.
     * @return a wild card
     * @ensures Returns a new wild Card.
     */
    private Card wild() {
        return new Card(0, Card.CardColor.WILD);
    }


    /**
     * Board stub that returns a fixed set of build piles.
     * @requires Build piles are provided at construction.
     * @ensures #getBuildPile(int) returns from the fixed set.
     */
    private static class TestBoard extends Board{
        private final BuildPile[] buildPiles;

        /**
         * Creates a board backed by the provided build piles.
         * @param piles build piles to expose
         * @requires piles is non-null and non-empty.
         * @ensures The board uses the supplied piles for lookups.
         */
        TestBoard(BuildPile... piles) {
            super(new DrawPile());
            this.buildPiles = piles;
        }

        /**
         * Returns a build pile by index with relaxed bounds for tests.
         * @param index pile index
         * @return the requested pile, or the first pile if out of range
         * @requires index is not negative.
         * @ensures A non-null build pile is returned.
         */
        @Override
        public BuildPile getBuildPile(int index) {
            if (index < 0) throw new IllegalArgumentException("index < 0 ");
            if (index >= buildPiles.length) return buildPiles[0];
            return buildPiles[index];
        }
    }

    /**
     * Player stub that performs no moves.
     * @ensures #determineMove(Game) returns null.
     */
    private static class TestPlayer extends Player {
        /**
         * Creates a test player with the supplied piles.
         * @param name player name
         * @param hand player hand
         * @param stockPile player stock pile
         * @requires Parameters are non-null.
         * @ensures A player instance is created.
         */
        public TestPlayer(String name, Hand hand, StockPile stockPile) {
            super(name, hand, stockPile);
        }

        /**
         * Returns no scripted move.
         * @param game game context
         * @return null
         * @ensures Always returns null.
         */
        @Override
        public Move determineMove(Game game) {
            return null;
        }
    }

    /**
     * Game stub that uses a fixed board.
     * @requires Board and players are provided.
     * @ensures #getBoard() returns the fixed board.
     */
    private static class TestGame extends Game {
        private final Board board;

        /**
         * Creates a test game with the given board and players.
         * @param board board instance to expose
         * @param players players to include
         * @requires board is non-null.
         * @ensures Game is initialized with at least two players.
         */
        TestGame(Board board, Player... players) {
            super(buildPlayers(players));
            this.board = board;
        }

        /**
         * Returns the fixed board.
         * @return board instance
         * @ensures Returns the board supplied at construction.
         */
        @Override
        public Board getBoard() {
            return board;
        }
    }

    /**
     * Initializes the strategy instance for each test.
     * @ensures strategy is a fresh NaiveStrategy.
     */
    @BeforeEach
    void setUp() {
        strategy = new NaiveStrategy();
    }

    /**
     * Builds a list of players, ensuring at least two are present.
     * @param players players to include
     * @return list containing the provided players and a dummy if needed
     * @requires players is not null.
     * @ensures Returned list contains at least two players.
     */
    private static List<Player> buildPlayers(Player... players) {
        List<Player> list = new java.util.ArrayList<>(List.of(players));
        if (list.size() < 2) {
            list.add(new TestPlayer("Dummy", new Hand(), new StockPile()));
        }
        return list;
    }

    /**
     * Verifies the strategy name is "Naive".
     * @requires strategy is initialized.
     * @ensures NaiveStrategy#getName() returns "Naive".
     */
    @Test
    void getNameReturnsNaive() {
        assertEquals("Naive", strategy.getName());
    }

    /**
     * Ensures stock-to-build is preferred when available.
     * @requires Stock card can be placed on a build pile.
     * @ensures A StockMove is selected.
     */
    @Test
    void prefersStockToBuild() {
        BuildPile buildPile = new BuildPile();
        buildPile.placeCard(card(1));

        TestBoard board = new TestBoard(buildPile);

        Hand hand = new Hand();
        StockPile stock = new StockPile();

        TestPlayer player = new TestPlayer("CPU", hand, stock);

        Game game = new TestGame(board, player);
        TestSupport.clearPlayer(player);
        stock.addCardOnTop(card(2));
        Move move = strategy.determineMove(game, player);

        StockMove sm = assertInstanceOf(StockMove.class, move);

        assertEquals('s', sm.getAction());
        assertEquals(0, sm.getBuildIndex());
    }

    /**
     * Ensures discard-to-build is preferred over hand plays.
     * @requires A discard card can be placed; hand also has cards.
     * @ensures A discard-to-build CardMove is selected.
     */
    @Test
    void prefersDiscardOverHand() {
        BuildPile buildPile = new BuildPile();
        TestBoard board = new TestBoard(buildPile);

        Hand hand = new Hand();
        StockPile stock = new StockPile();
        TestPlayer player = new TestPlayer("CPU", hand, stock);

        Game game = new TestGame(board, player);
        TestSupport.clearPlayer(player);
        hand.addToHand(card(5));
        player.getDiscardPile(2).discard(card(1));
        Move move = strategy.determineMove(game, player);

        CardMove cm = assertInstanceOf(CardMove.class, move);

        assertEquals('d', cm.getFromPile());
        assertEquals(2, cm.getFromIndex());
        assertEquals('b', cm.getToPile());
        assertEquals(0, cm.getToIndex());
    }

    /**
     * Ensures hand-to-build is chosen when stock and discard cannot play.
     * @requires Only a hand card can be placed on the build pile.
     * @ensures A hand-to-build CardMove is selected.
     */
    @Test
    void prefersHandWhenNoStockOrDiscard() {
        BuildPile buildPile = new BuildPile();
        TestBoard board = new TestBoard(buildPile);

        Hand hand = new Hand();
        StockPile stock = new StockPile();
        TestPlayer player = new TestPlayer("CPU", hand, stock);

        Game game = new TestGame(board, player);
        TestSupport.clearPlayer(player);
        hand.addToHand(card(1));
        Move move = strategy.determineMove(game, player);

        CardMove cm = assertInstanceOf(CardMove.class, move);

        assertEquals('h', cm.getFromPile());
        assertEquals(1, cm.getFromIndex());
        assertEquals('b', cm.getToPile());
        assertEquals(0, cm.getToIndex());
    }

    /**
     * Ensures the strategy discards the highest non-wild card to a smallest discard pile.
     * @requires No build moves are available and discard piles are uneven.
     * @ensures A hand-to-discard move ends the turn with the highest non-wild.
     */
    @Test
    void endsTurnByDiscardingHighestNonWild() {
        BuildPile buildPile = new BuildPile();
        for (int i = 1; i <= 12; i++) {
            buildPile.placeCard(card(i));
        }
        TestBoard board = new TestBoard(buildPile);

        Hand hand = new Hand();
        StockPile stock = new StockPile();
        TestPlayer player = new TestPlayer("CPU", hand, stock);

        Game game = new TestGame(board, player);
        TestSupport.clearPlayer(player);
        hand.addToHand(wild());
        hand.addToHand(card(3));
        hand.addToHand(card(11));
        stock.addCardOnTop(card(7));
        player.getDiscardPile(1).discard(card(1));
        player.getDiscardPile(3).discard(card(2));
        player.getDiscardPile(3).discard(card(3));
        Move move = strategy.determineMove(game, player);

        CardMove cm = assertInstanceOf(CardMove.class, move);

        assertEquals('h', cm.getFromPile());
        assertEquals(3, cm.getFromIndex());
        assertEquals('d', cm.getToPile());
        assertEquals(2, cm.getToIndex());
        assertTrue(cm.endTurn());

        Card chosen = hand.getCard(cm.getFromIndex());
        assertFalse(chosen.isWild());
        assertEquals(11, chosen.getCardNumber());
    }

}
