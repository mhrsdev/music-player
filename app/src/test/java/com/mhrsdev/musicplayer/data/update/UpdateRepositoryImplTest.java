package com.mhrsdev.musicplayer.data.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageInstaller;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;
import com.mhrsdev.musicplayer.domain.model.UpdateState;
import com.mhrsdev.musicplayer.domain.model.UpdateState.ErrorKind;
import com.mhrsdev.musicplayer.domain.model.UpdateState.Kind;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** State machine, throttling and download pipeline against fakes. The real APK signer check and PackageInstaller are not covered (platform classes). */
public class UpdateRepositoryImplTest {
    private static final String APK_URL = "https://github.com/mhrsdev/music-player-mowj/releases/download/v1.0.0/MusicPlayer-v1.0.0.apk";
    private static final String SHA_URL = APK_URL + ".sha256";
    private static final String LIST_URL = "https://api.github.com/repos/mhrsdev/music-player-mowj/releases?per_page=5";
    private static final String LATEST_URL = "https://api.github.com/repos/mhrsdev/music-player-mowj/releases/latest";
    private static final long HOUR = 60L * 60 * 1000;
    private static final long DAY = 24 * HOUR;

    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private final FakeUpdates.Http http = new FakeUpdates.Http();
    private final FakeUpdates.Store store = new FakeUpdates.Store();
    private final FakeUpdates.Inspector inspector = new FakeUpdates.Inspector();
    private final FakeUpdates.Installer installer = new FakeUpdates.Installer();
    private final FakeUpdates.Net net = new FakeUpdates.Net();
    private final byte[] apkBytes = FakeUpdates.bytes("pretend this is an apk");
    private final List<UpdateState> states = new ArrayList<>();
    private long now = 1_000 * DAY;
    private File dir;
    private UpdateRepositoryImpl repo;

    @Before public void setUp() throws Exception {
        dir = new File(tmp.getRoot(), "updates");
        http.answer(LIST_URL, 200, "\"e1\"", Fixtures.read("update/releases-list.json"));
        http.files.put(APK_URL, apkBytes);
        File ref = tmp.newFile();
        Files.write(ref.toPath(), apkBytes);
        http.answer(SHA_URL, 200, null, Sha256.of(ref) + "  MusicPlayer-v1.0.0.apk\n");
        repo = create("0.1.0");
    }

    private UpdateRepositoryImpl create(String version) {
        UpdateRepositoryImpl r = new UpdateRepositoryImpl(Runnable::run, store, http, inspector, installer, net, () -> now, version, "mhrsdev/music-player-mowj", dir);
        r.availableUpdate().observeForever(a -> { });
        r.state().observeForever(states::add);
        return r;
    }

    private Kind kind() {
        return repo.state().getValue().kind();
    }

    private List<Kind> kinds() {
        return states.stream().map(UpdateState::kind).collect(Collectors.toList());
    }

    private File apkFile() {
        return new File(dir, "update.apk");
    }

    // --- checking ---

    @Test public void manualCheckFindsTheNewestAndStampsTheTime() {
        repo.checkNow();
        assertEquals(List.of(Kind.IDLE, Kind.CHECKING, Kind.AVAILABLE), kinds());
        assertEquals("v1.0.0", repo.state().getValue().release().tag());
        assertEquals("v1.0.0", repo.availableUpdate().getValue().tag());
        assertEquals(now, repo.settings().getValue().lastCheckedMs());
    }

    @Test public void upToDateAndFailedChecks() {
        repo = create("1.0.0");
        repo.checkNow();
        assertEquals(Kind.UP_TO_DATE, kind());
        assertNull(repo.availableUpdate().getValue());

        http.offline = true;
        now += HOUR;
        repo.checkNow();
        assertEquals(ErrorKind.NETWORK, repo.state().getValue().error());
        assertEquals("a failed check does not count as checked", now - HOUR, store.settings().lastCheckedMs());
    }

