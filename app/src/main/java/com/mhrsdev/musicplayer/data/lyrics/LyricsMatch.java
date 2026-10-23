package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Similarity of a search result to the (cleaned) track metadata: Dice coefficient over character bigrams of the
 * normalised strings (robust to spacing, ZWNJ, small spelling differences in Persian/English). Pure Java.
 */
public class LyricsMatch {
    /** Minimum combined score for a free-text search hit. */
    static final double ACCEPT = 0.72;
    /** The title alone must be at least this similar. */
    static final double MIN_TITLE = 0.6;

    private LyricsMatch() { }

    /**
     * @return 0..1; title weighs 0.65 and artist 0.35, or title alone when the track's artist is unknown. A title below
     *     {@link #MIN_TITLE} scores 0.
     */
    public static double score(String title, String artist, @Nullable String candTitle, @Nullable String candArtist) {
        if (candTitle == null) return 0;
        double t = dice(norm(title), norm(LyricsQueryNormalizer.title(candTitle)));
        if (t < MIN_TITLE) return 0;
        String a = norm(artist);
        if (a.isEmpty()) return t;
        double ar = candArtist == null ? 0 : dice(a, norm(LyricsQueryNormalizer.artist(candArtist)));
        return 0.65 * t + 0.35 * ar;
    }

    static String norm(String s) {
        return LyricsJunkFilter.key(s);
    }

    /** Dice coefficient over character bigrams; equal strings = 1, single-char strings compared directly. */
    static double dice(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1;
        if (a.length() < 2 || b.length() < 2) return 0;
        Map<Integer, Integer> grams = new HashMap<>();
        for (int i = 0; i + 1 < a.length(); i++) grams.merge(a.charAt(i) << 16 | a.charAt(i + 1), 1, Integer::sum);
        int common = 0;
        for (int i = 0; i + 1 < b.length(); i++) {
            int g = b.charAt(i) << 16 | b.charAt(i + 1);
            Integer n = grams.get(g);
            if (n != null && n > 0) {
                common++;
                grams.put(g, n - 1);
            }
        }
        return 2.0 * common / (a.length() - 1 + b.length() - 1);
    }
}
