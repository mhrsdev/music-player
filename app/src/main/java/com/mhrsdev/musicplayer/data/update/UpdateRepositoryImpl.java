package com.mhrsdev.musicplayer.data.update;

import android.content.pm.PackageInstaller;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;
import com.mhrsdev.musicplayer.domain.model.UpdateState;
import com.mhrsdev.musicplayer.domain.model.UpdateState.ErrorKind;
import com.mhrsdev.musicplayer.domain.model.UpdateState.Kind;
import com.mhrsdev.musicplayer.domain.repository.UpdateRepository;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * The update state machine: IDLE -> CHECKING -> UP_TO_DATE | AVAILABLE | ERROR; AVAILABLE -> DOWNLOADING -> VERIFYING
 * -> READY_TO_INSTALL | ERROR. One operation at a time. Downloaded files live in {@code dir} and are wiped before every
 * download and once per process start. Network and disk work runs on {@code io}.
 */
final class UpdateRepositoryImpl implements UpdateRepository {
    static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    static final long RETRY_AFTER_FAILURE_MS = 60L * 60 * 1000;
    private static final String APK_FILE = "update.apk"; // fixed name: never a path taken from the release JSON

    private final Executor io;
    private final UpdateStore store;
    private final UpdateChecker checker;
    private final UpdateHttp http;
    private final ApkInspector inspector;
    private final ApkInstaller installer;
    private final Connectivity network;
    private final LongSupplier clock;
    private final SemVer current;
    private final File dir;

    private final MutableLiveData<UpdateState> state = new MutableLiveData<>(UpdateState.IDLE);
    private final MutableLiveData<UpdateSettings> settings = new MutableLiveData<>();
    private final LiveData<ReleaseInfo> available;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicBoolean cleaned = new AtomicBoolean();
    private volatile long lastAttemptMs;
    @Nullable private volatile File readyApk;

    UpdateRepositoryImpl(Executor io, UpdateStore store, UpdateHttp http, ApkInspector inspector, ApkInstaller installer,
            Connectivity network, LongSupplier clock, String versionName, String repo, File dir) {
        this.io = io;
        this.store = store;
        this.http = http;
        this.checker = new UpdateChecker(http, store, repo);
        this.inspector = inspector;
        this.installer = installer;
        this.network = network;
        this.clock = clock;
        SemVer parsed = SemVer.parse(versionName);
        this.current = parsed != null ? parsed : new SemVer(0, 0, 0, null);
        this.dir = dir;
        settings.setValue(store.settings());
        available = Transformations.map(state, s ->
                s.kind() == Kind.AVAILABLE || s.kind() == Kind.READY_TO_INSTALL ? s.release() : null);
        InstallResultReceiver.listener = this::onInstallStatus;
    }

    @Override public LiveData<UpdateState> state() {
        return state;
    }

    @Override public LiveData<ReleaseInfo> availableUpdate() {
        return available;
    }

    @Override public LiveData<UpdateSettings> settings() {
        return settings;
    }

    @Override public void setAutoCheck(boolean on) {
        UpdateSettings s = store.settings();
        saveSettings(new UpdateSettings(on, s.includePrereleases(), s.wifiOnly(), s.lastCheckedMs()));
    }

    @Override public void setIncludePrereleases(boolean on) {
        UpdateSettings s = store.settings();
        saveSettings(new UpdateSettings(s.autoCheck(), on, s.wifiOnly(), s.lastCheckedMs()));
        checkNow();
    }

    @Override public void setWifiOnly(boolean on) {
        UpdateSettings s = store.settings();
        saveSettings(new UpdateSettings(s.autoCheck(), s.includePrereleases(), on, s.lastCheckedMs()));
    }

    @Override public void checkNow() {
        check(true);
    }

    @Override public void checkIfDue() {
        if (cleaned.compareAndSet(false, true)) io.execute(this::wipeDownloads);
        UpdateSettings s = store.settings();
        if (!s.autoCheck() || busy.get()) return;
        long now = clock.getAsLong();
        boolean due = s.lastCheckedMs() == 0 || now - s.lastCheckedMs() >= CHECK_INTERVAL_MS || now < s.lastCheckedMs();
        if (!due) {
            if (state.getValue().kind() == Kind.IDLE) io.execute(this::showCached); // survives a process restart without any request
        } else if (now - lastAttemptMs >= RETRY_AFTER_FAILURE_MS && network.isOnline() && !(s.wifiOnly() && network.isMetered())) {
            check(false);
        }
    }

    @Override public void skipVersion() {
        UpdateState s = state.getValue();
        if (s.release() == null || busy.get()) return;
        store.putSkippedTag(s.release().tag());
        io.execute(this::wipeDownloads);
        post(UpdateState.UP_TO_DATE);
    }

