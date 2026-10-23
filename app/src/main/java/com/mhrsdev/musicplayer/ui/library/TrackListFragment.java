package com.mhrsdev.musicplayer.ui.library;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.LibraryListHeaderBinding;
import com.mhrsdev.musicplayer.databinding.LibraryTrackListBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.playlist.AddToPlaylistSheet;
import com.mhrsdev.musicplayer.ui.playlist.PlaylistDialogs;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Album / Artist / Playlist / Year detail and the Songs, Liked, History, Recently added, Most played, Never played
 * lists: header + track rows, sort, multi-select with a contextual bar, and (playlists) rename / delete with undo,
 * swipe-to-remove with undo and drag-to-reorder.
 */
public class TrackListFragment extends Fragment implements TrackAdapter.Listener {
    private static final String ARG_KIND = "kind";
    private static final String ARG_ID = "id";

    private LibraryTrackListBinding b;
    private TrackListViewModel vm;
    private TrackList kind;
    private TrackAdapter tracks;
    private HeaderAdapter header;
    private EmptyAdapter empty;
    private SortPrefs sortPrefs;
    private ItemTouchHelper touchHelper;
    private String playingFrom = "";
    private OnBackPressedCallback exitSelection;

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
        sortPrefs = new SortPrefs(requireContext());
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        SortOrder initial = SortPrefs.sortable(kind) ? sortPrefs.get(kind) : SortOrder.DEFAULT;
        vm = new ViewModelProvider(this, TrackListViewModel.factory(c.library, c.libraryAdmin(), c.libraryBrowser(),
                c.player, c.ioExecutor, locale, kind, args.getString(ARG_ID), initial)).get(TrackListViewModel.class);

        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        header = new HeaderAdapter();
        tracks = new TrackAdapter(this);
        tracks.setReorderable(kind == TrackList.PLAYLIST);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        b.list.setLayoutManager(lm);
        empty = new EmptyAdapter();
        b.list.setAdapter(new ConcatAdapter(header, tracks, empty));
        StateView.hide(b.state);
        b.list.setHasFixedSize(true);
        b.scroller.attach(b.list, 1);
        if (kind == TrackList.PLAYLIST) {
            touchHelper = new ItemTouchHelper(new PlaylistTouch());
            touchHelper.attachToRecyclerView(b.list);
        }

