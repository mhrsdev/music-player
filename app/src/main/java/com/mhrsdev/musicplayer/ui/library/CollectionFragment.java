package com.mhrsdev.musicplayer.ui.library;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.LibraryCollectionBinding;
import com.mhrsdev.musicplayer.databinding.LibraryGridItemBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.YearCount;
import com.mhrsdev.musicplayer.ui.playlist.PlaylistDialogs;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.Collection;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.util.ArrayList;
import java.util.List;

/** Two-column grid of albums, artists (circular, DESIGN.md 04) or playlists. */
public final class CollectionFragment extends Fragment {
    private static final String ARG_KIND = "kind";
    private LibraryCollectionBinding b;

    public static CollectionFragment newInstance(Collection kind) {
        CollectionFragment f = new CollectionFragment();
        Bundle args = new Bundle();
        args.putString(ARG_KIND, kind.name());
        f.setArguments(args);
        return f;
    }

    /** Grid cell model shared by the three kinds. */
    record Cell(String id, String title, String subtitle, @Nullable String art, int seed, boolean circle,
            @Nullable List<String> mosaic) {
        Cell(String id, String title, String subtitle, @Nullable String art, int seed, boolean circle) {
            this(id, title, subtitle, art, seed, circle, null);
        }
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = LibraryCollectionBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Collection kind = Collection.valueOf(requireArguments().getString(ARG_KIND));
        AppContainer c = Screens.container(this);
        LibraryViewModel vm = new ViewModelProvider(this, LibraryViewModel.factory(c.library, c.player, c.libraryBrowser()))
                .get(LibraryViewModel.class);
        Resources r = getResources();
        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        CellAdapter adapter = new CellAdapter(cell -> Screens.openTrackList(this, switch (kind) {
            case ALBUMS -> TrackList.ALBUM;
            case ARTISTS -> TrackList.ARTIST;
            case PLAYLISTS -> TrackList.PLAYLIST;
            case YEARS -> TrackList.YEAR;
        }, cell.id()));
        if (kind == Collection.PLAYLISTS) {
            adapter.longClick = cell -> playlistOptions(c, cell);
            b.newPlaylist.setVisibility(View.VISIBLE);
            b.newPlaylist.setOnClickListener(v -> PlaylistDialogs.create(requireContext(), c.library,
                    p -> { if (p != null && isAdded()) Screens.openTrackList(this, TrackList.PLAYLIST, p.id()); }));
        }
        b.grid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        b.grid.setAdapter(adapter);

        switch (kind) {
            case ALBUMS -> {
                b.title.setText(R.string.library_albums);
                vm.getAlbums().observe(getViewLifecycleOwner(), list -> {
                    List<Cell> cells = new ArrayList<>();
                    for (Album a : list) cells.add(new Cell(a.id(), a.title(), a.artistName(), a.artworkUri(), a.placeholderSeed(), false));
                    show(adapter, cells);
                });
            }
            case ARTISTS -> {
                b.title.setText(R.string.library_artists);
                vm.getArtists().observe(getViewLifecycleOwner(), list -> {
                    List<Cell> cells = new ArrayList<>();
                    for (Artist a : list) {
                        cells.add(new Cell(a.id(), a.name(),
                                r.getQuantityString(R.plurals.library_songs, a.trackCount(), a.trackCount()),
                                a.imageUri(), a.placeholderSeed(), true));
                    }
                    show(adapter, cells);
                });
            }
            case PLAYLISTS -> {
                b.title.setText(R.string.library_playlists);
                vm.getPlaylists().observe(getViewLifecycleOwner(), list -> {
                    List<Cell> cells = new ArrayList<>();
                    for (Playlist p : list) {
                        cells.add(new Cell(p.id(), p.name(),
                                r.getQuantityString(R.plurals.library_songs, p.trackCount(), p.trackCount()),
                                p.artworkUris().isEmpty() ? null : p.artworkUris().get(0), p.placeholderSeed(), false,
                                p.artworkUris()));
                    }
                    show(adapter, cells);
                });
            }
            case YEARS -> {
                b.title.setText(R.string.u2_years);
                vm.getYears().observe(getViewLifecycleOwner(), list -> {
                    List<Cell> cells = new ArrayList<>();
                    java.util.Locale locale = r.getConfiguration().getLocales().get(0);
                    for (YearCount y : list) {
                        cells.add(new Cell(String.valueOf(y.year()), String.format(locale, "%d", y.year()),
                                r.getQuantityString(R.plurals.library_songs, y.trackCount(), y.trackCount()),
                                null, y.year(), false));
                    }
                    show(adapter, cells);
                });
            }
        }
    }

