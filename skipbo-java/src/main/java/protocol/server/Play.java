package protocol.server;

import protocol.Command;

/**
 * Client Command
 * Informs all players in a game about a play a player did
 */
public class Play implements Command {

    public static final String COMMAND = "PLAY";
    public String player;
    public String fromToken;
    public String toToken;


    public Play(String fromToken, String toToken, String player){
        this.fromToken = fromToken;
        this.toToken = toToken;
        this.player = player;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND
                + SEPERATOR
                + player
                + SEPERATOR
                + this.fromToken
                + SEPERATOR
                + this.toToken;
    }
}
