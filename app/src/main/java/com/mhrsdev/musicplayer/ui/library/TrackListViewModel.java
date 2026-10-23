package com.mhrsdev.musicplayer.ui.library;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryAdmin;
import com.mhrsdev.musicplayer.domain.repository.LibraryBrowser;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Every track-list screen: album / artist / playlist / year detail and the Songs, Liked, History, Recently added,
 * Most played and Never played lists. Owns the sort order, the multi-select state and the playlist edits.
 */
public final class TrackListViewModel extends ViewModel {

    /**
     * Header shown above the list. {@code title} is null for the built-in lists (the screen uses its own label).
     * {@code artworkUris}: one cover, or up to four for a playlist mosaic.
     */
    public record Header(@Nullable String title, @Nullable String subtitle, int seed, boolean circle,
            List<String> artworkUris) {
        Header(@Nullable String title, @Nullable String subtitle, int seed, boolean circle) {
            this(title, subtitle, seed, circle, Collections.emptyList());
        }
    }

    /** Sorted rows plus the fast-scroller sections (empty when the order is not alphabetical). */
    public record Rows(List<Track> tracks, List<Section> sections) { }

    /** A fast-scroller section starting at adapter position {@code start}. */
    public record Section(String label, int start) { }

    /** A playlist row removal that can be undone. */
    public record Removed(Track track, int index) { }

    /** A deleted playlist that can be recreated. */
    public record DeletedPlaylist(String name, List<String> trackIds) { }

    private final PlayerController player;
    private final LibraryRepository library;
    private final LibraryAdmin admin;
    private final LibraryBrowser browser;
    private final Executor sorter;
    private final Locale locale;
    private final TrackList kind;
    @Nullable private final String id;
    private final LiveData<List<Track>> source;
    private final LiveData<Header> header;
    private final MediatorLiveData<Rows> rows = new MediatorLiveData<>();
    private final MutableLiveData<SortOrder> sort;
    private final MutableLiveData<Set<String>> selection = new MutableLiveData<>(Collections.emptySet());
    @Nullable private Map<String, Integer> playCounts;
    private boolean countsAttached;
    private int sortGeneration;

    TrackListViewModel(LibraryRepository library, LibraryAdmin admin, LibraryBrowser browser, PlayerController player,
            Executor sorter, Locale locale, TrackList kind, @Nullable String id, SortOrder initialSort) {
        this.player = player;
        this.library = library;
        this.admin = admin;
        this.browser = browser;
        this.sorter = sorter;
        this.locale = locale;
        this.kind = kind;
        this.id = id;
        this.sort = new MutableLiveData<>(initialSort);
        switch (kind) {
            case ALBUM -> {
                source = library.getAlbumTracks(id);
                header = Transformations.map(library.getAlbum(id), a -> a == null ? null
                        : new Header(a.title(), a.artistName() + (a.year() > 0 ? " · " + a.year() : ""),
                        a.placeholderSeed(), false, single(a.artworkUri())));
            }
            case ARTIST -> {
                source = library.getArtistTracks(id);
                header = Transformations.map(library.getArtist(id), a -> a == null ? null
                        : new Header(a.name(), null, a.placeholderSeed(), true, single(a.imageUri())));
            }
            case PLAYLIST -> {
                source = library.getPlaylistTracks(id);
                header = Transformations.map(library.getPlaylist(id), p -> p == null ? null
                        : new Header(p.name(), p.description(), p.placeholderSeed(), false, p.artworkUris()));
            }
            case YEAR -> {
                source = browser.getYearTracks(parseYear(id));
                header = new MutableLiveData<>(new Header(id, null, parseYear(id), false));
            }
            case LIKED -> {
                source = library.getLikedTracks();
                header = new MutableLiveData<>(new Header(null, null, 1, false));
            }
            case HISTORY -> {
                source = library.getHistory();
                header = new MutableLiveData<>(new Header(null, null, 4, false));
            }
            case SONGS -> {
                source = library.getAllTracks();
                header = new MutableLiveData<>(new Header(null, null, 2, false));
            }
            case MOST_PLAYED -> {
                source = browser.getMostPlayed();
                header = new MutableLiveData<>(new Header(null, null, 3, false));
            }
            case NEVER_PLAYED -> {
                source = browser.getNeverPlayed();
                header = new MutableLiveData<>(new Header(null, null, 5, false));
            }
            default -> {
                source = library.getRecentlySaved();
                header = new MutableLiveData<>(new Header(null, null, 6, false));
            }
        }
        rows.addSource(source, list -> resort());
        rows.addSource(sort, s -> {
            if (s.field() == SortOrder.Field.MOST_PLAYED && !countsAttached) {
                countsAttached = true; // only listen to play counts when they matter (each play would re-sort)
                rows.addSource(browser.getPlayCounts(), counts -> {
                    playCounts = counts;
                    resort();
                });
            }
            resort();
        });
    }

    private static List<String> single(@Nullable String uri) {
        return uri == null ? Collections.emptyList() : Collections.singletonList(uri);
    }