    /** Long-press on a playlist: rename / delete (with undo). */
    private void playlistOptions(AppContainer c, Cell cell) {
        TrackListViewModel.Header h = new TrackListViewModel.Header(cell.title(), null, cell.seed(), false,
                cell.mosaic() == null ? List.of() : cell.mosaic());
        PlaylistMenuSheet.show(requireActivity(), h,
                () -> PlaylistDialogs.rename(requireContext(), cell.title(), name -> c.libraryAdmin().renamePlaylist(cell.id(), name)),
                () -> PlaylistDialogs.confirmDelete(requireContext(), cell.title(), () -> deletePlaylist(c, cell)),
                () -> Screens.openTrackList(this, TrackList.PLAYLIST, cell.id()));
    }

    private void deletePlaylist(AppContainer c, Cell cell) {
        // snapshot the members first so Undo can rebuild the playlist
        androidx.lifecycle.LiveData<List<com.mhrsdev.musicplayer.domain.model.Track>> members = c.library.getPlaylistTracks(cell.id());
        members.observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<>() {
            @Override public void onChanged(List<com.mhrsdev.musicplayer.domain.model.Track> list) {
                members.removeObserver(this);
                List<String> ids = new ArrayList<>();
                for (com.mhrsdev.musicplayer.domain.model.Track t : list) ids.add(t.id());
                c.libraryAdmin().deletePlaylist(cell.id());
                TrackListViewModel.DeletedPlaylist d = new TrackListViewModel.DeletedPlaylist(cell.title(), ids);
                com.google.android.material.snackbar.Snackbar.make(requireActivity().findViewById(android.R.id.content),
                        getString(R.string.u2_playlist_deleted, cell.title()),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.queue_undo, v -> TrackListViewModel.undoDelete(c.library, c.libraryBrowser(), d))
                        .show();
            }
        });
    }

    private void show(CellAdapter adapter, List<Cell> cells) {
        adapter.submitList(cells);
        if (cells.isEmpty() && b.newPlaylist.getVisibility() == View.VISIBLE) {
            StateView.empty(b.state, getString(R.string.u2_no_playlists), getString(R.string.u2_no_playlists_body),
                    getString(R.string.library_new_playlist), v -> b.newPlaylist.performClick());
        } else if (cells.isEmpty()) {
            StateView.empty(b.state, getString(R.string.library_empty), null, null, null);
        } else {
            StateView.hide(b.state);
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private static final class CellAdapter extends ListAdapter<Cell, CellAdapter.Holder> {
        interface Listener {
            void onClick(Cell cell);
        }

        private final Listener listener;
        @Nullable Listener longClick;

        CellAdapter(Listener listener) {
            super(new DiffUtil.ItemCallback<>() {
                @Override public boolean areItemsTheSame(@NonNull Cell a, @NonNull Cell b) { return a.id().equals(b.id()); }

                @Override public boolean areContentsTheSame(@NonNull Cell a, @NonNull Cell b) { return a.equals(b); }
            });
            this.listener = listener;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LibraryGridItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            Cell cell = getItem(position);
            com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.title, cell.title());
            com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.subtitle, cell.subtitle());
            int gravity = cell.circle() ? Gravity.CENTER_HORIZONTAL : Gravity.START;
            h.b.title.setGravity(gravity);
            h.b.subtitle.setGravity(gravity);
            boolean mosaic = cell.mosaic() != null;
            h.b.mosaic.setVisibility(mosaic ? View.VISIBLE : View.GONE);
            h.b.art.setVisibility(mosaic ? View.INVISIBLE : View.VISIBLE);
            if (mosaic) {
                h.b.mosaic.setCovers(cell.mosaic(), cell.seed());
            } else if (cell.circle()) {
                Art.artist(h.b.art, cell.art(), cell.title(), cell.seed());
            } else {
                Art.bind(h.b.art, cell.art(), cell.seed(), 24, false);
            }
            h.itemView.setContentDescription(cell.title() + ", " + cell.subtitle());
            h.itemView.setOnClickListener(v -> listener.onClick(cell));
            h.itemView.setOnLongClickListener(longClick == null ? null : v -> {
                longClick.onClick(cell);
                return true;
            });
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final LibraryGridItemBinding b;

            Holder(LibraryGridItemBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
