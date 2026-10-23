package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.LyricWord;
import java.util.List;
import org.junit.Test;

public class WordTimingEstimatorTest {

    @Test public void wordsCoverTheLineInOrder_withTail() {
        List<LyricWord> w = WordTimingEstimator.estimate("Could you dance with a girl like me?", 10_000, 13_000);
        assertEquals(8, w.size());
        assertEquals(10_000, w.get(0).startMs());
        for (int i = 1; i < w.size(); i++) assertEquals(w.get(i - 1).endMs(), w.get(i).startMs());
        long end = w.get(w.size() - 1).endMs();
        assertTrue("tail before next line", end <= 13_000 - WordTimingEstimator.MIN_TAIL_MS);
        assertEquals("me?", w.get(7).text());
    }

    @Test public void longGapIsCappedByNaturalPace() {
        List<LyricWord> w = WordTimingEstimator.estimate("hello", 0, 60_000);
        assertTrue(w.get(0).endMs() < 2_000);
    }

    @Test public void longerWordsGetMoreTime() {
        List<LyricWord> w = WordTimingEstimator.estimate("I wonderfully go", 0, 5_000);
        long i = w.get(0).endMs() - w.get(0).startMs();
        long wonderfully = w.get(1).endMs() - w.get(1).startMs();
        assertTrue(wonderfully > 2 * i);
    }

    @Test public void punctuationAddsPause() {
        List<LyricWord> a = WordTimingEstimator.estimate("no, no more", 0, 4_000);
        List<LyricWord> b = WordTimingEstimator.estimate("no no more", 0, 4_000);
        assertTrue(a.get(0).endMs() - a.get(0).startMs() > b.get(0).endMs() - b.get(0).startMs());
    }

    @Test public void persianAndCjk() {
        List<LyricWord> fa = WordTimingEstimator.estimate("دل من دریاست", 0, 3_000);
        assertEquals(3, fa.size());
        assertEquals("دریاست", fa.get(2).text());
        assertTrue(fa.get(2).endMs() - fa.get(2).startMs() > fa.get(1).endMs() - fa.get(1).startMs());
        List<LyricWord> ja = WordTimingEstimator.estimate("君の名は", 0, 2_000);
        assertEquals(4, ja.size());
    }

    @Test public void deterministicAndOffsetsMatchText() {
        String t = "  Lo que me den, lo pruebo ";
        List<LyricWord> a = WordTimingEstimator.estimate(t, 500, 4_500);
        assertEquals(a, WordTimingEstimator.estimate(t, 500, 4_500));
        for (LyricWord w : a) assertEquals(w.text(), t.substring(w.start(), w.end()));
    }

    @Test public void applySkipsPlainAndWordTimedLines() {
        List<LyricLine> out = WordTimingEstimator.apply(List.of(
                new LyricLine(0, "a b"), new LyricLine(1_000, ""), new LyricLine(2_000, "last line")));
        assertTrue(out.get(0).estimated());
        assertTrue(out.get(0).wordsEndMs() <= 1_000);
        assertEquals(null, out.get(1).words());
        assertTrue(out.get(2).wordsEndMs() <= 2_000 + WordTimingEstimator.LAST_LINE_MS);
    }

    @Test public void activeWordIndex() {
        LyricLine l = WordTimingEstimator.apply(List.of(new LyricLine(1_000, "one two"), new LyricLine(3_000, "x"))).get(0);
        assertEquals(-1, l.activeWordIndex(999));
        assertEquals(0, l.activeWordIndex(1_000));
        assertEquals(1, l.activeWordIndex(l.words().get(1).startMs()));
        assertEquals(1, l.activeWordIndex(10_000));
        assertEquals(0.5f, l.words().get(0).progress((l.words().get(0).startMs() + l.words().get(0).endMs()) / 2), 0.01f);
    }
}
