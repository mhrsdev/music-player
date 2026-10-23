package com.mhrsdev.musicplayer.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of a manual lyrics search: the candidates (best first), or {@code failed} (offline, HTTP error, timeout). */
public record LyricsSearchResult(List<LyricsCandidate> items, boolean failed) {

    public LyricsSearchResult {
        items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static LyricsSearchResult failure() {
        return new LyricsSearchResult(Collections.emptyList(), true);
    }
}
