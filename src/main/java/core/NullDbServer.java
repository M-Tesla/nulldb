package core;

import memory.BufferPoolManager;
import network.NioSocketServer;
import network.ProtocolParser;
import recovery.WalManager;
import storage.BTreeIndex;
import storage.DiskManager;

import java.util.logging.Logger;

public class NullDbServer {

    private static final Logger logger = Logger.getLogger(NullDbServer.class.getName());
    private static final int DEFAULT_PORT = 5432;

    public static void main(String[] args) {
        logger.info("Booting Null_Pointer_Engine...");

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Port out of range: " + port);
                }
            } catch (NumberFormatException e) {
                logger.severe("Invalid port argument. Using default port " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
        }

        DiskManager diskManager = new DiskManager("nulldb.db");
        WalManager walManager = new WalManager("nulldb.log");

        BufferPoolManager bufferPool = new BufferPoolManager(diskManager, 1024);

        BackgroundWorker bgWorker = new BackgroundWorker(bufferPool);
        bgWorker.start();
        BTreeIndex index = new BTreeIndex(bufferPool);

        ProtocolParser parser = new ProtocolParser(index, walManager);
        NioSocketServer server = new NioSocketServer(port, parser);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("SIGTERM received from OS. Initiating graceful shutdown...");
            bgWorker.shutDown();
            bufferPool.flushAllPages();
            walManager.shutDown();
            diskManager.shutDown();
            logger.info("System safely halted.");
        }));

        server.start();
    }
}
