package com.mhrsdev.musicplayer.data.update;

import android.content.Context;
import com.mhrsdev.musicplayer.domain.repository.UpdateRepository;
import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Builds the real {@link UpdateRepository}. Construction does no I/O beyond opening the preferences file. */
public class UpdateModule {
    private UpdateModule() { }

    /**
     * @param versionName the running app's version (BuildConfig.VERSION_NAME)
     * @param repo        {@code owner/name} of the releases repository (BuildConfig.GITHUB_REPO)
     */
    public static UpdateRepository create(Context context, String versionName, String repo) {
        // Own thread, not AppContainer.ioExecutor: a download takes minutes and must not stall database and lyrics work.
        ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        io.allowCoreThreadTimeOut(true);
        return new UpdateRepositoryImpl(io, new PrefsUpdateStore(context),
                new UrlConnectionUpdateHttp("MusicPlayer/" + versionName + " (https://github.com/" + repo + ")"),
                new PackageApkInspector(context), new SessionApkInstaller(context), new SystemConnectivity(context),
                System::currentTimeMillis, versionName, repo, new File(context.getCacheDir(), "updates"));
    }
}
