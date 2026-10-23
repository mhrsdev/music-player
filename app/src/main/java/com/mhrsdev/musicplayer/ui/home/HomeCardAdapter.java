package com.mhrsdev.musicplayer.ui.home;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.HomeCardBinding;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.ui.library.Art;

/** Cards inside a Home carousel. The hero variant is the focused-card width of {@link HeroCarousel} with the cookie play on the cover. */
final class HomeCardAdapter extends ListAdapter<HomeItem, HomeCardAdapter.Holder> {

    interface Listener {
        void onItemClick(HomeItem item);
    }

    private static final DiffUtil.ItemCallback<HomeItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull HomeItem a, @NonNull HomeItem b) { return id(a).equals(id(b)); }

        @Override public boolean areContentsTheSame(@NonNull HomeItem a, @NonNull HomeItem b) { return a.equals(b); }
    };

    static String id(HomeItem i) {
        return switch (i.kind()) {
            case TRACK -> "t:" + i.track().id();
            case ALBUM -> "a:" + i.album().id();
            case PLAYLIST -> "p:" + i.playlist().id();
        };
    }

    private final boolean hero;
    private final Listener listener;

    HomeCardAdapter(boolean hero, Listener listener) {
        super(DIFF);
        this.hero = hero;
        this.listener = listener;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        HomeCardBinding b = HomeCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (hero) {
            float d = parent.getResources().getDisplayMetrics().density;
            int w = HeroCarousel.cardWidth(parent.getWidth(), d);
            b.getRoot().getLayoutParams().width = w;
            b.artFrame.getLayoutParams().height = w - Math.round(8 * d);
        }
        return new Holder(b);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        HomeItem item = getItem(position);
        Resources r = h.itemView.getResources();
        String title;
        String subtitle;
        int seed;
        String art;
        switch (item.kind()) {
            case TRACK -> {
                title = item.track().title();
                subtitle = item.track().artistName();
                seed = item.track().placeholderSeed();
                art = item.track().artworkUri();
            }
            case ALBUM -> {
                title = item.album().title();
                subtitle = android.text.BidiFormatter.getInstance().unicodeWrap(item.album().artistName()) + " · "
                        + r.getQuantityString(R.plurals.library_songs, item.album().trackCount(), item.album().trackCount());
                seed = item.album().placeholderSeed();
                art = item.album().artworkUri();
            }
            default -> {
                title = item.playlist().name();
                subtitle = r.getQuantityString(R.plurals.library_songs, item.playlist().trackCount(),
                        item.playlist().trackCount());
                seed = item.playlist().placeholderSeed();
                art = item.playlist().artworkUris().isEmpty() ? null : item.playlist().artworkUris().get(0);
            }
        }
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.title, title);
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.subtitle, subtitle);
        Art.bind(h.b.art, art, seed, hero ? 28 : 24, false);
        boolean showPlay = hero;
        h.b.play.setVisibility(showPlay ? View.VISIBLE : View.GONE);
        h.b.play.setOnClickListener(v -> listener.onItemClick(item));
        h.itemView.setOnClickListener(v -> listener.onItemClick(item));
        h.itemView.setContentDescription(title + ", " + subtitle);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HomeCardBinding b;

        Holder(HomeCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
