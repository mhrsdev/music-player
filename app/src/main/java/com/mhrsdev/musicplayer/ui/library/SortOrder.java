package com.mhrsdev.musicplayer.ui.library;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How a track list is ordered. Pure Java (unit-tested); sorting runs off the main thread. */
public record SortOrder(Field field, boolean ascending) {

    public enum Field { DEFAULT, TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION, MOST_PLAYED }

    public static final SortOrder DEFAULT = new SortOrder(Field.DEFAULT, true);

    /** True when rows are grouped by a text key, so the fast scroller can show letters. */
    public boolean alphabetical() {
        return field == Field.TITLE || field == Field.ARTIST || field == Field.ALBUM;
    }

    /** The text the list is sorted by (null for non-text orders). */
    @Nullable public static String key(Field field, Track t) {
        return switch (field) {
            case TITLE -> t.title();
            case ARTIST -> t.artistName();
            case ALBUM -> t.albumTitle();
            default -> null;
        };
    }

    /**
     * Returns a sorted copy ({@code source} itself for DEFAULT). Text orders use precomputed collation keys
     * (one allocation per row, then cheap compares) so 10k rows sort in a few tens of ms.
     */
    public List<Track> apply(List<Track> source, @Nullable Map<String, Integer> playCounts, Locale locale) {
        if (field == Field.DEFAULT) {
            if (ascending) return source;
            List<Track> reversed = new ArrayList<>(source);
            Collections.reverse(reversed);
            return reversed;
        }
        int n = source.size();
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Comparator<Integer> cmp;
        if (alphabetical()) {
            Collator collator = Collator.getInstance(locale);
            collator.setStrength(Collator.PRIMARY);
            CollationKey[] keys = new CollationKey[n];
            for (int i = 0; i < n; i++) keys[i] = collator.getCollationKey(key(field, source.get(i)));
            cmp = (a, b) -> keys[a].compareTo(keys[b]);
        } else if (field == Field.DATE_ADDED) {
            cmp = Comparator.comparingLong(i -> source.get(i).addedAtMs());
        } else if (field == Field.DURATION) {
            cmp = Comparator.comparingLong(i -> source.get(i).durationMs());
        } else {
            Map<String, Integer> counts = playCounts == null ? Collections.emptyMap() : playCounts;
            cmp = Comparator.comparingInt(i -> counts.getOrDefault(source.get(i).id(), 0));
        }
        if (!ascending) cmp = cmp.reversed();
        java.util.Arrays.sort(idx, cmp.thenComparingInt(i -> i)); // stable
        List<Track> out = new ArrayList<>(n);
        for (Integer i : idx) out.add(source.get(i));
        return out;
    }

    /** Section label for the fast scroller: first letter (upper-cased), "#" for digits / symbols. */
    public static String sectionOf(@Nullable String text, Locale locale) {
        if (text == null || text.isEmpty()) return "#";
        int cp = text.codePointAt(0);
        if (!Character.isLetter(cp)) return "#";
        return new String(Character.toChars(cp)).toUpperCase(locale);
    }

    String encode() {
        return field.name() + ":" + (ascending ? "asc" : "desc");
    }

    static SortOrder decode(@Nullable String s) {
        if (s == null) return DEFAULT;
        int colon = s.indexOf(':');
        try {
            return new SortOrder(Field.valueOf(s.substring(0, colon)), s.endsWith("asc"));
        } catch (RuntimeException bad) {
            return DEFAULT;
        }
    }
}
