package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Source-scan guards for docs/PERF_BUDGET.md (battery / RAM rules). They read the Java sources and the manifest, so a
 * new feature that adds background work, a permission, a leaking thread or per-frame allocation fails here with the
 * offending file. If a change is justified, update the allow-list below AND docs/PERF_BUDGET.md in the same commit.
 */
public class PerfBudgetTest {
    private static final Path MAIN = new File("src/main").toPath();

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN.resolve("java"))) {
            return s.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/fake/"))
                    .collect(Collectors.toList());
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static String name(Path p) {
        return MAIN.relativize(p).toString();
    }

    /** Files (relative to src/main) containing {@code pattern}, comments included on purpose: keep the words out. */
    private static Set<String> filesMatching(String pattern) throws IOException {
        Pattern re = Pattern.compile(pattern);
        Set<String> out = new TreeSet<>();
        for (Path p : javaFiles()) if (re.matcher(read(p)).find()) out.add(name(p));
        return out;
    }

    @Test public void noScheduledBackgroundWork() throws IOException {
        // WorkManager / alarms / jobs / extra wake locks would wake the device while the app is idle (rule B1, B2).
        Set<String> found = filesMatching("WorkManager|AlarmManager|JobScheduler|JobService|JobInfo|newWakeLock|WakeLock\\.acquire"
                + "|setExactAndAllowWhileIdle|setAlarmClock|SyncAdapter|BOOT_COMPLETED|FirebaseMessaging");
        assertEquals("background scheduling APIs are forbidden: " + found, new TreeSet<String>(), found);
    }

    @Test public void manifestPermissionsAndComponentsAreTheReviewedSet() throws IOException {
        String manifest = read(MAIN.resolve("AndroidManifest.xml"));
        Set<String> permissions = new TreeSet<>();
        Matcher m = Pattern.compile("<uses-permission android:name=\"([^\"]+)\"").matcher(manifest);
        while (m.find()) permissions.add(m.group(1).replace("android.permission.", ""));
        Set<String> expected = new TreeSet<>(Arrays.asList("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                "POST_NOTIFICATIONS", "WAKE_LOCK", "ACCESS_NETWORK_STATE", "READ_MEDIA_AUDIO", "READ_EXTERNAL_STORAGE",
                "INTERNET", "REQUEST_INSTALL_PACKAGES", // install: user-started self-update only
                "WRITE_EXTERNAL_STORAGE")); // C1: maxSdkVersion 28 only, asked at the first "Delete from device" on Android 8-9
        // BLUETOOTH_CONNECT is optional (output chip name); allowed if the UI team adds it.
        permissions.remove("BLUETOOTH_CONNECT");
        assertEquals("new permission: review battery impact and update docs/PERF_BUDGET.md", expected, permissions);
        assertTrue("legacy storage write must stay capped at API 28",
                manifest.contains("WRITE_EXTERNAL_STORAGE\" android:maxSdkVersion=\"28\""));
        assertTrue("largeHeap must stay off", !manifest.contains("largeHeap=\"true\""));
        assertEquals("only PlaybackService may run as a service", 1, count(manifest, "<service"));
        Set<String> receivers = new TreeSet<>();
        Matcher r = Pattern.compile("<receiver\\s+android:name=\"([^\"]+)\"").matcher(manifest);
        while (r.find()) receivers.add(r.group(1));
        assertEquals("new receiver: it must not wake the app on boot/connectivity/timers; update PERF_BUDGET.md",
                new TreeSet<>(Arrays.asList(".data.update.InstallResultReceiver", ".playback.MediaButtonRestartReceiver",
                        ".appwidget.CompactWidgetProvider", ".appwidget.SquareWidgetProvider", ".appwidget.WideWidgetProvider",
                        ".appwidget.WidgetActionReceiver")), receivers);
        for (String wake : new String[] {"BOOT_COMPLETED", "CONNECTIVITY_CHANGE", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM",
                "RECEIVE_BOOT"}) {
            assertTrue("manifest must not use " + wake, !manifest.contains(wake));
        }
        try (Stream<Path> xml = Files.walk(MAIN.resolve("res/xml"))) {
            for (Path p : xml.filter(x -> x.getFileName().toString().startsWith("widget_info")).collect(Collectors.toList())) {
                assertTrue(name(p) + ": widgets redraw on playback events only, updatePeriodMillis must be 0",
                        read(p).contains("updatePeriodMillis=\"0\""));
            }
        }
        assertTrue("service type", manifest.contains("foregroundServiceType=\"mediaPlayback\""));
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }

    @Test public void delayedHandlerCallbacksOnlyInReviewedPlaces() throws IOException {
        // Every postDelayed is a potential timer that runs while backgrounded; each one below is gated (see BATTERY_AUDIT).
        Set<String> allowed = new TreeSet<>(Arrays.asList(
                "java/com/mhrsdev/musicplayer/playback/ExoPlayerController.java", // tick only playing+observed; sleep; play count
                "java/com/mhrsdev/musicplayer/playback/PlaybackService.java",     // idle stop after 60 s
                "java/com/mhrsdev/musicplayer/playback/PlaybackStore.java",       // 1 s queue-save debounce
                "java/com/mhrsdev/musicplayer/playback/HeadsetControls.java",     // one 350 ms click window per headset button press
                "java/com/mhrsdev/musicplayer/ui/widget/SkeletonView.java",       // only while attached + visible + motion on
                "java/com/mhrsdev/musicplayer/di/AppContainer.java",              // MediaStore-change scan debounce, removed in onAppBackground
                "java/com/mhrsdev/musicplayer/playback/NowPlayingBridge.java",     // one coalesced widget push per playback event
                "java/com/mhrsdev/musicplayer/ui/library/FastScrollerView.java",  // one-shot hide after a touch
                "java/com/mhrsdev/musicplayer/ui/player/PlayerFragment.java"));   // lyrics auto-scroll resume, view-scoped
        Set<String> found = filesMatching("postDelayed\\(|scheduleAtFixedRate|scheduleWithFixedDelay|new Timer\\(");
        found.removeAll(allowed);
        assertEquals("new delayed callbacks: gate them on visibility/playback and add to the allow-list: " + found,
                new TreeSet<String>(), found);
    }

    @Test public void loopingAnimationsRideTheSharedFrameDriver() throws IOException {
        // Decorative loops use PeriodicTick / FrameDriver (one Choreographer callback for the app); a private
        // postInvalidateDelayed / postOnAnimationDelayed loop is a second wake-up source that is easy to leave running.
        Set<String> found = filesMatching("postInvalidateDelayed\\(|postOnAnimationDelayed\\(|Choreographer\\.getInstance");
        found.remove("java/com/mhrsdev/musicplayer/ui/motion/FrameDriver.java");
        assertEquals("use ui.motion.PeriodicTick: " + found, new TreeSet<String>(), found);
    }

    @Test public void threadsAlwaysTimeOut() throws IOException {
        // Fixed / single / cached executors keep an idle thread (stack + scheduler wakeups) for the whole process life.
        Set<String> found = filesMatching("Executors\\.new(Fixed|SingleThread|Cached|Scheduled)");
        assertEquals("use ThreadPoolExecutor + allowCoreThreadTimeOut(true): " + found, new TreeSet<String>(), found);
    }

    @Test public void noContextOrViewHeldInStaticFields() throws IOException {
        Pattern leak = Pattern.compile("static\\s+(?!final\\s)[\\w.<>]*\\s*\\b(Context|Activity|View|Fragment|Resources)\\b\\s+\\w+\\s*(=|;)");
        Set<String> found = new TreeSet<>();
        for (Path p : javaFiles()) if (leak.matcher(read(p)).find()) found.add(name(p));
        found.remove("java/com/mhrsdev/musicplayer/ui/motion/Motion.java"); // holds the APPLICATION context only
        assertEquals("static Context/View leaks the activity: " + found, new TreeSet<String>(), found);
    }

    @Test public void noAllocationInDrawPaths() throws IOException {
        Pattern alloc = Pattern.compile("new\\s+(Paint|Path|RectF|Rect|Matrix|LinearGradient|RadialGradient|SweepGradient"
                + "|BitmapShader|PorterDuffXfermode|ColorMatrix|ColorMatrixColorFilter|float\\[|int\\[|String\\[|"
                + "StringBuilder|ArrayList|HashMap)\\b|String\\.format|createBitmap\\(");
        List<String> violations = new ArrayList<>();
        Pattern head = Pattern.compile("(void\\s+(onDraw|dispatchDraw|draw|drawChild|onDrawForeground|onScrolled)\\s*\\([^)]*\\)\\s*\\{)");
        for (Path p : javaFiles()) {
            String src = read(p);
            Matcher h = head.matcher(src);
            while (h.find()) {
                int open = h.end() - 1, depth = 0, i = open;
                for (; i < src.length(); i++) {
                    char c = src.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}' && --depth == 0) break;
                }
                Matcher a = alloc.matcher(src.substring(open, i));
                if (a.find()) violations.add(name(p) + ": " + a.group());
            }
        }
        assertEquals("allocation inside a draw method (hoist into a field; rule R3): " + violations,
                new ArrayList<String>(), violations);
    }

    @Test public void applicationOnCreateStaysCheap() throws IOException {
        String app = read(MAIN.resolve("java/com/mhrsdev/musicplayer/MusicPlayerApp.java"));
        int start = app.indexOf("void onCreate()");
        String body = app.substring(start, app.indexOf("\n    }", start));
        Set<String> forbidden = new HashSet<>(Arrays.asList("getWritableDatabase", "getReadableDatabase", "startService",
                "new Thread", "Executor", "MediaStore", "openInputStream", "Thread.sleep", "BitmapFactory"));
        for (String f : forbidden) assertTrue("Application.onCreate must not call " + f, !body.contains(f));
    }

    // ---- PF-B (D29): background / sustained CPU budgets, v0.3.1 ----

    private static final String PKG = "java/com/mhrsdev/musicplayer/";

    /** Value of {@code NAME = 5 * 60_000L;} style constants (products of integer literals) in a source file. */
    private static long constant(String file, String name) throws IOException {
        Matcher m = Pattern.compile(name + "\\s*=\\s*([0-9_L *]+);").matcher(read(MAIN.resolve(PKG + file)));
        assertTrue(file + " must define " + name, m.find());
        long v = 1;
        for (String f : m.group(1).split("\\*")) v *= Long.parseLong(f.trim().replace("_", "").replace("L", ""));
        return v;
    }

    @Test public void libraryScanIsDebouncedThrottledAndOnItsOwnLowPriorityThread() throws IOException {
        assertTrue("a download touches MediaStore many times: wait for the burst",
                constant("di/AppContainer.java", "OBSERVER_DEBOUNCE_MS") >= 2_000);
        assertTrue("foreground rescans at most every 20 s",
                constant("di/AppContainer.java", "FOREGROUND_SCAN_THROTTLE_MS") >= 20_000);
        assertTrue("without a MediaStore generation (API < 30) every check is a full pass: at most every 5 min",
                constant("di/AppContainer.java", "LEGACY_FOREGROUND_SCAN_THROTTLE_MS") >= 5 * 60_000);
        String container = read(MAIN.resolve(PKG + "di/AppContainer.java"));
        assertTrue("scan thread priority", container.contains("Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)"));
        assertTrue("MediaStore is only watched while the UI is visible", container.contains("unregisterContentObserver"));
        // The scanner reads MediaStore columns only: decoding or tag-parsing every file would be a library-sized job.
        String scanner = read(MAIN.resolve(PKG + "data/scanner/MediaStoreScanner.java"));
        for (String heavy : new String[] {"MediaMetadataRetriever", "MediaExtractor", "MediaCodec"}) {
            assertTrue("the scan must not open audio files (" + heavy + ")", !scanner.contains(heavy));
        }
    }

    @Test public void waveformDecodingIsOnDemandBackgroundPriorityAndGated() throws IOException {
        String module = read(MAIN.resolve(PKG + "di/WaveformModule.java"));
        assertTrue("one extractor thread at background priority",
                (module.contains("new ThreadPoolExecutor(1, 1,") || module.contains("SafeExecutor(1, 1,"))
                        && module.contains("THREAD_PRIORITY_BACKGROUND"));
        String container = read(MAIN.resolve(PKG + "di/AppContainer.java"));
        for (String gate : new String[] {"getCurrentThermalStatus", "THERMAL_STATUS_MODERATE", "Build.VERSION_CODES.Q",
                "!uiVisible", "usesWaveform()", "batterySaverActive()"}) {
            assertTrue("prefetch gate missing: " + gate, container.contains(gate));
        }
        String repo = read(MAIN.resolve(PKG + "data/waveform/WaveformRepositoryImpl.java"));
        assertTrue("throttled applies to prefetch and to on-screen jobs",
                count(repo, "throttled.getAsBoolean()") == 2 && count(repo, "skipPrefetch.getAsBoolean()") == 2);
        // Only the next-track listener may start a decode without an on-screen observer.
        Set<String> callers = filesMatching("\\.prefetch\\(|::prefetch");
        assertEquals(new TreeSet<>(Arrays.asList("java/com/mhrsdev/musicplayer/di/AppContainer.java")), callers);
    }

    @Test public void widgetsAreRedrawnOnPlaybackStateEventsOnlyAndCoalesced() throws IOException {
        String bridge = read(MAIN.resolve(PKG + "playback/NowPlayingBridge.java"));
        assertTrue("coalescing window >= 200 ms", constant("playback/NowPlayingBridge.java", "COALESCE_MS") >= 200);
        for (String tick : new String[] {"EVENT_POSITION_DISCONTINUITY", "EVENT_TIMELINE_CHANGED", "EVENT_IS_LOADING_CHANGED",
                "EVENT_MEDIA_METADATA_CHANGED", "TICK"}) {
            assertTrue("a widget push on " + tick + " would redraw on every tick/seek/buffer", !bridge.contains(tick));
        }
        assertTrue("the cover of one track is resolved once, not per play/pause",
                read(MAIN.resolve(PKG + "appwidget/WidgetUpdater.java")).contains("lastArtKey"));
    }

    @Test public void playerStateTickNeverCopiesTheQueue() throws IOException {
        assertEquals("the 4 Hz tick period", 250, constant("playback/ExoPlayerController.java", "TICK_MS"));
        assertTrue(read(MAIN.resolve(PKG + "domain/model/PlaybackState.java")).contains("queue = frozen(queue);"));
        assertTrue(read(MAIN.resolve(PKG + "playback/ExoPlayerController.java")).contains("return PlaybackState.frozen(tracks);"));
    }

    @Test public void noRegexCompiledPerCall() throws IOException {
        // String.matches / replaceAll / replaceFirst with a regex literal compile a Pattern each call; hot loops use a
        // static final Pattern. (ui/ is reviewed by the UI budget; SemVer runs once per release comparison.)
        Set<String> found = filesMatching("\\.(matches|replaceAll|replaceFirst)\\(\"(\\[|[^\"]*\\\\\\\\)");
        found.removeIf(f -> f.contains("/ui/"));
        found.remove("java/com/mhrsdev/musicplayer/data/update/SemVer.java");
        assertEquals("hoist the regex into a static final Pattern: " + found, new TreeSet<String>(), found);
    }

    @Test public void updateCheckIsAtMostDaily() throws IOException {
        assertEquals(24L * 60 * 60 * 1000, constant("data/update/UpdateRepositoryImpl.java", "CHECK_INTERVAL_MS"));
        assertTrue(constant("data/update/UpdateRepositoryImpl.java", "RETRY_AFTER_FAILURE_MS") >= 60 * 60 * 1000);
    }

    @Test public void aBaselineProfileCompilesTheAppAheadOfTime() throws IOException {
        // A sideloaded APK has no cloud profile: after every install / update the app runs interpreted + JIT until the
        // nightly dexopt. The catch-all profile makes AGP expand the wildcards (expandReleaseArtProfileWildcards).
        String profile = read(MAIN.resolve("baseline-prof.txt"));
        assertTrue(profile.contains("HSPLcom/mhrsdev/musicplayer/**->**(**)**"));
        assertTrue(profile.contains("Lcom/mhrsdev/musicplayer/**;"));
    }
}
