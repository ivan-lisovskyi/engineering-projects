package protocol.server;

import protocol.Command;

/**
 * Server Command
 * Inform all players in a game, which player is current turn.
 */
public class Turn implements Command {

    public static final String COMMAND = "TURN";
    public String player;

    public Turn(String player){
        this.player = player;
    }
    
    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + player;
    }
}