    @Test public void devBuildWithoutAVersionIsTreatedAsOlderThanEverything() {
        repo = create("dev");
        repo.checkNow();
        assertEquals(Kind.AVAILABLE, kind());
    }

    @Test public void automaticCheckRunsOnceADayAndSendsTheEtagNextTime() {
        repo.checkIfDue();
        assertEquals(Kind.AVAILABLE, kind());
        int requests = http.requests.size();

        now += DAY - 1;
        repo.checkIfDue();
        assertEquals("not due yet: no request", requests, http.requests.size());

        now += 1;
        http.answers.get(LIST_URL).add(new UpdateHttp.Response(304, null, null));
        repo.checkIfDue();
        assertEquals(requests + 1, http.requests.size());
        assertEquals(LIST_URL + "|\"e1\"", http.requests.get(http.requests.size() - 1));
        assertEquals(Kind.AVAILABLE, kind());
    }

    @Test public void automaticCheckRespectsTheSwitchesAndTheNetwork() {
        store.settings = new UpdateSettings(false, true, false, 0);
        repo = create("0.1.0");
        repo.checkIfDue();
        assertTrue("auto-check off", http.requests.isEmpty());

        store.settings = new UpdateSettings(true, true, true, 0);
        net.metered = true;
        repo.checkIfDue();
        assertTrue("Wi-Fi only on a metered network", http.requests.isEmpty());

        net.metered = false;
        net.online = false;
        repo.checkIfDue();
        assertTrue("offline", http.requests.isEmpty());

        repo.checkNow(); // by hand it always goes through the settings (offline fake network is irrelevant to the HTTP fake)
        assertEquals(1, http.requests.size());
    }

    @Test public void aFailedAutomaticCheckIsSilentAndRetriedAfterAnHour() {
        http.offline = true;
        repo.checkIfDue();
        assertEquals("the screen shows nothing for a background failure", Kind.IDLE, kind());
        assertEquals(1, http.requests.size());

        now += HOUR - 1;
        repo.checkIfDue();
        assertEquals(1, http.requests.size());

        now += 1;
        http.offline = false;
        repo.checkIfDue();
        assertEquals(Kind.AVAILABLE, kind());
    }

    @Test public void aSkippedVersionIsHiddenFromAutomaticChecksButNotFromManualOnes() {
        repo.checkNow();
        repo.skipVersion();
        assertEquals(Kind.UP_TO_DATE, kind());
        assertNull(repo.availableUpdate().getValue());

        now += DAY;
        repo.checkIfDue();
        assertEquals(Kind.UP_TO_DATE, kind());

        repo.checkNow();
        assertEquals(Kind.AVAILABLE, kind());
    }

    @Test public void aStaleCacheIsShownAfterARestartWithoutAnyRequest() {
        repo.checkIfDue(); // fills the cache and stamps the time
        int requests = http.requests.size();
        states.clear();

        repo = create("0.1.0"); // new process: state is IDLE again
        now += HOUR;
        repo.checkIfDue();
        assertEquals(Kind.AVAILABLE, kind());
        assertEquals(requests, http.requests.size());
    }

    @Test public void togglingPrereleasesChecksAgainWithTheOtherEndpoint() {
        http.answer(LATEST_URL, 404, null, null);
        repo.setIncludePrereleases(false);
        assertEquals(LATEST_URL + "|null", http.requests.get(0));
        assertEquals(Kind.UP_TO_DATE, kind());
        assertFalse(store.settings().includePrereleases());
    }

    // --- download, verify, install ---

    @Test public void downloadsVerifiesAndGetsReadyThenInstalls() {
        repo.checkNow();
        repo.download();
        assertEquals(List.of(Kind.IDLE, Kind.CHECKING, Kind.AVAILABLE, Kind.DOWNLOADING, Kind.DOWNLOADING, Kind.DOWNLOADING,
                Kind.DOWNLOADING, Kind.VERIFYING, Kind.READY_TO_INSTALL), kinds());
        assertTrue(apkFile().isFile());

        repo.install();
        assertEquals(List.of(apkFile()), installer.installed);
    }

