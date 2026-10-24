package com.mhrsdev.musicplayer.diag;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ThreadPoolExecutor} whose tasks cannot take the app down: a {@link RuntimeException} thrown by a
 * background task (a MediaStore provider error, "database is locked", a malformed tag...) would otherwise reach the
 * thread's uncaught-exception handler and kill the whole process, playback included. Release builds record it as a
 * non-fatal report (Settings &gt; Diagnostics) and carry on; debug builds still crash so bugs stay loud in tests.
 * A {@link LinkageError} (ExceptionInInitializerError, NoClassDefFoundError) is caught too: it is a bug in one helper, not a broken VM. Other Errors ({@link OutOfMemoryError}, ...) are never caught.
 */
public class SafeExecutor extends ThreadPoolExecutor {
    private final boolean rethrow;

    public SafeExecutor(int core, int max, long keepAlive, TimeUnit unit, BlockingQueue<Runnable> queue, boolean rethrow) {
        super(core, max, keepAlive, unit, queue);
        this.rethrow = rethrow;
    }

    public SafeExecutor(int core, int max, long keepAlive, TimeUnit unit, BlockingQueue<Runnable> queue,
            ThreadFactory factory, boolean rethrow) {
        super(core, max, keepAlive, unit, queue, factory);
        this.rethrow = rethrow;
    }

    @Override public void execute(Runnable task) {
        super.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException | LinkageError e) { // LinkageError: a failing static initializer / missing class
                if (rethrow) throw e;
                CrashReporter.nonFatal(Thread.currentThread(), e);
            }
        });
    }
}
