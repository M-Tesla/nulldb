package memory;

import java.nio.ByteBuffer;

public class Page {

    public static final int PAGE_SIZE = 4096;

    private int pageId;
    private final ByteBuffer data;
    private boolean isDirty;

    public Page(int pageId) {
        this.pageId = pageId;
        this.data = ByteBuffer.allocateDirect(PAGE_SIZE);
        this.isDirty = false;
    }

    public int getPageId() {
        return pageId;
    }

    public ByteBuffer getData() {
        return data;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public void setPageId(int pageId) {
        this.pageId = pageId;
    }
}