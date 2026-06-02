package recovery;

public class LogRecord {

    private final long lsn;
    private final byte opCode;
    private final int pageId;
    private final byte[] payload;

    public LogRecord(long lsn, byte opCode, int pageId, byte[] payload) {
        this.lsn = lsn;
        this.opCode = opCode;
        this.pageId = pageId;
        this.payload = payload;
    }

    public long getLsn() {
        return lsn;
    }

    public byte getOpCode() {
        return opCode;
    }

    public int getPageId() {
        return pageId;
    }

    public byte[] getPayload() {
        return payload;
    }
}