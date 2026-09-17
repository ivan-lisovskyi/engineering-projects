package protocol.server;

import protocol.Command;

/**
 * Server Command
 * Broadcast a chat message to all connected clients.
 */
public class Chat implements Command {

    public static final String COMMAND = "CHAT";
    private final String player;
    private final String message;

    public Chat(String player, String message) {
        this.player = player;
        this.message = message;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND
                + SEPERATOR
                + player
                + SEPERATOR
                + (message == null ? "" : message);
    }
}
