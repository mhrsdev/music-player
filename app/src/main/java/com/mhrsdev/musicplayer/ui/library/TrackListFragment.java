package com.mhrsdev.musicplayer.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.LibraryListHeaderBinding;
import com.mhrsdev.musicplayer.databinding.LibraryTrackListBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.util.List;

/** AlbumDetail / ArtistDetail / PlaylistDetail / Liked / History / Recently saved: header + track rows. */
public final class TrackListFragment extends Fragment implements TrackAdapter.Listener {
    private static final String ARG_KIND = "kind";
    private static final String ARG_ID = "id";

    private LibraryTrackListBinding b;
    private TrackListViewModel vm;
    private TrackList kind;
    private String playingFrom = "";

    public static TrackListFragment newInstance(TrackList kind, @Nullable String id) {
        TrackListFragment f = new TrackListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_KIND, kind.name());
        args.putString(ARG_ID, id);
        f.setArguments(args);
        return f;
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = LibraryTrackListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = requireArguments();
        kind = TrackList.valueOf(args.getString(ARG_KIND));
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, TrackListViewModel.factory(c.library, c.player, kind, args.getString(ARG_ID)))
                .get(TrackListViewModel.class);

        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        HeaderAdapter header = new HeaderAdapter();
        TrackAdapter tracks = new TrackAdapter(this);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(new ConcatAdapter(header, tracks));

        vm.getHeader().observe(getViewLifecycleOwner(), h -> {
            if (h == null) return;
            playingFrom = h.title() != null ? h.title() : getString(fallbackTitle());
            header.bind(h, playingFrom);
        });
        vm.getTracks().observe(getViewLifecycleOwner(), list -> {
            tracks.submitList(list);
            header.setCount(list == null ? 0 : list.size(), list);
            if (list != null && list.isEmpty()) {
                StateView.empty(b.state, getString(emptyText()), null, null, null);
            } else {
                StateView.hide(b.state);
            }
        });
    }

    private int fallbackTitle() {
        return switch (kind) {
            case LIKED -> R.string.library_liked;
            case HISTORY -> R.string.library_history;
            case RECENT -> R.string.library_recently_saved;
            default -> R.string.library_title;
        };
    }

    private int emptyText() {
        return switch (kind) {
            case LIKED -> R.string.library_liked_empty;
            case HISTORY -> R.string.library_history_empty;
            case PLAYLIST -> R.string.library_playlist_empty;
            default -> R.string.library_empty;
        };
    }

    @Override public void onTrackClick(List<Track> list, int position) {
        vm.playAt(position, playingFrom);
    }

    @Override public void onTrackMore(View anchor, Track track) {
        TrackMenu.show(anchor, track);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    /** Single header row inside the list so the whole screen scrolls and recycles together. */
    private final class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.Holder> {
        private TrackListViewModel.Header header;
        private String title = "";
        private int count;
        private long totalMs;

        void bind(TrackListViewModel.Header h, String title) {
            this.header = h;
            this.title = title;
            notifyItemChanged(0);
        }

        void setCount(int n, @Nullable List<Track> list) {
            count = n;
            totalMs = list == null ? 0 : LibraryViewModel.totalMs(list);
            notifyItemChanged(0);
        }

        @Override public int getItemCount() { return 1; }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LibraryListHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.title, title);
            String songs = getResources().getQuantityString(R.plurals.library_songs, count, count)
                    + " · " + Format.duration(totalMs, getResources().getConfiguration().getLocales().get(0));
            String sub = header != null && header.subtitle() != null ? header.subtitle() + " · " + songs : songs;
            com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.subtitle, sub);
            Art.bind(h.b.art, null, header == null ? 0 : header.seed(), 32, header != null && header.circle());
            h.b.play.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_play, 0, 0, 0);
            h.b.shuffle.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_shuffle, 0, 0, 0);
            h.b.play.setOnClickListener(v -> vm.playAt(0, playingFrom));
            h.b.shuffle.setOnClickListener(v -> vm.shuffle(playingFrom));
            h.b.play.setEnabled(count > 0);
            h.b.shuffle.setEnabled(count > 0);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final LibraryListHeaderBinding b;

            Holder(LibraryListHeaderBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
