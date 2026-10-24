package com.mhrsdev.musicplayer.data.update;

import com.mhrsdev.musicplayer.diag.Regexes;
import androidx.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code [v]MAJOR.MINOR.PATCH[-pre][+build]} with semver 2.0 ordering: a pre-release is lower than its release. */
public record SemVer(long major, long minor, long patch, @Nullable String pre) implements Comparable<SemVer> {
    private static final Pattern FORMAT =
            Regexes.compile("^v?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    /** @return null for anything that is not a version (such tags are ignored) */
    @Nullable
    public static SemVer parse(@Nullable String text) {
        if (text == null) return null;
        Matcher m = FORMAT.matcher(text.trim());
        if (!m.matches()) return null;
        return new SemVer(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)), m.group(4));
    }

    @Override public int compareTo(SemVer o) {
        int c = Long.compare(major, o.major);
        if (c == 0) c = Long.compare(minor, o.minor);
        if (c == 0) c = Long.compare(patch, o.patch);
        if (c != 0) return c;
        if (pre == null || o.pre == null) return pre == null ? (o.pre == null ? 0 : 1) : -1;
        String[] a = pre.split("\\.");
        String[] b = o.pre.split("\\.");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            c = compareIdentifier(a[i], b[i]);
            if (c != 0) return c;
        }
        return Integer.compare(a.length, b.length);
    }

    /** Numeric identifiers compare as numbers and sort below alphanumeric ones. */
    private static int compareIdentifier(String a, String b) {
        boolean na = a.chars().allMatch(Character::isDigit);
        boolean nb = b.chars().allMatch(Character::isDigit);
        if (na && nb) {
            String x = a.replaceFirst("^0+(?=.)", "");
            String y = b.replaceFirst("^0+(?=.)", "");
            return x.length() != y.length() ? Integer.compare(x.length(), y.length()) : x.compareTo(y);
        }
        if (na != nb) return na ? -1 : 1;
        return a.compareTo(b);
    }
}