    @Override public void download() {
        UpdateState s = state.getValue();
        ReleaseInfo release = s.release();
        if (release == null || (s.kind() != Kind.AVAILABLE && s.kind() != Kind.ERROR) || !busy.compareAndSet(false, true)) return;
        post(UpdateState.downloading(release, 0));
        io.execute(() -> {
            try {
                post(downloadAndVerify(release));
            } finally {
                busy.set(false);
            }
        });
    }

    @Override public void install() {
        UpdateState s = state.getValue();
        File apk = readyApk;
        if (s.kind() != Kind.READY_TO_INSTALL || apk == null || !busy.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                installer.install(apk);
            } catch (IOException | RuntimeException e) {
                post(UpdateState.error(ErrorKind.INSTALL, s.release()));
            } finally {
                busy.set(false);
            }
        });
    }

    /** Session outcome from {@link InstallResultReceiver}: success replaces the process anyway; cancelling is not an error. */
    private void onInstallStatus(int status) {
        UpdateState s = state.getValue();
        if (status == PackageInstaller.STATUS_SUCCESS) post(UpdateState.IDLE);
        else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) post(UpdateState.error(ErrorKind.INSTALL, s.release()));
    }

    private void check(boolean manual) {
        if (!busy.compareAndSet(false, true)) return;
        UpdateState before = state.getValue();
        post(UpdateState.CHECKING);
        lastAttemptMs = clock.getAsLong();
        io.execute(() -> {
            try {
                UpdateSettings s = store.settings();
                List<ReleaseInfo> releases = checker.fetch(s.includePrereleases());
                saveSettings(new UpdateSettings(s.autoCheck(), s.includePrereleases(), s.wifiOnly(), clock.getAsLong()));
                post(evaluate(releases, s.includePrereleases(), manual));
            } catch (UpdateException e) {
                post(manual ? UpdateState.error(e.kind, null) : before); // a failed background check is silent
            } finally {
                busy.set(false);
            }
        });
    }

    private void showCached() {
        List<ReleaseInfo> cached = checker.cached(store.settings().includePrereleases());
        if (cached != null && !busy.get() && state.getValue().kind() == Kind.IDLE) {
            post(evaluate(cached, store.settings().includePrereleases(), false));
        }
    }

    private UpdateState evaluate(List<ReleaseInfo> releases, boolean includePrereleases, boolean manual) {
        ReleaseInfo best = UpdateChecker.newest(releases, includePrereleases, current);
        if (best == null || (!manual && best.tag().equals(store.skippedTag()))) return UpdateState.UP_TO_DATE;
        return UpdateState.available(best);
    }

    /** @return the state to end in: READY_TO_INSTALL, or an ERROR that keeps the release so the user can retry */
    private UpdateState downloadAndVerify(ReleaseInfo release) {
        wipeDownloads();
        File apk = new File(dir, APK_FILE);
        try {
            String expected = release.sha256Hex() != null ? release.sha256Hex() : fetchChecksum(release.sha256Url());
            if (expected == null) return UpdateState.error(ErrorKind.CHECKSUM, release); // unverifiable: do not even download
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            int[] last = {-1};
            http.download(release.apkUrl(), apk, p -> {
                if (p != last[0]) {
                    last[0] = p;
                    post(UpdateState.downloading(release, p));
                }
            });
            post(UpdateState.verifying(release));
            if (!expected.equalsIgnoreCase(Sha256.of(apk))) return reject(apk, ErrorKind.CHECKSUM, release);
            switch (inspector.inspect(apk)) {
                case OK:
                    readyApk = apk;
                    return UpdateState.readyToInstall(release);
                case DIFFERENT_SIGNER:
                    return reject(apk, ErrorKind.DIFFERENT_SIGNATURE, null); // retrying cannot help
                default:
                    return reject(apk, ErrorKind.INVALID_APK, release);
            }
        } catch (IOException e) {
            apk.delete();
            return UpdateState.error(ErrorKind.DOWNLOAD, release);
        }
    }

    @Nullable
    private String fetchChecksum(@Nullable String url) throws IOException {
        if (url == null) return null;
        UpdateHttp.Response r = http.get(url, null);
        if (r.code() != 200) throw new IOException("HTTP " + r.code());
        return Sha256.parseChecksumFile(r.body());
    }

    private static UpdateState reject(File apk, ErrorKind kind, @Nullable ReleaseInfo retry) {
        apk.delete();
        return UpdateState.error(kind, retry);
    }

    private void wipeDownloads() {
        readyApk = null;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    private void saveSettings(UpdateSettings s) {
        store.putSettings(s);
        if (Looper.myLooper() == Looper.getMainLooper()) settings.setValue(s); else settings.postValue(s);
    }

    private void post(UpdateState s) {
        if (Looper.myLooper() == Looper.getMainLooper()) state.setValue(s); else state.postValue(s);
    }
}
