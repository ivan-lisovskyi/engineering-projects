package protocol.client;

import protocol.Command;

/**
 * Client Command
 * Indicates the end of a turn
 */
public class End implements Command {

    public static final String COMMAND = "END";

    @Override
    public String transformToProtocolString() {
        return COMMAND;
    }
}
