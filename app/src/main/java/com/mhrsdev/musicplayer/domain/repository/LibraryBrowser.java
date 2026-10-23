package com.mhrsdev.musicplayer.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.model.TrackDetails;
import com.mhrsdev.musicplayer.domain.model.YearCount;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Library browsing and management beyond {@link LibraryRepository} / {@link LibraryAdmin}: smart lists, years,
 * play counts for sorting, batch playlist edits with duplicate feedback, track details and forgetting deleted files.
 * Same conventions: LiveData on the main thread, fire-and-forget writes, callbacks on the main thread.
 * Obtain it from {@code AppContainer.libraryBrowser()}.
 */
public interface LibraryBrowser {

    /** Played tracks, most plays first (ties: most recent play first). */
    LiveData<List<Track>> getMostPlayed();

    /** Tracks with no recorded play, newest added first. */
    LiveData<List<Track>> getNeverPlayed();

    /** Years that occur in the library (unknown year excluded), newest first. */
    LiveData<List<YearCount>> getYears();

    /** Tracks of one release year, by album then track number. */
    LiveData<List<Track>> getYearTracks(int year);

    /** Track id to play count, only tracks played at least once (for "most played" sorting). */
    LiveData<Map<String, Integer>> getPlayCounts();

    /**
     * Appends {@code trackIds} in order, skipping ones already in the playlist. {@code callback} (may be null) gets the
     * number actually added (0 = all were duplicates).
     */
    void addAllToPlaylist(String playlistId, List<String> trackIds, @Nullable Callback<Integer> callback);

    /** Puts {@code trackId} back at {@code index} of the playlist (undo of a removal); clamps the index. */
    void restoreToPlaylist(String playlistId, String trackId, int index);

    /** Loads the details of {@code track}; never delivers null. */
    void loadDetails(Track track, Callback<TrackDetails> callback);

    /** Drops tracks whose files were deleted from the device; their playlist / like / history rows become inert. */
    void forgetTracks(Collection<String> trackIds);
}
