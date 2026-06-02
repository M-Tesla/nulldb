package core;

import memory.BufferPoolManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BackgroundWorker {

    private static final Logger logger = Logger.getLogger(BackgroundWorker.class.getName());

    private final ScheduledExecutorService scheduler;
    private final BufferPoolManager bufferPool;

    public BackgroundWorker(BufferPoolManager bufferPool) {
        this.bufferPool = bufferPool;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "NullDB-BgWriter");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::performCheckpoint, 30, 30, TimeUnit.SECONDS);
        logger.info("Background Worker started. Automatic Checkpoint interval: 30s.");
    }

    private void performCheckpoint() {
        logger.info("[BACKGROUND] Waking up. Starting automatic Checkpoint...");
        long start = System.currentTimeMillis();

        bufferPool.flushAllPages();

        long latency = System.currentTimeMillis() - start;
        logger.info("[BACKGROUND] Checkpoint finished. Flushed pages in " + latency + "ms. Going back to sleep.");

    }

    public void shutDown() {
        logger.info("Halting Background Worker...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}