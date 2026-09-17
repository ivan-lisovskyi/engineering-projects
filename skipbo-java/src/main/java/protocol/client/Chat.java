package protocol.client;

import protocol.Command;

/**
 * Client Command
 * Send a chat message to the server.
 */
public class Chat implements Command {

    public static final String COMMAND = "CHAT";
    private final String message;

    public Chat(String message) {
        this.message = message;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + (message == null ? "" : message);
    }
}
