package com.mhrsdev.musicplayer.data.lyrics;

import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.LyricWord;
import java.util.ArrayList;
import java.util.List;

/**
 * Emulated word-by-word timing for synced lines that only carry a line timestamp (plain LRC, LRCLIB without enhanced
 * tags). Pure and deterministic: the same line always gets the same timings, so the result is computed once per
 * lyrics load (on the loading thread) and stored in the {@link LyricLine} with {@code estimated = true}.
 *
 * <p>Model: a line is sung from its timestamp for a "sung" span and then held/silent for a tail before the next line.
 * The sung span is the gap to the next line minus a tail (10 %, 120..700 ms), capped by a natural pace of
 * {@link #MS_PER_SYLLABLE} per syllable (so a line followed by a long instrumental does not crawl). Inside the span
 * each word gets time in proportion to its weight:
 * <ul>
 *   <li>Latin/Cyrillic/Greek: vowel groups (a rough syllable count), at least 1;</li>
 *   <li>Persian/Arabic/Hebrew (abjads, vowels mostly unwritten): about one syllable per two letters;</li>
 *   <li>CJK / kana / Hangul: one syllable per character, and every character is its own unit;</li>
 *   <li>digits: one per digit; trailing punctuation adds a pause ({@code , ، ؛ ;} short, {@code . ! ? … ؟} long);</li>
 *   <li>the last word is held a little longer (singers stretch the line end).</li>
 * </ul>
 */
public class WordTimingEstimator {
    static final long MS_PER_SYLLABLE = 330;
    static final long LAST_LINE_MS = 4_000;
    static final long MIN_TAIL_MS = 120, MAX_TAIL_MS = 700;
    private static final float SHORT_PAUSE = 0.6f, LONG_PAUSE = 1.0f, LAST_WORD_HOLD = 1.35f;

    private WordTimingEstimator() { }

    /**
     * Adds estimated words to every synced line without words; other lines are returned as they are.
     *
     * @param lines time-ordered lines of one synced lyrics
     */
    public static List<LyricLine> apply(List<LyricLine> lines) {
        List<LyricLine> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            LyricLine l = lines.get(i);
            if (l.timeMs() < 0 || l.hasWords() || l.text().isEmpty()) {
                out.add(l);
                continue;
            }
            long next = -1;
            for (int j = i + 1; j < lines.size(); j++) {
                if (lines.get(j).timeMs() > l.timeMs()) { next = lines.get(j).timeMs(); break; }
            }
            List<LyricWord> words = estimate(l.text(), l.timeMs(), next);
            out.add(words.isEmpty() ? l : l.withWords(words, true));
        }
        return out;
    }

    /**
     * @param nextStartMs start of the next line, or -1 for the last line
     * @return the words of {@code text} with estimated timings (empty for text without letters/digits)
     */
    public static List<LyricWord> estimate(String text, long startMs, long nextStartMs) {
        List<int[]> spans = tokenize(text);
        List<LyricWord> out = new ArrayList<>(spans.size());
        if (spans.isEmpty()) return out;
        float[] w = new float[spans.size()];
        float syllables = 0, total = 0;
        for (int i = 0; i < w.length; i++) {
            int[] s = spans.get(i);
            float syl = syllables(text, s[0], s[1]);
            syllables += syl;
            w[i] = syl + pause(text, s[1]);
        }
        w[w.length - 1] *= LAST_WORD_HOLD;
        for (float v : w) total += v;

        long natural = (long) (syllables * MS_PER_SYLLABLE) + 250;
        long gap = nextStartMs > startMs ? nextStartMs - startMs : LAST_LINE_MS;
        long tail = Math.max(MIN_TAIL_MS, Math.min(MAX_TAIL_MS, gap / 10));
        long sung = Math.max(Math.min(gap - tail, natural), Math.min(gap, 200L * w.length));
        sung = Math.max(1, Math.min(sung, gap));

        float acc = 0;
        for (int i = 0; i < w.length; i++) {
            int[] s = spans.get(i);
            long a = startMs + Math.round(sung * (acc / total));
            acc += w[i];
            long b = startMs + Math.round(sung * (acc / total));
            out.add(new LyricWord(a, Math.max(a, b), text.substring(s[0], s[1]), s[0], s[1]));
        }
        return out;
    }

    /** Word spans {start, end} with trailing punctuation included; CJK characters are single-char spans. */
    static List<int[]> tokenize(String text) {
        List<int[]> out = new ArrayList<>();
        int n = text.length(), i = 0;
        while (i < n) {
            while (i < n && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= n) break;
            if (isCjk(text.charAt(i))) {
                int e = i + 1;
                while (e < n && isTrailingPunct(text.charAt(e))) e++;
                out.add(new int[]{i, e});
                i = e;
                continue;
            }
            int e = i;
            while (e < n && !Character.isWhitespace(text.charAt(e)) && !isCjk(text.charAt(e))) e++;
            if (hasLetterOrDigit(text, i, e)) out.add(new int[]{i, e}); // a lone "-" or "..." is no word
            i = e;
        }
        return out;
    }

    /** Rough syllable estimate of {@code text[from, to)}, at least 1 for any word. */
    static float syllables(String text, int from, int to) {
        int vowelGroups = 0, abjad = 0, cjk = 0, digits = 0, other = 0;
        boolean inVowel = false;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (isCjk(c)) { cjk++; inVowel = false; continue; }
            if (c >= '֐' && c <= 'ࣿ' || c >= 'יִ' && c <= 'ﻼ') {
                if (Character.isLetter(c)) abjad++;
                inVowel = false;
                continue;
            }
            if (Character.isDigit(c)) { digits++; inVowel = false; continue; }
            if (Character.isLetter(c)) {
                boolean v = isVowel(c);
                if (v && !inVowel) vowelGroups++;
                if (!v) other++;
                inVowel = v;
            } else {
                inVowel = false;
            }
        }
        float s = vowelGroups + (abjad + 1) / 2 + cjk + digits;
        if (s == 0 && other > 0) s = 1; // "hmm", "shh"
        return Math.max(1f, s);
    }

    private static float pause(String text, int end) {
        char c = end > 0 ? text.charAt(end - 1) : ' ';
        switch (c) {
            case ',': case ';': case ':': case '،': case '؛': case '、': case '，':
                return SHORT_PAUSE;
            case '.': case '!': case '?': case '…': case '؟': case '。': case '！': case '？':
                return LONG_PAUSE;
            default:
                return 0f;
        }
    }

    private static boolean isVowel(char c) {
        return "aeiouyAEIOUYàáâãäåèéêëìíîïòóôõöùúûüýÿæœаеёиоуыэюяАЕЁИОУЫЭЮЯαεηιουωάέήίόύώ".indexOf(c) >= 0;
    }

    static boolean isCjk(char c) {
        return c >= '぀' && c <= 'ヿ' || c >= '㐀' && c <= '䶿' || c >= '一' && c <= '鿿'
                || c >= '가' && c <= '힯' || c >= '豈' && c <= '﫿';
    }

    private static boolean isTrailingPunct(char c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && !isCjk(c);
    }

    private static boolean hasLetterOrDigit(String s, int from, int to) {
        for (int i = from; i < to; i++) if (Character.isLetterOrDigit(s.charAt(i))) return true;
        return false;
    }
}
