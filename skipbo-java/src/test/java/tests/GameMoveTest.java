package tests;

import model.Board;
import model.Card;
import model.Game;
import model.moves.CardMove;
import model.moves.Move;
import model.moves.StockMove;
import model.players.Player;
import model.piles.DiscardPile;
import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests move application paths in Game.
 * @ensures Only test-local game state is mutated.
 */
public class GameMoveTest {
    private TestPlayer p1;
    private TestPlayer p2;
    private Game game;

    /**
     * Player stub that returns no moves.
     * @ensures #determineMove(Game) returns null.
     */
    private static class TestPlayer extends Player {
        /**
         * Creates a player with empty piles.
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
     * Initializes a two-player game and starts a new round.
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
     * Verifies a legal stock move places a card on a build pile.
     * @requires Stock pile contains a playable card.
     * @ensures Stock is reduced and build pile size increases.
     */
    @Test
    void stockMovePlacesCard() {
        p1.getStockPile().draw(new Card(1, Card.CardColor.BLUE));

        Game.MoveResult result = game.applyMove(new StockMove('s', 0), p1);

        assertTrue(result.isApplied());
        assertFalse(result.isEndTurn());
        assertEquals(0, p1.getStockPile().size());
        assertEquals(1, game.getBuildPile(0).size());
        assertEquals(1, game.getBuildPile(0).peekTopCard().getCardNumber());
    }

    /**
     * Ensures stock moves are rejected when the stock pile is empty.
     * @requires Stock pile is empty.
     * @ensures Move is not applied and an error message is returned.
     */
    @Test
    void stockMoveRejectedWhenEmpty() {
        Game.MoveResult result = game.applyMove(new StockMove('s', 0), p1);

        assertFalse(result.isApplied());
        assertFalse(result.isEndTurn());
        assertTrue(result.getMessage().contains("Stock pile is empty"));
    }

    /**
     * Ensures a hand-to-discard move ends the turn and updates piles.
     * @requires Hand has a card and discard pile is empty.
     * @ensures Hand size decreases, discard pile size increases, and turn ends.
     */
    @Test
    void handToDiscardEndsTurn() {
        p1.getHand().draw(new Card(7, Card.CardColor.RED));

        Game.MoveResult result = game.applyMove(new CardMove('h', 1, 'd', 1), p1);

        assertTrue(result.isApplied());
        assertTrue(result.isEndTurn());
        assertEquals(0, p1.getHand().size());
        assertEquals(1, p1.getDiscardPile(1).size());
    }

    /**
     * Verifies a hand-to-build move places a card without ending the turn.
     * @requires Hand contains a playable card.
     * @ensures Hand size decreases and build pile size increases.
     */
    @Test
    void handToBuildPlacesCard() {
        p1.getHand().draw(new Card(1, Card.CardColor.GREEN));

        Game.MoveResult result = game.applyMove(new CardMove('h', 1, 'b', 0), p1);

        assertTrue(result.isApplied());
        assertFalse(result.isEndTurn());
        assertEquals(0, p1.getHand().size());
        assertEquals(1, game.getBuildPile(0).size());
    }

    /**
     * Verifies a discard-to-build move places a card without ending the turn.
     * @requires Discard pile contains a playable card.
     * @ensures Discard pile size decreases and build pile size increases.
     */
    @Test
    void discardToBuildPlacesCard() {
        DiscardPile discard = p1.getDiscardPile(1);
        discard.discard(new Card(1, Card.CardColor.YELLOW));

        Game.MoveResult result = game.applyMove(new CardMove('d', 1, 'b', 0), p1);

        assertTrue(result.isApplied());
        assertFalse(result.isEndTurn());
        assertEquals(0, discard.size());
        assertEquals(1, game.getBuildPile(0).size());
    }

    /**
     * Ensures discard moves are rejected when the discard pile is empty.
     * @requires Discard pile is empty.
     * @ensures Move is not applied and an error message is returned.
     */
    @Test
    void discardMoveRejectedWhenEmpty() {
        Game.MoveResult result = game.applyMove(new CardMove('d', 1, 'b', 0), p1);

        assertFalse(result.isApplied());
        assertTrue(result.getMessage().contains("Discard pile 1 is empty"));
    }

    /**
     * Ensures invalid build pile indices are rejected.
     * @requires Stock contains a playable card.
     * @ensures Move is not applied and an error message is returned.
     */
    @Test
    void invalidBuildIndexRejected() {
        p1.getStockPile().draw(new Card(1, Card.CardColor.BLUE));

        Game.MoveResult result = game.applyMove(new StockMove('s', Board.BUILD_PILE_COUNT), p1);

        assertFalse(result.isApplied());
        assertTrue(result.getMessage().contains("Build pile index out of range"));
    }

    /**
     * Ensures invalid hand indices are rejected.
     * @requires Hand contains a card.
     * @ensures Move is not applied and an error message is returned.
     */
    @Test
    void invalidHandIndexRejected() {
        p1.getHand().draw(new Card(1, Card.CardColor.BLUE));

        Game.MoveResult result = game.applyMove(new CardMove('h', 2, 'b', 0), p1);

        assertFalse(result.isApplied());
        assertTrue(result.getMessage().contains("Hand index out of range"));
    }

    /**
     * Ensures unknown move types are rejected.
     * @ensures Move is not applied and an "Unknown move" message is returned.
     */
    @Test
    void unknownMoveRejected() {
        Move unknown = new Move() {
            /**
             * Indicates this unknown move does not end the turn.
             * @return false
             * @ensures Always returns false.
             */
            @Override
            public boolean endTurn() {
                return false;
            }
        };

        Game.MoveResult result = game.applyMove(unknown, p1);

        assertFalse(result.isApplied());
        assertEquals("Unknown move.", result.getMessage());
    }

}
