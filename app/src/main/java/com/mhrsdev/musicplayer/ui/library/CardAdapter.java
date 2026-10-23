package com.mhrsdev.musicplayer.ui.library;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.databinding.HomeCardBinding;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;

/** 152 dp cover cards for a horizontal row of tracks (Library "Recently saved"). */
final class CardAdapter extends ListAdapter<Track, CardAdapter.Holder> {

    interface Listener {
        void onCardClick(List<Track> list, int position);
    }

    private static final DiffUtil.ItemCallback<Track> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Track a, @NonNull Track b) { return a.id().equals(b.id()); }

        @Override public boolean areContentsTheSame(@NonNull Track a, @NonNull Track b) { return a.equals(b); }
    };

    private final Listener listener;

    CardAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(HomeCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Track t = getItem(position);
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.title, t.title());
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.subtitle, t.artistName());
        Art.bind(h.b.art, t.artworkUri(), t.placeholderSeed(), 24, false);
        h.itemView.setContentDescription(t.title() + ", " + t.artistName());
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onCardClick(getCurrentList(), p);
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HomeCardBinding b;

        Holder(HomeCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
