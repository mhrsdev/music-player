package com.mhrsdev.musicplayer.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The last {@link #SIZE} things the app did ("screen HomeFragment", "scan start", "trim 15"...), kept in memory only and
 * written into the crash file. Lock-free and allocation-free apart from the caller's string: callers pass constants or
 * short concatenations on rare events (navigation, player actions, scans), never per frame.
 */
public class Breadcrumbs {
    /** Power of two (index masking). */
    public static final int SIZE = 32;

    private static final AtomicReferenceArray<String> MESSAGES = new AtomicReferenceArray<>(SIZE);
    private static final AtomicLongArray TIMES = new AtomicLongArray(SIZE);
    private static final AtomicInteger NEXT = new AtomicInteger();

    private Breadcrumbs() { }

    /** Records {@code message} (any thread). */
    public static void add(String message) {
        int i = NEXT.getAndIncrement() & (SIZE - 1);
        TIMES.set(i, System.currentTimeMillis());
        MESSAGES.set(i, message);
    }

    /** Oldest first, each "HH:mm:ss.SSS message" (UTC-free local clock time of day). */
    public static List<String> snapshot() {
        int end = NEXT.get();
        int start = Math.max(0, end - SIZE);
        List<String> out = new ArrayList<>(end - start);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int n = start; n < end; n++) {
            int i = n & (SIZE - 1);
            String m = MESSAGES.get(i);
            if (m == null) continue;
            cal.setTimeInMillis(TIMES.get(i));
            out.add(String.format(Locale.US, "%02d:%02d:%02d.%03d %s", cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.SECOND),
                    cal.get(java.util.Calendar.MILLISECOND), m));
        }
        return out;
    }

    /** The most recent message, or null. */
    public static String last() {
        int end = NEXT.get();
        return end == 0 ? null : MESSAGES.get((end - 1) & (SIZE - 1));
    }

    /** Tests only. */
    static void clear() {
        for (int i = 0; i < SIZE; i++) {
            MESSAGES.set(i, null);
            TIMES.set(i, 0);
        }
        NEXT.set(0);
    }
}
