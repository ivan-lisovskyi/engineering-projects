package networking;

import protocol.Command;
import protocol.ProtocolCodec;
import protocol.client.Chat;
import protocol.client.Game;
import protocol.client.Hello;
import protocol.common.ErrorCode;
import protocol.common.Feature;
import protocol.server.Queue;
import protocol.server.Welcome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Owns the server-side lobby and connection state.
 * It validates incoming commands, tracks connected clients and their names, and
 * starts a GameRoom when the required number of human players are present.
 * The server supports bots by reducing the required human count accordingly.
 */
public class ServerState {
    // Connected clients indexed by player name.
    private final Map<String, ClientHandler> clientsByName = new HashMap<>();
    // Reverse lookup from handler to player name.
    private final Map<ClientHandler, String> namesByHandler = new HashMap<>();
    // Join order used to choose who enters a game first.
    private final List<ClientHandler> joinOrder = new ArrayList<>();
    // Total players requested (humans + bots); null until specified.
    private Integer expectedPlayers;
    // Number of bot seats configured for the server.
    private final int botCount;
    // Active game room, if a match is running.
    private GameRoom gameRoom;

    /**
     * Constructor for the ServerState class.
     * Initializes the server state with a specified number of expected players and bot count.
     * Also checks if the game can be started immediately based on the provided player and bot details.
     * @param expectedPlayers the number of players expected to join the game; can be null if unspecified
     * @param botCount the number of bot players to be used in the game; must be 0 or greater
     */
    public ServerState(Integer expectedPlayers, int botCount) {
        this.expectedPlayers = expectedPlayers;
        this.botCount = Math.max(0, botCount);
        if (expectedPlayers != null && expectedPlayers == this.botCount) {
            maybeStartGame();
        }
    }

    /**
     * Routes a command line received from a client to the appropriate handling logic,
     * based on the command contained in the line. The method processes the command,
     * validates it, and executes the corresponding actions or sends appropriate error
     * responses if the command is invalid or not allowed in the current state.
     * @param handler The ClientHandler instance representing the client sending the command.
     *                This object provides information about the client and methods to send responses
     *                back to it.
     * @param line The command line sent by the client, which is expected to follow a predefined
     *             protocol format. The line is parsed to determine the command and its parameters.
     */
    public void routeLine(ClientHandler handler, String line) {
        String[] parts = line.split(Pattern.quote(Command.SEPERATOR), -1);
        if (parts.length == 0) {
            handler.sendError(ErrorCode.INVALID_COMMAND);
            return;
        }
        String command = parts[0].trim().toUpperCase(Locale.ROOT);

        if (handler.getPlayerName() == null) {
            if (!Hello.COMMAND.equals(command)) {
                handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
                handler.close();
                return;
            }
            if (parts.length < 2 || parts.length > 3) {
                handler.sendError(ErrorCode.INVALID_COMMAND);
                handler.close();
                return;
            }
            boolean accepted = handleHello(handler, parts);
            if (!accepted) {
                handler.close();
            }
            return;
        }

        if (Game.COMMAND.equals(command)) {
            if (parts.length != 2) {
                handler.sendError(ErrorCode.INVALID_COMMAND);
                return;
            }
            handleGameRequest(handler, parts);
            return;
        }

        if (Chat.COMMAND.equals(command)) {
            if (parts.length < 2) {
                handler.sendError(ErrorCode.INVALID_COMMAND);
                return;
            }
            String message = String.join(Command.SEPERATOR, Arrays.copyOfRange(parts, 1, parts.length));
            if (message.isBlank()) {
                handler.sendError(ErrorCode.INVALID_COMMAND);
                return;
            }
            String payload = new protocol.server.Chat(handler.getPlayerName(), message).transformToProtocolString();
            broadcast(payload);
            return;
        }

        GameRoom room = getGameRoom();
        if (room == null) {
            if (handler.isGameEndedByDisconnect()) {
                handler.sendError(ErrorCode.PLAYER_DISCONNECTED);
                return;
            }
            handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        room.handleCommand(handler, command, parts);
    }

    /**
     * Handles a "HELLO" command from a client to join the server. This method validates the player's name
     * and features, checks for various constraints such as duplicate names and room capacity, and adds the
     * player to the server's state upon successful validation. If any validation fails, the appropriate error
     * message is sent back to the client.
     * @param handler The ClientHandler instance representing the client sending the "HELLO" command.
     *                This object is used to send responses or errors to the client and manage client state.
     * @param parts An array of command parameters sent by the client. The array is expected to include the
     *              player's name and optional feature string, adhering to the protocol's format.
     * @return true if the client is successfully added to the server; false if the command
     *         fails validation or is otherwise not allowed.
     */
    public boolean handleHello(ClientHandler handler, String[] parts) {
        String name = parts.length > 1 ? parts[1].trim() : "";
        String featureRaw = parts.length > 2 ? parts[2].trim() : "";

        if (!ProtocolCodec.isValidPlayerName(name)) {
            handler.sendError(ErrorCode.INVALID_PLAYER_NAME);
            return false;
        }

        Feature[] features = ProtocolCodec.parseFeaturesStrict(featureRaw);
        if (features == null) {
            handler.sendError(ErrorCode.INVALID_COMMAND);
            return false;
        }

        synchronized (this) {
            if (gameRoom != null) {
                handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
                return false;
            }
            int maxHumans = expectedPlayers == null ? Integer.MAX_VALUE : Math.max(0, expectedPlayers - botCount);
            if (clientsByName.size() >= maxHumans) {
                handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
                return false;
            }
            if (clientsByName.containsKey(name)) {
                handler.sendError(ErrorCode.NAME_IN_USE);
                return false;
            }

            clientsByName.put(name, handler);
            namesByHandler.put(handler, name);
            joinOrder.add(handler);
            handler.setPlayerName(name);
        }

        String welcome = new Welcome(name, features).transformToProtocolString();
        handler.send(welcome);
        broadcastToOthers(handler, welcome);
        maybeStartGame();
        return true;
    }

    /**
     * Handles a game join request from a client. This method validates the request parameters
     * such as the number of requested players, ensures that the request complies with server rules,
     * checks the current game state, and determines if the request can be accepted. If valid, it
     * informs the client and potentially starts the game. If invalid, appropriate error messages
     * are sent to the client.
     * @param handler The ClientHandler instance representing the client that sent
     *                the game join request. It is used for sending responses back to the client.
     * @param parts   An array of command parameters included in the request. The second element
     *                (if present) is expected to specify the number of players requested for
     *                the game. It is validated to ensure it conforms to server settings.
     */
    public void handleGameRequest(ClientHandler handler, String[] parts) {
        int requested;
        try {
            requested = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : -1;
        } catch (NumberFormatException e) {
            handler.sendError(ErrorCode.INVALID_COMMAND);
            return;
        }

        if (requested < 2 || requested > 6 || requested < botCount) {
            handler.sendError(ErrorCode.INVALID_COMMAND);
            return;
        }

        boolean accepted = false;
        boolean alreadyStarting = false;
        synchronized (this) {
            if (gameRoom != null) {
                if (expectedPlayers != null && expectedPlayers == requested) {
                    alreadyStarting = true;
                } else {
                    handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
                    return;
                }
            } else {
                if (expectedPlayers == null) {
                    int requiredHumans = Math.max(0, requested - botCount);
                    if (clientsByName.size() > requiredHumans) {
                        handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
                        return;
                    }
                    expectedPlayers = requested;
                    accepted = true;
                } else if (expectedPlayers == requested) {
                    accepted = true;
                } else {
                    handler.sendError(ErrorCode.COMMAND_NOT_ALLOWED);
                    return;
                }
            }
        }

        if (accepted) {
            handler.send(new Queue().transformToProtocolString());
            maybeStartGame();
        }
        // If alreadyStarting is true, the game is already in progress
    }

    /**
     * Broadcasts a message to all connected clients except the sender.
     * This method retrieves a snapshot of the currently connected clients
     * to avoid issues with concurrent modifications while iterating through the list.
     * @param sender  The ClientHandler instance representing the client
     *                sending the message. This client will be excluded from receiving the broadcast.
     * @param message The message to be broadcast to all other clients.
     */
    private void broadcastToOthers(ClientHandler sender, String message) {
        List<ClientHandler> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(joinOrder);
        }
        for (ClientHandler handler : snapshot) {
            if (handler == sender) {
                continue;
            }
            handler.send(message);
        }
    }

