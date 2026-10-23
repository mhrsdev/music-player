package com.mhrsdev.musicplayer.di;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.os.Process;
import androidx.core.content.ContextCompat;
import com.mhrsdev.musicplayer.BuildConfig;
import com.mhrsdev.musicplayer.appwidget.WidgetUpdater;
import com.mhrsdev.musicplayer.data.lyrics.SqliteLyricsCacheStore;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.data.update.UpdateModule;
import com.mhrsdev.musicplayer.data.waveform.WaveformRepositoryImpl;
import com.mhrsdev.musicplayer.domain.repository.AudioEffectsController;
import com.mhrsdev.musicplayer.domain.repository.BackupService;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import com.mhrsdev.musicplayer.domain.repository.LibraryAdmin;
import com.mhrsdev.musicplayer.domain.repository.LibraryBrowser;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import com.mhrsdev.musicplayer.domain.repository.MusicSource;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.domain.repository.UpdateRepository;
import com.mhrsdev.musicplayer.domain.repository.WaveformRepository;
import com.mhrsdev.musicplayer.fake.FakeAudioEffects;
import com.mhrsdev.musicplayer.fake.FakeOutputDevices;
import com.mhrsdev.musicplayer.domain.repository.OutputDevices;
import com.mhrsdev.musicplayer.playback.DeviceProfileApplier;
import com.mhrsdev.musicplayer.playback.DeviceProfiles;
import com.mhrsdev.musicplayer.fake.FakeBackupService;
import com.mhrsdev.musicplayer.playback.NowPlayingBridge;
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

/**
 * Hand-written dependency container (plain Java DI), created once by {@code MusicPlayerApp}. Screens never construct
 * repositories; they read them from here.
 *
 * <p>Real implementations by default; {@link #USE_FAKES} (the single switch) or the two-argument constructor binds the
 * in-memory fakes instead (debug galleries, tests). Construction is cheap: no database, MediaStore or network access
 * happens until something is observed or called; the only eager piece is the player, which must exist when the
 * process is started by a media button.
 */
public class AppContainer {
    /** The one place that decides real vs fake for the running app. */
    static final boolean USE_FAKES = false;
    /** App start: skip the incremental scan if one finished this recently. */
    static final long FOREGROUND_SCAN_THROTTLE_MS = 20_000L;
    /** After a MediaStore change event: wait for the burst (a download) to settle before scanning. */
    static final long OBSERVER_DEBOUNCE_MS = 2_500L;

    /** Shared background thread for disk / DB / network work. Its thread exits after 30 s idle (battery, RAM). */
    public final Executor ioExecutor;
    public final LibraryRepository library;
    public final PlayerController player;
    public final LyricsRepository lyrics;
    public final WaveformRepository waveform;
    public final HomeFeedSource homeFeed;
    public final BackupService backup;
    /** Always the fake: there is no online catalog in Phase 1. */
    public final MusicSource musicSource;

