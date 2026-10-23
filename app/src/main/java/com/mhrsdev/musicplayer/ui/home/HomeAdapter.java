package com.mhrsdev.musicplayer.ui.home;

import android.content.Context;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.HomeActionsBinding;
import com.mhrsdev.musicplayer.databinding.HomeCarouselBinding;
import com.mhrsdev.musicplayer.databinding.HomeContinueBinding;
import com.mhrsdev.musicplayer.databinding.HomeHeaderBinding;
import com.mhrsdev.musicplayer.databinding.HomeStatsBinding;
import com.mhrsdev.musicplayer.databinding.LibraryRowTrackBinding;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.HomeStats;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.library.Art;
import com.mhrsdev.musicplayer.ui.library.Format;
import com.mhrsdev.musicplayer.ui.library.TrackMenu;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Flattens Home sections into header / row / track rows so every row recycles individually. */
class HomeAdapter extends ListAdapter<HomeAdapter.Row, RecyclerView.ViewHolder> {

    interface Listener extends HomeCardAdapter.Listener {
        void onTrackClick(HomeSection section, Track track);

        void onShuffle(HomeSection section);

        /** "See all" on any section header. */
        void onSeeAll(HomeSection section);

        void onContinue(Track track);

        void onShuffleLibrary();

        void onSurprise();

        /** The hero carousel settled on {@code item} after a user swipe (never fires per scroll frame). */
        void onHeroFocused(HomeItem item);
    }

    /** One row of the Home list. */
    record Row(int type, HomeSection section, Track track) {
        static final int HEADER = 0, CAROUSEL = 1, HERO = 2, TRACK = 3, CONTINUE = 4, AVATARS = 5, CHIPS = 6,
                ACTIONS = 7, STATS = 8;

        String key() {
            return type + ":" + section.id() + (track == null ? "" : ":" + track.id());
        }
    }

    /** Cards in one horizontal row; the rest is on the "See all" page. */
    static final int ROW_PREVIEW = 15;
    static final int CHIP_PREVIEW = 20;

    static int listPreview(HomeSection s) {
        return "quick-picks".equals(s.id()) ? 8 : 5;
    }

    static List<Row> flatten(List<HomeSection> sections) {
        List<Row> rows = new ArrayList<>();
        for (HomeSection s : sections) {
            if (s.style() != HomeSectionStyle.ACTIONS) rows.add(new Row(Row.HEADER, s, null));
            switch (s.style()) {
                case LIST -> {
                    int n = 0;
                    for (HomeItem i : s.items()) {
                        if (n++ == listPreview(s)) break;
                        if (i.kind() == HomeItem.Kind.TRACK) rows.add(new Row(Row.TRACK, s, i.track()));
                    }
                }
                case HERO_CAROUSEL -> rows.add(new Row(Row.HERO, s, null));
                case CONTINUE -> rows.add(new Row(Row.CONTINUE, s, null));
                case AVATARS -> rows.add(new Row(Row.AVATARS, s, null));
                case CHIPS -> rows.add(new Row(Row.CHIPS, s, null));
                case ACTIONS -> rows.add(new Row(Row.ACTIONS, s, null));
                case STATS -> rows.add(new Row(Row.STATS, s, null));
                default -> rows.add(new Row(Row.CAROUSEL, s, null));
            }
        }
        return rows;
    }

    /** True when the header shows "See all" (more than fits in the row, or a carousel worth browsing as a grid). */
    static boolean hasSeeAll(HomeSection s) {
        return switch (s.style()) {
            case LIST -> !"quick-picks".equals(s.id()) && s.items().size() > listPreview(s);
            case CAROUSEL, HERO_CAROUSEL, AVATARS -> s.items().size() > 4;
            case CHIPS -> s.items().size() > 6;
            default -> false;
        };
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) { return a.key().equals(b.key()); }

