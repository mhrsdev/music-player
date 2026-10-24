package com.mhrsdev.musicplayer.di;

import android.content.Context;
import com.mhrsdev.musicplayer.BuildConfig;
import com.mhrsdev.musicplayer.data.backup.SqliteBackupService;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.repository.ChangeNotifier;
import com.mhrsdev.musicplayer.data.repository.LocalHomeFeedSource;
import com.mhrsdev.musicplayer.data.repository.SqliteLibraryBrowser;
import com.mhrsdev.musicplayer.data.repository.SqliteLibraryReader;
import com.mhrsdev.musicplayer.data.repository.SqliteLibraryRepository;
import com.mhrsdev.musicplayer.data.repository.SqliteWaveformStore;
import com.mhrsdev.musicplayer.data.scanner.MediaStoreScanner;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import java.util.concurrent.Executor;

/**
 * Builds the whole data layer from a Context and the io executor (which must run tasks one at a time, like
 * {@code AppContainer.ioExecutor}). Nothing here touches the database or MediaStore until something is observed
 * or called, so creating it on the main thread at startup is cheap.
 */
public class DataModule {
    public final DbHelper dbHelper;
    public final SettingsStore settings;
    public final MediaStoreScanner scanner;
    /** Also the entry point for {@code rescan}, {@code scanIfStale} and the extra playlist edits. */
    public final SqliteLibraryRepository library;
    public final LocalHomeFeedSource homeFeed;
    /** Smart lists, years, batch playlist edits, track details. */
    public final SqliteLibraryBrowser browser;
    /** Persistent waveform cache, ready to hand to {@code WaveformModule} (blocking calls, background thread). */
    public final SqliteWaveformStore waveformStore;

    /** User-data export / import over the same database and settings. */
    public final SqliteBackupService backup;

    /** Blocking library reads for Android Auto / external browsing (see {@code PlaybackModule.setLibraryReader}). */
    public final SqliteLibraryReader reader;

    private DataModule(Context context, Executor io, Executor scanIo, SettingsStore settings) {
        dbHelper = new DbHelper(context);
        this.settings = settings;
        scanner = new MediaStoreScanner(context, dbHelper, settings);
        ChangeNotifier notifier = new ChangeNotifier();
        library = new SqliteLibraryRepository(dbHelper, io, scanIo, notifier, scanner);
        homeFeed = new LocalHomeFeedSource(dbHelper, io, notifier);
        reader = new SqliteLibraryReader(dbHelper);
        browser = new SqliteLibraryBrowser(dbHelper, io, notifier, context.getContentResolver());
        waveformStore = new SqliteWaveformStore(dbHelper);
        backup = new SqliteBackupService(dbHelper, io, notifier, settings, BuildConfig.VERSION_NAME, System::currentTimeMillis);
    }

    /**
     * @param scanIo single-thread executor reserved for library scans (keeps {@code io} free for reads)
     * @param settings shared with the rest of the app (the scanner reads the folder filters from it)
     */
    public static DataModule create(Context context, Executor io, Executor scanIo, SettingsStore settings) {
        return new DataModule(context, io, scanIo, settings);
    }
}
