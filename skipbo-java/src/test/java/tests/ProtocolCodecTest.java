package tests;

import org.junit.jupiter.api.Test;
import protocol.ProtocolCodec;
import protocol.common.Feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests parsing and validation helpers in ProtocolCodec.
 * @ensures Only protocol parsing helpers are exercised.
 */
public class ProtocolCodecTest {

    /**
     * Verifies feature parsing enforces strict ordering and uniqueness.
     * @requires Feature tokens are provided in different orders.
     * @ensures Invalid or unsorted feature strings are rejected.
     */
    @Test
    void featuresAreStrictAndSorted() {
        Feature[] features = ProtocolCodec.parseFeaturesStrict("CLM");
        assertNotNull(features);
        assertEquals(3, features.length);
        assertNull(ProtocolCodec.parseFeaturesStrict("LC"));
        assertNull(ProtocolCodec.parseFeaturesStrict("CC"));
        assertNull(ProtocolCodec.parseFeaturesStrict("CMX"));
    }

    /**
     * Verifies card token parsing matches the protocol rules.
     * @requires Input tokens follow protocol formatting rules.
     * @ensures Valid tokens parse and invalid tokens return null.
     */
    @Test
    void cardTokenParsingMatchesProtocol() {
        assertNotNull(ProtocolCodec.parseCardToken("SB", false));
        assertNull(ProtocolCodec.parseCardToken("SB4", false));
        assertNotNull(ProtocolCodec.parseCardToken("SB4", true));
        assertNotNull(ProtocolCodec.parseCardToken("12", false));
        assertNull(ProtocolCodec.parseCardToken("13", false));
        assertNull(ProtocolCodec.parseCardToken("X", false));
    }

    /**
     * Verifies position token parsing and normalization.
     * @requires Input tokens follow protocol formatting rules.
     * @ensures Valid tokens are parsed and normalized; invalid tokens return null.
     */
    @Test
    void positionTokenParsingMatchesProtocol() {
        ProtocolCodec.PositionToken stock = ProtocolCodec.parsePositionToken("S");
        assertNotNull(stock);
        assertEquals(ProtocolCodec.PositionKind.STOCK, stock.kind);
        assertEquals("S", stock.normalized);

        ProtocolCodec.PositionToken draw = ProtocolCodec.parsePositionToken("r");
        assertNotNull(draw);
        assertEquals(ProtocolCodec.PositionKind.DRAW, draw.kind);
        assertEquals("R", draw.normalized);

        ProtocolCodec.PositionToken hand = ProtocolCodec.parsePositionToken("H.8");
        assertNotNull(hand);
        assertEquals(ProtocolCodec.PositionKind.HAND, hand.kind);
        assertEquals("8", hand.cardToken);
        assertEquals("H.8", hand.normalized);

        ProtocolCodec.PositionToken skipBoHand = ProtocolCodec.parsePositionToken("H.SB");
        assertNotNull(skipBoHand);
        assertEquals(ProtocolCodec.PositionKind.HAND, skipBoHand.kind);
        assertEquals("SB", skipBoHand.cardToken);
        assertEquals("H.SB", skipBoHand.normalized);

        assertNull(ProtocolCodec.parsePositionToken("H.SB4"));

        for (int i = 0; i <= 3; i++) {
            ProtocolCodec.PositionToken discard = ProtocolCodec.parsePositionToken("D." + i);
            assertNotNull(discard);
            assertEquals(ProtocolCodec.PositionKind.DISCARD, discard.kind);
            assertEquals(i, discard.index);
            assertEquals("D." + i, discard.normalized);
        }

        for (int i = 0; i <= 3; i++) {
            ProtocolCodec.PositionToken build = ProtocolCodec.parsePositionToken("B." + i);
            assertNotNull(build);
            assertEquals(ProtocolCodec.PositionKind.BUILD, build.kind);
            assertEquals(i, build.index);
            assertEquals("B." + i, build.normalized);
        }

        assertNull(ProtocolCodec.parsePositionToken("B.4"));
        assertNull(ProtocolCodec.parsePositionToken("D.-1"));
        assertNull(ProtocolCodec.parsePositionToken("H."));
        assertNull(ProtocolCodec.parsePositionToken("R.1"));
        assertNull(ProtocolCodec.parsePositionToken("Q"));
    }

    /**
     * Verifies player name validation follows protocol requirements.
     * @requires Input names use a range of valid and invalid characters.
     * @ensures Only valid names are accepted.
     */
    @Test
    void playerNameValidationMatchesProtocol() {
        assertTrue(ProtocolCodec.isValidPlayerName("A_B-9"));
        assertEquals(0, ProtocolCodec.parseFeaturesStrict("").length);
        assertEquals(false, ProtocolCodec.isValidPlayerName("Bad Name"));
        assertEquals(false, ProtocolCodec.isValidPlayerName("!bad"));
    }
}
