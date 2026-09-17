package protocol.server;

import protocol.Command;

/**
 * Server Command
 * Informs a specific player about the cards in their hand
 */
public class Hand implements Command {

    public static final String COMMAND = "HAND";
    public String[] cards;

    public Hand(String[] cards){
        this.cards = cards;
    }
    
    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + String.join(LIST_SEPERATOR, cards);
    }
}
