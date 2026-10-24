package com.mhrsdev.musicplayer.diag;

/**
 * {@code ApplicationExitInfo.REASON_*} and importance values as plain ints (stable platform constants), so the
 * summary logic runs on any API level and on the JVM.
 */
public class ExitReasons {
    public static final int UNKNOWN = 0;
    public static final int EXIT_SELF = 1;
    public static final int SIGNALED = 2;
    public static final int LOW_MEMORY = 3;
    public static final int CRASH = 4;
    public static final int CRASH_NATIVE = 5;
    public static final int ANR = 6;
    public static final int INITIALIZATION_FAILURE = 7;
    public static final int PERMISSION_CHANGE = 8;
    public static final int EXCESSIVE_RESOURCE_USAGE = 9;
    public static final int USER_REQUESTED = 10;
    public static final int USER_STOPPED = 11;
    public static final int DEPENDENCY_DIED = 12;
    public static final int OTHER = 13;
    public static final int FREEZER = 14;
    public static final int PACKAGE_STATE_CHANGE = 15;
    public static final int PACKAGE_UPDATED = 16;

    /** {@code RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE}: at or below this the person could see / hear the app. */
    static final int IMPORTANCE_PERCEPTIBLE = 230;

    /** What the person is told (one string per kind). */
    public enum Kind { CRASH, FROZE, LOW_MEMORY, MEMORY_LIMIT, SYSTEM, USER, UPDATE, NORMAL, UNKNOWN }

    /**
     * Android 17 per-app memory limiter: the app was killed for using too much memory itself (not because the phone
     * ran low). Reported as {@link #OTHER} with this marker in the description.
     */
    static final String MEMORY_LIMITER = "MemoryLimiter";

    private static final String[] NAMES = {"UNKNOWN", "EXIT_SELF", "SIGNALED", "LOW_MEMORY", "CRASH", "CRASH_NATIVE",
            "ANR", "INITIALIZATION_FAILURE", "PERMISSION_CHANGE", "EXCESSIVE_RESOURCE_USAGE", "USER_REQUESTED",
            "USER_STOPPED", "DEPENDENCY_DIED", "OTHER", "FREEZER", "PACKAGE_STATE_CHANGE", "PACKAGE_UPDATED"};

    private ExitReasons() { }

    public static String name(int reason) {
        return reason >= 0 && reason < NAMES.length ? NAMES[reason] : "REASON_" + reason;
    }

    /** As {@link #kind(int)}, recognising the Android 17 memory limiter from the description. */
    public static Kind kind(int reason, String description) {
        if (reason == OTHER && description != null && description.contains(MEMORY_LIMITER)) return Kind.MEMORY_LIMIT;
        return kind(reason);
    }

    public static Kind kind(int reason) {
        return switch (reason) {
            case CRASH, CRASH_NATIVE, INITIALIZATION_FAILURE -> Kind.CRASH;
            case ANR -> Kind.FROZE;
            case LOW_MEMORY -> Kind.LOW_MEMORY;
            case SIGNALED, EXCESSIVE_RESOURCE_USAGE, FREEZER, DEPENDENCY_DIED, PERMISSION_CHANGE, OTHER -> Kind.SYSTEM;
            case USER_REQUESTED, USER_STOPPED -> Kind.USER;
            case PACKAGE_STATE_CHANGE, PACKAGE_UPDATED -> Kind.UPDATE;
            case EXIT_SELF -> Kind.NORMAL;
            default -> Kind.UNKNOWN;
        };
    }

    /**
     * Worth asking the person for a log: any crash / freeze, or a system kill while the app was visible or playing
     * (a cached app killed for memory in the background is normal Android behaviour, not a bug).
     */
    public static boolean unexpected(int reason, int importance) {
        return unexpected(reason, importance, "");
    }

    /** As {@link #unexpected(int, int)}; a memory-limiter kill always counts (it is our own memory use). */
    public static boolean unexpected(int reason, int importance, String description) {
        Kind k = kind(reason, description);
        if (k == Kind.MEMORY_LIMIT) return true;
        if (k == Kind.CRASH || k == Kind.FROZE) return true;
        boolean kill = k == Kind.LOW_MEMORY || reason == SIGNALED || reason == EXCESSIVE_RESOURCE_USAGE;
        return kill && importance > 0 && importance <= IMPORTANCE_PERCEPTIBLE;
    }
}
