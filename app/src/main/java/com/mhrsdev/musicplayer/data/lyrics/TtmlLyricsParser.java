package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.LyricWord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TTML lyrics ({@code .ttml}/{@code .xml}, Apple-Music style): each {@code <p begin end>} is a line, each
 * {@code <span begin end>} holding text is a word/syllable unit. Container spans (background vocals, roles) are walked
 * through. A small tag scanner, no XML library: no DTD/entity expansion (XXE-safe) and it runs in JVM tests.
 * Times: {@code hh:mm:ss.fff}, {@code mm:ss.fff}, {@code ss.fff}, {@code 12.3s}, {@code 450ms}.
 */
class TtmlLyricsParser {
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][\\w:.-]*)([^>]*?)(/?)>");
    private static final Pattern ATTR = Pattern.compile("([\\w:.-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
    private static final Pattern CLOCK = Pattern.compile("(?:(\\d+):)?(?:(\\d+):)?(\\d+(?:\\.\\d+)?)");

    private TtmlLyricsParser() { }

    static boolean looksLikeTtml(String raw) {
        int i = 0;
        while (i < raw.length() && (Character.isWhitespace(raw.charAt(i)) || raw.charAt(i) == '\uFEFF')) i++;
        return raw.startsWith("<", i) && raw.contains("<tt") && raw.contains("<p");
    }

    /** @return time-ordered lines (words may be null for untimed spans), or null when nothing usable is found */
    @Nullable
    static List<LyricLine> parse(String raw) {
        List<LyricLine> lines = new ArrayList<>();
        Matcher m = TAG.matcher(raw);
        int pos = 0;
        long pBegin = -1, pEnd = -1;
        boolean inP = false;
        StringBuilder text = new StringBuilder();
        List<LyricWord> words = new ArrayList<>();
        List<long[]> spanStack = new ArrayList<>(); // {begin, end} of open spans (-1 = untimed)
        while (m.find()) {
            if (inP) appendText(raw.substring(pos, m.start()), text, words, spanStack);
            pos = m.end();
            String name = localName(m.group(2));
            boolean closing = !m.group(1).isEmpty(), selfClosing = !m.group(4).isEmpty();
            if (name.equals("p")) {
                if (!closing) {
                    inP = !selfClosing;
                    pBegin = time(attr(m.group(3), "begin"));
                    pEnd = time(attr(m.group(3), "end"));
                    text.setLength(0);
                    words.clear();
                    spanStack.clear();
                } else if (inP) {
                    inP = false;
                    addLine(lines, pBegin, pEnd, text, words);
                }
            } else if (inP && name.equals("span")) {
                if (closing) {
                    if (!spanStack.isEmpty()) spanStack.remove(spanStack.size() - 1);
                } else if (!selfClosing) {
                    spanStack.add(new long[]{time(attr(m.group(3), "begin")), time(attr(m.group(3), "end"))});
                }
            } else if (inP && name.equals("br")) {
                appendText(" ", text, words, spanStack);
            }
        }
        if (lines.isEmpty()) return null;
        lines.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
        return lines;
    }

    private static void addLine(List<LyricLine> lines, long begin, long end, StringBuilder text,
            List<LyricWord> words) {
        int len = text.length();
        while (len > 0 && text.charAt(len - 1) == ' ') len--;
        String t = text.substring(0, len);
        if (t.isEmpty()) return;
        long start = begin >= 0 ? begin : words.isEmpty() ? LyricLine.UNSYNCED : words.get(0).startMs();
        List<LyricWord> ws = new ArrayList<>();
        for (LyricWord w : words) {
            long e = w.endMs() >= 0 ? w.endMs() : end >= 0 ? end : w.startMs() + 500;
            ws.add(new LyricWord(w.startMs(), Math.max(w.startMs(), e), w.text(), w.start(), Math.min(w.end(), len)));
        }
        lines.add(new LyricLine(start, t, ws.isEmpty() ? null : ws, false));
    }

    /** Appends decoded, whitespace-collapsed text; text inside a timed span becomes (or extends) a unit. */
    private static void appendText(String raw, StringBuilder text, List<LyricWord> words, List<long[]> spans) {
        if (raw.isEmpty()) return;
        String s = decode(raw);
        long[] span = null;
        for (int i = spans.size() - 1; i >= 0; i--) if (spans.get(i)[0] >= 0) { span = spans.get(i); break; }
        int from = -1, to = -1;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (Character.isWhitespace(c)) {
                if (text.length() > 0 && text.charAt(text.length() - 1) != ' ') text.append(' ');
                continue;
            }
            if (from < 0) from = text.length();
            text.append(c);
            to = text.length();
        }
        if (from < 0 || span == null) return;
        LyricWord last = words.isEmpty() ? null : words.get(words.size() - 1);
        if (last != null && last.startMs() == span[0] && last.end() == from) { // same span split by an entity/tag
            words.set(words.size() - 1, new LyricWord(last.startMs(), last.endMs(), text.substring(last.start(), to),
                    last.start(), to));
        } else {
            words.add(new LyricWord(span[0], span[1], text.substring(from, to), from, to));
        }
    }

    private static String localName(String qname) {
        int c = qname.indexOf(':');
        return (c >= 0 ? qname.substring(c + 1) : qname).toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static String attr(String attrs, String name) {
        Matcher a = ATTR.matcher(attrs);
        while (a.find()) {
            if (localName(a.group(1)).equals(name)) return a.group(3) != null ? a.group(3) : a.group(4);
        }
        return null;
    }

    /** Milliseconds, or -1 when absent/unreadable. */
    static long time(@Nullable String v) {
        if (v == null) return -1;
        String s = v.trim();
        try {
            if (s.endsWith("ms")) return Math.round(Double.parseDouble(s.substring(0, s.length() - 2)));
            if (s.endsWith("s")) return Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * 1000);
            Matcher m = CLOCK.matcher(s);
            if (!m.matches()) return -1;
            double sec = Double.parseDouble(m.group(3));
            long a = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
            long b = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
            long h = m.group(2) == null ? 0 : a, min = m.group(2) == null ? a : b;
            return Math.round(((h * 60 + min) * 60 + sec) * 1000);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int semi = c == '&' ? s.indexOf(';', i) : -1;
            if (semi < 0 || semi - i > 10) { out.append(c); continue; }
            String e = s.substring(i + 1, semi);
            String r = switch (e) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                default -> numeric(e);
            };
            if (r == null) { out.append(c); continue; }
            out.append(r);
            i = semi;
        }
        return out.toString();
    }

    @Nullable
    private static String numeric(String e) {
        try {
            if (e.startsWith("#x") || e.startsWith("#X")) return new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
            if (e.startsWith("#")) return new String(Character.toChars(Integer.parseInt(e.substring(1))));
        } catch (IllegalArgumentException ignored) {
            // not an entity
        }
        return null;
    }
}
