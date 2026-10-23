package com.mhrsdev.musicplayer.data.scanner;

import androidx.annotation.Nullable;
import java.util.Set;

/**
 * Include / exclude folder rules from the settings (absolute directory paths). An empty include set means "everything";
 * an exclude always wins. Matching is by directory prefix, so {@code /sdcard/Music} does not match {@code /sdcard/Music2}.
 */
public final class FolderFilter {
    private final Set<String> include;
    private final Set<String> exclude;

    public FolderFilter(Set<String> include, Set<String> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    /** @param filePath absolute path of the audio file; null (unknown) is accepted unless includes are set */
    public boolean accepts(@Nullable String filePath) {
        if (filePath != null) {
            for (String dir : exclude) if (isUnder(filePath, dir)) return false;
        }
        if (include.isEmpty()) return true;
        if (filePath == null) return false;
        for (String dir : include) if (isUnder(filePath, dir)) return true;
        return false;
    }

    private static boolean isUnder(String filePath, String dir) {
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        return filePath.startsWith(prefix);
    }
}
