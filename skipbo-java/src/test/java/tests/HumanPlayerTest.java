package tests;

import model.players.HumanPlayer;
import model.piles.Hand;
import model.piles.StockPile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests HumanPlayer identity and human flag behavior.
 * @ensures Only test-local player instances are created.
 */
public class HumanPlayerTest {
    private Hand hand;
    private StockPile stockPile;
    private HumanPlayer player;

    /**
     * Initializes a human player with empty piles.
     * @ensures player is created with empty hand and stock pile.
     */
    @BeforeEach
    void setUp() {
        hand = new Hand();
        stockPile = new StockPile();
        player = new HumanPlayer("Alice", hand, stockPile);
    }

    /**
     * Confirms human players report true for HumanPlayer#isHuman().
     * @requires player is initialized.
     * @ensures isHuman() returns true.
     */
    @Test
    void isHumanReturnsTrue() {
        assertTrue(player.isHuman());
    }

    /**
     * Ensures getName returns the name supplied at construction.
     * @requires player is initialized.
     * @ensures Returned name matches the constructor argument.
     */
    @Test
    void getNameReturnsConstructorName() {
        assertEquals("Alice", player.getName());
    }

}
