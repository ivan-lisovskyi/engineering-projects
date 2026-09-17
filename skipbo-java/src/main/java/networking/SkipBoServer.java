package networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line entry point for the Skip-Bo server.
 * Parses CLI options, configures logging, opens a server socket, and spawns a
 * ClientHandler thread for each incoming connection.
 */
public class SkipBoServer {
    /**
     * Starts the server and begins accepting client connections.
     * Arguments:
     *   args[0]: server port;
     *   args[1]: optional total player counting;
     *   args[2]: optional bot count;
     *   >--log or --log=true|false: enable or disable server logging;
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java networking.SkipBoServer <port> [players] [bots] [--log]");
            return;
        }

        int port;
        Integer players = null;
        int bots = 0;
        boolean logEnabled = false;
        List<String> numericArgs = new ArrayList<>();
        // Split numeric args from flags (currently only --log).
        for (String arg : args) {
            if (arg.startsWith("--log")) {
                String value = null;
                int idx = arg.indexOf('=');
                if (idx >= 0 && idx + 1 < arg.length()) {
                    value = arg.substring(idx + 1).trim();
                }
                logEnabled = value == null || parseBoolean(value);
                continue;
            }
            if (arg.startsWith("-")) {
                System.out.println("Unknown option: " + arg);
                System.out.println("Usage: java networking.SkipBoServer <port> [players] [bots] [--log]");
                return;
            }
            numericArgs.add(arg);
        }
        if (logEnabled) {
            System.setProperty("skipbo.server.log", "true");
        }
        if (numericArgs.isEmpty()) {
            System.out.println("Usage: java networking.SkipBoServer <port> [players] [bots] [--log]");
            return;
        }
        try {
            port = Integer.parseInt(numericArgs.get(0));
            // Validate optional players and bots.
            if (numericArgs.size() > 1) {
                players = Integer.parseInt(numericArgs.get(1));
                if (players < 2 || players > 6) {
                    System.out.println("Players must be between 2 and 6.");
                    return;
                }
                if (numericArgs.size() > 2) {
                    bots = Integer.parseInt(numericArgs.get(2));
                    if (bots < 0 || bots > players) {
                        System.out.println("Bots must be between 0 and players.");
                        return;
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Port, players, and bots must be numbers.");
            return;
        }

        ServerState state = new ServerState(players, bots);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Skip-Bo server listening on port " + port + ".");
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                // Handle each connection on its own thread.
                ClientHandler handler = new ClientHandler(socket, state);
                new Thread(handler, "client-" + socket.getPort()).start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Parses a boolean flag value for CLI options.
     * @param value raw string value
     * @return true for "true", "1", or "yes" (case-insensitive)
     */
    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }
}
