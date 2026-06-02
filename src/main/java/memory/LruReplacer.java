package memory;

import java.util.LinkedHashSet;

public class LruReplacer {
    private final LinkedHashSet<Integer> unpinnedFrames;

    public LruReplacer(int capacity) {
        this.unpinnedFrames = new LinkedHashSet<>(capacity);
    }

    public void pin(int frameId) {
        unpinnedFrames.remove(frameId);
    }

    public void unpin(int frameId) {
        unpinnedFrames.add(frameId);
    }

    public Integer evict() {
        if (unpinnedFrames.isEmpty()) {
            return null;
        }
        int victimFrame = unpinnedFrames.getFirst();
        unpinnedFrames.remove(victimFrame);
        return victimFrame;
    }
}