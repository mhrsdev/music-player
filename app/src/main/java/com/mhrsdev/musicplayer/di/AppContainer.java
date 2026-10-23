package com.mhrsdev.musicplayer.di;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.content.ContextCompat;
import com.mhrsdev.musicplayer.BuildConfig;
import com.mhrsdev.musicplayer.data.lyrics.SqliteLyricsCacheStore;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.data.waveform.WaveformRepositoryImpl;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import com.mhrsdev.musicplayer.domain.repository.LibraryAdmin;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import com.mhrsdev.musicplayer.domain.repository.MusicSource;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.domain.repository.WaveformRepository;
import com.mhrsdev.musicplayer.fake.FakeHomeFeedSource;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakeLyricsRepository;
import com.mhrsdev.musicplayer.fake.FakeMusicSource;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import com.mhrsdev.musicplayer.fake.FakeWaveformRepository;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hand-written dependency container (plain Java DI), created once by {@code MusicPlayerApp}. Screens never construct
 * repositories; they read them from here.
 *
 * <p>Real implementations by default; {@link #USE_FAKES} (the single switch) or the two-argument constructor binds the
 * in-memory fakes instead (debug galleries, tests). Construction is cheap: no database, MediaStore or network access
 * happens until something is observed or called; the only eager piece is the player, which must exist when the
 * process is started by a media button.
 */
public final class AppContainer {
    /** The one place that decides real vs fake for the running app. */
    static final boolean USE_FAKES = false;

    /** Shared background thread for disk / DB / network work. Its thread exits after 30 s idle (battery, RAM). */
    public final Executor ioExecutor;
    public final LibraryRepository library;
    public final PlayerController player;
    public final LyricsRepository lyrics;
    public final WaveformRepository waveform;
    public final HomeFeedSource homeFeed;
    /** Always the fake: there is no online catalog in Phase 1. */
    public final MusicSource musicSource;

    private final Context appContext;
    private final boolean useFakes;
    private final SettingsStore settings;
    private final LibraryAdmin libraryAdmin;
    private final AtomicBoolean foregroundScanDone = new AtomicBoolean();

    public AppContainer(Context context) {
        this(context, USE_FAKES);
    }

    /** @param context any context (the application context is kept) */
    public AppContainer(Context context, boolean useFakes) {
        appContext = context.getApplicationContext();
        this.useFakes = useFakes;
        ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        io.allowCoreThreadTimeOut(true);
        ioExecutor = io;
        settings = new SettingsStore(appContext);
        musicSource = new FakeMusicSource();

        if (useFakes) {
            FakeLibraryRepository fake = new FakeLibraryRepository();
            library = fake;
            libraryAdmin = fake;
            player = new FakePlayerController();
            lyrics = new FakeLyricsRepository();
            waveform = new FakeWaveformRepository();
            homeFeed = new FakeHomeFeedSource(library);
        } else {
            DataModule data = DataModule.create(appContext, io, settings);
            library = data.library;
            libraryAdmin = data.library;
            homeFeed = data.homeFeed;
            WaveformRepositoryImpl realWaveform = WaveformModule.create(appContext, data.waveformStore, this::batterySaverActive);
            waveform = realWaveform;
            player = PlaybackModule.createController(appContext, data.library::recordPlay, realWaveform::prefetch);
            lyrics = LyricsModule.create(appContext, BuildConfig.VERSION_NAME,
                    new SqliteLyricsCacheStore(data.dbHelper, System::currentTimeMillis),
                    () -> !Boolean.FALSE.equals(settings.lyricsOnline().getValue()));
        }
    }

    /** Settings are real in both modes (SharedPreferences only). */
    public SettingsStore settings() {
        return settings;
    }

    /** Scanning and the extra playlist edits (rename, delete, remove, reorder). */
    public LibraryAdmin libraryAdmin() {
        return libraryAdmin;
    }

    /** Call when the user grants the audio permission: runs the first scan (or a stale one). */
    public void onAudioPermissionGranted() {
        foregroundScanDone.set(true);
        libraryAdmin.scanIfStale();
    }

    /** Call when the first activity of this process starts: refreshes the library once if the device's media changed. */
    public void onAppForeground() {
        if (!useFakes && hasAudioPermission() && foregroundScanDone.compareAndSet(false, true)) {
            libraryAdmin.scanIfStale();
        }
    }

    /** The user's battery-saver setting combined with the system's; read on any thread. */
    private boolean batterySaverActive() {
        PowerManager power = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        return settings.batterySaver().getValue().active(power != null && power.isPowerSaveMode());
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
