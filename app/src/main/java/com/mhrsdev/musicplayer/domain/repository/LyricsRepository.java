package com.mhrsdev.musicplayer.domain.repository;

import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;

public interface LyricsRepository {

    /**
     * Emits nothing while loading, then exactly one {@link Lyrics}: the lyrics (synced or plain) or
     * {@link Lyrics#none(String)} when nothing was found.
     */
    LiveData<Lyrics> lyricsFor(Track track);
}
