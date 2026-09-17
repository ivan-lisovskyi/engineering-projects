package networking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;


/**
 * TCP client connection wrapper for line-based messaging.
 * This class manages Socket connected to a remote host and exposes:
 *  send(String) for sending a single text line to the server
 *  readLine()} for reading a single text line from the server
 * Messages are transmitted as lines of text.
 * readLine()}blocks until a full line is available or the connection is closed.
 * The class implements Closeable so it can be used with try-with-resources.
 */

public class ClientConnection implements Closeable {

    // Underlying TCP socket connected to the server.
    private final Socket socket;
    //Reader for text coming from the server.
    private final BufferedReader reader;
    // Writer for text sent to the server.
    private final PrintWriter writer;


    /**
     * Connects to the given host and port and initializes the text streams.
     * @param host the server host name or IP address
     * @param port the server port
     * @throws IOException if the connection cannot be established or streams cannot be created
     */
    public ClientConnection(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        // Reduces delay for small messages.
        this.socket.setTcpNoDelay(true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send text lines to the server autoFlush=true flushes on println()
        this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    /**
     * Sends a single line message to the server.
     * If message is null, the method does nothing.
     * @param message the message to send (sent as one line)
     */

    public void send(String message) {
        if (message == null) {
            return;
        }
        // Synchronize so multiple threads cannot interleave characters on the same writer.
        synchronized (writer) {
            writer.println(message);
            writer.flush();
        }
    }

    /**
     * Reads a single line message from the server.
     * This method blocks until a line is available. If the connection is closed, it returns null.
     * @return the next line from the server, or null if end-of-stream is reached
     * @throws IOException if an I/O error occurs while reading
     */

    public String readLine() throws IOException {
        return reader.readLine();
    }

    /**
     * Closes the underlying socket and releases all resources.
     * Closing the socket also closes its input and output streams.
     * @throws IOException if closing the socket fails
     */

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
