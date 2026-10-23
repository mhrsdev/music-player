package com.mhrsdev.musicplayer.data.update;

import java.io.File;
import java.io.IOException;

/** Hands a verified APK to the system installer. */
interface ApkInstaller {

    /** Commits the install session; the outcome arrives later through {@link InstallResultReceiver}. */
    void install(File apk) throws IOException;
}
