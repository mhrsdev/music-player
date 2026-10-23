package com.mhrsdev.musicplayer.ui.settings;

import androidx.annotation.Nullable;

/**
 * Turns a Storage Access Framework folder pick into the absolute path the scanner's include/exclude lists use.
 * Only the system storage provider ({@code com.android.externalstorage.documents}) maps to real paths: its tree
 * document ids look like {@code primary:Music/Rock} (internal storage) or {@code 1A2B-3C4D:Music} (SD card / USB).
 * Anything else (Drive, Downloads provider, ...) has no file path and returns null.
 */
public class TreePaths {
    public static final String EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";
    private static final String PRIMARY_ROOT = "/storage/emulated/0";

    private TreePaths() { }

    /** {@code treeDocumentId} is {@code DocumentsContract.getTreeDocumentId(uri)}. */
    @Nullable
    public static String toPath(@Nullable String authority, @Nullable String treeDocumentId) {
        if (treeDocumentId == null || treeDocumentId.isEmpty()) return null;
        if (treeDocumentId.startsWith("raw:")) return trim(treeDocumentId.substring(4));
        if (!EXTERNAL_STORAGE_AUTHORITY.equals(authority)) return null;
        int colon = treeDocumentId.indexOf(':');
        if (colon <= 0) return null;
        String volume = treeDocumentId.substring(0, colon);
        String rel = treeDocumentId.substring(colon + 1);
        String root;
        if ("primary".equalsIgnoreCase(volume)) {
            root = PRIMARY_ROOT;
        } else if ("home".equalsIgnoreCase(volume)) {
            root = PRIMARY_ROOT + "/Documents";
        } else if (volume.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}") || volume.matches("[0-9A-Fa-f]{8,16}")) {
            root = "/storage/" + volume;
        } else {
            return null;
        }
        return trim(rel.isEmpty() ? root : root + "/" + rel);
    }

    /** Short label for a path: the last segment, or the whole path for a volume root. */
    public static String label(String path) {
        String p = trim(path);
        int slash = p.lastIndexOf('/');
        String last = slash >= 0 ? p.substring(slash + 1) : p;
        return last.isEmpty() || p.equals(PRIMARY_ROOT) ? p : last;
    }

    private static String trim(String p) {
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
