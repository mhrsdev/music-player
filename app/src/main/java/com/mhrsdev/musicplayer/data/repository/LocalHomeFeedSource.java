package com.mhrsdev.musicplayer.data.repository;

import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.PlayEventQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Home sections built from the local library and listening history (rules in {@link HomeFeedBuilder} /
 * {@link HomeRanking} / {@link MixRanking}). Three queries per load, all on the io executor; nothing runs while Home is
 * not observed. Section order and mixes are seeded by the local day; random picks by a per-launch seed that
 * {@link #refresh()} advances.
 */
public class LocalHomeFeedSource implements HomeFeedSource {
    /** Bounds the history read: a year of heavy listening stays a few hundred KB. */
    private static final int EVENT_LIMIT = 20_000;

    private final DbLiveData<List<HomeSection>> sections;
    private final AtomicLong discoverySeed = new AtomicLong(System.nanoTime());

    public LocalHomeFeedSource(DbHelper helper, Executor io, ChangeNotifier notifier) {
        sections = new DbLiveData<>(io, notifier, ChangeNotifier.ALL, () -> {
            var db = helper.getWritableDatabase();
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            long day = java.time.LocalDate.now(zone).toEpochDay();
            return HomeFeedBuilder.build(new HomeFeedBuilder.Input(new TrackQueries(db).feedTracks(),
                    new PlayEventQueries(db).since(now - 400L * HomeFeedBuilder.DAY_MS, EVENT_LIMIT),
                    new PlaylistQueries(db).all(), now, zone, day, discoverySeed.get()));
        });
    }

    @Override public LiveData<List<HomeSection>> getSections() {
        return sections;
    }

    @Override public void refresh() {
        discoverySeed.incrementAndGet();
        sections.reload();
    }
}
