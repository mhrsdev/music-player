package com.mhrsdev.musicplayer.data.scanner;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides, row by row while the MediaStore cursor streams past, which tracks need writing, and afterwards which stored
 * tracks vanished. Keeps only ids in memory, never the scanned rows.
 */
public final class IncrementalDiff {
    public enum Change { UNCHANGED, ADDED, UPDATED }

    private final Map<String, Long> storedModified;
    private final Set<String> missing;

    /** @param storedModified track id to stored modified time (ms); the map is not modified */
    public IncrementalDiff(Map<String, Long> storedModified) {
        this.storedModified = storedModified;
        this.missing = new HashSet<>(storedModified.keySet());
    }

    public Change visit(String id, long modifiedMs) {
        missing.remove(id);
        Long stored = storedModified.get(id);
        if (stored == null) return Change.ADDED;
        return stored == modifiedMs ? Change.UNCHANGED : Change.UPDATED;
    }

    /** Stored ids never visited: files deleted, moved out of the included folders, or filtered away. */
    public Set<String> missingIds() {
        return missing;
    }
}
