package protocol.client;

import protocol.Command;

/**
 * Client Command
 * Request the cards in your hand to the server
 */
public class Hand implements Command {

    public static final String COMMAND = "HAND";

    @Override
    public String transformToProtocolString() {
        return COMMAND;
    }
}
