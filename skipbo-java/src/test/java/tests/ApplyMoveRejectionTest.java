package tests;

import model.Card;
import model.Game;
import model.moves.CardMove;
import model.moves.StockMove;
import model.players.ComputerPlayer;
import model.players.NaiveStrategy;
import model.players.Player;
import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that illegal moves are rejected without consuming cards.
 * @ensures Only test-local game state is mutated.
 */
public class ApplyMoveRejectionTest {
    /**
     * Creates a computer player with a naive strategy and empty piles.
     * @param name player's name
     * @return a new computer player
     * @requires name is not null.
     * @ensures The returned player has empty piles.
     */
    private Player newPlayer(String name) {
        return new ComputerPlayer(name, new Hand(), new StockPile(), new NaiveStrategy());
    }

    /**
     * Ensures rejected discard-to-build moves do not remove discard cards.
     * @requires Player has a discard card that cannot be placed.
     * @ensures Discard size and top card remain unchanged.
     */
    @Test
    void rejectedDiscardKeepsCard() {
        Player p1 = newPlayer("P1");
        Player p2 = newPlayer("P2");
        Game game = new Game(List.of(p1, p2));
        TestSupport.clearPlayer(p1);
        TestSupport.clearPlayer(p2);

        Card discardCard = new Card(5, Card.CardColor.RED);
        p1.getDiscardPile(1).discard(discardCard);

        int sizeBefore = p1.getDiscardPile(1).size();
        Card topBefore = p1.getDiscardPile(1).peekTopCard();

        Game.MoveResult result = game.applyMove(new CardMove('d', 1, 'b', 0), p1);

        assertFalse(result.isApplied());
        assertEquals(sizeBefore, p1.getDiscardPile(1).size());
        assertSame(topBefore, p1.getDiscardPile(1).peekTopCard());
        assertEquals(0, game.getBuildPile(0).size());
    }

    /**
     * Ensures rejected stock moves do not consume stock cards.
     * @requires Stock card cannot be placed on the build pile.
     * @ensures Stock size and top card remain unchanged.
     */
    @Test
    void rejectedStockKeepsCard() {
        Player p1 = newPlayer("P1");
        Player p2 = newPlayer("P2");
        Game game = new Game(List.of(p1, p2));
        TestSupport.clearPlayer(p1);
        TestSupport.clearPlayer(p2);

        Card stockCard = new Card(2, Card.CardColor.RED); // build piles expect 1
        p1.getStockPile().draw(stockCard);

        Game.MoveResult result = game.applyMove(new StockMove('s', 0), p1);

        assertFalse(result.isApplied());
        assertTrue(result.getMessage().contains("Cannot place stock card"));
        assertEquals(1, p1.getStockPile().size());
        assertSame(stockCard, p1.getStockPile().peekTopCard());
        assertEquals(0, game.getBuildPile(0).size());
    }

    /**
     * Ensures rejected hand-to-build moves do not remove hand cards.
     * @requires Hand card cannot be placed on the build pile.
     * @ensures Hand size and card position remain unchanged.
     */
    @Test
    void rejectedHandToBuildKeepsCard() {
        Player p1 = newPlayer("P1");
        Player p2 = newPlayer("P2");
        Game game = new Game(List.of(p1, p2));
        TestSupport.clearPlayer(p1);
        TestSupport.clearPlayer(p2);

        Card handCard = new Card(2, Card.CardColor.YELLOW);
        p1.getHand().draw(handCard);

        Game.MoveResult result = game.applyMove(new CardMove('h', 1, 'b', 0), p1);

        assertFalse(result.isApplied());
        assertTrue(result.getMessage().contains("Cannot place that card"));
        assertEquals(1, p1.getHand().size());
        assertSame(handCard, p1.getHand().getCard(1));
        assertEquals(0, game.getBuildPile(0).size());
    }

    /**
     * Ensures moves with unknown targets are rejected without hand mutations.
     * @requires Hand contains a valid card.
     * @ensures Hand size and card remain unchanged.
     */
    @Test
    void unknownTargetFromHandKeepsCard() {
        Player p1 = newPlayer("P1");
        Player p2 = newPlayer("P2");
        Game game = new Game(List.of(p1, p2));
        TestSupport.clearPlayer(p1);
        TestSupport.clearPlayer(p2);

        Card handCard = new Card(1, Card.CardColor.GREEN);
        p1.getHand().draw(handCard);

        Game.MoveResult result = game.applyMove(new CardMove('h', 1, 'x', 0), p1);

        assertFalse(result.isApplied());
        assertTrue(result.getMessage().contains("Unknown target pile"));
        assertEquals(1, p1.getHand().size());
        assertSame(handCard, p1.getHand().getCard(1));
    }

    /**
     * Verifies a null move ends the turn without applying a move.
     * @ensures Result indicates end turn and not applied.
     */
    @Test
    void nullMoveEndsTurn() {
        Player p1 = newPlayer("P1");
        Player p2 = newPlayer("P2");
        Game game = new Game(List.of(p1, p2));

        Game.MoveResult result = game.applyMove(null, p1);

        assertFalse(result.isApplied());
        assertTrue(result.isEndTurn());
        assertEquals("Turn ended.", result.getMessage());
    }
}
