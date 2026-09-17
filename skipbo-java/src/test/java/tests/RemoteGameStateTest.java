package tests;

import networking.RemoteGameState;
import org.junit.jupiter.api.Test;
import protocol.common.ErrorCode;
import protocol.common.position.HandPosition;
import protocol.common.position.NumberedPilePosition;
import protocol.common.position.StockPilePosition;
import protocol.server.Play;
import protocol.server.Queue;
import protocol.server.Round;
import protocol.server.Start;
import protocol.server.Stock;
import protocol.server.Table;
import protocol.server.Turn;
import protocol.server.Winner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests RemoteGameState updates when processing protocol messages.
 * @ensures Only test-local remote state is mutated.
 */
public class RemoteGameStateTest {

    /**
     * Builds a ready-to-play remote state with initial sync applied.
     * @return initialized RemoteGameState
     * @ensures Returned state is started, has two players, and a current turn.
     */
    private RemoteGameState readyState() {
        RemoteGameState state = new RemoteGameState("Alice");
        state.applyMessage(new Start(new String[]{"Alice", "Bob"}).transformToProtocolString());
        state.applyMessage(new protocol.server.Hand(new String[]{"1", "2", "3", "4", "5"}).transformToProtocolString());
        state.applyMessage(new Stock("Alice", "5").transformToProtocolString());
        state.applyMessage(new Stock("Bob", "SB").transformToProtocolString());
        state.applyMessage(new Turn("Alice").transformToProtocolString());
        return state;
    }

    /**
     * Verifies START requires initial sync before rendering.
     * @requires A new RemoteGameState instance.
     * @ensures Rendering is delayed until hand/stock/turn are received.
     */
    @Test
    void startRequiresInitialSyncBeforeRender() {
        RemoteGameState state = new RemoteGameState("Alice");
        RemoteGameState.Update start =
                state.applyMessage(new Start(new String[]{"Alice", "Bob"}).transformToProtocolString());
        assertFalse(start.shouldRender);
        assertEquals("Game started.", start.notice);
        assertTrue(state.isStarted());
        assertEquals(List.of("Alice", "Bob"), state.getPlayers());

        assertFalse(state.applyMessage(
                new protocol.server.Hand(new String[]{"1", "2", "3", "4", "5"}).transformToProtocolString()
        ).shouldRender);
        assertFalse(state.applyMessage(new Stock("Alice", "5").transformToProtocolString()).shouldRender);
        assertFalse(state.applyMessage(new Stock("Bob", "SB").transformToProtocolString()).shouldRender);
        RemoteGameState.Update turn = state.applyMessage(new Turn("Alice").transformToProtocolString());
        assertTrue(turn.shouldRender);
        assertEquals("Your turn.", state.getLastEvent());

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        RemoteGameState.PlayerView bob = state.getPlayerView("Bob");
        assertNull(alice.stockCount);
        assertNull(bob.stockCount);
        assertEquals(0, alice.score);
        assertEquals(0, bob.score);
        assertEquals("5", alice.stockTop);
        assertEquals("SB", bob.stockTop);
        assertEquals(List.of("1", "2", "3", "4", "5"), state.getHand());
    }

    /**
     * Verifies QUEUE sets queued state and notice.
     * @requires A new RemoteGameState instance.
     * @ensures Queue state and last event are updated.
     */
    @Test
    void queueSetsQueuedAndNotice() {
        RemoteGameState state = new RemoteGameState("Alice");
        RemoteGameState.Update update = state.applyMessage(new Queue().transformToProtocolString());
        assertFalse(update.shouldRender);
        assertEquals("Waiting for more players.", update.notice);
        assertTrue(state.isQueued());
        assertEquals("Waiting for more players.", state.getLastEvent());
    }

    /**
     * Verifies stock plays update build piles and clear stock info.
     * @requires State is synchronized and it is Alice's turn.
     * @ensures Build pile updates and Alice's stock is cleared.
     */
    @Test
    void playFromStockUpdatesBuildPileAndStockCount() {
        RemoteGameState state = readyState();
        RemoteGameState.Update update = state.applyMessage(playMessage(
                "Alice",
                new StockPilePosition(),
                new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, 0)
        ));
        assertTrue(update.shouldRender);

