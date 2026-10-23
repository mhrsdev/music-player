package com.mhrsdev.musicplayer.ui.home;

import android.content.Context;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.HomeAvatarBinding;
import com.mhrsdev.musicplayer.databinding.HomeCardBinding;
import com.mhrsdev.musicplayer.databinding.HomeChipBinding;
import com.mhrsdev.musicplayer.databinding.HomeMixCardBinding;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeMix;
import com.mhrsdev.musicplayer.ui.library.Art;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;

/**
 * Cards inside a Home row (and the "See all" grids). One adapter, several view types so every horizontal row can share
 * one {@link RecyclerView.RecycledViewPool}: cover cards, the hero card ({@link HeroCarousel} width + cookie play), mix
 * cards (label over the cover), round artist avatars and pills (decades / genres / folders).
 */
class HomeCardAdapter extends ListAdapter<HomeItem, HomeCardAdapter.Holder> {

    interface Listener {
        void onItemClick(HomeItem item);
    }

    /** Layout modes == view types (shared pool keys). */
    static final int CARD = 10, HERO = 11, MIX = 12, AVATAR = 13, CHIP = 14;

    private static final DiffUtil.ItemCallback<HomeItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull HomeItem a, @NonNull HomeItem b) { return id(a).equals(id(b)); }

        @Override public boolean areContentsTheSame(@NonNull HomeItem a, @NonNull HomeItem b) { return a.equals(b); }
    };

    static String id(HomeItem i) {
        return switch (i.kind()) {
            case TRACK -> "t:" + i.track().id();
            case ALBUM -> "a:" + i.album().id();
            case PLAYLIST -> "p:" + i.playlist().id();
            case ARTIST -> "r:" + i.artist().id();
            case MIX -> "m:" + i.mix().id();
        };
    }

    @androidx.annotation.Nullable private RecyclerView host;
    private final int mode;
    private final boolean grid;
    private final Listener listener;

    /** @param grid true on "See all" pages: cards fill their grid cell instead of the fixed row width */
    HomeCardAdapter(int mode, boolean grid, Listener listener) {
        super(DIFF);
        this.mode = mode;
        this.grid = grid;
        this.listener = listener;
    }

    HomeCardAdapter(boolean hero, Listener listener) {
        this(hero ? HERO : CARD, false, listener);
    }

    @Override public int getItemViewType(int position) {
        if (mode == CARD && getItem(position).kind() == HomeItem.Kind.MIX) return MIX;
        return mode;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        float d = parent.getResources().getDisplayMetrics().density;
        switch (type) {
            case AVATAR: {
                HomeAvatarBinding b = HomeAvatarBinding.inflate(inf, parent, false);
                if (grid) b.getRoot().getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                return new Holder(b);
            }
            case CHIP:
                return new Holder(HomeChipBinding.inflate(inf, parent, false));
            case MIX: {
                HomeMixCardBinding b = HomeMixCardBinding.inflate(inf, parent, false);
                if (grid) fitCell(b.getRoot(), b.artFrame, parent, d);
                return new Holder(b);
            }
            default: {
                HomeCardBinding b = HomeCardBinding.inflate(inf, parent, false);
                if (type == HERO) {
                    int w = HeroCarousel.cardWidth(rowWidth(parent), d);
                    b.getRoot().getLayoutParams().width = w;
                    b.artFrame.getLayoutParams().height = w - Math.round(8 * d);
                } else if (grid) {
                    fitCell(b.getRoot(), b.artFrame, parent, d);
                }
                return new Holder(b);
            }
        }
    }

    @Override public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        host = rv;
    }

    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView rv) {
        host = null;
    }

    /**
     * The row's width. During the first auto-measure pass a RecyclerView is still 0 wide, which used to size hero cards
     * with the 272 dp fallback (too wide: only one card and a sliver visible); Home rows are full-width, so the window
     * width is the right fallback.
     */
    private static int rowWidth(@androidx.annotation.Nullable View row) {
        if (row != null && row.getWidth() > 0) return row.getWidth();
        if (row != null && row.getMeasuredWidth() > 0) return row.getMeasuredWidth();
        return (row != null ? row.getResources() : Resources.getSystem()).getDisplayMetrics().widthPixels;
    }

    /** Square cover in a grid cell: cell width from the span count minus margins and padding. */
    private static void fitCell(View root, View artFrame, ViewGroup parent, float d) {
        int span = parent instanceof RecyclerView rv && rv.getLayoutManager() instanceof GridLayoutManager g
                ? g.getSpanCount() : 2;
        int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        int cell = inner / Math.max(1, span) - Math.round(8 * d);
        root.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
        artFrame.getLayoutParams().height = Math.max(Math.round(96 * d), cell - Math.round(8 * d));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        HomeItem item = getItem(position);
        Context c = h.itemView.getContext();
        Resources r = c.getResources();
        String title;
        String subtitle;
        if (h.b instanceof HomeAvatarBinding a) {
            title = SectionText.artist(c, item.artist().name());
            subtitle = r.getQuantityString(R.plurals.library_songs, item.artist().trackCount(), item.artist().trackCount());
            Art.artist(a.art, item.artist().imageUri(), title, item.artist().placeholderSeed());
            ScriptFont.setText(a.title, title);
            ScriptFont.setText(a.subtitle, subtitle);
        } else if (h.b instanceof HomeChipBinding chip) {
            HomeMix m = item.mix();
            title = SectionText.mixTitle(c, m);
            subtitle = SectionText.mixSubtitle(c, m);
            ScriptFont.setText(chip.chip, title);
            chip.chip.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    m.kind() == HomeMix.Kind.FOLDER ? R.drawable.ic_mp_folder : 0, 0, 0, 0);
        } else if (h.b instanceof HomeMixCardBinding mc) {
            HomeMix m = item.mix();
            title = SectionText.mixTitle(c, m);
            subtitle = SectionText.mixSubtitle(c, m);
            ScriptFont.setText(mc.label, title);
            ScriptFont.setText(mc.subtitle, subtitle);
            // the mix label sits on the cover: generated covers stay text-less
            Art.bind(mc.art, m.artworkUri(), m.placeholderSeed(), 24, false, CoverSpec.fromSeed(m.placeholderSeed()));
        } else {
            HomeCardBinding b = (HomeCardBinding) h.b;
            String art;
            int seed;
            CoverSpec cover;
            switch (item.kind()) {
                case TRACK -> {
                    title = item.track().title();
                    subtitle = SectionText.artist(c, item.track().artistName());
                    seed = item.track().placeholderSeed();
                    art = item.track().artworkUri();
                    cover = CoverSpec.forTrack(item.track());
                }
                case ALBUM -> {
                    title = SectionText.album(c, item.album().title());
                    subtitle = SectionText.isolate(SectionText.artist(c, item.album().artistName())) + " · "
                            + r.getQuantityString(R.plurals.library_songs, item.album().trackCount(),
                            item.album().trackCount());
                    seed = item.album().placeholderSeed();
                    art = item.album().artworkUri();
                    cover = CoverSpec.forAlbum(item.album());
                }
                case ARTIST -> {
                    title = item.artist().name();
                    subtitle = "";
                    seed = item.artist().placeholderSeed();
                    art = item.artist().imageUri();
                    cover = CoverSpec.forArtist(item.artist());
                }
                case MIX -> {
                    title = SectionText.mixTitle(c, item.mix());
                    subtitle = SectionText.mixSubtitle(c, item.mix());
                    seed = item.mix().placeholderSeed();
                    art = item.mix().artworkUri();
                    cover = CoverSpec.fromSeed(seed);
                }
                default -> {
                    title = item.playlist().name();
                    subtitle = r.getQuantityString(R.plurals.library_songs, item.playlist().trackCount(),
                            item.playlist().trackCount());
                    seed = item.playlist().placeholderSeed();
                    art = item.playlist().artworkUris().isEmpty() ? null : item.playlist().artworkUris().get(0);
                    cover = CoverSpec.forPlaylist(item.playlist().id(), item.playlist().name());
                }
            }
            ScriptFont.setText(b.title, title);
            ScriptFont.setText(b.subtitle, subtitle);
            boolean hero = mode == HERO;
            if (hero) { // width from the real row width; a lone hero card spans the row (no blank space after it)
                int parentWidth = rowWidth(host);
                float d = r.getDisplayMetrics().density;
                int w = getItemCount() == 1
                        ? parentWidth - Math.round(2 * (HeroCarousel.START_DP + 4) * d) : HeroCarousel.cardWidth(parentWidth, d);
                if (b.getRoot().getLayoutParams().width != w) {
                    b.getRoot().getLayoutParams().width = w;
                    b.artFrame.getLayoutParams().height = Math.min(w, HeroCarousel.cardWidth(parentWidth, d)) - Math.round(8 * d);
                    b.getRoot().requestLayout();
                }
            }
            // hero: the play button sits on the cover's text corner and the caption repeats it -> scene only
            Art.bind(b.art, art, seed, hero ? 28 : 24, false, hero ? cover.textless() : cover);
            b.play.setVisibility(hero ? View.VISIBLE : View.GONE);
            b.play.setOnClickListener(v -> listener.onItemClick(item));
        }
        h.itemView.setOnClickListener(v -> listener.onItemClick(item));
        h.itemView.setContentDescription(subtitle.isEmpty() ? title : title + ", " + subtitle);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ViewBinding b;

        Holder(ViewBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
