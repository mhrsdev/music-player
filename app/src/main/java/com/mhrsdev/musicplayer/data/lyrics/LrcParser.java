package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.LyricWord;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tolerant LRC parser (pure Java, no Android types).
 *
 * <p>Handles {@code [mm:ss.xx]}, {@code [mm:ss.xxx]}, {@code [mm:ss]}, {@code [mm:ss:xx]}, {@code [h:mm:ss.xx]}, several
 * timestamps on one line, {@code [offset:+/-ms]} (positive = lyrics appear earlier), header tags
 * ({@code ti ar al au by length re ve tool lang}), Persian/Arabic-Indic digits in timestamps, invisible bidi marks,
 * BOM, LF/CRLF/CR.
 *
 * <p>Word timing: enhanced LRC / A2 {@code [00:12.00]<00:12.00>Hel<00:12.30>lo <00:12.80>world<00:13.40>} (the first
 * unit may omit its tag: it then starts at the line time), the inline-bracket variant
 * {@code [00:12.00]Hello [00:12.80]world}, and Walaoke voice prefixes ({@code M: F: D:}, removed). A tag right after a
 * unit is its end; a last unit without one ends at the next line (at most {@link #OPEN_END_MS} later).
 * Output is sorted by time (stable, so equal timestamps keep file order; exact duplicates are dropped).
 * Without any timestamp the text becomes unsynced lines.
 */
public class LrcParser {
    private static final Pattern SPACE_RUNS = Pattern.compile("[ \\t]{2,}");
    private static final Pattern WORD_TAG = Pattern.compile("<\\s*\\d+\\s*:\\s*\\d+(?:\\s*[.:,]\\s*\\d+)?\\s*>");
    private static final Pattern SHORT_TS = Pattern.compile("(\\d{1,5}):(\\d{1,2})(?:[.:,](\\d{1,3}))?");
    private static final Pattern LONG_TS = Pattern.compile("(\\d{1,4}):(\\d{1,2}):(\\d{1,2})[.,](\\d{1,3})");
    /** A word tag inside line text: {@code <ts>} or (after the line's own timestamps) {@code [ts]}. */
    private static final Pattern INLINE_TAG = Pattern.compile("<([^<>]{3,16})>|\\[([^\\[\\]]{3,16})]");
    private static final Pattern WALAOKE = Pattern.compile("^(?:[MFD]|v\\d)\\s*:\\s+");
    static final long OPEN_END_MS = 3_000;
    private static final Pattern META = Pattern.compile("([A-Za-z]+)\\s*:(.*)", Pattern.DOTALL);
    private static final Set<String> META_KEYS = new HashSet<>(
            Arrays.asList("ti", "ar", "al", "au", "by", "offset", "length", "re", "ve", "tool", "lang"));

    private LrcParser() { }

    /** @return the parsed lyrics, or null when the text holds no lyric line at all */
    @Nullable
    public static ParsedLrc parse(@Nullable String raw) {
        if (raw == null) return null;
        List<LyricLine> timed = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        Map<String, String> meta = new LinkedHashMap<>();

        for (String rawLine : raw.split("\r\n|\n|\r", -1)) {
            int i = skipInvisible(rawLine, 0);
            int start = i;
            List<Long> times = new ArrayList<>();
            boolean isMeta = false;
            while (i < rawLine.length() && rawLine.charAt(i) == '[') {
                int close = rawLine.indexOf(']', i);
                if (close < 0) break;
                String inner = rawLine.substring(i + 1, close);
                Long ts = parseTimestamp(inner);
                if (ts != null) {
                    times.add(ts);
                    i = skipInvisible(rawLine, close + 1);
                    continue;
                }
                Matcher m = times.isEmpty() ? META.matcher(inner) : null;
                if (m != null && m.matches() && META_KEYS.contains(m.group(1).toLowerCase(Locale.ROOT))) {
                    meta.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2).trim());
                    isMeta = true;
                }
                break;
            }
            if (isMeta) continue;
            if (times.isEmpty()) {
                plain.add(clean(rawLine.substring(start)));
            } else {
                LyricLine first = wordLine(rawLine.substring(i), times.get(0));
                for (long t : times) timed.add(shift(first, t, t - times.get(0)));
            }
        }

        long offset = parseOffset(meta.get("offset"));
        if (!timed.isEmpty()) {
            List<LyricLine> lines = syncedLines(timed, offset);
            if (!hasText(lines)) return null;
            // "[00:00.00]" on every line carries no timing: treat as plain text.
            boolean allSame = lines.size() > 1 && lines.stream().allMatch(l -> l.timeMs() == lines.get(0).timeMs());
            if (!allSame) return new ParsedLrc(lines, true, meta, offset);
            plain.clear();
            for (LyricLine l : lines) plain.add(l.text());
        }
        return plainLines(plain, meta, offset);
    }

    /** Parses {@code raw} and wraps it as {@link Lyrics}; null if empty. */
    @Nullable
    public static Lyrics toLyrics(
            String trackId, @Nullable String raw, @Nullable String source) {
        ParsedLrc p = parse(raw);
        return p == null ? null : p.toLyrics(trackId, source);
    }

    private static List<LyricLine> syncedLines(List<LyricLine> timed, long offset) {
        List<LyricLine> shifted = new ArrayList<>(timed.size());
        for (LyricLine l : timed) shifted.add(shift(l, Math.max(0, l.timeMs() - offset), -offset));
        shifted.sort(Comparator.comparingLong(LyricLine::timeMs)); // stable
        closeOpenEnds(shifted);
        List<LyricLine> out = new ArrayList<>(shifted.size());
        Set<LyricLine> seenAtTime = new HashSet<>();
        long seenTime = -1;
        for (LyricLine l : shifted) {
            if (l.timeMs() != seenTime) {
                seenAtTime.clear();
                seenTime = l.timeMs();
            }
            if (!seenAtTime.add(l)) continue; // exact duplicate
            LyricLine prev = out.isEmpty() ? null : out.get(out.size() - 1);
            if (l.text().isEmpty() && prev != null && prev.text().isEmpty()) continue; // collapse blank runs
            out.add(l);
        }
        return out;
    }

    /**
     * Parses the text after the line timestamps into clean text plus word units; words stay null without any word tag.
     * Unknown ends are -1 here and fixed by {@link #closeOpenEnds}.
     */
    static LyricLine wordLine(String rest, long lineTime) {
        Matcher m = INLINE_TAG.matcher(rest);
        StringBuilder text = new StringBuilder(rest.length());
        List<LyricWord> words = new ArrayList<>();
        long pending = lineTime; // A2: the first unit may come without a tag
        boolean anyTag = false;
        int pos = 0;
        while (true) {
            boolean found = m.find();
            Long ts = null;
            if (found) {
                ts = parseTimestamp(m.group(1) != null ? m.group(1) : m.group(2));
                if (ts == null) continue; // "<3" or "[chorus]": ordinary text
            }
            int segEnd = found ? m.start() : rest.length();
            String seg = rest.substring(pos, segEnd);
            if (!anyTag && words.isEmpty() && text.length() == 0) seg = WALAOKE.matcher(seg.stripLeading()).replaceFirst("");
            appendUnit(text, words, seg, pending);
            if (!found) break;
            anyTag = true;
            LyricWord last = words.isEmpty() ? null : words.get(words.size() - 1);
            if (last != null && last.endMs() < 0) {
                words.set(words.size() - 1, new LyricWord(last.startMs(), Math.max(last.startMs(), ts), last.text(),
                        last.start(), last.end()));
            }
            pending = ts;
            pos = m.end();
        }
        int len = text.length();
        while (len > 0 && text.charAt(len - 1) == ' ') len--;
        text.setLength(len);
        return new LyricLine(lineTime, text.toString(), anyTag && !words.isEmpty() ? words : null, false);
    }

    /** Appends one tagged segment (collapsed whitespace) and records it as a unit starting at {@code startMs}. */
    private static void appendUnit(StringBuilder text, List<LyricWord> words, String seg, long startMs) {
        int from = -1, to = -1;
        for (int k = 0; k < seg.length(); k++) {
            char c = seg.charAt(k);
            if (c == '\uFEFF' || c == '\u200B') continue;
            if (c == ' ' || c == '\t') {
                if (text.length() > 0 && text.charAt(text.length() - 1) != ' ') text.append(' ');
                continue;
            }
            if (from < 0) from = text.length();
            text.append(c);
            to = text.length();
        }
        if (from < 0) return;
        words.add(new LyricWord(startMs, -1, text.substring(from, to), from, to));
    }

    /** Moves a line (and its words) to {@code time}, word times by {@code delta}. */
    private static LyricLine shift(LyricLine l, long time, long delta) {
        if (!l.hasWords()) return new LyricLine(time, l.text());
        List<LyricWord> ws = new ArrayList<>(l.words().size());
        for (LyricWord w : l.words()) {
            long s = Math.max(0, w.startMs() + delta);
            ws.add(new LyricWord(s, w.endMs() < 0 ? -1 : Math.max(s, w.endMs() + delta), w.text(), w.start(), w.end()));
        }
        return new LyricLine(time, l.text(), ws, false);
    }

    /** A last word without an end tag ends at the next line's start, at most {@link #OPEN_END_MS} after it began. */
    static void closeOpenEnds(List<LyricLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            LyricLine l = lines.get(i);
            if (!l.hasWords()) continue;
            long next = Long.MAX_VALUE;
            for (int j = i + 1; j < lines.size(); j++) {
                if (lines.get(j).timeMs() > l.timeMs()) { next = lines.get(j).timeMs(); break; }
            }
            List<LyricWord> ws = new ArrayList<>(l.words());
            boolean changed = false;
            for (int k = 0; k < ws.size(); k++) {
                LyricWord w = ws.get(k);
                if (w.endMs() >= 0) continue;
                long nextStart = k + 1 < ws.size() ? ws.get(k + 1).startMs() : next;
                long end = Math.max(w.startMs(), Math.min(nextStart, w.startMs() + OPEN_END_MS));
                ws.set(k, new LyricWord(w.startMs(), end, w.text(), w.start(), w.end()));
                changed = true;
            }
            if (changed) lines.set(i, l.withWords(ws, false));
        }
    }

    @Nullable
    private static ParsedLrc plainLines(List<String> texts, Map<String, String> meta, long offset) {
        List<LyricLine> out = new ArrayList<>();
        for (String t : texts) {
            if (t.isEmpty() && (out.isEmpty() || out.get(out.size() - 1).text().isEmpty())) continue;
            out.add(new LyricLine(LyricLine.UNSYNCED, t));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).text().isEmpty()) out.remove(out.size() - 1);
        return out.isEmpty() ? null : new ParsedLrc(out, false, meta, offset);
    }

    private static boolean hasText(List<LyricLine> lines) {
        for (LyricLine l : lines) if (!l.text().isEmpty()) return true;
        return false;
    }

    /** Milliseconds for the inside of a {@code [...]} tag, or null if it is not a timestamp. */
    @Nullable
    static Long parseTimestamp(String inner) {
        String s = normalizeDigits(inner.trim());
        Matcher m = LONG_TS.matcher(s);
        if (m.matches()) {
            long h = toLong(m.group(1));
            return ((h * 60 + toLong(m.group(2))) * 60 + toLong(m.group(3))) * 1000 + fractionMs(m.group(4));
        }
        m = SHORT_TS.matcher(s);
        if (m.matches()) {
            long ms = (toLong(m.group(1)) * 60 + toLong(m.group(2))) * 1000;
            return m.group(3) == null ? ms : ms + fractionMs(m.group(3));
        }
        return null;
    }

    private static long toLong(String digits) {
        return Long.parseLong(digits);
    }

    /** "5" -> 500, "50" -> 500, "05" -> 50, "500" -> 500. */
    private static long fractionMs(String frac) {
        StringBuilder sb = new StringBuilder(frac);
        while (sb.length() < 3) sb.append('0');
        return Long.parseLong(sb.substring(0, 3));
    }

    private static long parseOffset(@Nullable String value) {
        if (value == null) return 0;
        try {
            String v = normalizeDigits(value.trim());
            return Long.parseLong(v.startsWith("+") ? v.substring(1) : v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Maps Persian/Arabic-Indic digits, the Arabic decimal separator and the full-width colon to ASCII. */
    private static String normalizeDigits(String s) {
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 128) {
                if (sb != null) sb.append(c);
                continue;
            }
            if (sb == null) sb = new StringBuilder(s.length()).append(s, 0, i);
            int d = Character.digit(c, 10);
            sb.append(d >= 0 ? (char) ('0' + d) : c == '\u066B' ? '.' : c == '\uFF1A' ? ':' : c);
        }
        return sb == null ? s : sb.toString();
    }

    /** Removes word tags (on unsynced lines) and collapses whitespace; keeps all other characters intact. */
    private static String clean(String s) {
        if (s.indexOf('<') >= 0) s = WORD_TAG.matcher(s).replaceAll("");
        return SPACE_RUNS.matcher(s).replaceAll(" ").trim();
    }

    private static int skipInvisible(String s, int from) {
        int i = from;
        while (i < s.length() && isInvisible(s.charAt(i))) i++;
        return i;
    }

    private static boolean isInvisible(char c) {
        return c == ' ' || c == '\t' || c == '\uFEFF' || c == '\u200B' || c == '\u200E' || c == '\u200F'
                || c == '\u061C' || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069');
    }
}
