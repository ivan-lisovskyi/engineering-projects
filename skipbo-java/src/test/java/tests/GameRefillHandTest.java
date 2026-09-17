package tests;

import model.Board;
import model.Card;
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
 * Tests Game#refillHand(Player) behavior when draw piles are depleted.
 * @ensures Only test-local game state is mutated.
 */
public class GameRefillHandTest {
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
     * Starts a new round and clears both players' piles.
     * @ensures Game is started and both players are cleared.
     */
    @BeforeEach
    void setUp() {
        p1 = new TestPlayer("P1");
        p2 = new TestPlayer("P2");
        game = new Game(List.of(p1, p2));
        game.startNewRound(p1);
        TestSupport.clearPlayer(p1);
        TestSupport.clearPlayer(p2);
    }

    /**
     * Verifies refillHand draws from completed pile when draw pile is empty.
     * @requires Draw pile is empty and completed pile has cards.
     * @ensures Hand is refilled and completed pile is cleared.
     */
    @Test
    void refillHandUsesCompletedWhenDrawEmpty() {
        Board board = game.getBoard();
        board.clearDrawPile();

        TestSupport.buildToExpected(board, 0, 12);
        assertTrue(board.putCard(0, new Card(12, Card.CardColor.BLUE)));

        assertEquals(12, board.getCompletedPileSize());

        game.refillHand(p1);

        assertEquals(5, p1.getHand().size());
        assertEquals(0, board.getCompletedPileSize());
        assertEquals(7, board.getDrawPile().size());
    }

    /**
     * Ensures refillHand is a no-op when no cards are available.
     * @requires Draw pile and completed pile are empty.
     * @ensures Hand remains empty and progress version is unchanged.
     */
    @Test
    void refillHandDoesNothingWhenNoCardsAvailable() {
        Board board = game.getBoard();
        board.clearDrawPile();

        long versionBefore = game.getProgressVersion();
        game.refillHand(p1);

        assertEquals(0, p1.getHand().size());
        assertEquals(versionBefore, game.getProgressVersion());
    }
}
