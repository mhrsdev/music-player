package com.mhrsdev.musicplayer.ui.home;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.data.db.query.TrackQueries.FeedTrack;
import com.mhrsdev.musicplayer.data.repository.FeedFixtures;
import com.mhrsdev.musicplayer.data.repository.HomeFeedBuilder;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.ScrollBench;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * SC1 (D39) Home scroll path: per-row costs of every Home row type and whole scroll frames, owner-shaped library
 * (2,000 cover-less downloads + 200 covered tracks). Light pass: allocation budgets; {@code SC1_BENCH=1}: full numbers.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xxhdpi")
public class HomeScrollBenchTest {

    static List<HomeSection> ownerFeed() {
        FeedFixtures.Library lib = FeedFixtures.library(ScrollBench.COVERLESS + ScrollBench.COVERED, 1);
        List<Track> owner = ScrollBench.library();
        List<FeedTrack> tracks = new ArrayList<>();
        for (int i = 0; i < lib.tracks().size(); i++) {
            FeedTrack f = lib.tracks().get(i);
            Track o = owner.get(i), t = f.track();
            tracks.add(new FeedTrack(new Track(t.id(), o.title(), o.artistId(), o.artistName(), o.albumId(), o.albumTitle(),
                    o.durationMs(), o.artworkUri(), o.placeholderSeed(), o.mediaUri(), o.explicit(), t.liked(), t.year(),
                    t.trackNumber(), t.addedAtMs()), f.plays(), f.lastPlayedMs(), f.genre(), f.path()));
        }
        return HomeFeedBuilder.build(new HomeFeedBuilder.Input(tracks, lib.events(), lib.playlists(), FeedFixtures.NOW,
                FeedFixtures.ZONE, 7, 11));
    }

    static HomeAdapter.Listener noop() {
        return (HomeAdapter.Listener) Proxy.newProxyInstance(HomeAdapter.class.getClassLoader(),
                new Class<?>[] {HomeAdapter.Listener.class}, (p, m, a) -> null);
    }

    /** Positions a holder of the first {@code rowType} row's view type is bound to while scrolling Home. */
    static int[] positionsOf(HomeAdapter a, int rowType) {
        int viewType = -1;
        for (int i = 0; i < a.getItemCount() && viewType < 0; i++) {
            if (a.getCurrentList().get(i).type() == rowType) viewType = a.getItemViewType(i);
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < a.getItemCount(); i++) if (a.getItemViewType(i) == viewType) out.add(i);
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    static int[] all(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    @Test public void homeRowsAndFrames() throws Exception {
        Context c = ScrollBench.context();
        ScrollBench.installLoader(c);
        List<HomeSection> feed = ownerFeed();
        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        HomeAdapter adapter = new HomeAdapter(noop());
        rv.setAdapter(adapter);
        adapter.submitList(HomeAdapter.flatten(feed));
        ScrollBench.settle(3);
        ScrollBench.layOut(rv, 915);
        ScrollBench.settle(10);
        System.out.println("PERF home: " + feed.size() + " sections, " + adapter.getItemCount() + " rows");

        int[] types = {HomeAdapter.Row.HEADER, HomeAdapter.Row.TRACK, HomeAdapter.Row.CAROUSEL, HomeAdapter.Row.HERO,
                HomeAdapter.Row.CONTINUE, HomeAdapter.Row.AVATARS, HomeAdapter.Row.CHIPS, HomeAdapter.Row.ACTIONS,
                HomeAdapter.Row.STATS};
        String[] names = {"home header", "home track row", "home carousel (cards)", "home hero carousel",
                "home continue", "home avatars", "home chips", "home actions", "home stats"};
        for (int i = 0; i < types.length; i++) {
            int[] pos = positionsOf(adapter, types[i]);
            if (pos.length == 0) continue;
            ScrollBench.RowCost row = ScrollBench.rowCost(names[i], rv, adapter.getItemViewType(pos[0]), pos, 120);
            System.out.println(row);
            // SC1 budgets, JVM bytes per bind (v0.3.2: track row 18.7 KB, stats 14 MB, carousel re-layout 745 KB)
            long budget = types[i] == HomeAdapter.Row.STATS || types[i] == HomeAdapter.Row.CONTINUE ? 64 * 1024
                    : types[i] == HomeAdapter.Row.TRACK || types[i] == HomeAdapter.Row.HEADER ? 24 * 1024 : 4 * 1024;
            assertTrue(row.toString(), row.bindBytes() < budget);
            assertTrue(row.toString(), row.layoutBytes() < 32 * 1024);
        }
        System.out.println(ScrollBench.scroll("home vertical", rv, 240, 0, 36, null));

        // the hero carousel and one card row on their own (horizontal scroll inside Home)
        HomeSection heroSection = null, cardSection = null;
        for (HomeSection s : feed) {
            if (s.style() == HomeSectionStyle.HERO_CAROUSEL) heroSection = s;
            if (s.style() == HomeSectionStyle.CAROUSEL && cardSection == null && s.items().size() >= 8) cardSection = s;
        }
        RecyclerView hero = new RecyclerView(c);
        HeroCarousel.attach(hero);
        HomeCardAdapter cards = new HomeCardAdapter(HomeCardAdapter.HERO, false, i -> { });
        hero.setAdapter(cards);
        cards.submitList(heroSection.items());
        ScrollBench.layOut(hero, 420);
        ScrollBench.settle(10);
        System.out.println(ScrollBench.rowCost("hero card", hero, HomeCardAdapter.HERO, all(cards.getItemCount()), 120));
        System.out.println(ScrollBench.scroll("hero carousel", hero, 120, 24, 0, null));

        RecyclerView row = new RecyclerView(c);
        row.setLayoutManager(new LinearLayoutManager(c, RecyclerView.HORIZONTAL, false));
        HomeCardAdapter rowCards = new HomeCardAdapter(HomeCardAdapter.CARD, false, i -> { });
        row.setAdapter(rowCards);
        rowCards.submitList(cardSection.items());
        ScrollBench.layOut(row, 240);
        ScrollBench.settle(10);
        System.out.println(ScrollBench.rowCost("home card", row, rowCards.getItemViewType(0),
                all(rowCards.getItemCount()), 120));
        System.out.println(ScrollBench.scroll("home card row", row, 120, 24, 0, null));
    }
}
