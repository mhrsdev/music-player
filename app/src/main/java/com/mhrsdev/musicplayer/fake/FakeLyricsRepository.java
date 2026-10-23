package com.mhrsdev.musicplayer.fake;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;

public final class FakeLyricsRepository implements LyricsRepository {
    @Override public LiveData<Lyrics> lyricsFor(Track track) {
        return new MutableLiveData<>(SampleData.lyricsFor(track.id()));
    }
}
