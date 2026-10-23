package com.mhrsdev.musicplayer.data.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.robolectric.shadows.ShadowLooper;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.LikeQueries;
import com.mhrsdev.musicplayer.data.db.query.PlayStatsQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.data.repository.ChangeNotifier;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.data.settings.ThemeMode;
import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import com.mhrsdev.musicplayer.domain.model.RestoreMode;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Export from one database, import into another whose MediaStore ids differ. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SqliteBackupServiceTest {
    private Context context;
    private SettingsStore settings;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        settings = new SettingsStore(context);
    }

    private static Track track(String id, String title) {
        return new Track(id, title, "ar", "Artist", "al", "Album", 200_000, null, 1, "content://media/" + id, false,
                false, 2020, 1, 1);
    }

    private SqliteBackupService service(DbHelper helper) {
        return new SqliteBackupService(helper, Runnable::run, new ChangeNotifier(), settings, "t", () -> 1000L);
    }

    private DbHelper freshDb() {
        context.deleteDatabase(DbHelper.NAME);
        return new DbHelper(context);
    }

    @Test public void exportThenRestoreOnDeviceWithDifferentIds() throws Exception {
        DbHelper helper = freshDb();
        SqliteBackupService service = service(helper);
        SQLiteDatabase db = helper.getWritableDatabase();
        TrackQueries tracks = new TrackQueries(db);
        for (int i = 1; i <= 3; i++) tracks.upsert(track("" + i, "Song " + i), 1);
        new LikeQueries(db).toggle("2", 50);
        PlaylistQueries pl = new PlaylistQueries(db);
        String id = pl.create("Mix", 7);
        pl.addTrack(id, "3");
        pl.addTrack(id, "1");
        new PlayStatsQueries(db).recordPlay("1", 100);
        new PlayStatsQueries(db).recordPlay("1", 200);
        settings.setTheme(ThemeMode.DARK);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        AtomicReference<BackupSummary> exported = new AtomicReference<>();
        service.export(file, exported::set);
        ShadowLooper.idleMainLooper();
        assertEquals(new BackupSummary(1, 1, 1, exported.get().settings(), 0, BackupSummary.Error.NONE), exported.get());
        assertTrue(exported.get().settings() >= 1);

        // other device: song 3 is missing, ids shifted by 100, one extra local playlist with the same name
        helper.close();
        helper = freshDb();
        service = service(helper);
        db = helper.getWritableDatabase();
        tracks = new TrackQueries(db);
        tracks.upsert(track("101", "Song 1"), 1);
        tracks.upsert(track("102", "Song 2"), 1);
        pl = new PlaylistQueries(db);
        String existing = pl.create("Mix", 1);
        pl.addTrack(existing, "102");
        settings.setTheme(ThemeMode.LIGHT);

        AtomicReference<BackupSummary> preview = new AtomicReference<>();
        service.preview(new ByteArrayInputStream(file.toByteArray()), preview::set);
        ShadowLooper.idleMainLooper();
        assertEquals(1, preview.get().unmatched());
        assertEquals(1, preview.get().playlists());

        AtomicReference<BackupSummary> restored = new AtomicReference<>();
        service.restore(RestoreMode.MERGE, restored::set);
        ShadowLooper.idleMainLooper();
        assertTrue(restored.get().ok());
        assertEquals(new LikeQueries(db).allTrackIds(), java.util.Set.of("102"));
        assertEquals(List.of("102", "101"), pl.orderedTrackIds(existing)); // merged by name, missing "3" skipped
        assertEquals(1, pl.all().size());
        assertEquals(ThemeMode.DARK, settings.theme().getValue());

        // second restore of the same file is not possible without a new preview
        AtomicReference<BackupSummary> again = new AtomicReference<>();
        service.restore(RestoreMode.MERGE, again::set);
        ShadowLooper.idleMainLooper();
        assertEquals(BackupSummary.Error.NO_PREVIEW, again.get().error());

        // replace wipes the extra membership and recreates the playlist from the file
        service.preview(new ByteArrayInputStream(file.toByteArray()), preview::set);
        service.restore(RestoreMode.REPLACE, restored::set);
        ShadowLooper.idleMainLooper();
        assertEquals(1, pl.all().size());
        assertEquals(List.of("101"), pl.orderedTrackIds(pl.all().get(0).id()));
        helper.close();
    }

    @Test public void corruptAndNewerFilesChangeNothing() {
        DbHelper helper = freshDb();
        SqliteBackupService service = service(helper);
        AtomicReference<BackupSummary> r = new AtomicReference<>();
        service.preview(new ByteArrayInputStream("garbage".getBytes()), r::set);
        ShadowLooper.idleMainLooper();
        assertEquals(BackupSummary.Error.CORRUPT, r.get().error());
        service.preview(new ByteArrayInputStream(("{\"app\":\"musicplayer-backup\",\"version\":99}").getBytes()), r::set);
        ShadowLooper.idleMainLooper();
        assertEquals(BackupSummary.Error.NEWER_VERSION, r.get().error());
        service.restore(RestoreMode.REPLACE, r::set);
        ShadowLooper.idleMainLooper();
        assertEquals(BackupSummary.Error.NO_PREVIEW, r.get().error());
        helper.close();
    }
}
