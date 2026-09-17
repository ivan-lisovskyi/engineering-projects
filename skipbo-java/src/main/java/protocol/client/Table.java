package protocol.client;

import protocol.Command;

/**
 * Client Command
 * Request an overview what the table currently looks like
 */
public class Table implements Command {

    public static final String COMMAND = "TABLE";

    @Override
    public String transformToProtocolString() {
        return COMMAND;
    }
}