    private static int parseYear(@Nullable String id) {
        try {
            return id == null ? 0 : Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void resort() {
        List<Track> list = source.getValue();
        SortOrder order = sort.getValue();
        if (list == null || order == null) return;
        int gen = ++sortGeneration;
        if (order.field() == SortOrder.Field.DEFAULT && order.ascending()) {
            rows.setValue(new Rows(list, Collections.emptyList()));
            pruneSelection(list);
            return;
        }
        Map<String, Integer> counts = playCounts;
        sorter.execute(() -> {
            List<Track> sorted = order.apply(list, counts, locale);
            List<Section> sections = order.alphabetical() ? sections(sorted, order.field(), locale) : List.of();
            Rows result = new Rows(sorted, sections);
            if (gen == sortGeneration) rows.postValue(result);
        });
        pruneSelection(list);
    }

    static List<Section> sections(List<Track> sorted, SortOrder.Field field, Locale locale) {
        List<Section> out = new ArrayList<>();
        String last = null;
        for (int i = 0; i < sorted.size(); i++) {
            String label = SortOrder.sectionOf(SortOrder.key(field, sorted.get(i)), locale);
            if (!label.equals(last)) {
                out.add(new Section(label, i));
                last = label;
            }
        }
        return out;
    }

    public TrackList kind() { return kind; }

    @Nullable public String id() { return id; }

    public LiveData<Rows> getRows() { return rows; }

    /** Current rows (sorted), or an empty list. */
    public List<Track> current() {
        Rows r = rows.getValue();
        return r == null ? Collections.emptyList() : r.tracks();
    }

    public LiveData<Header> getHeader() { return header; }

    public LiveData<SortOrder> getSort() { return sort; }

    public void setSort(SortOrder order) { sort.setValue(order); }

    // ---- playback ----

    public void playAt(int index, String playingFrom) {
        List<Track> list = current();
        if (index >= 0 && index < list.size()) player.play(list, index, playingFrom);
    }

    public void shuffle(String playingFrom) {
        List<Track> list = current();
        if (list.isEmpty()) return;
        List<Track> copy = new ArrayList<>(list);
        Collections.shuffle(copy);
        player.play(copy, 0, playingFrom);
    }

    // ---- multi-select ----

    public LiveData<Set<String>> getSelection() { return selection; }

    public boolean selecting() { return !selection.getValue().isEmpty(); }

    public void toggleSelected(String trackId) {
        Set<String> next = new LinkedHashSet<>(selection.getValue());
        if (!next.remove(trackId)) next.add(trackId);
        selection.setValue(Collections.unmodifiableSet(next));
    }

    public void selectAll() {
        Set<String> all = new LinkedHashSet<>();
        for (Track t : current()) all.add(t.id());
        selection.setValue(Collections.unmodifiableSet(all));
    }

    public void clearSelection() {
        if (selecting()) selection.setValue(Collections.emptySet());
    }

    /** Selected tracks in list order. */
    public List<Track> selectedTracks() {
        Set<String> ids = selection.getValue();
        List<Track> out = new ArrayList<>(ids.size());
        for (Track t : current()) if (ids.contains(t.id())) out.add(t);
        return out;
    }

    /** Selected tracks go after the current one, in list order. */
    public void playSelectedNext() {
        List<Track> picked = selectedTracks();
        for (int i = picked.size() - 1; i >= 0; i--) player.playNext(picked.get(i));
        clearSelection();
    }

    public void queueSelected() {
        for (Track t : selectedTracks()) player.addToQueue(t);
        clearSelection();
    }

    /** Likes every selected track; if all were liked already, unlikes them instead. */
    public void likeSelected() {
        List<Track> picked = selectedTracks();
        boolean allLiked = true;
        for (Track t : picked) allLiked &= t.liked();
        for (Track t : picked) if (t.liked() == allLiked) library.toggleLike(t.id());
        clearSelection();
    }

    private void pruneSelection(List<Track> list) {
        Set<String> sel = selection.getValue();
        if (sel.isEmpty()) return;
        Set<String> present = new LinkedHashSet<>();
        for (Track t : list) if (sel.contains(t.id())) present.add(t.id());
        if (present.size() != sel.size()) selection.setValue(Collections.unmodifiableSet(present));
    }

    // ---- playlist edits (PLAYLIST kind) ----

    @Nullable public Removed removeAt(int index) {
        List<Track> list = current();
        if (kind != TrackList.PLAYLIST || index < 0 || index >= list.size()) return null;
        Track t = list.get(index);
        admin.removeFromPlaylist(id, t.id());
        return new Removed(t, index);
    }

    public void undoRemove(Removed r) {
        browser.restoreToPlaylist(id, r.track().id(), r.index());
    }

    public void removeSelectedFromPlaylist() {
        if (kind != TrackList.PLAYLIST) return;
        for (Track t : selectedTracks()) admin.removeFromPlaylist(id, t.id());
        clearSelection();
    }

    public void move(int from, int to) {
        if (kind == TrackList.PLAYLIST && from != to) admin.moveInPlaylist(id, from, to);
    }

    public void rename(String name) {
        if (kind == TrackList.PLAYLIST && !name.trim().isEmpty()) admin.renamePlaylist(id, name.trim());
    }

    /** Deletes the playlist and returns what is needed to recreate it. */
    @Nullable public DeletedPlaylist deletePlaylist() {
        if (kind != TrackList.PLAYLIST) return null;
        Header h = header.getValue();
        List<String> ids = new ArrayList<>();
        for (Track t : current()) ids.add(t.id());
        admin.deletePlaylist(id);
        return new DeletedPlaylist(h == null || h.title() == null ? "" : h.title(), ids);
    }

    /** Recreates a deleted playlist (new id) with the same name and members. */
    public static void undoDelete(LibraryRepository library, LibraryBrowser browser, DeletedPlaylist d) {
        library.createPlaylist(d.name(), p -> {
            if (p != null) browser.addAllToPlaylist(p.id(), d.trackIds(), null);
        });
    }

    public static ViewModelProvider.Factory factory(LibraryRepository library, LibraryAdmin admin,
            LibraryBrowser browser, PlayerController player, Executor sorter, Locale locale, TrackList kind,
            @Nullable String id, SortOrder initialSort) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new TrackListViewModel(library, admin, browser, player, sorter, locale, kind, id,
                        initialSort));
            }
        };
    }
}
