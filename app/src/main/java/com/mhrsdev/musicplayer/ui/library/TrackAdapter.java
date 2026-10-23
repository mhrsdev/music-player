package com.mhrsdev.musicplayer.ui.library;

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
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;
import java.util.Locale;

/** Standard 72 dp track row (art 52 dp / title / artist / duration / overflow), shared by Home, Search and Library. */
public final class TrackAdapter extends ListAdapter<Track, TrackAdapter.Holder> {

    public interface Listener {
        void onTrackClick(List<Track> list, int position);

        void onTrackMore(View anchor, Track track);
    }

    private static final DiffUtil.ItemCallback<Track> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Track a, @NonNull Track b) { return a.id().equals(b.id()); }

        @Override public boolean areContentsTheSame(@NonNull Track a, @NonNull Track b) { return a.equals(b); }
    };

    private final Listener listener;
    @Nullable private String highlight;

    public TrackAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Text to bold inside titles (search match highlight). */
    public void setHighlight(@Nullable String query) {
        highlight = query;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LibraryRowTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Track t = getItem(position);
        Locale locale = h.itemView.getResources().getConfiguration().getLocales().get(0);
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.title, Highlight.apply(t.title(), highlight));
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.subtitle, t.artistName());
        h.b.duration.setText(Format.duration(t.durationMs(), locale));
        h.b.explicit.setVisibility(t.explicit() ? View.VISIBLE : View.GONE);
        h.b.more.setContentDescription(h.itemView.getContext().getString(R.string.library_more, t.title()));
        Art.bind(h.b.art, t.artworkUri(), t.placeholderSeed(), 12, false);
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onTrackClick(getCurrentList(), p);
        });
        h.b.more.setOnClickListener(v -> listener.onTrackMore(v, t));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LibraryRowTrackBinding b;

        Holder(LibraryRowTrackBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
