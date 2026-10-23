package com.mhrsdev.musicplayer.data.db.query;

/** Builds the argument for {@code LIKE ? ESCAPE '\'} so user input cannot act as a wildcard. */
public final class LikePattern {
    /** SQL fragment to append after a column name. */
    public static final String CLAUSE = " LIKE ? ESCAPE '\\'";

    /** "%query%" with {@code \}, {@code %}, {@code _} escaped; null for a blank query. */
    public static String contains(String query) {
        String q = query.trim();
        if (q.isEmpty()) return null;
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private LikePattern() { }
}
