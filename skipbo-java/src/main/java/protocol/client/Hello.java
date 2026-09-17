package protocol.client;

import protocol.Command;
import protocol.common.Feature;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Client Command
 * Used to announce yourself with a name to the server
 */
public class Hello implements Command {

    public static final String COMMAND = "HELLO";
    public String playerName;
    public Feature[] supportedFeatures;


    public Hello(String name, Feature[] supportedFeatures){
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
