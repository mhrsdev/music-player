package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cleans tag metadata before it is sent to LRCLIB (pure Java). Downloaded files carry site watermarks in their tags
 * ({@code "Reza Shiri @RozMusic.com"}, {@code "Ghalbam - Nex1Music"}, {@code "01 - Song [Free Download]"}), which
 * make exact lookups fail. Also unifies Arabic/Persian letter forms (\u064A \u0643 \u2192 \u06CC \u06A9), removes bidi marks and turns ZWNJ
 * and underscores into spaces so the same song always gets the same query and cache key.
 */
public class LyricsQueryNormalizer {
    private static final Pattern HANDLE = Pattern.compile("@\\S+");
    private static final Pattern URL = Pattern.compile(
            "(?i)(https?://\\S+|www\\.\\S+|\\b[\\p{L}\\p{N}-]+\\.(com|ir|net|org|info|me|io|co|biz|xyz|fm|tv|top|site|online|club|music)\\b)");
    private static final Pattern BRACKETS = Pattern.compile("\\([^()]*\\)|\\[[^\\[\\]]*]|\\{[^{}]*}|\u3010[^\u3011]*\u3011");
    private static final Pattern FEAT = Pattern.compile(
            "(?i)\\s+(feat\\.?|ft\\.?|featuring|\u0628\u0627\\s+\u062D\u0636\u0648\u0631|\u0641\u062A)\\s+.*$");
    /** Artists only: "A x B" collaborations keep A. */
    private static final Pattern FEAT_ARTIST = Pattern.compile("(?i)\\s+(x|\u00D7)\\s+.*$");
    private static final Pattern TRACK_NO = Pattern.compile("^\\s*\\d{1,3}\\s*[-._)\\]]+\\s*");
    private static final Pattern SPACES = Pattern.compile("[\\s\u00A0]+");
    private static final Pattern EDGE_PUNCT = Pattern.compile("^[\\s\\-\u2013\u2014|:,.~_*]+|[\\s\\-\u2013\u2014|:,.~_*]+$");

    private LyricsQueryNormalizer() { }

    /** Arabic yeh/kaf \u2192 Persian forms, Arabic-Indic digits kept, bidi marks dropped, ZWNJ \u2192 space. */
    public static String persianForms(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u064A', '\u0649' -> b.append('\u06CC');
                case '\u0643' -> b.append('\u06A9');
                case '\u200C', '_' -> b.append(' ');
                case '\u200B', '\u200E', '\u200F', '\uFEFF', '\u061C', '\u202A', '\u202B', '\u202C', '\u202D',
                        '\u202E', '\u2066', '\u2067', '\u2068', '\u2069', '\u0640' -> { } // marks, tatweel
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /** Clean artist: no handle/URL/site suffix, no "feat." part, first-listed artist kept with its co-artists. */
    public static String artist(@Nullable String raw) {
        String s = base(raw);
        s = FEAT.matcher(s).replaceFirst("");
        s = FEAT_ARTIST.matcher(s).replaceFirst("");
        return tidy(s);
    }

    /** Clean title: also drops bracket tags ("(Official Video)", "[Free Download]") and a leading track number. */
    public static String title(@Nullable String raw) {
        String s = base(raw);
        s = TRACK_NO.matcher(s).replaceFirst("");
        String noBrackets = tidy(BRACKETS.matcher(s).replaceAll(" "));
        if (!noBrackets.isEmpty()) s = noBrackets; // a title that is only "(Intro)" keeps its text
        s = FEAT.matcher(s).replaceFirst("");
        return tidy(s);
    }

    public static String album(@Nullable String raw) {
        String s = base(raw);
        String noBrackets = tidy(BRACKETS.matcher(s).replaceAll(" "));
        return noBrackets.isEmpty() ? tidy(s) : noBrackets;
    }

    /** Lower-case comparison/cache form of a cleaned value. */
    public static String key(String cleaned) {
        return SPACES.matcher(cleaned.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private static String base(@Nullable String raw) {
        if (raw == null) return "";
        String s = persianForms(raw).trim();
        if (s.equalsIgnoreCase("<unknown>")) return "";
        s = HANDLE.matcher(s).replaceAll(" ");
        s = URL.matcher(s).replaceAll(" ");
        s = dropSiteSuffix(s);
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    /** "Ghalbam - RozMusic" / "Song | Nex1Music" \u2192 "Ghalbam" / "Song" (only when that part is a site). */
    private static String dropSiteSuffix(String s) {
        String[] seps = {" - ", " | ", " \u2013 ", " \u2014 ", " _ "};
        for (String sep : seps) {
            int at = s.lastIndexOf(sep);
            if (at <= 0) continue;
            String tail = s.substring(at + sep.length());
            if (LyricsJunkFilter.isWatermark(tail) || tail.trim().isEmpty()) return s.substring(0, at);
        }
        return s;
    }

    private static String tidy(String s) {
        String t = SPACES.matcher(s).replaceAll(" ").trim();
        return EDGE_PUNCT.matcher(t).replaceAll("").trim();
    }
}
