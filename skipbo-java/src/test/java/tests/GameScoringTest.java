package tests;

import model.Game;
import model.moves.Move;
import model.players.Player;
import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests scoring rules for finished rounds and stalemates.
 * @ensures Only test-local game state is mutated.
 */
public class GameScoringTest {
    private Player p1;
    private Player p2;
    private Game game;

    /**
     * Player stub that returns no moves.
     * @ensures #determineMove(Game) returns null.
     */
    private static class TestPlayer extends Player {
        /**
         * Creates a test player with empty piles.
         * @param name player name
         * @requires name is not null.
         * @ensures Player has empty hand and stock pile.
         */
        TestPlayer(String name) {
            super(name, new Hand(), new StockPile());
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
     * Initializes a two-player game.
     * @ensures game is ready for scoring tests.
     */
    @BeforeEach
    void setUp() {
        p1 = new TestPlayer("P1");
        p2 = new TestPlayer("P2");
        game = new Game(List.of(p1, p2));
    }

    /**
     * Verifies finishRound awards points based on opponent stock size.
     * @requires Round is started and stock sizes are configured.
     * @ensures Winner receives 25 + 5 * opponent stock cards.
     */
    @Test
    void finishRoundAwardsOpponentStockPoints() {
        game.startNewRound(p1);
        TestSupport.setStockSize(p1.getStockPile(), 0);
        TestSupport.setStockSize(p2.getStockPile(), 4);

        assertTrue(game.gameOver());

        Game.RoundResult result = game.finishRound();

        assertEquals(25 + 4 * 5, result.getPointsAwarded());
        assertEquals(result.getPointsAwarded(), game.getScore(p1));
        assertEquals(0, game.getScore(p2));

        Game.RoundResult resultAgain = game.finishRound();
        assertEquals(result.getTotalScore(), resultAgain.getTotalScore());
        assertEquals(result.getPointsAwarded(), resultAgain.getPointsAwarded());
    }

    /**
     * Ensures stalemates award points to the player with the smallest stock.
     * @requires Round is started and both players have stock.
     * @ensures Player with smaller stock receives the points.
     */
    @Test
    void resolveStalemateAwardsSmallestStockPoints() {
        game.startNewRound(p1);
        TestSupport.setStockSize(p1.getStockPile(), 2);
        TestSupport.setStockSize(p2.getStockPile(), 5);

        Game.RoundResult result = game.resolveStalemate();

        assertSame(p1, result.getWinner());
        assertEquals(25 + 5 * 5, result.getPointsAwarded());
        assertEquals(result.getPointsAwarded(), game.getScore(p1));
    }

}
