package com.mhrsdev.musicplayer.ui.diagnostics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiagnosticsViewModelTest {
    @Test public void excerptIsTheExceptionAndFirstFrames() {
        String crash = "Crash at 2026-10-24 05:18:00\nApp 0.3.0 (30)\nThread \"main\"\nHeap used 1 KB\n\n"
                + "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)\n\tat a.B.d(B.java:2)\n"
                + "\tat a.B.e(B.java:3)\n\tat a.B.f(B.java:4)\n\tat a.B.g(B.java:5)\n";
        assertEquals("java.lang.IllegalStateException: boom\nat a.B.c(B.java:1)\nat a.B.d(B.java:2)\n"
                + "at a.B.e(B.java:3)\nat a.B.f(B.java:4)", DiagnosticsViewModel.excerpt(crash));
    }
}