    public synchronized GameRoom getGameRoom() {
        return gameRoom;
    }

    /**
     * Handles the disconnection of a client from the server. This involves removing
     * the client from the server's internal tracking structures, updating the list
     * of connected players, and notifying the game room (if any) about the player's
     * disconnection. If the client is not found, the method exits without further action.
     * @param handler The ClientHandler instance representing the disconnected client.
     *                This object is used to identify and remove the client from the server's
     *                internal state and to perform any necessary cleanup.
     */
    public void handleDisconnect(ClientHandler handler) {
        String name;
        synchronized (this) {
            name = namesByHandler.remove(handler);
            if (name == null) {
                return;
            }
            clientsByName.remove(name);
            joinOrder.remove(handler);
        }

        GameRoom room = getGameRoom();
        if (room != null) {
            room.playerDisconnected(name);
        }
    }

    /**
     * Ends the current game room session by clearing the associated server state.
     * This method synchronizes access to ensure thread safety while modifying
     * shared resources related to the game room state.
     * After this method is invoked, the game room and expected players state
     * are set to null, effectively marking the end of the room lifecycle
     * in the server.
     */
    public void endRoom() {
        synchronized (this) {
            gameRoom = null;
            expectedPlayers = null;
        }
    }

    /**
     * Broadcasts a message to all connected clients.
     * This method takes a synchronized snapshot of the current client list
     * to avoid concurrent modification issues during iteration.
     * @param message The message to be broadcast to all connected clients.
     */
    public void broadcast(String message) {
        List<ClientHandler> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(joinOrder);
        }
        for (ClientHandler handler : snapshot) {
            handler.send(message);
        }
    }


    /**
     * Starts a new game room when enough human players are connected and the total player count
     * (humans + bots) has been specified. The method is safe to call repeatedly; it only starts
     * a room once and leaves if a game is already running or requirements are not met.
     * <p>This method takes a synchronized snapshot of the current join order to choose the first
     * requiredHumans handlers, then clears disconnect flags and launches the room in a
     * dedicated daemon thread outside the lock.</p>
     */
    private void maybeStartGame() {
        GameRoom roomToStart = null;
        List<ClientHandler> handlersSnapshot = null;
        synchronized (this) {
            if (gameRoom == null && expectedPlayers != null) {
                int requiredHumans = Math.max(0, expectedPlayers - botCount);
                if (clientsByName.size() >= requiredHumans) {
                    List<ClientHandler> handlers = new ArrayList<>(joinOrder.subList(0, requiredHumans));
                    roomToStart = new GameRoom(this, handlers, botCount);
                }
            }
            if (roomToStart != null) {
                gameRoom = roomToStart;
                handlersSnapshot = new ArrayList<>(joinOrder);
            }
        }
        if (roomToStart != null) {
            for (ClientHandler handler : handlersSnapshot) {
                handler.clearGameEndedByDisconnect();
            }
            Thread runner = new Thread(roomToStart::start, "game-room");
            runner.setDaemon(true);
            runner.start();
        }
    }
}