    private final Context appContext;
    private final boolean useFakes;
    private final SettingsStore settings;
    private final LibraryAdmin libraryAdmin;
    private final LibraryBrowser libraryBrowser;
    private final AudioEffectsController audioEffects;
    private final OutputDevices outputDevices;
    private final DeviceProfiles deviceProfiles;
    private final DeviceProfileApplier deviceProfileApplier;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable observedScan = this::scanAfterMediaChange;
    private ContentObserver mediaObserver;
    private UpdateRepository updates;

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
        // Library scans get their own low-priority thread so Home / search reads never queue behind a 10k-row scan.
        ThreadPoolExecutor scan = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                r -> new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    r.run();
                }, "scan"));
        scan.allowCoreThreadTimeOut(true);
        settings = new SettingsStore(appContext);
        deviceProfiles = new DeviceProfiles(appContext);
        musicSource = new FakeMusicSource();

        if (useFakes) {
            FakeLibraryRepository fake = new FakeLibraryRepository();
            library = fake;
            libraryAdmin = fake;
            libraryBrowser = fake;
            player = new FakePlayerController();
            audioEffects = new FakeAudioEffects();
            outputDevices = new FakeOutputDevices();
            lyrics = new FakeLyricsRepository();
            waveform = new FakeWaveformRepository();
            homeFeed = new FakeHomeFeedSource(library);
            backup = new FakeBackupService();
        } else {
            DataModule data = DataModule.create(appContext, io, scan, settings);
            library = data.library;
            libraryAdmin = data.library;
            libraryBrowser = data.browser;
            homeFeed = data.homeFeed;
            backup = data.backup;
            WaveformRepositoryImpl realWaveform = WaveformModule.create(appContext, data.waveformStore, this::batterySaverActive);
            waveform = realWaveform;
            player = PlaybackModule.createController(appContext, data.library::recordPlay, realWaveform::prefetch);
            PlaybackModule.setLibraryReader(data.reader);
            // Home-screen widgets: redrawn on track / play-pause changes only, and only while this process lives.
            NowPlayingBridge.attach(appContext, np -> WidgetUpdater.update(appContext, np, io));
            audioEffects = PlaybackModule.createAudioEffects(appContext);
            outputDevices = PlaybackModule.createOutputDevices(appContext);
            PlaybackModule.setLikeHandler(data.library::toggleLike);
            lyrics = LyricsModule.create(appContext, BuildConfig.VERSION_NAME,
                    new SqliteLyricsCacheStore(data.dbHelper, System::currentTimeMillis),
                    () -> !Boolean.FALSE.equals(settings.lyricsOnline().getValue()));
        }
        // Saved per-device sounds are applied when their device becomes the output; listens only if any are saved.
        deviceProfileApplier = new DeviceProfileApplier(outputDevices, deviceProfiles, audioEffects, player);
        deviceProfileApplier.startIfNeeded();
    }

    /** Settings are real in both modes (SharedPreferences only). */
    public SettingsStore settings() {
        return settings;
    }

    /** Equalizer, bass boost, virtualizer and loudness (real effects on the player's audio session, or a fake). */
    public AudioEffectsController audioEffects() {
        return audioEffects;
    }

    /** Connected audio outputs and the media volume of the active one (the system callback runs only while observed). */
    public OutputDevices outputDevices() {
        return outputDevices;
    }

    /** Saved sound per output device. */
    public DeviceProfiles deviceProfiles() {
        return deviceProfiles;
    }

    /** Starts applying saved device sounds (call after the first profile is saved). */
    public void startDeviceProfiles() {
        deviceProfileApplier.startIfNeeded();
    }

    /** Scanning and the extra playlist edits (rename, delete, remove, reorder). */
    public LibraryAdmin libraryAdmin() {
        return libraryAdmin;
    }

    /** Smart lists, years, play counts, batch playlist edits, track details, forgetting deleted files. */
    public LibraryBrowser libraryBrowser() {
        return libraryBrowser;
    }

    /** Self-update from GitHub Releases; created on first use (no network or disk work until a check runs). */
    public synchronized UpdateRepository updates() {
        if (updates == null) updates = UpdateModule.create(appContext, BuildConfig.VERSION_NAME, BuildConfig.GITHUB_REPO);
        return updates;
    }

    /** Call when the user grants the audio permission: runs the first scan (or a stale one). */
    public void onAudioPermissionGranted() {
        libraryAdmin.scanIfStale();
    }

    /**
     * Call on EVERY activity start: an incremental scan if MediaStore changed (throttled to once per
     * {@link #FOREGROUND_SCAN_THROTTLE_MS}; the generation check makes it nearly free), and watches MediaStore for new
     * files while the app is visible; also runs the automatic update check if it is due (at most daily). Pair with {@link #onAppBackground()}.
     */
    public void onAppForeground() {
        if (useFakes) return;
        updates().checkIfDue();
        if (mediaObserver == null) {
            mediaObserver = new ContentObserver(mainHandler) {
                @Override public void onChange(boolean selfChange) {
                    // A download touches MediaStore many times: scan once, shortly after it settles.
                    mainHandler.removeCallbacks(observedScan);
                    mainHandler.postDelayed(observedScan, OBSERVER_DEBOUNCE_MS);
                }
            };
            appContext.getContentResolver().registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        }
        if (hasAudioPermission()) libraryAdmin.scanIfStale(FOREGROUND_SCAN_THROTTLE_MS);
    }

    /** Call when no activity is visible any more: stops watching MediaStore (no background observers). */
    public void onAppBackground() {
        mainHandler.removeCallbacks(observedScan);
        if (mediaObserver != null) {
            appContext.getContentResolver().unregisterContentObserver(mediaObserver);
            mediaObserver = null;
        }
    }

    private void scanAfterMediaChange() {
        if (hasAudioPermission()) libraryAdmin.scanIfStale();
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
