package com.mhrsdev.musicplayer.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.HomeCarouselBinding;
import com.mhrsdev.musicplayer.databinding.HomeHeaderBinding;
import com.mhrsdev.musicplayer.databinding.LibraryRowTrackBinding;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.library.Art;
import com.mhrsdev.musicplayer.ui.library.Format;
import com.mhrsdev.musicplayer.ui.library.TrackMenu;
import java.util.ArrayList;
import java.util.List;

/** Flattens Home sections into header / carousel / track rows so every row recycles individually. */
final class HomeAdapter extends ListAdapter<HomeAdapter.Row, RecyclerView.ViewHolder> {

    interface Listener extends HomeCardAdapter.Listener {
        void onTrackClick(HomeSection section, Track track);

        void onShuffle(HomeSection section);

        /** "See all" on the Recently added hero row. */
        void onSeeAll(HomeSection section);
    }

    /** One row of the Home list. */
    record Row(int type, HomeSection section, Track track) {
        static final int HEADER = 0, CAROUSEL = 1, HERO = 2, TRACK = 3;

        String key() {
            return type + ":" + section.id() + (track == null ? "" : ":" + track.id());
        }
    }

    static List<Row> flatten(List<HomeSection> sections) {
        List<Row> rows = new ArrayList<>();
        for (HomeSection s : sections) {
            rows.add(new Row(Row.HEADER, s, null));
            if (s.style() == HomeSectionStyle.LIST) {
                for (HomeItem i : s.items()) {
                    if (i.kind() == HomeItem.Kind.TRACK) rows.add(new Row(Row.TRACK, s, i.track()));
                }
            } else {
                rows.add(new Row(s.style() == HomeSectionStyle.HERO_CAROUSEL ? Row.HERO : Row.CAROUSEL, s, null));
            }
        }
        return rows;
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) { return a.key().equals(b.key()); }

        @Override public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) { return a.equals(b); }
    };

    private final Listener listener;
    private final RecyclerView.RecycledViewPool cardPool = new RecyclerView.RecycledViewPool();

    HomeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @Override public int getItemViewType(int position) {
        return getItem(position).type();
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (type) {
            case Row.HEADER:
                return new Bound<>(HomeHeaderBinding.inflate(inf, parent, false));
            case Row.TRACK:
                return new Bound<>(LibraryRowTrackBinding.inflate(inf, parent, false));
            default: {
                HomeCarouselBinding b = HomeCarouselBinding.inflate(inf, parent, false);
                if (type == Row.HERO) {
                    HeroCarousel.attach(b.carousel);
                } else {
                    b.carousel.setLayoutManager(new LinearLayoutManager(parent.getContext(), RecyclerView.HORIZONTAL, false));
                }
                b.carousel.setAdapter(new HomeCardAdapter(type == Row.HERO, listener));
                if (type == Row.CAROUSEL) b.carousel.setRecycledViewPool(cardPool);
                return new Bound<>(b);
            }
        }
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        Object b = ((Bound<?>) holder).b;
        if (b instanceof HomeHeaderBinding h) {
            com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.title, row.section().title());
            HomeSectionStyle style = row.section().style();
            h.action.setVisibility(style == HomeSectionStyle.CAROUSEL ? View.GONE : View.VISIBLE);
            if (style == HomeSectionStyle.LIST) {
                h.action.setText(R.string.home_shuffle);
                h.action.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_shuffle, 0, 0, 0);
                h.action.setOnClickListener(v -> listener.onShuffle(row.section()));
            } else {
                h.action.setText(R.string.home_see_all);
                h.action.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.nav_ic_chevron, 0);
                h.action.setOnClickListener(v -> listener.onSeeAll(row.section()));
            }
        } else if (b instanceof LibraryRowTrackBinding t) {
            Track track = row.track();
            com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(t.title, track.title());
            com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(t.subtitle, track.artistName());
            t.duration.setText(Format.duration(track.durationMs(),
                    holder.itemView.getResources().getConfiguration().getLocales().get(0)));
            t.explicit.setVisibility(track.explicit() ? View.VISIBLE : View.GONE);
            t.more.setContentDescription(holder.itemView.getContext().getString(R.string.library_more, track.title()));
            Art.bind(t.art, track.artworkUri(), track.placeholderSeed(), 12, false);
            t.getRoot().setOnClickListener(v -> listener.onTrackClick(row.section(), track));
            t.more.setOnClickListener(v -> TrackMenu.show(v, track));
        } else if (b instanceof HomeCarouselBinding c) {
            ((HomeCardAdapter) c.carousel.getAdapter()).submitList(row.section().items());
        }
    }

    static final class Bound<B extends androidx.viewbinding.ViewBinding> extends RecyclerView.ViewHolder {
        final B b;

        Bound(B b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
