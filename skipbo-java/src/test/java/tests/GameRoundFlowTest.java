package tests;

import model.Game;
import model.moves.Move;
import model.players.Player;
import model.exceptions.RoundNotOverException;
import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests round flow APIs such as finishing rounds and playing matches.
 * @ensures Only test-local game state is mutated.
 */
public class GameRoundFlowTest {
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
     * @ensures game is ready for round flow tests.
     */
    @BeforeEach
    void setUp() {
        p1 = new TestPlayer("P1");
        p2 = new TestPlayer("P2");
        game = new Game(List.of(p1, p2));
    }

    /**
     * Ensures finishRound throws when a round is still in progress.
     * @requires No round has ended.
     * @ensures RoundNotOverException is thrown.
     */
    @Test
    void finishRoundThrowsWhenNotOver() {
        assertFalse(game.gameOver());
        assertThrows(RoundNotOverException.class, game::finishRound);
    }

    /**
     * Verifies playRound returns a winner and awards points.
     * @requires A round is started and stock sizes are configured.
     * @ensures Winner and awarded points match expectations.
     */
    @Test
    void playRoundReturnsWinnerAndScores() {
        game.startNewRound(p1);
        TestSupport.setStockSize(p1.getStockPile(), 0);
        TestSupport.setStockSize(p2.getStockPile(), 3);

        Game.RoundResult result = game.playRound();

        assertSame(p1, result.getWinner());
        assertEquals(25 + 3 * 5, result.getPointsAwarded());
        assertEquals(result.getPointsAwarded(), game.getScore(p1));
    }

    /**
     * Ensures playMatch ends when a player reaches the winning score.
     * @requires A round is started with stock sizes configured for 500 points.
     * @ensures Winner is returned and match is marked as over.
     */
    @Test
    void playMatchStopsAtWinningScore() {
        game.startNewRound(p1);
        TestSupport.setStockSize(p1.getStockPile(), 0);
        TestSupport.setStockSize(p2.getStockPile(), 95); // 25 + 95*5 = 500

        Player winner = game.playMatch();

        assertSame(p1, winner);
        assertTrue(game.isMatchOver());
        assertSame(p1, game.getMatchWinner());
    }

}
