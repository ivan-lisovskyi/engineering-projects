package protocol.client;

import protocol.Command;
import protocol.common.position.Position;

/**
 * Client Command
 * To a specific move (play a card from a position ot another position)
 */
public class Play implements Command {

    public static final String COMMAND = "PLAY";
    public Position from;
    public Position to;


    public Play(Position from, Position to){
        this.from = from;
        this.to = to;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND
                + SEPERATOR
                + this.from
                + SEPERATOR
                + this.to;
    }
}
