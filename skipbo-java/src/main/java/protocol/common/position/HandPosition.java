package protocol.common.position;

import protocol.Command;
import protocol.common.Card;

public class HandPosition implements Position {

    private final static String REP = "H";
    private Card card;

    public HandPosition(Card card){
        this.card = card;
    }

    public String toString(){
        if (card == null) {
            throw new IllegalArgumentException("Hand position requires a card");
        }
        return REP + Command.VALUE_SEPERATOR + card.toString();
    }
}
