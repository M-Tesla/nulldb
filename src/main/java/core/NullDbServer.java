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

    public static void main(String[] args) {
        logger.info("Booting Null_Pointer_Engine...");

        DiskManager diskManager = new DiskManager("nulldb.db");
        WalManager walManager = new WalManager("nulldb.log");

        BufferPoolManager bufferPool = new BufferPoolManager(diskManager, 1024);

        BackgroundWorker bgWorker = new BackgroundWorker(bufferPool);
        bgWorker.start();
        BTreeIndex index = new BTreeIndex(bufferPool);

        ProtocolParser parser = new ProtocolParser(index, walManager);
        NioSocketServer server = new NioSocketServer(5432, parser);

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