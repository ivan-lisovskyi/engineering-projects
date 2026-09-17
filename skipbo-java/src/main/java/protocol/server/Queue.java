package protocol.server;

import protocol.Command;

/**
 * Server Command
 * Command used to inform the client that its game request is received.
 */
public class Queue implements Command {

    public static final String COMMAND = "QUEUE";

    @Override
    public String transformToProtocolString() {
        return COMMAND;
    }
}
