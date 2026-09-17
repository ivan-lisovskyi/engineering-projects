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
 * Tests match-ending conditions in Game.
 * @ensures Only test-local game state is mutated.
 */
public class GameMatchTest {
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
     * Creates a two-player game for match tests.
     * @ensures game is initialized with two players.
     */
    @BeforeEach
    void setUp() {
        p1 = new TestPlayer("P1");
        p2 = new TestPlayer("P2");
        game = new Game(List.of(p1, p2));
    }

    /**
     * Verifies the match ends when a player reaches the winning score.
     * @requires A round is started and opponent stock is sized to push score to 500.
     * @ensures Match is over and winner is set.
     */
    @Test
    void matchEndsAtWinningScore() {
        game.startNewRound(p1);
        TestSupport.setStockSize(p1.getStockPile(), 0);
        TestSupport.setStockSize(p2.getStockPile(), 95); // 25 + 95*5 = 500

        Game.RoundResult result = game.finishRound();

        assertEquals(500, result.getPointsAwarded());
        assertTrue(game.isMatchOver());
        assertSame(p1, game.getMatchWinner());
    }

}
