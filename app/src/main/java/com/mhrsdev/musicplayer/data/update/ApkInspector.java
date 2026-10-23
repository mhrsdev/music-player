package com.mhrsdev.musicplayer.data.update;

import java.io.File;

/** Decides whether a downloaded APK may replace the running app. Needs the platform, so the real one is untested on the JVM. */
interface ApkInspector {

    enum Verdict { OK, INVALID, DIFFERENT_SIGNER }

    Verdict inspect(File apk);
}
