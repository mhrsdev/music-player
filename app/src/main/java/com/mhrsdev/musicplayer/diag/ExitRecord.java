package com.mhrsdev.musicplayer.diag;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One past process exit (a compact copy of {@code ApplicationExitInfo}). Stored one per line, tab-separated; the
 * optional ANR / native-crash trace lives in its own file next to it.
 *
 * @param summary our own {@code setProcessStateSummary} text from that process (last screen etc.), or empty
 */
public record ExitRecord(long timestamp, int reason, int status, int importance, long pssKb, long rssKb,
        String description, String summary) {

    public ExitReasons.Kind kind() {
        return ExitReasons.kind(reason, description);
    }

    public boolean unexpected() {
        return ExitReasons.unexpected(reason, importance, description);
    }

    /** One line, no tabs or newlines inside fields. */
    public String encode() {
        return timestamp + "\t" + reason + "\t" + status + "\t" + importance + "\t" + pssKb + "\t" + rssKb + "\t"
                + clean(description) + "\t" + clean(summary);
    }

    /** The line written by {@link #encode()}, or null when it is not one. */
    @Nullable public static ExitRecord decode(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 8) return null;
        try {
            return new ExitRecord(Long.parseLong(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                    Integer.parseInt(f[3]), Long.parseLong(f[4]), Long.parseLong(f[5]), f[6], f[7]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Human-readable, for the shared log. */
    public String describe() {
        return String.format(Locale.US, "%s  %s status=%d importance=%d pss=%dKB rss=%dKB%s%s",
                Diagnostics.formatTime(timestamp), ExitReasons.name(reason), status, importance, pssKb, rssKb,
                description.isEmpty() ? "" : "  \"" + description + "\"",
                summary.isEmpty() ? "" : "  [" + summary + "]");
    }

    /** Stored + fresh, de-duplicated by timestamp, newest first, at most {@code max}. */
    public static List<ExitRecord> merge(Collection<ExitRecord> stored, Collection<ExitRecord> fresh, int max) {
        Map<Long, ExitRecord> byTime = new LinkedHashMap<>();
        for (ExitRecord r : stored) byTime.put(r.timestamp(), r);
        for (ExitRecord r : fresh) byTime.putIfAbsent(r.timestamp(), r);
        List<ExitRecord> out = new ArrayList<>(byTime.values());
        out.sort(Comparator.comparingLong(ExitRecord::timestamp).reversed());
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    static String clean(@Nullable String s) {
        if (s == null) return "";
        String c = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        return c.length() > 300 ? c.substring(0, 300) : c;
    }
}
