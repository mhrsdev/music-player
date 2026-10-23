package com.mhrsdev.musicplayer.domain.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.LyricsCandidate;
import com.mhrsdev.musicplayer.domain.model.LyricsSearchResult;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.Collections;

public interface LyricsRepository {

    /**
     * Emits nothing while loading, then exactly one {@link Lyrics}: the lyrics (synced or plain) or
     * {@link Lyrics#none(String)} when nothing was found ({@link Lyrics#failed()} when the online step failed).
     */
    LiveData<Lyrics> lyricsFor(Track track);

    /** "Search again": like {@link #lyricsFor} but ignores the cached online answer (also a negative one). */
    default LiveData<Lyrics> searchAgain(Track track) {
        return lyricsFor(track);
    }

    /** "Search manually": free-text online search; emits once. */
    default LiveData<LyricsSearchResult> search(String artist, String title) {
        return new MutableLiveData<>(new LyricsSearchResult(Collections.emptyList(), false));
    }

    /** Keeps {@code candidate} as this track's lyrics from now on, then emits the track's lyrics. */
    default LiveData<Lyrics> useCandidate(Track track, LyricsCandidate candidate) {
        return lyricsFor(track);
    }

    /**
     * "Add lyrics file": copies the document at {@code uri} (.lrc/.txt/.ttml/.elrc picked by the user) into app
     * storage for this track, preferred from now on; then emits the track's lyrics.
     */
    default LiveData<Lyrics> importFile(Track track, String uri) {
        return lyricsFor(track);
    }
}
