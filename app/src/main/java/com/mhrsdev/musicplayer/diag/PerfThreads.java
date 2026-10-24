package com.mhrsdev.musicplayer.diag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-thread CPU time of the process over a recording (from {@code /proc/self/task/<tid>/stat}, read by the probe
 * every 30 s): which thread burns the battery, without a profiler. Threads are grouped by name with digits folded
 * ({@code pool-3-thread-2} and {@code pool-5-thread-1} are one "pool-#-thread-#"). Pure Java apart from the reader in
 * {@link PerfProbe}; the first pass is the baseline, a thread first seen later counts from zero.
 */
public class PerfThreads {
    /** A group of same-named threads. {@code ticks} are clock ticks (usually 1/100 s) used during the recording. */
    public record Group(String name, int threads, long ticks) { }

    private static class Entry {
        String name;
        long base, last;
    }

    private final Map<Integer, Entry> byTid = new HashMap<>();
    private boolean baselineDone;

    /** One thread seen in the current pass. */
    public synchronized void sample(int tid, String name, long ticks) {
        Entry e = byTid.get(tid);
        if (e == null) {
            e = new Entry();
            e.name = normalize(name);
            e.base = baselineDone ? 0 : ticks;
            byTid.put(tid, e);
        }
        e.last = ticks;
    }

    /** Ends a pass (the first one is the baseline). */
    public synchronized void endPass() {
        baselineDone = true;
    }

    public synchronized boolean hasData() {
        return baselineDone && !byTid.isEmpty();
    }

    /** Groups by CPU used, most first. */
    public synchronized List<Group> groups() {
        Map<String, long[]> sum = new HashMap<>(); // {threads, ticks}
        for (Entry e : byTid.values()) {
            long[] g = sum.computeIfAbsent(e.name, k -> new long[2]);
            g[0]++;
            g[1] += Math.max(0, e.last - e.base);
        }
        List<Group> out = new ArrayList<>(sum.size());
        for (Map.Entry<String, long[]> g : sum.entrySet()) out.add(new Group(g.getKey(), (int) g.getValue()[0], g.getValue()[1]));
        out.sort((a, b) -> Long.compare(b.ticks, a.ticks));
        return out;
    }

    public static long totalTicks(List<Group> groups) {
        long n = 0;
        for (Group g : groups) n += g.ticks;
        return n;
    }

    /** Digit runs become '#': "pool-12-thread-3" to "pool-#-thread-#". */
    static String normalize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        boolean digits = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= '0' && c <= '9') {
                if (!digits) sb.append('#');
                digits = true;
            } else {
                sb.append(c);
                digits = false;
            }
        }
        return sb.toString();
    }

    /** The thread name of a {@code stat} line: between the first '(' and the LAST ')' (the name may contain both). */
    static String commOf(String stat) {
        int a = stat.indexOf('('), z = stat.lastIndexOf(')');
        return a < 0 || z < a ? "?" : stat.substring(a + 1, z);
    }

    /** utime + stime (fields 14 and 15) of a {@code stat} line, or -1 when it does not parse. */
    static long ticksOf(String stat) {
        int z = stat.lastIndexOf(')');
        if (z < 0) return -1;
        // after ") ": state is field 3, so utime (14) is the 12th token and stime (15) the 13th
        int field = 3;
        long utime = -1, stime = -1;
        int i = z + 1, n = stat.length();
        while (i < n && field <= 15) {
            while (i < n && stat.charAt(i) == ' ') i++;
            int j = i;
            while (j < n && stat.charAt(j) != ' ') j++;
            if (field == 14 || field == 15) {
                try {
                    long v = Long.parseLong(stat.substring(i, j));
                    if (field == 14) utime = v; else stime = v;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            field++;
            i = j;
        }
        return utime < 0 || stime < 0 ? -1 : utime + stime;
    }
}
