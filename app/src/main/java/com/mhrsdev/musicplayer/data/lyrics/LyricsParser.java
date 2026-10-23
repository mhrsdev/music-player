package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for raw lyrics text from any source: sniffs the format (TTML, JSON word-sync, else LRC/plain), parses
 * it, and adds emulated word timing ({@link WordTimingEstimator}) to synced lines that have none. Runs on the
 * loading thread, once per lyrics load; the UI only reads the result.
 */
public class LyricsParser {
    private LyricsParser() { }

    /** @return the lyrics, or null when {@code raw} holds no lyric line */
    @Nullable
    public static Lyrics toLyrics(String trackId, @Nullable String raw, @Nullable String source) {
        if (raw == null || raw.trim().isEmpty()) return null;
        List<LyricLine> lines = null;
        if (TtmlLyricsParser.looksLikeTtml(raw)) lines = TtmlLyricsParser.parse(raw);
        else if (JsonLyricsParser.looksLikeJson(raw)) lines = JsonLyricsParser.parse(raw);
        Lyrics parsed;
        if (lines != null) {
            boolean synced = false;
            for (LyricLine l : lines) synced |= l.timeMs() >= 0;
            if (!synced) {
                List<LyricLine> plain = new ArrayList<>();
                for (LyricLine l : lines) plain.add(new LyricLine(LyricLine.UNSYNCED, l.text()));
                lines = plain;
            } else {
                lines.removeIf(l -> l.timeMs() < 0);
            }
            parsed = new Lyrics(trackId, lines, synced, source);
        } else {
            parsed = LrcParser.toLyrics(trackId, raw, source);
        }
        if (parsed == null || !parsed.synced()) return parsed;
        return new Lyrics(trackId, WordTimingEstimator.apply(parsed.lines()), true, parsed.source());
    }
}