        @Override public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) { return a.equals(b); }
    };

    private final Listener listener;
    /** One pool for every horizontal row: cards / mixes / avatars / chips recycle across rows. */
    private final RecyclerView.RecycledViewPool cardPool = new RecyclerView.RecycledViewPool();
    @Nullable private String nowTrackId;
    private float nowProgress = -1f;

    HomeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        cardPool.setMaxRecycledViews(HomeCardAdapter.CARD, 24);
        cardPool.setMaxRecycledViews(HomeCardAdapter.MIX, 12);
        cardPool.setMaxRecycledViews(HomeCardAdapter.AVATAR, 12);
        cardPool.setMaxRecycledViews(HomeCardAdapter.CHIP, 24);
    }

    /** Player position for the Continue card (only rebinds that one row, and only when the value moved). */
    void setNowPlaying(@Nullable String trackId, float progress) {
        float q = progress < 0 ? -1f : Math.round(progress * 50) / 50f;
        if (java.util.Objects.equals(trackId, nowTrackId) && q == nowProgress) return;
        nowTrackId = trackId;
        nowProgress = q;
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).type() == Row.CONTINUE) notifyItemChanged(i);
        }
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
            case Row.CONTINUE:
                return new Bound<>(HomeContinueBinding.inflate(inf, parent, false));
            case Row.ACTIONS: {
                HomeActionsBinding b = HomeActionsBinding.inflate(inf, parent, false);
                b.shuffleAll.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_shuffle, 0, 0, 0);
                b.shuffleAll.setOnClickListener(v -> listener.onShuffleLibrary());
                b.surprise.setOnClickListener(v -> listener.onSurprise());
                return new Bound<>(b);
            }
            case Row.STATS:
                return new Bound<>(HomeStatsBinding.inflate(inf, parent, false));
            default: {
                HomeCarouselBinding b = HomeCarouselBinding.inflate(inf, parent, false);
                int mode = switch (type) {
                    case Row.HERO -> HomeCardAdapter.HERO;
                    case Row.AVATARS -> HomeCardAdapter.AVATAR;
                    case Row.CHIPS -> HomeCardAdapter.CHIP;
                    default -> HomeCardAdapter.CARD;
                };
                if (type == Row.HERO) {
                    HeroCarousel.attach(b.carousel);
                    b.carousel.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                            if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                            HomeItem focused = focusedHero(rv);
                            if (focused != null) listener.onHeroFocused(focused);
                        }
                    });
                } else {
                    LinearLayoutManager lm = new LinearLayoutManager(parent.getContext(), RecyclerView.HORIZONTAL, false);
                    lm.setInitialPrefetchItemCount(4);
                    b.carousel.setLayoutManager(lm);
                    b.carousel.setRecycledViewPool(cardPool);
                }
                b.carousel.setAdapter(new HomeCardAdapter(mode, false, listener));
                return new Bound<>(b);
            }
        }
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        Object b = ((Bound<?>) holder).b;
        Context c = holder.itemView.getContext();
        if (b instanceof HomeHeaderBinding h) {
            ScriptFont.setText(h.title, SectionText.title(c, row.section()));
            HomeSection s = row.section();
            if ("quick-picks".equals(s.id())) {
                h.action.setVisibility(View.VISIBLE);
                h.action.setText(R.string.home_shuffle);
                h.action.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_shuffle, 0, 0, 0);
                h.action.setOnClickListener(v -> listener.onShuffle(s));
            } else if (hasSeeAll(s)) {
                h.action.setVisibility(View.VISIBLE);
                h.action.setText(R.string.home_see_all);
                h.action.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.nav_ic_chevron, 0);
                h.action.setOnClickListener(v -> listener.onSeeAll(s));
            } else {
                h.action.setVisibility(View.GONE);
            }
        } else if (b instanceof LibraryRowTrackBinding t) {
            Track track = row.track();
            ScriptFont.setText(t.title, track.title());
            ScriptFont.setText(t.subtitle, SectionText.artist(c, track.artistName()));
            t.duration.setText(Format.duration(track.durationMs(), locale(c)));
            t.explicit.setVisibility(track.explicit() ? View.VISIBLE : View.GONE);
            t.more.setContentDescription(c.getString(R.string.library_more, track.title()));
            Art.bind(t.art, track.artworkUri(), track.placeholderSeed(), 12, false, CoverSpec.forTrack(track));
            t.getRoot().setOnClickListener(v -> listener.onTrackClick(row.section(), track));
            t.more.setOnClickListener(v -> TrackMenu.show(v, track));
        } else if (b instanceof HomeContinueBinding k) {
            bindContinue(k, row.section());
        } else if (b instanceof HomeStatsBinding st) {
            bindStats(st, row.section().stats());
        } else if (b instanceof HomeCarouselBinding car) {
            List<HomeItem> items = row.section().items();
            int cap = row.type() == Row.CHIPS ? CHIP_PREVIEW : ROW_PREVIEW;
            ((HomeCardAdapter) car.carousel.getAdapter()).submitList(
                    items.size() > cap ? new ArrayList<>(items.subList(0, cap)) : items);
        }
    }

    /** The hero card HeroCarousel scaled to full size (the focused keyline). */
    @Nullable static HomeItem focusedHero(RecyclerView rv) {
        View best = null;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View v = rv.getChildAt(i);
            if (best == null || v.getScaleX() > best.getScaleX()) best = v;
        }
        if (best == null) return null;
        int pos = rv.getChildAdapterPosition(best);
        HomeCardAdapter a = (HomeCardAdapter) rv.getAdapter();
        return a == null || pos == RecyclerView.NO_POSITION || pos >= a.getItemCount() ? null : a.getCurrentList().get(pos);
    }

    private void bindContinue(HomeContinueBinding k, HomeSection s) {
        Track track = s.items().get(0).track();
        ScriptFont.setText(k.title, track.title());
        ScriptFont.setText(k.subtitle, SectionText.artist(k.getRoot().getContext(), track.artistName()));
        Art.bind(k.art, track.artworkUri(), track.placeholderSeed(), 14, false);
        boolean current = track.id().equals(nowTrackId) && nowProgress >= 0;
        k.progressTrack.setVisibility(current ? View.VISIBLE : View.INVISIBLE);
        if (current) {
            k.progressFill.setPivotX(k.getRoot().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? k.progressTrack.getWidth() : 0);
            k.progressFill.setScaleX(Math.max(0.02f, nowProgress));
        }
        k.resume.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_play, 0, 0, 0);
        k.resume.setOnClickListener(v -> listener.onContinue(track));
        k.getRoot().setOnClickListener(v -> listener.onContinue(track));
        k.getRoot().setContentDescription(k.getRoot().getContext().getString(R.string.home_resume) + ", "
                + track.title() + ", " + track.artistName());
    }

    private static void bindStats(HomeStatsBinding st, @Nullable HomeStats s) {
        if (s == null) return;
        Context c = st.getRoot().getContext();
        NumberFormat nf = NumberFormat.getIntegerInstance(locale(c));
        st.row.removeAllViews();
        addStat(st.row, nf.format(s.tracks()), c.getString(R.string.home_stat_songs));
        addStat(st.row, nf.format(Math.round(s.libraryMs() / 3_600_000.0)), c.getString(R.string.home_stat_hours));
        addStat(st.row, nf.format(s.artists()), c.getString(R.string.home_stat_artists));
        addStat(st.row, nf.format(s.playsMonth()), c.getString(R.string.home_stat_plays_month));
        st.topArtist.setVisibility(s.topArtistMonth() == null ? View.GONE : View.VISIBLE);
        if (s.topArtistMonth() != null) {
            ScriptFont.setText(st.topArtist, c.getString(R.string.home_stat_top_artist,
                    SectionText.isolate(SectionText.artist(c, s.topArtistMonth()))));
        }
        st.listened.setVisibility(s.listenedMsMonth() > 0 ? View.VISIBLE : View.GONE);
        st.listened.setText(c.getString(R.string.home_stat_listened_month, Format.duration(s.listenedMsMonth(), locale(c))));
    }

    private static void addStat(LinearLayout row, String value, String label) {
        Context c = row.getContext();
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView v = new TextView(c);
        v.setTextAppearance(R.style.TextAppearance_MusicPlayer_Headline);
        v.setTextColor(com.mhrsdev.musicplayer.ui.theme.Tokens.color(c, R.attr.mpOnSurface));
        v.setText(value);
        TextView l = new TextView(c);
        l.setTextAppearance(R.style.TextAppearance_MusicPlayer_Supporting);
        l.setText(label);
        l.setMaxLines(2);
        cell.addView(v);
        cell.addView(l);
        cell.setContentDescription(value + " " + label);
        cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private static Locale locale(Context c) {
        return c.getResources().getConfiguration().getLocales().get(0);
    }

    static final class Bound<B extends androidx.viewbinding.ViewBinding> extends RecyclerView.ViewHolder {
        final B b;

        Bound(B b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
