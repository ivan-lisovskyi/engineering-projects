package tests;

import model.Card;
import model.Game;
import model.moves.Move;
import model.players.Player;
import model.piles.DiscardPile;
import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that starting a new round resets piles and deals cards.
 * @ensures Only test-local game state is mutated.
 */
public class GameRoundResetTest {
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
     * @ensures game is ready for round reset tests.
     */
    @BeforeEach
    void setUp() {
        p1 = new TestPlayer("P1");
        p2 = new TestPlayer("P2");
        game = new Game(List.of(p1, p2));
    }

    /**
     * Verifies startNewRound resets piles and deals cards to each player.
     * @requires Players have pre-filled hands and discard piles.
     * @ensures Piles are reset and hands/stock are dealt to full sizes.
     */
    @Test
    void startNewRoundResetsPilesAndDealsCards() {
        p1.getHand().draw(new Card(1, Card.CardColor.GREEN));
        p1.getDiscardPile(1).discard(new Card(2, Card.CardColor.BLUE));
        p2.getDiscardPile(2).discard(new Card(3, Card.CardColor.RED));

        game.startNewRound(p2);

        assertSame(p2, game.getCurrentPlayer());
        assertEquals(0, game.getBoard().getCompletedPileSize());

        assertEquals(5, p1.getHand().size());
        assertEquals(5, p2.getHand().size());
        assertEquals(30, p1.getStockPile().size());
        assertEquals(30, p2.getStockPile().size());

        for (int i = 1; i <= p1.DISCARD_PILE_COUNT; i++) {
            DiscardPile pile = p1.getDiscardPile(i);
            assertTrue(pile.isEmpty());
        }
        for (int i = 1; i <= p2.DISCARD_PILE_COUNT; i++) {
            DiscardPile pile = p2.getDiscardPile(i);
            assertTrue(pile.isEmpty());
        }
    }
}
