package protocol.common;

import protocol.ProtocolException;

public class Card {
    private Integer number = null;
    private boolean skipBo = false;

    /**
     * Method for creating an card representing a number form 1 to 12
     * @param number a number between 1 and 12
     * @throws ProtocolException if the number is < 1 or > 12
     */
    public Card(int number) throws ProtocolException {
        if(number < 1 || number > 12)
            throw new ProtocolException("Invalid card number");

        this.number = number;
    }

    /**
     * Method for creating a card representing a Skip-BO
     * Needs a number when placed on a Building Pile
     */
    public Card(Integer number){
        this.number = number;
        this.skipBo = true;
    }

    public Integer getNumber(){
        return this.number;
    }

    public String toString(){
        if (skipBo) {
            return number == null ? "SB" : "SB" + number;
        }
        return String.valueOf(number);
    }

    public boolean isSkipBo() {
        return skipBo;
    }
}