    @Test public void aCorruptDownloadIsDeletedAndNeverInstalled() {
        http.files.put(APK_URL, FakeUpdates.bytes("tampered"));
        repo.checkNow();
        repo.download();
        assertEquals(ErrorKind.CHECKSUM, repo.state().getValue().error());
        assertNotNull("retry is possible", repo.state().getValue().release());
        assertFalse(apkFile().exists());
        repo.install();
        assertTrue(installer.installed.isEmpty());
    }

    @Test public void anUnreadableChecksumFileRefusesBeforeDownloading() {
        http.answers.get(SHA_URL).clear();
        http.answer(SHA_URL, 200, null, "<html>Not Found</html>");
        repo.checkNow();
        repo.download();
        assertEquals(ErrorKind.CHECKSUM, repo.state().getValue().error());
        assertTrue(http.downloads.isEmpty());
    }

    @Test public void aDifferentSigningKeyIsRefusedAndNotRetryable() {
        inspector.verdict = ApkInspector.Verdict.DIFFERENT_SIGNER;
        repo.checkNow();
        repo.download();
        UpdateState s = repo.state().getValue();
        assertEquals(ErrorKind.DIFFERENT_SIGNATURE, s.error());
        assertNull("no install, no retry button", s.release());
        assertFalse(apkFile().exists());
        repo.install();
        assertTrue(installer.installed.isEmpty());
    }

    @Test public void anApkThatIsNotThisAppIsRefused() {
        inspector.verdict = ApkInspector.Verdict.INVALID;
        repo.checkNow();
        repo.download();
        assertEquals(ErrorKind.INVALID_APK, repo.state().getValue().error());
        assertFalse(apkFile().exists());
    }

    @Test public void aFailedDownloadCanBeRetried() {
        http.files.remove(APK_URL);
        repo.checkNow();
        repo.download();
        assertEquals(ErrorKind.DOWNLOAD, repo.state().getValue().error());

        http.files.put(APK_URL, apkBytes);
        http.answer(SHA_URL, 200, null, "ignored: the first answer is queued ahead");
        repo.download();
        assertEquals(Kind.READY_TO_INSTALL, kind());
    }

    @Test public void oldDownloadsAreDeletedBeforeANewOneAndOnStart() throws Exception {
        assertTrue(dir.mkdirs());
        File stale = new File(dir, "old-release.apk");
        Files.write(stale.toPath(), apkBytes);
        repo.checkIfDue(); // first call after start wipes leftovers (executor is inline here)
        assertFalse(stale.exists());

        Files.write(stale.toPath(), apkBytes);
        assertEquals(Kind.AVAILABLE, kind());
        repo.download();
        assertEquals(Kind.READY_TO_INSTALL, kind());
        assertFalse(stale.exists());
        assertTrue(apkFile().exists());
    }

    @Test public void installOutcomesFromTheSystem() {
        repo.checkNow();
        repo.download();
        InstallResultReceiver.listener.accept(PackageInstaller.STATUS_FAILURE_ABORTED);
        assertEquals("cancelling the dialog is not an error", Kind.READY_TO_INSTALL, kind());

        InstallResultReceiver.listener.accept(PackageInstaller.STATUS_FAILURE_STORAGE);
        assertEquals(ErrorKind.INSTALL, repo.state().getValue().error());
        assertNotNull(repo.state().getValue().release());
    }

    @Test public void aFailingInstallSessionIsReported() {
        installer.fail = true;
        repo.checkNow();
        repo.download();
        repo.install();
        assertEquals(ErrorKind.INSTALL, repo.state().getValue().error());
    }

    @Test public void installAndDownloadDoNothingInTheWrongState() {
        repo.install();
        repo.download();
        repo.skipVersion();
        assertEquals(List.of(Kind.IDLE), kinds());
        assertTrue(installer.installed.isEmpty());
    }
}
