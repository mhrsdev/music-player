package com.mhrsdev.musicplayer.diag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** X1 (D30): crash file + ring, handler chaining, exit-history summarising, breadcrumbs, "unseen incident". */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DiagnosticsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private Diagnostics store;

    @Before public void setUp() throws Exception {
        store = new Diagnostics(new File(tmp.getRoot(), "crash"));
        Breadcrumbs.clear();
    }

    /** Records what the platform's handler would have received. */
    private static class Previous implements Thread.UncaughtExceptionHandler {
        Thread thread;
        Throwable error;

        @Override public void uncaughtException(Thread t, Throwable e) {
            thread = t;
            error = e;
        }
    }

    @Test public void crashWritesReportAndStillReachesThePreviousHandler() {
        Previous prev = new Previous();
        CrashReporter r = new CrashReporter(prev, store, "App 0.3.0 (30)");
        Breadcrumbs.add("screen HomeFragment");
        Breadcrumbs.add("mini play/pause");
        Throwable boom = new IllegalStateException("outer", new ArithmeticException("divide by zero"));
        Thread t = new Thread("artwork");

        r.uncaughtException(t, boom);

        assertSame("never swallow the crash", boom, prev.error);
        assertSame(t, prev.thread);
        String text = store.lastCrash();
        assertNotNull(text);
        assertTrue(text.contains("App 0.3.0 (30)"));
        assertTrue(text.contains("Thread \"artwork\""));
        assertTrue(text.contains("java.lang.IllegalStateException: outer"));
        assertTrue("causes included", text.contains("Caused by: java.lang.ArithmeticException: divide by zero"));
        assertTrue(text.contains("screen HomeFragment"));
        assertTrue(text.contains("mini play/pause"));
        assertTrue(text.contains("Heap used"));
        assertTrue(store.lastCrashTime() > 0);
    }

    @Test public void ringKeepsTheNewestFive() throws Exception {
        for (int i = 1; i <= 7; i++) store.writeCrash("crash " + i, 1000L * i);
        File[] ring = store.crashFiles();
        assertEquals(Diagnostics.MAX_CRASHES, ring.length);
        assertEquals("crash-3000.txt", ring[0].getName());
        assertEquals("crash 7", store.lastCrash());
        assertEquals(7000L, store.lastCrashTime());
    }

    @Test public void aBrokenStoreStillDelegatesTheCrash() throws Exception {
        File notADir = tmp.newFile("blocked");
        Previous prev = new Previous();
        CrashReporter r = new CrashReporter(prev, new Diagnostics(new File(notADir, "crash")), "h");
        RuntimeException e = new RuntimeException("x");
        r.uncaughtException(Thread.currentThread(), e);
        assertSame(e, prev.error);
    }

    /** Without a previous handler (plain JVM) it must neither exit the JVM nor call itself through ThreadGroup. */
    @Test public void noPreviousHandlerDoesNotRecurse() {
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        CrashReporter r = new CrashReporter(null, store, "h");
        try {
            Thread.setDefaultUncaughtExceptionHandler(r);
            r.uncaughtException(new Thread("w"), new IllegalStateException("x"));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
        assertTrue(store.lastCrash().contains("IllegalStateException: x"));
    }

    @Test public void nonFatalIsWrittenAndCapped() throws Exception {
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler(null);
            android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
            CrashReporter.install(c, "1", 1);
            CrashReporter.NON_FATALS.set(0);
            Diagnostics app = Diagnostics.get(c);
            app.clear();
            SafeExecutor ex = new SafeExecutor(1, 1, 1, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(), false);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            ex.execute(() -> { throw new IllegalArgumentException("bad tag"); });
            ex.execute(done::countDown); // the worker survived the first task
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
            ex.shutdown();
            assertTrue(ex.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
            String last = app.lastCrash();
            assertNotNull(last);
            assertTrue(last.startsWith("NON-FATAL"));
            assertTrue(last.contains("IllegalArgumentException: bad tag"));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }

    @Test public void installingTwiceDoesNotChainItself() {
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        try {
            android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
            CrashReporter.install(c, "1", 1);
            Thread.UncaughtExceptionHandler first = Thread.getDefaultUncaughtExceptionHandler();
            CrashReporter.install(c, "1", 1);
            assertTrue(first instanceof CrashReporter);
            assertSame(first, Thread.getDefaultUncaughtExceptionHandler());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }

    @Test public void exitHistoryIsMergedNewestFirstWithoutDuplicates() throws Exception {
        ExitRecord crash = new ExitRecord(3000, ExitReasons.CRASH, 0, 100, 200_000, 300_000, "boom", "screen=HomeFragment");
        ExitRecord lmk = new ExitRecord(2000, ExitReasons.LOW_MEMORY, 9, 100, 900_000, 1_100_000, "lmk", "");
        ExitRecord anr = new ExitRecord(1000, ExitReasons.ANR, 0, 100, 1, 2, "Input dispatching timed out", "");
        assertTrue(ExitInfoReader.ingest(store, List.of(), List.of(lmk, anr), Map.of(1000L, "\"main\" prio=5")));
        List<ExitRecord> stored = store.exits();
        // The next start sees lmk again (the platform keeps it) plus the new crash: only the crash is added.
        assertTrue(ExitInfoReader.ingest(store, stored, List.of(crash), Map.of()));
        assertFalse(ExitInfoReader.ingest(store, store.exits(), List.of(), Map.of()));

        List<ExitRecord> all = store.exits();
        assertEquals(3, all.size());
        assertEquals(crash, all.get(0));
        assertEquals(lmk, all.get(1));
        assertEquals("\"main\" prio=5", store.trace(1000));
        String report = store.report("Device X");
        assertTrue(report.contains("CRASH status=0"));
        assertTrue(report.contains("LOW_MEMORY"));
        assertTrue(report.contains("[screen=HomeFragment]"));
        assertTrue(report.contains("\"main\" prio=5"));
    }

    @Test public void exitHistoryIsCapped() throws Exception {
        List<ExitRecord> many = new ArrayList<>();
        for (int i = 1; i <= 15; i++) many.add(new ExitRecord(i, ExitReasons.USER_REQUESTED, 0, 400, 0, 0, "", ""));
        ExitInfoReader.ingest(store, List.of(), many, Map.of());
        assertEquals(Diagnostics.MAX_EXITS, store.exits().size());
        assertEquals(15, store.exits().get(0).timestamp());
    }

    @Test public void recordsSurviveTabsAndNewlines() {
        ExitRecord r = new ExitRecord(5, ExitReasons.CRASH_NATIVE, 6, 125, 7, 8,
                ExitRecord.clean("a\tb\nc"), ExitRecord.clean("screen=موج"));
        assertEquals(r, ExitRecord.decode(r.encode()));
        assertNull(ExitRecord.decode("garbage"));
        assertNull(ExitRecord.decode("x\t1\t2\t3\t4\t5\t6\t7"));
    }

    @Test public void onlyCrashesFreezesAndVisibleKillsAreUnexpected() {
        assertTrue(ExitReasons.unexpected(ExitReasons.CRASH, 400));
        assertTrue(ExitReasons.unexpected(ExitReasons.CRASH_NATIVE, 400));
        assertTrue(ExitReasons.unexpected(ExitReasons.INITIALIZATION_FAILURE, 400));
        assertTrue(ExitReasons.unexpected(ExitReasons.ANR, 100));
        assertTrue("killed for memory while on screen", ExitReasons.unexpected(ExitReasons.LOW_MEMORY, 100));
        assertTrue("killed while playing (foreground service)", ExitReasons.unexpected(ExitReasons.LOW_MEMORY, 125));
        assertFalse("cached app killed in the background is normal", ExitReasons.unexpected(ExitReasons.LOW_MEMORY, 400));
        assertFalse(ExitReasons.unexpected(ExitReasons.USER_REQUESTED, 100));
        assertFalse(ExitReasons.unexpected(ExitReasons.EXIT_SELF, 100));
        assertEquals(ExitReasons.Kind.CRASH, ExitReasons.kind(ExitReasons.CRASH_NATIVE));
        assertEquals("CRASH_NATIVE", ExitReasons.name(ExitReasons.CRASH_NATIVE));
        assertEquals("REASON_99", ExitReasons.name(99));
    }

    @Test public void incidentStaysUntilSeen() throws Exception {
        assertEquals(0, store.unseenIncident());
        ExitInfoReader.ingest(store, List.of(), List.of(new ExitRecord(4000, ExitReasons.USER_REQUESTED, 0, 100, 0, 0, "", "")),
                Map.of());
        assertEquals("a normal close is no incident", 0, store.unseenIncident());
        store.writeCrash("x", 5000);
        assertEquals(5000, store.unseenIncident());
        store.markSeen(5000);
        assertEquals(0, store.unseenIncident());
        ExitInfoReader.ingest(store, store.exits(), List.of(new ExitRecord(6000, ExitReasons.LOW_MEMORY, 0, 100, 0, 0, "", "")),
                Map.of());
        assertEquals(6000, store.unseenIncident());
        store.clear();
        assertEquals(0, store.unseenIncident());
        assertNull(store.lastCrash());
        assertTrue(store.exits().isEmpty());
    }

    @Test public void breadcrumbRingKeepsTheLast32InOrder() {
        for (int i = 0; i < 40; i++) Breadcrumbs.add("b" + i);
        List<String> s = Breadcrumbs.snapshot();
        assertEquals(Breadcrumbs.SIZE, s.size());
        assertTrue(s.get(0).endsWith(" b8"));
        assertTrue(s.get(31).endsWith(" b39"));
        assertEquals("b39", Breadcrumbs.last());
    }

    @Test public void nativeTombstoneKeepsReadableStringsOnly() {
        String raw = "\u0001\u0002abort message: boom\u0000\u0007xy\u0003libc.so\u0000";
        assertEquals("abort message: boom\nlibc.so\n", ExitInfoReader.printable(raw, 1000));
    }

    @Test public void breadcrumbsCanBeSavedForALowMemoryKill() throws Exception {
        Breadcrumbs.add("trim memory 15");
        store.saveBreadcrumbs();
        assertTrue(store.report("h").contains("trim memory 15"));
        assertTrue(new String(Files.readAllBytes(new File(tmp.getRoot(), "crash/breadcrumbs.txt").toPath()))
                .contains("trim memory 15"));
    }
}
