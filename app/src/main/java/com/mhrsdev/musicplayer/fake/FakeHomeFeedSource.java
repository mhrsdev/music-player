package com.mhrsdev.musicplayer.fake;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds Home sections from the library: New releases (newest albums), Throwbacks (oldest), playlists, Quick picks. */
public final class FakeHomeFeedSource implements HomeFeedSource {
    private final MediatorLiveData<List<HomeSection>> sections = new MediatorLiveData<>();

    public FakeHomeFeedSource(LibraryRepository library) {
        LiveData<List<Album>> albums = library.getAlbums();
        LiveData<List<Track>> recent = library.getRecentlySaved();
        LiveData<List<Track>> history = library.getHistory();
        LiveData<List<Playlist>> playlists = library.getPlaylists();
        Runnable rebuild = () -> {
            if (albums.getValue() == null || recent.getValue() == null
                    || history.getValue() == null || playlists.getValue() == null) return;
            sections.setValue(build(albums.getValue(), recent.getValue(), history.getValue(), playlists.getValue()));
        };
        for (LiveData<?> source : List.of(albums, recent, history, playlists)) sections.addSource(source, v -> rebuild.run());
    }

    @Override public LiveData<List<HomeSection>> getSections() {
        return sections;
    }

    private static List<HomeSection> build(List<Album> albums, List<Track> recent, List<Track> history,
                                           List<Playlist> playlists) {
        List<Album> newest = new ArrayList<>(albums);
        newest.sort(Comparator.comparingInt(Album::year).reversed());
        List<Album> oldest = new ArrayList<>(albums);
        oldest.sort(Comparator.comparingInt(Album::year));
        Map<String, Track> quick = new LinkedHashMap<>();
        for (Track t : history) quick.putIfAbsent(t.id(), t);
        for (Track t : recent) quick.putIfAbsent(t.id(), t);

        List<HomeSection> out = new ArrayList<>();
        out.add(new HomeSection("new-releases", "New releases", "Fresh in your library",
                HomeSectionStyle.HERO_CAROUSEL, albumItems(newest)));
        out.add(new HomeSection("throwbacks", "Throwbacks", "From a while ago",
                HomeSectionStyle.CAROUSEL, albumItems(oldest)));
        List<HomeItem> playlistItems = new ArrayList<>();
        for (Playlist p : playlists) playlistItems.add(HomeItem.of(p));
        out.add(new HomeSection("playlists", "Your playlists", null, HomeSectionStyle.CAROUSEL, playlistItems));
        List<HomeItem> quickItems = new ArrayList<>();
        for (Track t : quick.values()) {
            if (quickItems.size() == 8) break;
            quickItems.add(HomeItem.of(t));
        }
        out.add(new HomeSection("quick-picks", "Quick picks", "Start radio from a song", HomeSectionStyle.LIST, quickItems));
        out.removeIf(s -> s.items().isEmpty());
        return out;
    }

    private static List<HomeItem> albumItems(List<Album> albums) {
        List<HomeItem> items = new ArrayList<>();
        for (Album a : albums) {
            if (items.size() == 6) break;
            items.add(HomeItem.of(a));
        }
        return items;
    }
}
