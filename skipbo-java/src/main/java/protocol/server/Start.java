package protocol.server;

import protocol.Command;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Server Command
 * Informs specific clients a new game starts, with the specified players
 */
public class Start implements Command {

    public static final String COMMAND = "START";
    public String[] players;

    public Start(String[] players){
        this.players = players;
    }
    
    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + Stream.of(players).collect(Collectors.joining(LIST_SEPERATOR));
    }
}
