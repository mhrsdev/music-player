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

    /** Horizontal rows get one view type per section (encoded with {@link #CAROUSEL_TYPES}): see getItemViewType. */
    private static final int CAROUSEL_TYPES = 1000;
    private static final Object PAYLOAD_PROGRESS = new Object();

    private final Listener listener;
    private final java.util.HashMap<String, Integer> sectionSlots = new java.util.HashMap<>();
    /** The Continue card while it is bound: the 2 % progress steps go straight to it (no rebind, no layout pass). */
    @Nullable private Bound<HomeContinueBinding> continueHolder;
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
        // Only the progress strip shows these values: set it on the bound card (RenderNode scale, no layout, no
        // change animation). It used to be a full rebind + crossfade + a predictive layout pass of all of Home
        // every 2 % of a song, i.e. every ~4 s while music plays, right in the middle of a scroll.
        Bound<HomeContinueBinding> k = continueHolder;
        int pos = k == null ? RecyclerView.NO_POSITION : k.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION && pos < getItemCount() && getItem(pos).type() == Row.CONTINUE) {
            bindProgress(k.b, getItem(pos).section());
            return;
        }
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).type() == Row.CONTINUE) notifyItemChanged(i, PAYLOAD_PROGRESS);
        }
    }

    /**
     * Horizontal rows (carousels, avatars, chips, hero) get a view type per section, so a row is only ever rebound to
     * its own section: rebinding it costs nothing (same cards, same scroll position). With one shared type a row
     * scrolled back into view was handed another section's carousel: a full inner re-layout + diff + card
     * add/remove animations per row entering the screen (JVM: 2 ms and 745 KB per bind).
     */
    @Override public int getItemViewType(int position) {
        Row row = getItem(position);
        int t = row.type();
        if (t != Row.CAROUSEL && t != Row.HERO && t != Row.AVATARS && t != Row.CHIPS) return t;
        Integer slot = sectionSlots.get(row.section().id());
        if (slot == null) {
            slot = sectionSlots.size();
            sectionSlots.put(row.section().id(), slot);
        }
        return CAROUSEL_TYPES + slot * 16 + t;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        int type = viewType >= CAROUSEL_TYPES ? (viewType - CAROUSEL_TYPES) % 16 : viewType;
        switch (type) {
            case Row.HEADER:
                return new Bound<>(HomeHeaderBinding.inflate(inf, parent, false));
            case Row.TRACK:
                return trackHolder(LibraryRowTrackBinding.inflate(inf, parent, false));
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

    /** Listeners and the TalkBack label are per holder (they read the bound row), not allocated per bind. */
    private Bound<LibraryRowTrackBinding> trackHolder(LibraryRowTrackBinding t) {
        Bound<LibraryRowTrackBinding> h = new Bound<>(t);
        t.getRoot().setOnClickListener(v -> {
            Row row = rowAt(h);
            if (row != null) listener.onTrackClick(row.section(), row.track());
        });
        t.more.setOnClickListener(v -> {
            Row row = rowAt(h);
            if (row != null) TrackMenu.show(v, row.track());
        });
        t.more.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                Row row = rowAt(h);
                if (row != null) info.setContentDescription(host.getContext().getString(R.string.library_more, row.track().title()));
            }
        });
        return h;
    }

    @Nullable private Row rowAt(RecyclerView.ViewHolder h) {
        int p = h.getBindingAdapterPosition();
        if (p == RecyclerView.NO_POSITION || p >= getItemCount()) return null;
        Row row = getItem(p);
        return row.track() == null ? null : row;
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && ((Bound<?>) holder).b instanceof HomeContinueBinding k) {
            boolean progressOnly = true;
            for (Object p : payloads) progressOnly &= p == PAYLOAD_PROGRESS;
            if (progressOnly) {
                bindProgress(k, getItem(position).section());
                return;
            }
        }
        onBindViewHolder(holder, position);
    }

    @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder == continueHolder) continueHolder = null;
    }

    @SuppressWarnings("unchecked")
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        Object b = ((Bound<?>) holder).b;
        Context c = holder.itemView.getContext();
        if (b instanceof HomeHeaderBinding h) {
            HomeSection s = row.section();
            if (h.getRoot().getTag() == s) return; // same header scrolled back in: nothing changed
            h.getRoot().setTag(s);
            ScriptFont.setText(h.title, SectionText.title(c, s));
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
            Art.bind(t.art, track.artworkUri(), track.placeholderSeed(), 12, false, CoverSpec.forTrack(track));
        } else if (b instanceof HomeContinueBinding k) {
            continueHolder = (Bound<HomeContinueBinding>) holder;
            bindContinue(k, row.section());
        } else if (b instanceof HomeStatsBinding st) {
            bindStats(st, row.section().stats());
        } else if (b instanceof HomeCarouselBinding car) {
            List<HomeItem> items = row.section().items();
            int cap = row.type() == Row.CHIPS ? CHIP_PREVIEW : ROW_PREVIEW;
            ((HomeCardAdapter) car.carousel.getAdapter()).submitPreview(items, cap);
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
        bindProgress(k, s);
        k.resume.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_play, 0, 0, 0);
        k.resume.setOnClickListener(v -> listener.onContinue(track));
        k.getRoot().setOnClickListener(v -> listener.onContinue(track));
        k.getRoot().setContentDescription(k.getRoot().getContext().getString(R.string.home_resume) + ", "
                + track.title() + ", " + track.artistName());
    }

    /** The Continue card's progress strip (the only part that follows the player). */
    private void bindProgress(HomeContinueBinding k, HomeSection s) {
        Track track = s.items().get(0).track();
        boolean current = track.id().equals(nowTrackId) && nowProgress >= 0;
        k.progressTrack.setVisibility(current ? View.VISIBLE : View.INVISIBLE);
        if (current) {
            float pivot = k.getRoot().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? k.progressTrack.getWidth() : 0;
            if (k.progressFill.getPivotX() != pivot) k.progressFill.setPivotX(pivot);
            k.progressFill.setScaleX(Math.max(0.02f, nowProgress));
        }
    }

    /**
     * The four stat cells are built once per holder and only their texts change. Rebuilding them on every bind (8 new
     * TextViews with text appearances) was the most expensive bind on Home: 13 ms and 14 MB per bind on the JVM.
     */
    private static void bindStats(HomeStatsBinding st, @Nullable HomeStats s) {
        if (s == null) return;
        Context c = st.getRoot().getContext();
        NumberFormat nf = NumberFormat.getIntegerInstance(locale(c));
        if (st.row.getChildCount() != 4) {
            st.row.removeAllViews();
            for (int i = 0; i < 4; i++) addStat(st.row);
        }
        setStat(st.row, 0, nf.format(s.tracks()), c.getString(R.string.home_stat_songs));
        setStat(st.row, 1, nf.format(Math.round(s.libraryMs() / 3_600_000.0)), c.getString(R.string.home_stat_hours));
        setStat(st.row, 2, nf.format(s.artists()), c.getString(R.string.home_stat_artists));
        setStat(st.row, 3, nf.format(s.playsMonth()), c.getString(R.string.home_stat_plays_month));
        st.topArtist.setVisibility(s.topArtistMonth() == null ? View.GONE : View.VISIBLE);
        if (s.topArtistMonth() != null) {
            ScriptFont.setText(st.topArtist, c.getString(R.string.home_stat_top_artist,
                    SectionText.isolate(SectionText.artist(c, s.topArtistMonth()))));
        }
        st.listened.setVisibility(s.listenedMsMonth() > 0 ? View.VISIBLE : View.GONE);
        st.listened.setText(c.getString(R.string.home_stat_listened_month, Format.duration(s.listenedMsMonth(), locale(c))));
    }

    private static void addStat(LinearLayout row) {
        Context c = row.getContext();
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView v = new TextView(c);
        v.setTextAppearance(R.style.TextAppearance_MusicPlayer_Headline);
        v.setTextColor(com.mhrsdev.musicplayer.ui.theme.Tokens.color(c, R.attr.mpOnSurface));
        TextView l = new TextView(c);
        l.setTextAppearance(R.style.TextAppearance_MusicPlayer_Supporting);
        l.setMaxLines(2);
        cell.addView(v);
        cell.addView(l);
        cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private static void setStat(LinearLayout row, int i, String value, String label) {
        LinearLayout cell = (LinearLayout) row.getChildAt(i);
        TextView v = (TextView) cell.getChildAt(0);
        TextView l = (TextView) cell.getChildAt(1);
        if (!value.contentEquals(v.getText())) v.setText(value);
        if (!label.contentEquals(l.getText())) l.setText(label);
        cell.setContentDescription(value + " " + label);
    }

    private static Locale locale(Context c) {
        return c.getResources().getConfiguration().getLocales().get(0);
    }

    static class Bound<B extends androidx.viewbinding.ViewBinding> extends RecyclerView.ViewHolder {
        final B b;

        Bound(B b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
