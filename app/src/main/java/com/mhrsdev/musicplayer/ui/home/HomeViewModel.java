package com.mhrsdev.musicplayer.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Home feed. No Android UI classes; exposes LiveData only and talks to domain interfaces. */
public final class HomeViewModel extends ViewModel {
    private final LiveData<List<HomeSection>> sections;
    private final PlayerController player;

    HomeViewModel(HomeFeedSource feed, PlayerController player) {
        this.sections = feed.getSections();
        this.player = player;
    }

    /** null until the first load finishes (the screen shows its loading state). */
    public LiveData<List<HomeSection>> getSections() {
        return sections;
    }

    /** Plays the section's tracks starting at {@code track}. */
    public void playFromSection(HomeSection section, Track track) {
        List<Track> tracks = tracksOf(section);
        int index = Math.max(0, tracks.indexOf(track));
        player.play(tracks, index, section.title());
    }

    public void shuffleSection(HomeSection section) {
        List<Track> tracks = new ArrayList<>(tracksOf(section));
        if (tracks.isEmpty()) return;
        Collections.shuffle(tracks);
        player.play(tracks, 0, section.title());
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
