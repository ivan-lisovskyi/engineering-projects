package protocol.server;

import protocol.Command;
import protocol.common.Feature;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Server Command
 * Used to inform all connected clients, that a new player connected.
 */
public class Welcome implements Command {

    public static final String COMMAND = "WELCOME";
    public String playerName;
    public Feature[] supportedFeatures;


    public Welcome(String name, Feature[] supportedFeatures){
        this.playerName = name;
        this.supportedFeatures = supportedFeatures;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND
                + SEPERATOR
                + this.playerName
                + SEPERATOR
                + Stream.of(supportedFeatures)
                    .sorted()
                    .map(Feature::getLetter)
                    .map(Object::toString)
                    .collect(Collectors.joining());
    }
}
