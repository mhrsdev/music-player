package com.mhrsdev.musicplayer.data.lyrics;

import com.mhrsdev.musicplayer.diag.Regexes;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects lyrics that are not lyrics, and strips watermark/credit lines from lyrics that are. Pure Java.
 *
 * <p>Many downloaded MP3s (Persian sites especially) carry only the site name, a download/Telegram advert or the
 * track title in their lyrics frame. Such text must count as "no lyrics from this source" so the resolver moves on to
 * the cache and LRCLIB. Rules:
 * <ul>
 *   <li>watermark lines (URL, domain, {@code @handle}, known site name) are removed wherever they are; advert/credit
 *       lines ("Downloaded from", "\u06A9\u0627\u0646\u0627\u0644", "Lyrics by", "\u0634\u0639\u0631:"...) are removed in the first/last {@link #EDGE} lines;</li>
 *   <li>junk = nothing meaningful left; or fewer than {@link #MIN_LINES} lines left while the text carried a watermark
 *       signal; or every line identical (2+ lines); or the lines are only the artist / title; or no letters at all.</li>
 * </ul>
 * A short real poem without any watermark signal passes.
 */
public class LyricsJunkFilter {
    static final int MIN_LINES = 3;
    static final int EDGE = 4;
    /** Lines longer than this are treated as lyrics even if they mention a keyword. */
    private static final int MAX_WATERMARK_LEN = 90;

    private static final Pattern URL = Regexes.compile(
            "(?i)(https?://|www\\.|\\b[\\p{L}\\p{N}-]+\\.(com|ir|net|org|info|me|io|co|biz|xyz|fm|tv|top|site|online|club|music|app)\\b)");
    private static final Pattern HANDLE = Regexes.compile("(^|[\\s(\\[:])@[\\w.]{3,}");
    private static final String[] SITES = {
        "rozmusic", "melovaz", "nex1", "nexmusic", "ahangify", "radiojavan", "musicfa", "music-fa", "1music",
        "upmusics", "upmusic", "avangmusic", "tak3da", "mybia", "bia2", "golsarmusic", "irmusic", "mihanmusic",
        "navahang", "wikiseda", "ahangestan", "mytehranmusic", "persianmusic", "musicdel", "pmusic", "musicyar",
        "ahangestan", "iranmusic", "taraneh", "musiceiranian", "behmusic", "aparat", "soundcloud.com", "lyricsfa",
        "ritmo", "musicbaran", "melodiha", "ahanghaa", "sevilmusic", "mp3pm", "mp3juice", "zippyshare", "mrjatt",
        "djpunjab", "pagalworld", "songspk", "mymp3song", "freemp3", "musicmp3", "teranemusic", "mahmusic"
    };
    private static final Pattern ADVERT = Regexes.compile("(?i)(downloaded|download|uploaded|ripped|encoded|provided|"
            + "telegram|instagram|whatsapp|facebook|twitter|tiktok|youtube|subscribe|follow us|join us|visit|channel|"
            + "exclusive|free mp3|"
            + "\u06A9\u0627\u0646\u0627\u0644|\u062F\u0627\u0646\u0644\u0648\u062F|\u0644\u06CC\u0646\u06A9|\u0633\u0627\u06CC\u062A|"
            + "\u062A\u0644\u06AF\u0631\u0627\u0645|\u0627\u06CC\u0646\u0633\u062A\u0627\u06AF\u0631\u0627\u0645|"
            + "\u0627\u062E\u062A\u0635\u0627\u0635\u06CC|\u0639\u0636\u0648|\u062C\u0648\u06CC\u0646|\u0645\u0646\u062A\u0634\u0631)");
    /** "Lyrics by X", "Composer: X", "\u0634\u0639\u0631: X", "\u062A\u0631\u0627\u0646\u0647: X", "\u0622\u0647\u0646\u06AF\u0633\u0627\u0632: X", "\u062A\u0646\u0638\u06CC\u0645: X", "\u0645\u062A\u0646 \u0622\u0647\u0646\u06AF X". */
    private static final Pattern CREDIT = Regexes.compile("(?i)^\\s*(lyrics?|words|written|poem|composer|composed|"
            + "music|arrange(d|ment)?|mix(ed)?|master(ed|ing)?|producer|produced|vocals?|"
            + "\u0634\u0639\u0631|\u062A\u0631\u0627\u0646\u0647(\u200C?\u0633\u0631\u0627)?|\u0622\u0647\u0646\u06AF\u0633\u0627\u0632|"
            + "\u062A\u0646\u0638\u06CC\u0645|\u0645\u06CC\u06A9\u0633|\u0645\u0633\u062A\u0631|\u0645\u062A\u0646(\\s+\u0622\u0647\u0646\u06AF)?|"
            + "\u0622\u0647\u0646\u06AF|\u062E\u0648\u0627\u0646\u0646\u062F\u0647)\\s*(by\\b|:|\uFF1A|-|\u0627\u0632\\s)");

    private LyricsJunkFilter() { }

    /**
     * @param artist the track's artist (may be null/unknown)
     * @param title  the track's title (may be null)
     * @return {@code lyrics} without watermark lines, or null when it is junk
     */
    @Nullable
    public static Lyrics clean(@Nullable Lyrics lyrics, @Nullable String artist, @Nullable String title) {
        if (lyrics == null || lyrics.isEmpty()) return lyrics;
        List<LyricLine> in = lyrics.lines();
        boolean signal = false;
        int n = in.size();
        // index range of non-empty lines, for the "edge" rule
        List<Integer> textIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!in.get(i).text().trim().isEmpty()) textIdx.add(i);
        Set<Integer> edge = new HashSet<>();
        for (int k = 0; k < textIdx.size(); k++) {
            if (k < EDGE || k >= textIdx.size() - EDGE) edge.add(textIdx.get(k));
        }
        List<LyricLine> kept = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LyricLine l = in.get(i);
            String t = l.text().trim();
            if (t.isEmpty()) { kept.add(l); continue; }
            if (isWatermark(t)) { signal = true; continue; }
            if (edge.contains(i) && t.length() <= MAX_WATERMARK_LEN && (ADVERT.matcher(t).find() || CREDIT.matcher(t).find())) {
                signal |= ADVERT.matcher(t).find();
                continue;
            }
            kept.add(l);
        }
        List<String> meaningful = new ArrayList<>();
        for (LyricLine l : kept) {
            String t = l.text().trim();
            if (!t.isEmpty() && hasLetter(t)) meaningful.add(t);
        }
        if (meaningful.isEmpty()) return null;
        if (signal && meaningful.size() < MIN_LINES) return null;
        if (meaningful.size() >= 2 && allSame(meaningful)) return null;
        if (onlyMetadata(meaningful, artist, title)) return null;
        while (!kept.isEmpty() && kept.get(0).text().trim().isEmpty()) kept.remove(0);
        while (!kept.isEmpty() && kept.get(kept.size() - 1).text().trim().isEmpty()) kept.remove(kept.size() - 1);
        if (kept.size() == in.size()) return lyrics;
        return new Lyrics(lyrics.trackId(), kept, lyrics.synced(), lyrics.source());
    }

    /** A line that is only a site / URL / handle credit (short, and carries a URL, domain, handle or site name). */
    static boolean isWatermark(String line) {
        if (line.length() > MAX_WATERMARK_LEN) return false;
        if (URL.matcher(line).find() || HANDLE.matcher(line).find()) return true;
        String lower = line.toLowerCase(Locale.ROOT).replace(" ", "");
        for (String s : SITES) if (lower.contains(s)) return true;
        return false;
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    private static boolean allSame(List<String> lines) {
        String first = key(lines.get(0));
        for (String l : lines) if (!key(l).equals(first)) return false;
        return true;
    }

    /** Every remaining line is just the title, the artist, or both. */
    private static boolean onlyMetadata(List<String> lines, @Nullable String artist, @Nullable String title) {
        String a = artist == null ? "" : key(LyricsQueryNormalizer.artist(artist));
        String t = title == null ? "" : key(LyricsQueryNormalizer.title(title));
        if (a.isEmpty() && t.isEmpty()) return false;
        for (String l : lines) {
            String k = key(l);
            boolean meta = !k.isEmpty() && (k.equals(a) || k.equals(t) || k.equals(a + t) || k.equals(t + a));
            if (!meta) return false;
        }
        return true;
    }

    /** Letters and digits only, lower case, Persian letter forms unified. */
    static String key(String s) {
        String n = LyricsQueryNormalizer.persianForms(s).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }
}
