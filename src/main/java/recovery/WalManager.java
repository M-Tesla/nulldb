package recovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class WalManager {

    private static final Logger logger = Logger.getLogger(WalManager.class.getName());
    private final FileChannel logChannel;
    private final AtomicLong currentLsn;
    private final ByteBuffer walBuffer;

    public WalManager(String logFile) {

        try {
            Path path = Paths.get(logFile);
            this.logChannel = FileChannel.open(path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
            this.currentLsn = new AtomicLong(0);
            this.walBuffer = ByteBuffer.allocateDirect(4096);
            logger.info("WalManager initialized. Target file: " + logFile);
        } catch (IOException e) {
            throw new RuntimeException("System halt: Failed to open WAL file.", e);
        }
    }

    public long append(byte opCode, int pageId, byte[] payload) {
        long lsn = currentLsn.incrementAndGet();

        walBuffer.clear();
        walBuffer.putLong(lsn);
        walBuffer.put(opCode);
        walBuffer.putInt(pageId);
        walBuffer.putInt(payload.length);
        walBuffer.put(payload);
        walBuffer.flip();

        try {
            while (walBuffer.hasRemaining()) {
                int ignored = logChannel.write(walBuffer);
            }
        } catch (IOException e) {
            throw new RuntimeException("WAL Write Error at LSN: " + lsn, e);
        }

        return lsn;
    }

    public void flush() {
        try {
            logChannel.force(true);
            logger.info("WAL flushed to disk successfully.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to fsync WAL.", e);
        }
    }

    public void shutDown() {
        try {
            flush();
            logChannel.close();
            logger.info("WalManager gracefully shut down.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to close WAL channel.", e);
        }
    }
}