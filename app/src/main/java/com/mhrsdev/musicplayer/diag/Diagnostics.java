package com.mhrsdev.musicplayer.diag;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The on-disk diagnostics store, app-private ({@code files/crash/}): the last {@link #MAX_CRASHES} crash reports
 * ({@code crash-<time>.txt}, newest also as {@code last.txt}), past process exits ({@code exits.tsv} + trimmed traces),
 * breadcrumbs saved when memory runs low, and the time the person last saw / shared them. Nothing leaves the device
 * unless the person taps Share. All methods do file I/O: call them off the main thread (except from the crash handler).
 */
public class Diagnostics {
    static final int MAX_CRASHES = 5;
    static final int MAX_EXITS = 10;
    static final int TRACE_MAX = 8 * 1024;
    static final int CRASH_MAX = 48 * 1024;
    static final String LAST = "last.txt";
    private static final String EXITS = "exits.tsv";
    private static final String SEEN = "seen";
    private static final String CRUMBS = "breadcrumbs.txt";

    private static final MutableLiveData<Integer> VERSION = new MutableLiveData<>(0);
    private static int version;

    private final File dir;

    public Diagnostics(File dir) {
        this.dir = dir;
    }

    public static Diagnostics get(Context context) {
        return new Diagnostics(new File(context.getApplicationContext().getFilesDir(), "crash"));
    }

    /** Bumped (any thread) whenever stored diagnostics change; the Settings screen and the Home notice reload on it. */
    public static LiveData<Integer> changes() {
        return VERSION;
    }

    static synchronized void notifyChanged() {
        VERSION.postValue(++version);
    }

    // ---- crashes ----

    /** Writes one crash report and keeps only the newest {@link #MAX_CRASHES}. Called from the crash handler. */
    void writeCrash(String text, long time) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no dir");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        write(new File(dir, "crash-" + time + ".txt"), bytes);
        write(new File(dir, LAST), bytes);
        File[] ring = crashFiles();
        for (int i = 0; i < ring.length - MAX_CRASHES; i++) ring[i].delete(); // oldest first
    }

    /** The crash files, oldest first. */
    File[] crashFiles() {
        File[] f = dir.listFiles((d, name) -> name.startsWith("crash-") && name.endsWith(".txt"));
        if (f == null) return new File[0];
        Arrays.sort(f, (a, b) -> Long.compare(crashTime(a), crashTime(b)));
        return f;
    }

    private static long crashTime(File f) {
        String n = f.getName();
        try {
            return Long.parseLong(n.substring("crash-".length(), n.length() - ".txt".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @WorkerThread @Nullable
    public String lastCrash() {
        return read(new File(dir, LAST), CRASH_MAX);
    }

    /** Time of the newest crash report, 0 when there is none. */
    @WorkerThread
    public long lastCrashTime() {
        File[] f = crashFiles();
        return f.length == 0 ? 0 : crashTime(f[f.length - 1]);
    }

    // ---- exits ----

    @WorkerThread
    public List<ExitRecord> exits() {
        List<ExitRecord> out = new ArrayList<>();
        String all = read(new File(dir, EXITS), 64 * 1024);
        if (all == null) return out;
        for (String line : all.split("\n")) {
            ExitRecord r = ExitRecord.decode(line);
            if (r != null) out.add(r);
        }
        return out;
    }

    @WorkerThread
    void saveExits(List<ExitRecord> records) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no dir");
        StringBuilder sb = new StringBuilder();
        List<String> keepTraces = new ArrayList<>();
        for (ExitRecord r : records) {
            sb.append(r.encode()).append('\n');
            keepTraces.add(traceName(r.timestamp()));
        }
        write(new File(dir, EXITS), sb.toString().getBytes(StandardCharsets.UTF_8));
        File[] traces = dir.listFiles((d, name) -> name.startsWith("exit-") && !keepTraces.contains(name));
        if (traces != null) for (File f : traces) f.delete();
    }

    @WorkerThread
    void saveTrace(long timestamp, String trace) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no dir");
        write(new File(dir, traceName(timestamp)), trace.getBytes(StandardCharsets.UTF_8));
    }

    @WorkerThread @Nullable
    public String trace(long timestamp) {
        return read(new File(dir, traceName(timestamp)), TRACE_MAX * 2);
    }

    private static String traceName(long timestamp) {
        return "exit-" + timestamp + ".trace";
    }

    // ---- breadcrumbs saved before a likely kill ----

    /** Saves the in-memory breadcrumbs (memory is running low: a kill may follow and no handler would run). */
    @WorkerThread
    public void saveBreadcrumbs() {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            StringBuilder sb = new StringBuilder("saved ").append(formatTime(System.currentTimeMillis())).append('\n');
            for (String s : Breadcrumbs.snapshot()) sb.append(s).append('\n');
            write(new File(dir, CRUMBS), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            // diagnostics never break the app
        }
    }

    // ---- what the person has seen ----

    /** The newest crash or unexpected exit not yet seen / shared / dismissed; 0 when there is none. */
    @WorkerThread
    public long unseenIncident() {
        long seen = seenUntil();
        long newest = Math.max(lastCrashTime(), 0);
        for (ExitRecord r : exits()) if (r.unexpected()) newest = Math.max(newest, r.timestamp());
        return newest > seen ? newest : 0;
    }

    @WorkerThread
    public long seenUntil() {
        String s = read(new File(dir, SEEN), 32);
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The person saw everything up to {@code time} (dismissed the notice or shared the log). */
    @WorkerThread
    public void markSeen(long time) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            write(new File(dir, SEEN), Long.toString(time).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // best effort
        }
        notifyChanged();
    }

    /** Deletes every stored report (keeps the "seen" mark so an old exit does not come back as new). */
    @WorkerThread
    public void clear() {
        long seen = Math.max(seenUntil(), System.currentTimeMillis());
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) f.delete();
        markSeen(seen);
    }

    // ---- the shared text ----

    /** Plain text for Share: device header, exits (with traces), the last crash, saved breadcrumbs. No file paths. */
    @WorkerThread
    public String report(String header) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("Mowj diagnostics\n").append(header).append('\n');
        sb.append("\n== Recent exits (newest first) ==\n");
        List<ExitRecord> exits = exits();
        if (exits.isEmpty()) sb.append("(none recorded").append(Build.VERSION.SDK_INT < 30 ? ": needs Android 11+" : "")
                .append(")\n");
        for (ExitRecord r : exits) {
            sb.append(r.describe()).append('\n');
            String t = trace(r.timestamp());
            if (t != null) sb.append("-- trace --\n").append(t).append("\n-- end trace --\n");
        }
        sb.append("\n== Last crash ==\n");
        String crash = lastCrash();
        sb.append(crash == null ? "(none)\n" : crash);
        String crumbs = read(new File(dir, CRUMBS), 8 * 1024);
        if (crumbs != null) sb.append("\n== Breadcrumbs saved on low memory ==\n").append(crumbs);
        return sb.toString();
    }

    /** App version, device and OS, e.g. for the report header. */
    public static String deviceInfo(String versionName, long versionCode) {
        return "App " + versionName + " (" + versionCode + ")\n"
                + "Device " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n"
                + "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ") patch "
                + Build.VERSION.SECURITY_PATCH + "\n"
                + "ABI " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?") + "\n"
                + "Max heap " + (Runtime.getRuntime().maxMemory() >> 20) + " MB";
    }

    public static String formatTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(time));
    }

    // ---- io ----

    private static void write(File f, byte[] bytes) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
    }

    @Nullable static String read(File f, int max) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return readTrimmed(in, max);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** At most {@code max} bytes of {@code in} as UTF-8. */
    static String readTrimmed(InputStream in, int max) throws IOException {
        byte[] buf = new byte[Math.min(max, 16 * 1024)];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while (out.size() < max && (n = in.read(buf, 0, Math.min(buf.length, max - out.size()))) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
