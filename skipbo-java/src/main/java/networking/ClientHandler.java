package networking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import protocol.common.ErrorCode;

/**
 * Server-side handler for a single connected client.
 * One ClientHandler instance is created per client Socket. It runs on its own thread
 * implements Runnable and continuously:
 *   Reads protocol messages from the client.
 *   Delegates each message to ServerState for routing/processing
 *   Sends responses back to the client via send(String)
 * When the client disconnects (end-of-stream) or an I/O error occurs, the handler:
 *   Stops its read loop
 *   Notifies ServerState via state.handleDisconnect
 *   Closes the socket
 */

public class ClientHandler implements Runnable {
    // The TCP socket for this client connection.

    private final Socket socket;
    //Shared server state router that processes incoming protocol lines.
    private final ServerState state;
    //Reads text lines from the client
    private final BufferedReader reader;
    // Writes text lines to the client (newline-delimited).
    private final PrintWriter writer;
    //Controls if the handler's main read loop should continue running.
    // Marked as volatile so it updates from other threads are visible to the read loop thread.
    private volatile boolean running = true;
    //The name/identifier of the connected player.
    private String playerName;

    //Flag indicating that the game ended because this client disconnected.
    
    private volatile boolean endedByDisconnect;


    /**
     * Constructs a new handler for a connected client socket.
     * Initializes buffered text input/output around the provided socket.
     * @param socket the connected client socket
     * @param state the server state responsible for processing incoming lines
     * @throws IOException if the socket streams cannot be opened
     */

    public ClientHandler(Socket socket, ServerState state) throws IOException {
        this.socket = socket;
        this.state = state;

        // Read line-based protocol messages.
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        //  send line-based protocol messages; autoFlush=true flushes on println(),
        this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    /**
     * Returns the player name associated with this connection.
     * @return the player name, or null if not set yet
     */

    public String getPlayerName() {
        return playerName;
    }


    /**
     * Sets the player name for this connection.
     * @param playerName the registered name for this client
     */
    void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    /**
     * Marks that the current game ended due to a disconnect from this client.
     * This flag can be used by ServerState to decide whether and how to notify players,
     * update scores, or clean up sessions.
     */

    void markGameEndedByDisconnect() {
        this.endedByDisconnect = true;
    }

    /**
     * Clears the "game ended by disconnect" flag.
     */
    void clearGameEndedByDisconnect() {
        this.endedByDisconnect = false;
    }

    /**
     * Checks whether the game ended due to a disconnect from this client.
     * @return true if the game was ended by disconnect, false otherwise
     */
    boolean isGameEndedByDisconnect() {
        return endedByDisconnect;
    }

    /**
     * Sends a single line message to the client.
     * This method is thread-safe for concurrent send, as it synchronizes on the writer to prevent
     * output from multiple threads corrupting the protocol stream.
     * @param message the message to send; if null, nothing is sent
     */

    public void send(String message) {
        if (message == null) {
            return;
        }
        logLine("OUT", message);

        // Synchronize to keep each message atomic (one line at a time).
        synchronized (writer) {
            writer.println(message);
            writer.flush();
        }
    }

    /**
     * Sends a standardized protocol error message to the client.
     * @param code the error code to send
     */

    public void sendError(ErrorCode code) {
        send(new protocol.server.Error(code).transformToProtocolString());
    }


    /**
     * Main handler loop.
     * Continuously reads lines from the client while running is true
     *  the socket input is not closed (readLine() returns non-null)
     * Empty lines are ignored. Non-empty lines are forwarded to
     * handleLine(String)
     * Any IOException is treated as a disconnect. Cleanup always happens in finally.
     */
    @Override
    public void run() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                // Ignore blank messages to keep protocol handling simple.
                if (line.isEmpty()) {
                    continue;
                }
                logLine("IN", line);
                // Uses parsing/handling in the central server state/router.
                handleLine(line);
            }
        } catch (IOException ignored) {
            // Treat any I/O error as a disconnect.
        } finally {
            // Ensures stop, notifies server state, and releases resources exactly once.
            running = false;
            state.handleDisconnect(this);
            close();
        }
    }

    /**
     * Delegates a single incoming protocol line to ServerState.
     * @param line the incoming, trimmed, non-empty line
     */

    private void handleLine(String line) {
        state.routeLine(this, line);
    }

    /**
     * Logs one inbound/outbound protocol line if server logging is enabled.
     * Logging is controlled via the JVM system property skipbo.server.log.
     * @param direction label such as "IN" or "OUT"
     * @param line the line to log
     */
    private void logLine(String direction, String line) {
        if (!Boolean.getBoolean("skipbo.server.log")) {
            return;
        }
        // Prefer player name if known; otherwise use remote socket address.
        String who = playerName != null ? playerName : String.valueOf(socket.getRemoteSocketAddress());
        System.out.println(direction + " " + who + ": " + line);
    }

    /**
     * Stops the handler and closes its socket.
     * This method is safe to call multiple times. Any close errors are ignored.
     */

    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // Ignore close errors.
        }
    }
}
