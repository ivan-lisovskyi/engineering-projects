package model;

import model.exceptions.BuildPileIndexOutOfRangeException;
import model.piles.BuildPile;
import model.piles.CompletedPile;
import model.piles.DrawPile;

import java.util.*;

/**
 * Holds the shared board state: build piles, the draw pile, and the completed pile.
 * buildPiles != null
 * buildPiles.size() == BUILD_PILE_COUNT
 * drawPile != null
 *  completedPile != null
 */
public class Board {
    public static final int BUILD_PILE_COUNT = 4;

    private final List<BuildPile> buildPiles =  new ArrayList<>(); // shared build piles (4 total)
    private final DrawPile drawPile;
    private final CompletedPile completedPile;

    /**
     * Creates a new board with the provided draw pile and four empty build piles.
     *
     * @param drawPile the draw pile to use
     * @requires drawPile != null
     * @ensures buildPiles.size() == BUILD_PILE_COUNT
     * @ensures completedPile is empty
     */
    public Board(DrawPile drawPile) {
        this.drawPile = Objects.requireNonNull(drawPile, "drawPile");
        this.completedPile = new CompletedPile();
        for (int i = 0; i < BUILD_PILE_COUNT; i++) {
            buildPiles.add(new BuildPile());
        }
    }

    /**
     * Internal constructor used for copying an existing board.
     *
     * @param drawPile the draw pile to use
     * @param buildPiles the build piles to use
     * @param completedPile the completed pile to use
     */
    private Board(DrawPile drawPile, List<BuildPile> buildPiles, CompletedPile completedPile) {
        this.drawPile = Objects.requireNonNull(drawPile, "drawPile");
        this.buildPiles.addAll(Objects.requireNonNull(buildPiles, "buildPiles"));
        this.completedPile = Objects.requireNonNull(completedPile, "completedPile");
    }


    /**
     * Returns a structural copy of the board with new pile instances.
     * Card objects are not cloned.
     *
     * @return a copied board instance
     * @ensures result != null
     * @ensures the returned board has BUILD_PILE_COUNT build piles
     * @ensures result != this
     */
    public Board deepCopy() {
        DrawPile drawCopy = drawPile.deepCopy(); // cards are same refs, but piles are new
        CompletedPile completedCopy = completedPile.deepCopy();

        List<BuildPile> buildCopies = new ArrayList<>();
        for (BuildPile bp : buildPiles) {
            buildCopies.add(bp.deepCopy());
        }

        return new Board(drawCopy, buildCopies, completedCopy);
    }


    /**
     * Returns an unmodifiable view of the draw pile contents.
     *
     * @return the draw pile list (top card is the last element)
     */
    public List<Card> getDrawPile() {
        return Collections.unmodifiableList(drawPile.getPile()); // read-only view for UI/tests
    }

    /** Clears the draw pile (primarily for tests). */
    public void clearDrawPile() {
        drawPile.clear();
    }

    /**
     * Attempts to place a card onto a build pile.
     *
     * @return true if placed, false if illegal
     * @requires 0 <= pileIndex && pileIndex < BUILD_PILE_COUNT
     * @requires card != null
     * @ensures result == true implies the card is now on the build pile or has been recycled
     * @ensures result == false implies no build pile state changes
     */
    public boolean putCard(int pileIndex, Card card) {
        Objects.requireNonNull(card, "card");
        checkIndex(pileIndex);

        BuildPile bp = buildPiles.get(pileIndex);
        if (!bp.canPlaceCard(card)) return false;

        bp.placeCard(card);
        if (bp.isComplete()) {
            recycleBuildPile(bp); // completed build pile goes to recycling pile
        }
        return true;
    }


    /**
     * Returns the build pile at the given index.
     *
     * @param index the 0-based build pile index
     * @return the requested build pile
     * @requires 0 <= index && index < BUILD_PILE_COUNT
     * @ensures result != null
     */
    public BuildPile getBuildPile(int index) {
        checkIndex(index);
        return buildPiles.get(index);
    }
    /**
     * Validates a build pile index.
     *
     * @param idx the 0-based build pile index
     * @throws BuildPileIndexOutOfRangeException if the index is out of range
     */
    private void checkIndex(int idx) {
        if (idx < 0 || idx >= buildPiles.size()) {
            throw new BuildPileIndexOutOfRangeException("Build pile index out of range: " + idx);
        }
    }

    /** Transfers a completed build pile into the completed pile. */
    private void recycleBuildPile(BuildPile bp) {
        bp.moveAllTo(completedPile);
    }

    /**
     * Draws a card from the draw pile, refilling from the completed pile if needed.
     *
     * @return the drawn card, or null if no cards are available
     * @ensures result == null implies drawPile and completedPile are empty after refill attempt
     * @ensures result != null implies exactly one card was removed from the draw pile
     */
    public Card draw() {
        if (drawPile.isEmpty() && !completedPile.isEmpty()) {
            drawPile.refillFrom(completedPile); // shuffle completed pile back into draw
        }
        return drawPile.takeTopCard();
    }

    /**
     * Returns the number of cards in the completed pile.
     *
     * @return the completed pile size
     */
    public int getCompletedPileSize() {
        return completedPile.size();
    }

}
