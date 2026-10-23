package com.mhrsdev.musicplayer.playback;

/** Receives "this track has really been listened to" events; the app wires it to {@code LibraryRepository.recordPlay}. */
public interface PlayCountListener {
    /** Main thread, once per play-through of a track (see {@link PlayCountTracker}). */
    void onTrackPlayed(String trackId);
}
