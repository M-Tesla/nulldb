package memory;

import storage.DiskManager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;

public class BufferPoolManager {

    private static final Logger logger = Logger.getLogger(BufferPoolManager.class.getName());
    private final Page[] pool;

    private final DiskManager diskManager;
    private final LruReplacer replacer;
    private final Map<Integer, Integer> pageTable;
    private final Queue<Integer> freeFrames;

    public BufferPoolManager(DiskManager diskManager, int poolSize) {
        this.diskManager = diskManager;
        this.pool = new Page[poolSize];
        this.replacer = new LruReplacer(poolSize);
        this.pageTable = new HashMap<>();
        this.freeFrames = new LinkedList<>();

        for (int i = 0; i < poolSize; i++) {
            freeFrames.add(i);
            pool[i] = new Page(-1);
        }
    }

    public Page fetchPage(int pageId) {
        if (pageTable.containsKey(pageId)) {
            int frameId = pageTable.get(pageId);
            replacer.pin(frameId);
            return pool[frameId];
        }

        logger.info("[INFO] BufferPoolManager: Page cache miss for page_id=" + pageId + ". Fetching from disk.");

        int frameId = getAvailableFrame();
        Page targetPage = pool[frameId];

        if (targetPage.getPageId() != -1) {
            logger.info("[INFO] LruReplacer: Evicting page_id=" + targetPage.getPageId() + " to disk to free capacity.");
            if (targetPage.isDirty()) {
                diskManager.writePage(targetPage);
                targetPage.setDirty(false);
            }
            pageTable.remove(targetPage.getPageId());
        }

        targetPage.setPageId(pageId);
        diskManager.readPage(pageId, targetPage);

        pageTable.put(pageId, frameId);
        replacer.pin(frameId);

        return targetPage;
    }

    public void unpinPage(int pageId, boolean isDirty) {
        if (!pageTable.containsKey(pageId)) {
            return;
        }
        int frameId = pageTable.get(pageId);
        if (isDirty) {
            pool[frameId].setDirty(true);
        }
        replacer.unpin(frameId);
    }

    private int getAvailableFrame() {
        if (!freeFrames.isEmpty()) {
            return freeFrames.poll();
        }
        Integer evictedFrame = replacer.evict();
        if (evictedFrame == null) {
            throw new RuntimeException("System halt: Buffer pool capacity exhausted. No unpinned frames available.");
        }
        return evictedFrame;
    }

    public void flushAllPages() {
        logger.info("Flushing all dirty pages to disk checkpoint...");
        for (Page page : pool) {
            if (page.getPageId() != -1 && page.isDirty()) {
                diskManager.writePage(page);
                page.setDirty(false);
            }
        }
    }
}
