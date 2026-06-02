package network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.logging.Logger;

public class NioSocketServer {
    private static final Logger logger = Logger.getLogger(NioSocketServer.class.getName());

    private final int port;
    private final ProtocolParser parser;
    private Selector selector;
    private final ByteBuffer networkBuffer;

    public NioSocketServer(int port, ProtocolParser parser) {
        this.port = port;
        this.parser = parser;
        this.networkBuffer = ByteBuffer.allocateDirect(1024);
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

                    if (key.isAcceptable()) {
                        acceptConnection(key);
                    } else if (key.isReadable()) {
                        readAndRespond(key);
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Server catastrophic failure: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        logger.info("New client connection accepted from: " + clientChannel.getRemoteAddress());
    }

    private void readAndRespond(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        networkBuffer.clear();

        int bytesRead = -1;
        try {
            bytesRead = clientChannel.read(networkBuffer);
        } catch (IOException e) {
            logger.warning("Connection reset by peer.");
        }

        if (bytesRead == -1) {
            clientChannel.close();
            key.cancel();
            logger.info("Client disconnected.");
            return;
        }

        ByteBuffer responseBuffer = parser.parseAndExecute(networkBuffer);

        while (responseBuffer.hasRemaining()) {
            clientChannel.write(responseBuffer);
        }
    }
}