        RemoteGameState.BuildPileView[] buildPiles = state.getBuildPiles();
        assertEquals(1, buildPiles[0].size);
        assertEquals("5", buildPiles[0].topToken);

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        assertNull(alice.stockCount);
        assertNull(alice.stockTop);
    }

    /**
     * Verifies hand-to-discard play updates discard top token.
     * @requires State is synchronized and hand contains a skip-bo card.
     * @ensures Discard top token is updated for the target pile.
     */
    @Test
    void playFromHandUpdatesDiscardTop() {
        RemoteGameState state = readyState();
        state.applyMessage(playMessage(
                "Alice",
                new HandPosition(new protocol.common.Card((Integer) null)),
                new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, 2)
        ));

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        assertEquals("SB", alice.discardTops[2]);
    }

    /**
     * Verifies TABLE updates build piles, discard tops, and stock tops.
     * @requires State is synchronized.
     * @ensures Build piles and player views match the table snapshot.
     */
    @Test
    void tableMessageUpdatesBuildPilesAndDiscards() {
        RemoteGameState state = readyState();
        Table.PlayerTable[] players = {
                new Table.PlayerTable("Alice", "9", "1", "2", "3", "4"),
                new Table.PlayerTable("Bob", "X", null, null, null, null)
        };
        Table table = new Table(players, "1", null, "SB3", "4");
        state.applyMessage(table.transformToProtocolString());

        RemoteGameState.BuildPileView[] buildPiles = state.getBuildPiles();
        assertEquals(1, buildPiles[0].size);
        assertEquals("1", buildPiles[0].topToken);
        assertEquals(0, buildPiles[1].size);
        assertNull(buildPiles[1].topToken);
        assertEquals(3, buildPiles[2].size);
        assertEquals("SB3", buildPiles[2].topToken);
        assertEquals(4, buildPiles[3].size);
        assertEquals("4", buildPiles[3].topToken);

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        RemoteGameState.PlayerView bob = state.getPlayerView("Bob");
        assertEquals("9", alice.stockTop);
        assertEquals("1", alice.discardTops[0]);
        assertEquals("2", alice.discardTops[1]);
        assertEquals("3", alice.discardTops[2]);
        assertEquals("4", alice.discardTops[3]);
        assertNull(bob.stockTop);
        assertNull(bob.discardTops[0]);
    }

    /**
     * Verifies TABLE clears stock count when player stock is empty.
     * @requires State is synchronized with stock count tracking enabled.
     * @ensures Stock count becomes zero for empty stock.
     */
    @Test
    void tableMessageClearsStockCountWhenEmpty() {
        RemoteGameState state = new RemoteGameState("Alice", true);
        state.applyMessage(new Start(new String[]{"Alice", "Bob"}).transformToProtocolString());
        state.applyMessage(new protocol.server.Hand(new String[]{"1", "2", "3", "4", "5"}).transformToProtocolString());
        state.applyMessage(new Stock("Alice", "5").transformToProtocolString());
        state.applyMessage(new Stock("Bob", "SB").transformToProtocolString());
        state.applyMessage(new Turn("Alice").transformToProtocolString());

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        assertEquals(30, alice.stockCount);

        Table.PlayerTable[] players = {
                new Table.PlayerTable("Alice", "X", null, null, null, null),
                new Table.PlayerTable("Bob", "SB", null, null, null, null)
        };
        Table table = new Table(players, "X", "X", "X", "X");
        state.applyMessage(table.transformToProtocolString());

        RemoteGameState.PlayerView updated = state.getPlayerView("Alice");
        assertEquals(0, updated.stockCount);
    }

    /**
     * Verifies ROUND/WINNER scores and ERROR notices are tracked.
     * @requires State is synchronized and round/winner/error messages are applied.
     * @ensures Scores and notices are updated appropriately.
     */
    @Test
    void scoresAndErrorsAreTracked() {
        RemoteGameState state = readyState();
        Round.Score[] scores = {
                new Round.Score("Alice", 25),
                new Round.Score("Bob", 10)
        };
        RemoteGameState.Update round = state.applyMessage(new Round(scores).transformToProtocolString());
        assertTrue(round.shouldRender);
        assertEquals("Round ended. Scores: Alice.25 Bob.10", round.notice);

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        RemoteGameState.PlayerView bob = state.getPlayerView("Bob");
        assertEquals(25, alice.score);
        assertEquals(10, bob.score);

        Winner.Score[] winners = {
                new Winner.Score("Alice", 500),
                new Winner.Score("Bob", 320)
        };
        RemoteGameState.Update winner = state.applyMessage(new Winner(winners).transformToProtocolString());
        assertTrue(winner.shouldRender);
        assertEquals("Game over. Winner: Alice (500) Scores: Alice.500 Bob.320", winner.notice);
        assertEquals("Game over. Winner: Alice (500)", state.getLastEvent());

        RemoteGameState.Update error = state.applyMessage(
                new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString()
        );
        assertFalse(error.shouldRender);
        assertEquals("Error 205 (Command not allowed)", error.notice);
        assertEquals("Error 205 (Command not allowed)", state.getLastEvent());
    }

    /**
     * Ensures scores persist across a new START message.
     * @requires Scores are set via a ROUND message.
     * @ensures Scores are preserved after a new START.
     */
    @Test
    void scoresPersistAcrossRoundStart() {
        RemoteGameState state = readyState();
        Round.Score[] scores = {
                new Round.Score("Alice", 40),
                new Round.Score("Bob", 15)
        };
        state.applyMessage(new Round(scores).transformToProtocolString());
        state.applyMessage(new Start(new String[]{"Alice", "Bob"}).transformToProtocolString());

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        RemoteGameState.PlayerView bob = state.getPlayerView("Bob");
        assertEquals(40, alice.score);
        assertEquals(15, bob.score);
    }

    /**
     * Verifies player disconnect removes the other player in a two-player game.
     * @requires State has two players.
     * @ensures Disconnected player is removed from the player list.
     */
    @Test
    void disconnectErrorRemovesOtherPlayerInTwoPlayerGame() {
        RemoteGameState state = readyState();
        RemoteGameState.Update error = state.applyMessage(
                new protocol.server.Error(ErrorCode.PLAYER_DISCONNECTED).transformToProtocolString()
        );
        assertTrue(error.shouldRender);
        assertEquals("Error 103 (Player disconnected)", error.notice);
        assertEquals(List.of("Alice"), state.getPlayers());
        assertNull(state.getPlayerView("Bob"));
    }

    /**
     * Verifies assumed stock counts update on stock plays.
     * @requires State uses assumed stock counts.
     * @ensures Stock count decreases after a stock play.
     */
    @Test
    void assumeStockCountsTracksInitialAndStockPlays() {
        RemoteGameState state = new RemoteGameState("Alice", true);
        state.applyMessage(new Start(new String[]{"Alice", "Bob"}).transformToProtocolString());
        state.applyMessage(new protocol.server.Hand(new String[]{"1", "2", "3", "4", "5"}).transformToProtocolString());
        state.applyMessage(new Stock("Alice", "5").transformToProtocolString());
        state.applyMessage(new Stock("Bob", "SB").transformToProtocolString());
        state.applyMessage(new Turn("Alice").transformToProtocolString());

        RemoteGameState.PlayerView alice = state.getPlayerView("Alice");
        RemoteGameState.PlayerView bob = state.getPlayerView("Bob");
        assertEquals(30, alice.stockCount);
        assertEquals(30, bob.stockCount);

        state.applyMessage(playMessage(
                "Alice",
                new StockPilePosition(),
                new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, 0)
        ));
        alice = state.getPlayerView("Alice");
        assertEquals(29, alice.stockCount);
    }

    /**
     * Builds a PLAY message string with the given positions.
     * @param player player name
     * @param from source position
     * @param to destination position
     * @return serialized PLAY message
     * @requires Parameters are non-null.
     * @ensures Returned string matches the protocol PLAY format.
     */
    private String playMessage(String player,
                               protocol.common.position.Position from,
                               protocol.common.position.Position to) {
        return new Play(from.toString(), to.toString(), player).transformToProtocolString();
    }
}
