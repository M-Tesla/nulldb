package storage;

import memory.Page;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class DiskManager {

    private static final Logger logger = Logger.getLogger(DiskManager.class.getName());
    private final FileChannel fileChannel;
    private final AtomicInteger nextPageId;

    public DiskManager(String dbFile) {
        try {
            Path path = Paths.get(dbFile);
            this.fileChannel = FileChannel.open(path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            this.nextPageId = new AtomicInteger((int) (fileChannel.size() / Page.PAGE_SIZE));
            logger.info("DiskManager initialized. Target file: " + dbFile);
        } catch (IOException e) {
            throw new RuntimeException("System halt: Failed to open DB file.", e);
        }
    }

    public void readPage(int pageId, Page page) {
        long offset = (long) pageId * Page.PAGE_SIZE;
        try {
            page.getData().clear();
            int bytesRead = fileChannel.read(page.getData(), offset);

            if (bytesRead == -1) {
                page.getData().clear();
            } else {
                page.getData().flip();
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O Error: Failed to read page " + pageId, e);
        }
    }

    public void writePage(Page page) {
        long offset = (long) page.getPageId() * Page.PAGE_SIZE;
        try {
            page.getData().rewind();
            int ignore = fileChannel.write(page.getData(), offset);
            fileChannel.force(false);
        } catch (IOException e) {
            throw new RuntimeException("I/O Error: Failed to write page " + page.getPageId(), e);
        }
    }

    public int allocatePage() {
        int newPageId = nextPageId.getAndIncrement();
        logger.info("Allocated new physical page with ID: " + newPageId);
        return newPageId;
    }

    public void shutDown() {
        try {
            fileChannel.close();
            logger.info("DiskManager gracefully shut down.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to close file channel.", e);
        }
    }
}