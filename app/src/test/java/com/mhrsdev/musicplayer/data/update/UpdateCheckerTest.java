package com.mhrsdev.musicplayer.data.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import com.mhrsdev.musicplayer.domain.model.UpdateState.ErrorKind;
import java.util.List;
import org.junit.Test;

public class UpdateCheckerTest {
    private final FakeUpdates.Http http = new FakeUpdates.Http();
    private final FakeUpdates.Store store = new FakeUpdates.Store();
    private static final String REPO = "mhrsdev/music-player-mowj";
    private static final String LIST_URL = "https://api.github.com/repos/" + REPO + "/releases?per_page=5";
    private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private final UpdateChecker checker = new UpdateChecker(http, store, REPO);

    private static String list() throws Exception {
        return Fixtures.read("update/releases-list.json");
    }

    @Test public void endpointsComeFromTheRepoSlug() {
        assertEquals(LIST_URL, checker.listUrl());
        assertEquals(LATEST_URL, checker.latestUrl());
    }

    @Test public void usesTheListEndpointForPrereleasesAndLatestOtherwise() throws Exception {
        http.answer(LIST_URL, 200, "\"a\"", list());
        http.answer(LATEST_URL, 200, "\"b\"", Fixtures.read("update/release-latest.json"));
        assertEquals(4, checker.fetch(true).size());
        assertEquals(1, checker.fetch(false).size());
    }

    @Test public void secondCheckSendsTheEtagAndAnUnchangedAnswerIsServedFromTheCache() throws Exception {
        http.answer(LIST_URL, 200, "\"v1\"", list());
        http.answer(LIST_URL, 304, null, null);
        List<ReleaseInfo> first = checker.fetch(true);
        List<ReleaseInfo> second = checker.fetch(true);
        assertEquals(first, second);
        assertEquals(LIST_URL + "|null", http.requests.get(0));
        assertEquals(LIST_URL + "|\"v1\"", http.requests.get(1));
    }

    @Test public void aChangedAnswerReplacesTheCache() throws Exception {
        http.answer(LIST_URL, 200, "\"v1\"", list());
        http.answer(LIST_URL, 200, "\"v2\"", "[]");
        checker.fetch(true);
        assertEquals(0, checker.fetch(true).size());
        assertEquals("\"v2\"", store.cacheEtag());
        assertEquals("[]", store.cacheBody());
    }

    @Test public void theCacheOfOneEndpointIsNeverSentToTheOther() throws Exception {
        http.answer(LIST_URL, 200, "\"v1\"", list());
        http.answer(LATEST_URL, 200, "\"v9\"", "{}");
        checker.fetch(true);
        checker.fetch(false);
        assertEquals(LATEST_URL + "|null", http.requests.get(1));
        assertNull(checker.cached(true));
        assertNotNull(checker.cached(false));
    }

    @Test public void cachedReadsWithoutTheNetwork() throws Exception {
        http.answer(LIST_URL, 200, "\"v1\"", list());
        checker.fetch(true);
        http.offline = true;
        assertEquals(4, checker.cached(true).size());
        assertEquals(1, http.requests.size());
    }

    @Test public void noStableReleaseYet404MeansEmptyNotAnError() throws Exception {
        http.answer(LATEST_URL, 404, null, null);
        assertEquals(0, checker.fetch(false).size());
        assertNull(store.cacheUrl());
    }

    @Test public void failuresAreClassified() {
        http.offline = true;
        assertEquals(ErrorKind.NETWORK, assertThrows(UpdateException.class, () -> checker.fetch(true)).kind);
        http.offline = false;
        http.answer(LIST_URL, 403, null, null);
        assertEquals(ErrorKind.RATE_LIMITED, assertThrows(UpdateException.class, () -> checker.fetch(true)).kind);
        http.answers.clear();
        http.answer(LIST_URL, 429, null, null);
        assertEquals(ErrorKind.RATE_LIMITED, assertThrows(UpdateException.class, () -> checker.fetch(true)).kind);
        http.answers.clear();
        http.answer(LIST_URL, 500, null, null);
        assertEquals(ErrorKind.BAD_RESPONSE, assertThrows(UpdateException.class, () -> checker.fetch(true)).kind);
        http.answers.clear();
        http.answer(LIST_URL, 200, "\"x\"", "<html>");
        assertEquals(ErrorKind.BAD_RESPONSE, assertThrows(UpdateException.class, () -> checker.fetch(true)).kind);
        assertNull("a broken answer must not be cached", store.cacheBody());
    }

    @Test public void newestPicksTheHighestVersionAboveTheCurrentOne() throws Exception {
        List<ReleaseInfo> all = ReleaseParser.parse(list());
        assertEquals("v1.0.0", UpdateChecker.newest(all, true, SemVer.parse("0.1.0")).tag());
        assertEquals("v1.0.0", UpdateChecker.newest(all, false, SemVer.parse("0.1.0")).tag());
        assertEquals("v0.3.0-rc.1", UpdateChecker.newest(all.subList(1, 4), true, SemVer.parse("0.2.0")).tag());
        assertNull("pre-releases excluded", UpdateChecker.newest(all.subList(1, 4), false, SemVer.parse("0.1.0")));
        assertNull("already current", UpdateChecker.newest(all, true, SemVer.parse("1.0.0")));
        assertNull("newer than everything (dev build)", UpdateChecker.newest(all, true, SemVer.parse("2.0.0")));
        assertNull("a pre-release is older than its release", UpdateChecker.newest(all.subList(1, 2), true, SemVer.parse("0.3.0")));
    }
}