        vm.getHeader().observe(getViewLifecycleOwner(), h -> {
            if (h == null) {
                // playlist deleted elsewhere (or unknown id): nothing to show
                if (kind == TrackList.PLAYLIST && isResumed()) getParentFragmentManager().popBackStack();
                return;
            }
            playingFrom = h.title() != null ? h.title() : getString(fallbackTitle());
            header.bind(h, playingFrom);
        });
        vm.getRows().observe(getViewLifecycleOwner(), rows -> {
            List<Track> list = rows.tracks();
            tracks.submitList(list);
            b.scroller.setData(list.size(), rows.sections());
            header.setCount(list);
            empty.setShown(list.isEmpty());
        });
        vm.getSort().observe(getViewLifecycleOwner(), s -> header.notifyItemChanged(0));
        SortSheet.listen(getParentFragmentManager(), getViewLifecycleOwner(), picked -> {
            sortPrefs.put(kind, picked);
            vm.setSort(picked);
        });
        PlaylistMenuSheet.listen(getParentFragmentManager(), getViewLifecycleOwner(), this::onPlaylistAction);
        exitSelection = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { vm.clearSelection(); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), exitSelection);
        vm.getSelection().observe(getViewLifecycleOwner(), this::renderSelection);
        bindSelectionBar(c);
    }

    private void bindEmpty(com.mhrsdev.musicplayer.databinding.U2ListEmptyBinding e) {
        e.emptyIcon.setImageResource(symbol());
        e.emptyTitle.setText(kind == TrackList.PLAYLIST ? R.string.library_playlist_empty : emptyTitle());
        int body = emptyText();
        e.emptyBody.setVisibility(body == 0 ? View.GONE : View.VISIBLE);
        if (body != 0) e.emptyBody.setText(body);
        boolean find = kind == TrackList.PLAYLIST || kind == TrackList.LIKED || kind == TrackList.SONGS;
        e.emptyAction.setVisibility(find ? View.VISIBLE : View.GONE);
        e.emptyAction.setText(R.string.u2_find_songs);
        e.emptyAction.setOnClickListener(v -> {
            ScreenNavigator nav = ScreenNavigator.from(requireContext());
            if (nav != null) nav.openTab(ScreenNavigator.Tab.SEARCH);
        });
    }

    /** The list's symbol: empty-state badge and the cover of the built-in lists. */
    private int symbol() {
        return switch (kind) {
            case LIKED -> R.drawable.ic_mp_heart_filled;
            case HISTORY -> R.drawable.nav_ic_history;
            case MOST_PLAYED -> R.drawable.u2_ic_trending;
            case RECENT, NEVER_PLAYED -> R.drawable.u2_ic_sparkle;
            case YEAR -> R.drawable.u2_ic_calendar;
            case PLAYLIST -> R.drawable.library_ic_playlist;
            case ALBUM -> R.drawable.library_ic_album;
            case ARTIST -> R.drawable.library_ic_artist;
            default -> R.drawable.ic_mp_note;
        };
    }

    private int emptyTitle() {
        return switch (kind) {
            case LIKED -> R.string.u2_empty_liked;
            case HISTORY, MOST_PLAYED -> R.string.u2_empty_history;
            case NEVER_PLAYED -> R.string.u2_never_played_empty;
            default -> R.string.library_empty;
        };
    }

    private int fallbackTitle() {
        return switch (kind) {
            case LIKED -> R.string.library_liked;
            case HISTORY -> R.string.library_history;
            case RECENT -> R.string.library_recently_saved;
            case SONGS -> R.string.u2_songs;
            case MOST_PLAYED -> R.string.u2_most_played;
            case NEVER_PLAYED -> R.string.u2_never_played;
            default -> R.string.library_title;
        };
    }

    /** Hint under the empty title; 0 = none. */
    private int emptyText() {
        return switch (kind) {
            case LIKED -> R.string.library_liked_empty;
            case HISTORY, MOST_PLAYED -> R.string.library_history_empty;
            case PLAYLIST -> R.string.u2_playlist_empty_body;
            case SONGS -> R.string.u2_empty_songs_body;
            default -> 0;
        };
    }

    // ---- rows ----

    @Override public void onTrackClick(List<Track> list, int position) {
        if (vm.selecting()) {
            vm.toggleSelected(list.get(position).id());
        } else {
            vm.playAt(position, playingFrom);
        }
    }

    @Override public void onTrackLongClick(Track track, int position) {
        vm.toggleSelected(track.id());
        b.list.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    @Override public void onTrackMore(View anchor, Track track) {
        TrackMenu.show(anchor, track, kind == TrackList.PLAYLIST ? vm.id() : null);
    }

    @Override public void onStartDrag(RecyclerView.ViewHolder holder) {
        if (touchHelper != null && !vm.selecting()) touchHelper.startDrag(holder);
    }

    // ---- multi-select ----

    private void renderSelection(Set<String> selected) {
        tracks.setSelection(selected);
        boolean on = !selected.isEmpty();
        exitSelection.setEnabled(on);
        if (on != (b.selectionBar.getVisibility() == View.VISIBLE)) {
            b.selectionBar.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) {
                b.selectionBar.setTranslationY(-b.selectionBar.getHeight() / 2f);
                b.selectionBar.setAlpha(0f);
                b.selectionBar.animate().translationY(0).alpha(1f).setDuration(180).start();
            }
        }
        if (on) {
            String count = getResources().getQuantityString(R.plurals.u2_selected, selected.size(), selected.size());
            b.selCount.setText(count);
            boolean allLiked = true;
            for (Track t : vm.selectedTracks()) allLiked &= t.liked();
            b.selLike.setText(allLiked ? R.string.library_menu_unlike : R.string.library_menu_like);
            b.selLike.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    allLiked ? R.drawable.ic_mp_heart_filled : R.drawable.ic_mp_heart, 0, 0, 0);
        }
    }

    private void bindSelectionBar(AppContainer c) {
        b.selClose.setOnClickListener(v -> vm.clearSelection());
        b.selAll.setOnClickListener(v -> vm.selectAll());
        b.selPlayNext.setOnClickListener(v -> vm.playSelectedNext());
        b.selQueue.setOnClickListener(v -> vm.queueSelected());
        b.selLike.setOnClickListener(v -> vm.likeSelected());
        b.selPlaylist.setOnClickListener(v -> {
            AddToPlaylistSheet.show(getParentFragmentManager(), vm.selectedTracks());
            vm.clearSelection();
        });
        b.selShare.setOnClickListener(v -> TrackActions.share(requireActivity(), vm.selectedTracks()));
        b.selDelete.setOnClickListener(v -> {
            TrackActions.deleteFromDevice(requireActivity(), vm.selectedTracks());
            vm.clearSelection();
        });
        b.selRemove.setVisibility(kind == TrackList.PLAYLIST ? View.VISIBLE : View.GONE);
        b.selRemove.setOnClickListener(v -> vm.removeSelectedFromPlaylist());
    }

    // ---- playlist ----

    private void playlistMenu(View anchor) {
        TrackListViewModel.Header h = vm.getHeader().getValue();
        if (h != null) PlaylistMenuSheet.show(getParentFragmentManager(), vm.id(), h);
    }

    private void onPlaylistAction(String playlistId, PlaylistMenuSheet.Action action) {
        TrackListViewModel.Header h = vm.getHeader().getValue();
        if (!playlistId.equals(vm.id()) || h == null) return;
        String name = h.title() == null ? "" : h.title();
        switch (action) {
            case RENAME -> PlaylistDialogs.rename(requireContext(), name, vm::rename);
            case DELETE -> PlaylistDialogs.confirmDelete(requireContext(), name, this::deletePlaylist);
            case ADD_SONGS -> {
                ScreenNavigator nav = ScreenNavigator.from(requireContext());
                if (nav != null) nav.openTab(ScreenNavigator.Tab.SEARCH);
            }
        }
    }

    private void deletePlaylist() {
        TrackListViewModel.DeletedPlaylist d = vm.deletePlaylist();
        if (d == null) return;
        AppContainer c = Screens.container(this);
        View root = requireActivity().findViewById(android.R.id.content);
        Snackbar.make(root, getString(R.string.u2_playlist_deleted, d.name()), Snackbar.LENGTH_LONG)
                .setAction(R.string.queue_undo, v -> TrackListViewModel.undoDelete(c.library, c.libraryBrowser(), d))
                .show();
        getParentFragmentManager().popBackStack();
    }

    private class PlaylistTouch extends ItemTouchHelper.Callback {
        private int dragFrom = -1, dragTo = -1;

        @Override public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h) {
            if (!(h instanceof TrackAdapter.Holder) || vm.selecting()) return 0;
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.START | ItemTouchHelper.END);
        }

        @Override public boolean isLongPressDragEnabled() { return false; } // long-press = multi-select

        @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                @NonNull RecyclerView.ViewHolder to) {
            if (!(to instanceof TrackAdapter.Holder)) return false;
            int f = from.getBindingAdapterPosition(), t = to.getBindingAdapterPosition();
            if (dragFrom < 0) dragFrom = f;
            dragTo = t;
            tracks.moveLocal(f, t);
            return true;
        }

        @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h) {
            super.clearView(rv, h);
            h.itemView.setActivated(false);
            if (dragFrom >= 0 && dragTo >= 0 && dragFrom != dragTo) vm.move(dragFrom, dragTo);
            dragFrom = dragTo = -1;
        }

        @Override public void onSelectedChanged(@Nullable RecyclerView.ViewHolder h, int state) {
            super.onSelectedChanged(h, state);
            if (state == ItemTouchHelper.ACTION_STATE_DRAG && h != null) h.itemView.setActivated(true); // lifted look
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder h, int direction) {
            TrackListViewModel.Removed r = vm.removeAt(h.getBindingAdapterPosition());
            if (r == null) return;
            Snackbar.make(b.getRoot(), getString(R.string.u2_removed_from_playlist, r.track().title()),
                    Snackbar.LENGTH_LONG).setAction(R.string.queue_undo, v -> vm.undoRemove(r)).show();
        }

        @Override public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h,
                float dx, float dy, int state, boolean active) {
            if (state == ItemTouchHelper.ACTION_STATE_SWIPE) {
                h.itemView.setAlpha(1f - Math.min(1f, Math.abs(dx) / h.itemView.getWidth()));
            }
            super.onChildDraw(c, rv, h, dx, dy, state, active);
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Empty state as its own row under the header: never overlaps it at any width or font scale. */
    private class EmptyAdapter extends RecyclerView.Adapter<EmptyAdapter.Holder> {
        private boolean shown;

        void setShown(boolean show) {
            if (show == shown) return;
            shown = show;
            if (show) notifyItemInserted(0); else notifyItemRemoved(0);
        }

        @Override public int getItemCount() { return shown ? 1 : 0; }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(com.mhrsdev.musicplayer.databinding.U2ListEmptyBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            bindEmpty(h.b);
        }

        class Holder extends RecyclerView.ViewHolder {
            final com.mhrsdev.musicplayer.databinding.U2ListEmptyBinding b;

            Holder(com.mhrsdev.musicplayer.databinding.U2ListEmptyBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    /** Single header row inside the list so the whole screen scrolls and recycles together. */
    private class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.Holder> {
        private TrackListViewModel.Header header;
        private String title = "";
        private int count;
        private long totalMs;

        void bind(TrackListViewModel.Header h, String title) {
            this.header = h;
            this.title = title;
            notifyItemChanged(0);
        }

        void setCount(List<Track> list) {
            count = list.size();
            totalMs = LibraryViewModel.totalMs(list);
            notifyItemChanged(0);
        }

        @Override public int getItemCount() { return 1; }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LibraryListHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            ScriptFont.setText(h.b.title, title);
            String songs = getResources().getQuantityString(R.plurals.library_songs, count, count)
                    + " · " + Format.duration(totalMs, getResources().getConfiguration().getLocales().get(0));
            String own = header == null ? null : header.subtitle();
            String sub = count == 0 ? own : own != null ? own + " · " + songs : songs;
            h.b.subtitle.setVisibility(sub == null ? View.GONE : View.VISIBLE);
            ScriptFont.setText(h.b.subtitle, sub);
            List<String> covers = header == null ? List.of() : header.artworkUris();
            int seed = header == null ? 0 : header.seed();
            boolean playlist = kind == TrackList.PLAYLIST;
            h.b.mosaic.setVisibility(playlist ? View.VISIBLE : View.GONE);
            h.b.art.setVisibility(playlist ? View.GONE : View.VISIBLE);
            if (playlist) {
                h.b.mosaic.setCornerRadiusDp(32);
                h.b.mosaic.setCovers(covers, seed);
            } else if (header != null && header.title() == null) {
                // built-in list: its symbol on the seeded gradient (a blank monogram suppresses the note glyph)
                h.b.art.setCircle(false);
                h.b.art.setCornerRadiusDp(32);
                h.b.art.setMonogram(" ", seed);
            } else if (header != null && header.circle()) {
                Art.artist(h.b.art, covers.isEmpty() ? null : covers.get(0), title, seed);
            } else {
                Art.bind(h.b.art, covers.isEmpty() ? null : covers.get(0), seed, 32, false);
            }
            h.b.play.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_play, 0, 0, 0);
            h.b.shuffle.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_shuffle, 0, 0, 0);
            h.b.play.setOnClickListener(v -> vm.playAt(0, playingFrom));
            h.b.shuffle.setOnClickListener(v -> vm.shuffle(playingFrom));
            // empty list: a smaller cover leaves room for the empty state above the mini player
            ViewGroup.LayoutParams box = h.b.artBox.getLayoutParams();
            int side = dp(count > 0 ? 200 : 144);
            if (box.width != side) {
                box.width = side;
                box.height = side;
                h.b.artBox.setLayoutParams(box);
            }
            h.b.artIcon.getLayoutParams().width = h.b.artIcon.getLayoutParams().height = dp(count > 0 ? 88 : 64);
            boolean builtIn = !playlist && header != null && header.title() == null;
            h.b.artIcon.setVisibility(builtIn ? View.VISIBLE : View.GONE);
            if (builtIn) h.b.artIcon.setImageResource(symbol());
            // nothing to play: hide Play / Shuffle (a playlist keeps its options button)
            h.b.play.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            h.b.shuffle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            h.b.actions.setVisibility(count > 0 || playlist ? View.VISIBLE : View.GONE);
            ((ViewGroup.MarginLayoutParams) h.b.headerMore.getLayoutParams()).setMarginStart(count > 0 ? dp(12) : 0);
            h.b.headerMore.setVisibility(playlist ? View.VISIBLE : View.GONE);
            h.b.headerMore.setOnClickListener(TrackListFragment.this::playlistMenu);

            boolean sortable = SortPrefs.sortable(kind);
            h.b.tools.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            h.b.sort.setVisibility(sortable ? View.VISIBLE : View.INVISIBLE);
            SortOrder order = vm.getSort().getValue();
            if (sortable && order != null) {
                String arrow = order.field() == SortOrder.Field.DEFAULT ? "" : order.ascending() ? "  ↑" : "  ↓";
                h.b.sort.setText(getString(SortSheet.label(order.field())) + arrow);
                h.b.sort.setContentDescription(getString(R.string.u2_sort_by) + ": " + getString(SortSheet.label(order.field())));
                h.b.sort.setOnClickListener(v -> SortSheet.show(getParentFragmentManager(), order));
            }
            h.b.select.setOnClickListener(v -> vm.selectAll());
        }

        class Holder extends RecyclerView.ViewHolder {
            final LibraryListHeaderBinding b;

            Holder(LibraryListHeaderBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
