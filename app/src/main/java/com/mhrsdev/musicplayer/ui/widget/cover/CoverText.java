package com.mhrsdev.musicplayer.ui.widget.cover;

import java.util.ArrayList;
import java.util.List;

/**
 * Text fitting for cover typography (pure, JVM-testable through {@link Measurer}): the largest size between
 * {@code maxSize} and {@code minSize} at which the text wraps on word boundaries into at most {@code maxLines}, with
 * two-line results balanced. If even {@code minSize} does not fit, words are broken by code point and the last line
 * ends in an ellipsis. Every returned line measures at most {@code maxWidth}, so text is never cropped.
 */
public class CoverText {
    public static final String ELLIPSIS = "…";

    /** Width of {@code s} at text size {@code size}, in the same unit as {@code maxWidth}. */
    public interface Measurer {
        float width(String s, float size);
    }

    public record Fit(float size, List<String> lines, boolean ellipsized) { }

    private CoverText() { }

    public static Fit fit(String text, float maxWidth, int maxLines, float maxSize, float minSize, Measurer m) {
        String[] words = collapse(text).split(" ");
        if (words.length == 1 && words[0].isEmpty()) return new Fit(maxSize, List.of(), false);
        for (float size = maxSize; ; size = Math.max(minSize, size * 0.94f)) {
            List<String> lines = wrap(words, size, maxWidth, m);
            if (lines != null && lines.size() <= maxLines) {
                if (lines.size() == 2) lines = balance(words, size, maxWidth, m, lines);
                return new Fit(size, lines, false);
            }
            if (size <= minSize) break;
        }
        return new Fit(minSize, forced(words, minSize, maxWidth, maxLines, m), true);
    }

    /** Greedy wrap; null when a single word is wider than the line. */
    static List<String> wrap(String[] words, float size, float maxWidth, Measurer m) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            if (m.width(w, size) > maxWidth) return null;
            String candidate = line.length() == 0 ? w : line + " " + w;
            if (m.width(candidate, size) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** Best two-line split: the one whose longer line is shortest. */
    private static List<String> balance(String[] words, float size, float maxWidth, Measurer m, List<String> greedy) {
        List<String> best = greedy;
        float bestW = Math.max(m.width(greedy.get(0), size), m.width(greedy.get(1), size));
        for (int k = 1; k < words.length; k++) {
            String a = String.join(" ", java.util.Arrays.copyOfRange(words, 0, k));
            String b = String.join(" ", java.util.Arrays.copyOfRange(words, k, words.length));
            float wa = m.width(a, size), wb = m.width(b, size);
            if (wa <= maxWidth && wb <= maxWidth && Math.max(wa, wb) < bestW - 0.01f) {
                bestW = Math.max(wa, wb);
                best = List.of(a, b);
            }
        }
        return best;
    }

    /** Minimum size did not fit: break inside words where needed, cap the line count, ellipsize the last line. */
    private static List<String> forced(String[] words, float size, float maxWidth, int maxLines, Measurer m) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String rest = String.join(" ", words);
        int i = 0;
        while (i < rest.length() && lines.size() < maxLines) {
            int cp = rest.codePointAt(i);
            int next = i + Character.charCount(cp);
            String candidate = line + rest.substring(i, next);
            if (m.width(candidate, size) <= maxWidth) {
                line.append(rest, i, next);
                i = next;
                continue;
            }
            // break at the last space of this line when there is one, else mid-word
            int sp = line.lastIndexOf(" ");
            if (sp > 0 && cp != ' ') {
                lines.add(line.substring(0, sp));
                i -= line.length() - sp - 1;
            } else {
                lines.add(line.toString().trim());
                if (cp == ' ') i = next;
            }
            line.setLength(0);
            if (lines.size() == maxLines) break;
        }
        if (line.length() > 0 && lines.size() < maxLines) lines.add(line.toString().trim());
        if (i < rest.length() || lines.size() > maxLines) {
            int last = Math.min(lines.size(), maxLines) - 1;
            lines.set(last, ellipsize(lines.get(last) + rest.substring(Math.min(i, rest.length())), size, maxWidth, m));
            while (lines.size() > maxLines) lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /** Drops code points from the end until "text…" fits (never leaves a trailing space or ZWNJ before the dots). */
    static String ellipsize(String text, float size, float maxWidth, Measurer m) {
        String s = text;
        while (!s.isEmpty() && m.width(s + ELLIPSIS, size) > maxWidth) {
            s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
        }
        while (!s.isEmpty() && (s.endsWith(" ") || s.endsWith("‌"))) s = s.substring(0, s.length() - 1);
        return s + ELLIPSIS;
    }

    private static String collapse(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }
}
