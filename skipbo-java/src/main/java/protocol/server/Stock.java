package protocol.server;

import protocol.Command;

/**
 * Server Command
 * Informs everyone about a new top card on someone stock pile
 */
public class Stock implements Command {

    public static final String COMMAND = "STOCK";
    public String topCard;
    public String player;

    public Stock(String player, String topCard){
        this.player = player;
        this.topCard = topCard;
    }
    
    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + player + SEPERATOR + (topCard == null ? "X": topCard);
    }
}
