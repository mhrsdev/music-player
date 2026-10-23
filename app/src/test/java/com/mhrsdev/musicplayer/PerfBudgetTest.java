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
                "INTERNET", "REQUEST_INSTALL_PACKAGES")); // install: user-started self-update only
        // BLUETOOTH_CONNECT is optional (output chip name); allowed if the UI team adds it.
        permissions.remove("BLUETOOTH_CONNECT");
        assertEquals("new permission: review battery impact and update docs/PERF_BUDGET.md", expected, permissions);
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
}
