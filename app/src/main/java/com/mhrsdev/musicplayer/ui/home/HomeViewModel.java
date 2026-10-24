package com.mhrsdev.musicplayer.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Home feed. No Android UI classes; exposes LiveData only and talks to domain interfaces. */
public class HomeViewModel extends ViewModel {
    private final LiveData<List<HomeSection>> sections;
    private final PlayerController player;
    private final HomeFeedSource feed;

    HomeViewModel(HomeFeedSource feed, PlayerController player) {
        this.sections = feed.getSections();
        this.player = player;
        this.feed = feed;
    }

    /** Pull-to-refresh / "Surprise me": new random picks; section order stays as it is for the day. */
    public void refresh() {
        feed.refresh();
    }

    /** Continue card: resumes when the player still holds that track, else starts it. */
    public void continueListening(Track track, String playingFrom) {
        PlaybackState st = player.getState().getValue();
        if (st != null && st.currentTrack() != null && st.currentTrack().id().equals(track.id())) {
            player.resume();
        } else {
            player.play(List.of(track), 0, playingFrom);
        }
    }

    /** Plays {@code tracks} from {@code index} (mix / see-all pages). */
    public void play(List<Track> tracks, int index, String playingFrom) {
        if (!tracks.isEmpty()) player.play(tracks, Math.max(0, Math.min(index, tracks.size() - 1)), playingFrom);
    }

    public void playShuffled(List<Track> tracks, String playingFrom) {
        if (tracks.isEmpty()) return;
        List<Track> copy = new ArrayList<>(tracks);
        Collections.shuffle(copy);
        player.play(copy, 0, playingFrom);
    }

    /** null until the first load finishes (the screen shows its loading state). */
    public LiveData<List<HomeSection>> getSections() {
        return sections;
    }

    /** Plays the section's tracks starting at {@code track}. */
    public void playFromSection(HomeSection section, Track track) {
        playFromSection(section, track, section.title());
    }

    /** @param playingFrom the localized section title */
    public void playFromSection(HomeSection section, Track track, String playingFrom) {
        List<Track> tracks = tracksOf(section);
        int index = Math.max(0, tracks.indexOf(track));
        player.play(tracks, index, playingFrom);
    }

    public void shuffleSection(HomeSection section) {
        playShuffled(tracksOf(section), section.title());
    }

    public void shuffleSection(HomeSection section, String playingFrom) {
        playShuffled(tracksOf(section), playingFrom);
    }

    static List<Track> tracksOf(HomeSection section) {
        List<Track> out = new ArrayList<>();
        for (HomeItem item : section.items()) {
            if (item.kind() == HomeItem.Kind.TRACK) out.add(item.track());
        }
        return out;
    }

    public static ViewModelProvider.Factory factory(HomeFeedSource feed, PlayerController player) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new HomeViewModel(feed, player));
            }
        };
    }
}
