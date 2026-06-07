package network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class NioSocketServer {
    private static final Logger logger = Logger.getLogger(NioSocketServer.class.getName());

    private static final int MAX_CONNECTIONS = 256;
    private static final int READ_BUFFER_SIZE = 1024;

    private final int port;
    private final ProtocolParser parser;
    private Selector selector;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public NioSocketServer(int port, ProtocolParser parser) {
        this.port = port;
        this.parser = parser;
    }

    public void start() {
        try {
            selector = Selector.open();
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            serverSocket.configureBlocking(false);
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            logger.info("NullDB Engine started. Listening on TCP port: " + port);

            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    try {
                        if (key.isAcceptable()) {
                            acceptConnection(key);
                        } else if (key.isReadable()) {
                            readAndRespond(key);
                        }
                    } catch (Exception e) {
                        logger.warning("Unhandled error while processing client: " + e.getMessage());
                        closeChannel(key);
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Server catastrophic failure: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        if (activeConnections.get() >= MAX_CONNECTIONS) {
            logger.warning("Connection limit reached (" + MAX_CONNECTIONS + "). Rejecting new client.");
            return;
        }

        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel == null) {
            return;
        }

        clientChannel.configureBlocking(false);
        ByteBuffer clientBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
        clientChannel.register(selector, SelectionKey.OP_READ, clientBuffer);
        activeConnections.incrementAndGet();

        logger.info("New client connection accepted from: " + clientChannel.getRemoteAddress());
    }

    private void readAndRespond(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer clientBuffer = (ByteBuffer) key.attachment();

        if (clientBuffer == null) {
            logger.warning("Client buffer missing. Closing connection.");
            closeChannel(key);
            return;
        }

        clientBuffer.clear();

        int bytesRead;
        try {
            bytesRead = clientChannel.read(clientBuffer);
        } catch (IOException e) {
            logger.warning("Connection reset by peer: " + e.getMessage());
            closeChannel(key);
            return;
        }

        if (bytesRead == -1) {
            closeChannel(key);
            logger.info("Client disconnected.");
            return;
        }

        try {
            ByteBuffer responseBuffer = parser.parseAndExecute(clientBuffer);
            while (responseBuffer.hasRemaining()) {
                clientChannel.write(responseBuffer);
            }
        } catch (Exception e) {
            logger.warning("Failed to parse/execute request: " + e.getMessage());
            try {
                ByteBuffer errorResponse = ProtocolParser.buildStaticErrorResponse("Invalid request");
                while (errorResponse.hasRemaining()) {
                    clientChannel.write(errorResponse);
                }
            } catch (IOException writeEx) {
                logger.warning("Failed to send error response: " + writeEx.getMessage());
            }
        }
    }

    private void closeChannel(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException e) {
            logger.warning("Error closing client channel: " + e.getMessage());
        } finally {
            key.cancel();
            activeConnections.decrementAndGet();
        }
    }
}
