package com.mhrsdev.musicplayer.ui.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.LibraryRowTrackBinding;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.ui.library.Art;
import com.mhrsdev.musicplayer.ui.library.Format;
import com.mhrsdev.musicplayer.ui.library.Highlight;
import com.mhrsdev.musicplayer.ui.library.TrackMenu;

/** One row per result; tracks show duration + overflow, albums/artists/playlists show their kind. */
final class SearchResultAdapter extends ListAdapter<SearchResult, SearchResultAdapter.Holder> {

    interface Listener {
        void onResultClick(SearchResult result);
    }

    static String key(SearchResult r) {
        return switch (r.kind()) {
            case TRACK -> "t" + r.track().id();
            case ALBUM -> "a" + r.album().id();
            case ARTIST -> "r" + r.artist().id();
            case PLAYLIST -> "p" + r.playlist().id();
        };
    }

    private final Listener listener;
    @Nullable private String query;

    SearchResultAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override public boolean areItemsTheSame(@NonNull SearchResult a, @NonNull SearchResult b) {
                return key(a).equals(key(b));
            }

            @Override public boolean areContentsTheSame(@NonNull SearchResult a, @NonNull SearchResult b) {
                return a.equals(b);
            }
        });
        this.listener = listener;
    }

    /** Changing the query re-binds visible rows so the highlight follows. */
    void setQuery(@Nullable String q) {
        query = q;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LibraryRowTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        SearchResult r = getItem(position);
        Context c = h.itemView.getContext();
        String title;
        String sub;
        int seed;
        String art = null;
        boolean track = r.kind() == SearchResult.Kind.TRACK;
        switch (r.kind()) {
            case TRACK -> {
                title = r.track().title();
                sub = r.track().artistName();
                seed = r.track().placeholderSeed();
                art = r.track().artworkUri();
                h.b.duration.setText(Format.duration(r.track().durationMs(),
                        c.getResources().getConfiguration().getLocales().get(0)));
            }
            case ALBUM -> {
                title = r.album().title();
                sub = c.getString(R.string.search_kind_album) + " · " + r.album().artistName();
                seed = r.album().placeholderSeed();
                art = r.album().artworkUri();
            }
            case ARTIST -> {
                title = r.artist().name();
                sub = c.getString(R.string.search_kind_artist);
                seed = r.artist().placeholderSeed();
                art = r.artist().imageUri();
            }
            default -> {
                title = r.playlist().name();
                sub = c.getString(R.string.search_kind_playlist);
                seed = r.playlist().placeholderSeed();
            }
        }
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.title, Highlight.apply(title, query));
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.subtitle, sub);
        h.b.explicit.setVisibility(track && r.track().explicit() ? View.VISIBLE : View.GONE);
        h.b.duration.setVisibility(track ? View.VISIBLE : View.GONE);
        h.b.more.setVisibility(track ? View.VISIBLE : View.GONE);
        h.b.more.setContentDescription(c.getString(R.string.library_more, title));
        if (track) h.b.more.setOnClickListener(v -> TrackMenu.show(v, r.track()));
        if (r.kind() == SearchResult.Kind.ARTIST) {
            Art.artist(h.b.art, art, title, seed);
        } else {
            Art.bind(h.b.art, art, seed, 12, false);
        }
        h.itemView.setOnClickListener(v -> listener.onResultClick(r));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LibraryRowTrackBinding b;

        Holder(LibraryRowTrackBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
