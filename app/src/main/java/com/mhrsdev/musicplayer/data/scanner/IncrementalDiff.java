package com.mhrsdev.musicplayer.data.scanner;

import com.mhrsdev.musicplayer.data.db.query.TrackQueries.Stored;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Decides, row by row while the MediaStore cursor streams past, which tracks need writing, and afterwards which stored
 * tracks vanished. A file counts as changed when its modified time, size or path differs (a re-download or a move can
 * keep the timestamp). Keeps only ids in memory, never the scanned rows.
 */
public final class IncrementalDiff {
    public enum Change { UNCHANGED, ADDED, UPDATED }

    private final Map<String, Stored> stored;
    private final Set<String> missing;

    /** @param stored track id to what the last scan stored; the map is not modified */
    public IncrementalDiff(Map<String, Stored> stored) {
        this.stored = stored;
        this.missing = new HashSet<>(stored.keySet());
    }

    public Change visit(String id, long modifiedMs, long sizeBytes, String path) {
        missing.remove(id);
        Stored old = stored.get(id);
        if (old == null) return Change.ADDED;
        boolean same = old.modifiedMs() == modifiedMs && old.sizeBytes() == sizeBytes && Objects.equals(old.path(), path);
        return same ? Change.UNCHANGED : Change.UPDATED;
    }

    /** Stored ids never visited: files deleted, moved out of the included folders, or filtered away. */
    public Set<String> missingIds() {
        return missing;
    }
}
