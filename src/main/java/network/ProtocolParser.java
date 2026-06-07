package network;

import recovery.WalManager;
import storage.BTreeIndex;
import storage.Tuple;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class ProtocolParser {

    private static final Logger logger = Logger.getLogger(ProtocolParser.class.getName());
    private static final String INVALID_REQUEST = "Invalid request";

    private final BTreeIndex index;
    private final WalManager walManager;

    public ProtocolParser(BTreeIndex index, WalManager walManager) {
        this.index = index;
        this.walManager = walManager;
    }

    public ByteBuffer parseAndExecute(ByteBuffer requestBuffer) {
        requestBuffer.flip();

        if (!requestBuffer.hasRemaining()) {
            return buildResponse((byte) 0x00, "Empty payload");
        }

        byte opCode = requestBuffer.get();

        try {
            if (opCode == OpCode.INSERT) {
                return handleInsert(requestBuffer);
            } else if (opCode == OpCode.SELECT) {
                return handleSelect(requestBuffer);
            } else if (opCode == OpCode.DELETE) {
                return handleDelete(requestBuffer);
            } else if (opCode == OpCode.UPDATE) {
                return handleUpdate(requestBuffer);
            } else if (opCode == OpCode.SELECT_ALL) {
                return handleSelectAll();
            } else {
                logger.warning("Unknown OpCode received: " + opCode);
                return buildResponse((byte) 0x00, "Unknown command");
            }
        } catch (Exception e) {
            logger.warning("Failed to process opcode " + opCode + ": " + e.getMessage());
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }
    }

    private ByteBuffer handleInsert(ByteBuffer buffer) {
        if (buffer.remaining() < Long.BYTES) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        long id = buffer.getLong();
        if (id <= 0) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        byte[] payloadBytes = new byte[Tuple.PAYLOAD_SIZE];
        int bytesToRead = Math.min(buffer.remaining(), Tuple.PAYLOAD_SIZE);
        buffer.get(payloadBytes, 0, bytesToRead);

        if (index.pointQuery(id) != null) {
            logger.warning("PK Constraint Violation. ID " + id + " already exists.");
            return buildResponse((byte) 0x00, "ERROR_DUPLICATE_KEY");
        }

        long timestamp = System.currentTimeMillis();
        Tuple newTuple = new Tuple(id, timestamp, new String(payloadBytes).trim());

        walManager.append(OpCode.INSERT, 0, payloadBytes);
        walManager.flush();

        boolean success = index.insert(newTuple);

        if (success) {
            logger.info("INSERT executed successfully for ID: " + id);
            return buildResponse((byte) 0x01, "ACK");
        } else {
            return buildResponse((byte) 0x00, "ERROR_PAGE_FULL");
        }
    }

    private ByteBuffer handleSelect(ByteBuffer buffer) {
        if (buffer.remaining() < Long.BYTES) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        long id = buffer.getLong();
        if (id <= 0) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        Tuple result = index.pointQuery(id);

        if (result != null) {
            logger.info("SELECT executed successfully for ID: " + id);
            return buildResponse((byte) 0x01, result.getPayloadAsString());
        } else {
            return buildResponse((byte) 0x00, "NOT_FOUND");
        }
    }

    private ByteBuffer handleDelete(ByteBuffer buffer) {
        if (buffer.remaining() < Long.BYTES) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        long id = buffer.getLong();
        if (id <= 0) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        byte[] emptyPayload = new byte[Tuple.PAYLOAD_SIZE];
        walManager.append(OpCode.DELETE, 0, emptyPayload);
        walManager.flush();

        boolean success = index.delete(id);

        if (success) {
            logger.info("DELETE executed successfully for ID: " + id);
            return buildResponse((byte) 0x01, "ACK");
        } else {
            return buildResponse((byte) 0x00, "ERROR_NOT_FOUND");
        }
    }

    private ByteBuffer handleUpdate(ByteBuffer buffer) {
        if (buffer.remaining() < Long.BYTES) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        long id = buffer.getLong();
        if (id <= 0) {
            return buildResponse((byte) 0x00, INVALID_REQUEST);
        }

        byte[] payloadBytes = new byte[Tuple.PAYLOAD_SIZE];
        int bytesToRead = Math.min(buffer.remaining(), Tuple.PAYLOAD_SIZE);
        buffer.get(payloadBytes, 0, bytesToRead);

        walManager.append(OpCode.UPDATE, 0, payloadBytes);
        walManager.flush();

        boolean success = index.update(id, new String(payloadBytes).trim());

        if (success) {
            logger.info("UPDATE executed successfully for ID: " + id);
            return buildResponse((byte) 0x01, "ACK");
        } else {
            return buildResponse((byte) 0x00, "ERROR_NOT_FOUND");
        }
    }

    private ByteBuffer handleSelectAll() {
        logger.info("Executing Sequential Scan (SELECT_ALL)...");
        String allData = index.sequentialScan();
        return buildResponse((byte) 0x01, allData);
    }

    private ByteBuffer buildResponse(byte status, String message) {
        byte[] msgBytes = message.getBytes();
        ByteBuffer response = ByteBuffer.allocateDirect(1 + msgBytes.length);
        response.put(status);
        response.put(msgBytes);
        response.flip();
        return response;
    }

    public static ByteBuffer buildStaticErrorResponse(String message) {
        byte[] msgBytes = message.getBytes();
        ByteBuffer response = ByteBuffer.allocateDirect(1 + msgBytes.length);
        response.put((byte) 0x00);
        response.put(msgBytes);
        response.flip();
        return response;
    }
}
