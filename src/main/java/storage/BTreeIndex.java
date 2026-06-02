package storage;

import memory.BufferPoolManager;
import memory.Page;

import java.util.logging.Logger;

public class BTreeIndex {

    private static final Logger logger = Logger.getLogger(BTreeIndex.class.getName());

    private static final int PAGE_HEADER_SIZE = 64;
    private static final int MAX_TUPLES_PER_PAGE = (Page.PAGE_SIZE - PAGE_HEADER_SIZE) / Tuple.TUPLE_SIZE;

    private final BufferPoolManager bufferPool;
    private final int rootPageId;

    public BTreeIndex(BufferPoolManager bufferPool) {
        this.bufferPool = bufferPool;
        this.rootPageId = 0;
        logger.info("BTreeIndex initialized. Max tuples per leaf page: " + MAX_TUPLES_PER_PAGE);
    }

    public boolean insert(Tuple tuple) {
        Page targetPage = bufferPool.fetchPage(rootPageId);

        try {
            targetPage.getData().position(0);

            int currentRecords = targetPage.getData().getInt();

            if (currentRecords >= MAX_TUPLES_PER_PAGE) {
                logger.warning("B-Tree Leaf Node is full. Split required (Out of scope for POC).");
                return false;
            }

            int insertOffset = PAGE_HEADER_SIZE + (currentRecords * Tuple.TUPLE_SIZE);

            tuple.serialize(targetPage.getData(), insertOffset);

            targetPage.getData().position(0);
            targetPage.getData().putInt(currentRecords + 1);

            logger.info("Inserted Tuple ID: " + tuple.getId() + " at offset: " + insertOffset);
            return true;

        } finally {
            bufferPool.unpinPage(rootPageId, true);
        }

    }
    public boolean delete(long targetId) {
        Page targetPage = bufferPool.fetchPage(rootPageId);

        try {
            targetPage.getData().position(0);
            int currentRecords = targetPage.getData().getInt();

            Tuple tempTuple = new Tuple();

            for (int i = 0; i < currentRecords; i++) {
                int readOffset = PAGE_HEADER_SIZE + (i * Tuple.TUPLE_SIZE);
                tempTuple.deserialize(targetPage.getData(), readOffset);

                if (tempTuple.getId() == targetId) {
                    targetPage.getData().position(readOffset);
                    targetPage.getData().putLong(-1L);

                    logger.info("Tombstone placed for ID: " + targetId + " at offset: " + readOffset);
                    return true;
                }
            }

            logger.info("Delete failed: ID " + targetId + " not found.");
            return false;

        } finally {
            bufferPool.unpinPage(rootPageId, true);
        }
    }

    public boolean update(long targetId, String newPayloadStr) {
        Page targetPage = bufferPool.fetchPage(rootPageId);

        try {
            targetPage.getData().position(0);
            int currentRecords = targetPage.getData().getInt();

            Tuple tempTuple = new Tuple();

            for (int i = 0; i < currentRecords; i++) {
                int readOffset = PAGE_HEADER_SIZE + (i * Tuple.TUPLE_SIZE);
                tempTuple.deserialize(targetPage.getData(), readOffset);

                if (tempTuple.getId() == targetId) {
                    long newTimestamp = System.currentTimeMillis();
                    Tuple updatedTuple = new Tuple(targetId, newTimestamp, newPayloadStr);

                    updatedTuple.serialize(targetPage.getData(), readOffset);

                    logger.info("In-Place Update executed for ID: " + targetId + " at offset: " + readOffset);
                    return true;
                }
            }

            logger.warning("Update failed: ID " + targetId + " not found.");
            return false;

        } finally {
            bufferPool.unpinPage(rootPageId, true);
        }
    }

    public String sequentialScan() {
        Page targetPage = bufferPool.fetchPage(rootPageId);
        StringBuilder result = new StringBuilder();
        int activeRecords = 0;

        try {
            targetPage.getData().position(0);
            int currentRecords = targetPage.getData().getInt();
            Tuple tempTuple = new Tuple();

            for (int i = 0; i < currentRecords; i++) {
                int readOffset = PAGE_HEADER_SIZE + (i * Tuple.TUPLE_SIZE);
                tempTuple.deserialize(targetPage.getData(), readOffset);

                if (tempTuple.getId() != -1L) {
                    result.append("\n  [ID: ").append(tempTuple.getId()).append("] => ")
                            .append(tempTuple.getPayloadAsString());
                    activeRecords++;
                }
            }

            if (activeRecords == 0) {
                return "TABLE IS EMPTY";
            }

            return "Found " + activeRecords + " active records:" + result.toString();

        } finally {
            bufferPool.unpinPage(rootPageId, false);
        }
    }

    public Tuple pointQuery(long searchId) {
        Page targetPage = bufferPool.fetchPage(rootPageId);

        try {
            targetPage.getData().position(0);
            int currentRecords = targetPage.getData().getInt();

            Tuple tempTuple = new Tuple();

            for (int i = 0; i < currentRecords; i++) {
                int readOffset = PAGE_HEADER_SIZE + (i * Tuple.TUPLE_SIZE);
                tempTuple.deserialize(targetPage.getData(), readOffset);

                if (tempTuple.getId() == searchId) {
                    logger.info("Point Query HIT for ID: " + searchId);
                    return tempTuple;
                }
            }

            logger.info("Point Query MISS for ID: " + searchId);
            return null;

        } finally {
            bufferPool.unpinPage(rootPageId, false);
        }
    }
}