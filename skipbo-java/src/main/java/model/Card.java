package model;

/**
 * Represents a Skip-Bo card with a number and color. Wild cards use CardColor#WILD
 * and their number is not used for sequencing.
 */
public class Card {
    private int cardNumber;
    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 12;

    public enum CardColor {
        BLUE, RED, GREEN, YELLOW, WILD;
    }
    private CardColor cardColor;

    /**
     * Returns the card's number.
     *
     * @return the card number
     */
    public int getCardNumber() {
        return cardNumber;
    }

    /**
     * Sets the card's number.
     *
     * @param cardNumber the card number to set
     */
    public void setCardNumber(int cardNumber) {
        this.cardNumber = cardNumber;
    }

    /**
     * Returns the card's color.
     *
     * @return the card color
     */
    public CardColor getCardColor() {
        return cardColor;
    }

    /**
     * Sets the card's color.
     *
     * @param cardColor the card color to set
     */
    public void setCardColor(CardColor cardColor) {
        this.cardColor = cardColor;
    }

    /**
     * Creates a new card with the given number and color.
     *
     * @param cardNumber the card number
     * @param cardColor the card color
     */
    public Card(int cardNumber, CardColor cardColor){
        setCardNumber(cardNumber);
        setCardColor(cardColor);
    }

    /**
     * Indicates whether this card is a wild card.
     *
     * @return true if the card color is WILD
     */
    public boolean isWild(){
        return getCardColor() == CardColor.WILD; // color defines wild, not number
    }
}
