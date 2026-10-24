package com.mhrsdev.musicplayer.diag;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The only way the app compiles a regex it keeps in a static field. Android's {@code java.util.regex} is backed by ICU,
 * which rejects things the JDK (our unit tests) accepts (an unescaped {@code }} or {@code ]}, ...). A
 * {@link java.util.regex.PatternSyntaxException} in a static initializer kills the process with an
 * ExceptionInInitializerError (v0.3.1 crashed at launch on Android 10). Here a bad pattern is recorded in the
 * Diagnostics breadcrumbs and replaced by a pattern that never matches, so the feature degrades and the app lives.
 * {@code RegexIcuTest} fails the build for patterns ICU would reject.
 */
public class Regexes {
    /** Patterns that failed to compile in this process (tests assert 0). */
    static final AtomicInteger FAILURES = new AtomicInteger();
    /** Matches only this odd string: used instead of a pattern that does not compile. */
    private static final String NEVER = "￿￿￿ never ￿￿￿";

    private Regexes() { }

    public static Pattern compile(String regex) {
        return compile(regex, 0);
    }

    public static Pattern compile(String regex, int flags) {
        try {
            return Pattern.compile(regex, flags);
        } catch (RuntimeException e) { // PatternSyntaxException, or any other IllegalArgumentException
            FAILURES.incrementAndGet();
            CrashReporter.nonFatal(Thread.currentThread(), e);
            return Pattern.compile(Pattern.quote(NEVER));
        }
    }

    /** How many patterns fell back to the never-matching one. */
    public static int failures() {
        return FAILURES.get();
    }
}
