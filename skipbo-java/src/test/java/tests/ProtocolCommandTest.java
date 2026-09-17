package tests;

import org.junit.jupiter.api.Test;
import protocol.ProtocolException;
import protocol.client.End;
import protocol.client.Game;
import protocol.client.Hand;
import protocol.client.Play;
import protocol.client.Table;
import protocol.common.Card;
import protocol.common.ErrorCode;
import protocol.common.Feature;
import protocol.common.position.DrawPilePosition;
import protocol.common.position.HandPosition;
import protocol.common.position.NumberedPilePosition;
import protocol.common.position.StockPilePosition;
import protocol.server.Queue;
import protocol.server.Start;
import protocol.server.Stock;
import protocol.server.Turn;
import protocol.server.Welcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests protocol command serialization for client and server command types.
 * @ensures Only protocol command instances are created and serialized.
 */
public class ProtocolCommandTest {

    /**
     * Verifies hello/welcome commands sort feature flags.
     * @requires Features are provided in arbitrary order.
     * @ensures Serialized strings list features in sorted order.
     */
    @Test
    void helloAndWelcomeSortFeatures() {
        Feature[] features = {Feature.MASTER, Feature.CHAT, Feature.LOBBY};
        assertEquals("HELLO~Alice~CLM", new protocol.client.Hello("Alice", features).transformToProtocolString());
        assertEquals("WELCOME~Alice~CLM", new Welcome("Alice", features).transformToProtocolString());
    }

    /**
     * Verifies client command serialization matches the protocol.
     * @requires Command arguments are valid.
     * @ensures Serialized strings match the expected protocol format.
     */
    @Test
    void clientCommandsSerializeCorrectly() throws ProtocolException {
        assertEquals("GAME~3", new Game(3).transformToProtocolString());
        assertEquals("END", new End().transformToProtocolString());
        assertEquals("HAND", new Hand().transformToProtocolString());
        assertEquals("TABLE", new Table().transformToProtocolString());
        assertEquals("CHAT~hello", new protocol.client.Chat("hello").transformToProtocolString());

        HandPosition from = new HandPosition(new Card(7));
        NumberedPilePosition to = new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, 2);
        assertEquals("PLAY~H.7~B.2", new Play(from, to).transformToProtocolString());
    }

    /**
     * Verifies server command serialization matches the protocol.
     * @requires Command arguments are valid.
     * @ensures Serialized strings match the expected protocol format.
     */
    @Test
    void serverCommandsSerializeCorrectly() {
        assertEquals("QUEUE", new Queue().transformToProtocolString());
        assertEquals("START~Alice,Bob", new Start(new String[]{"Alice", "Bob"}).transformToProtocolString());
        assertEquals("TURN~Alice", new Turn("Alice").transformToProtocolString());
        assertEquals("STOCK~Alice~X", new Stock("Alice", null).transformToProtocolString());
        assertEquals("ERROR~205", new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString());
        assertEquals("HAND~1,2,3", new protocol.server.Hand(new String[]{"1", "2", "3"}).transformToProtocolString());
        assertEquals("CHAT~Alice~hello", new protocol.server.Chat("Alice", "hello").transformToProtocolString());

        protocol.server.Round.Score[] scores = {
                new protocol.server.Round.Score("Alice", 10),
                new protocol.server.Round.Score("Bob", 5)
        };
        assertEquals("ROUND~Alice.10,Bob.5", new protocol.server.Round(scores).transformToProtocolString());

        protocol.server.Winner.Score[] winnerScores = {
                new protocol.server.Winner.Score("Alice", 500),
                new protocol.server.Winner.Score("Bob", 320)
        };
        assertEquals("WINNER~Alice.500,Bob.320", new protocol.server.Winner(winnerScores).transformToProtocolString());

        protocol.server.Table.PlayerTable[] players = {
                new protocol.server.Table.PlayerTable("Alice", "SB", "1", "2", null, "4")
        };
        protocol.server.Table table = new protocol.server.Table(players, "1", null, "SB3", "4");
        assertEquals("TABLE~1.X.SB3.4~Alice.SB.1.2.X.4", table.transformToProtocolString());

        protocol.server.Play play = new protocol.server.Play("H.7", "B.0", "Alice");
        assertEquals("PLAY~Alice~H.7~B.0", play.transformToProtocolString());
    }

    /**
     * Verifies position and card token serialization.
     * @requires Card values are within valid protocol ranges.
     * @ensures String tokens match the expected protocol encoding.
     */
    @Test
    void positionAndCardSerializeCorrectly() throws ProtocolException {
        assertEquals("H.2", new HandPosition(new Card(2)).toString());
        assertEquals("H.SB", new HandPosition(new Card((Integer) null)).toString());
        assertEquals("SB4", new Card(Integer.valueOf(4)).toString());
        assertEquals("S", new StockPilePosition().toString());
        assertEquals("R", new DrawPilePosition().toString());
        assertEquals("B.3", new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, 3).toString());
        assertEquals("D.1", new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, 1).toString());
    }

    /**
     * Ensures invalid card numbers are rejected by the protocol card type.
     * @ensures Creating cards with invalid numbers throws ProtocolException.
     */
    @Test
    void cardRejectsInvalidNumbers() {
        assertThrows(ProtocolException.class, () -> new Card(0));
        assertThrows(ProtocolException.class, () -> new Card(13));
    }

    /**
     * Verifies ProtocolException preserves the provided message.
     * @requires A non-null message is supplied to the constructor.
     * @ensures ProtocolException#getMessage() returns the original message.
     */
    @Test
    void protocolExceptionRetainsMessage() {
        ProtocolException exception = new ProtocolException("Bad protocol");
        assertEquals("Bad protocol", exception.getMessage());
    }
}
