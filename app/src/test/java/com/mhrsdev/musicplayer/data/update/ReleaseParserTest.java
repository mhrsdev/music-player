package com.mhrsdev.musicplayer.data.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.junit.Test;

public class ReleaseParserTest {
    private static List<String> tags(List<ReleaseInfo> releases) {
        List<String> out = new ArrayList<>();
        for (ReleaseInfo r : releases) out.add(r.tag());
        return out;
    }

    @Test public void listKeepsOnlyInstallableReleasesInOrder() throws Exception {
        List<ReleaseInfo> all = ReleaseParser.parse(Fixtures.read("update/releases-list.json"));
        // dropped: v0.9.0 (draft), v0.1.5 (no checksum anywhere), nightly (not a version), v0.1.0 (no apk)
        assertEquals(List.of("v1.0.0", "v0.3.0-rc.1", "v0.2.0", "v0.1.4"), tags(all));
    }

    @Test public void mapsAssetsAndFlags() throws Exception {
        ReleaseInfo stable = ReleaseParser.parse(Fixtures.read("update/releases-list.json")).get(0);
        assertFalse(stable.prerelease());
        assertEquals("1.0.0", stable.versionName());
        assertEquals("https://github.com/mhrsdev/music-player-mowj/releases/download/v1.0.0/MusicPlayer-v1.0.0.apk", stable.apkUrl());
        assertEquals("https://github.com/mhrsdev/music-player-mowj/releases/download/v1.0.0/MusicPlayer-v1.0.0.apk.sha256", stable.sha256Url());
        assertNull(stable.sha256Hex());
        assertEquals(12345678L, stable.apkSize());
        assertTrue(stable.notes().contains("first stable"));
    }

    @Test public void aSuffixMarksAPreReleaseEvenWhenTheFlagIsFalse() throws Exception {
        String json = Fixtures.read("update/release-latest.json").replace("\"v1.0.0\"", "\"v1.0.0-rc.2\"")
                .replace("MusicPlayer-v1.0.0", "MusicPlayer-v1.0.0-rc.2");
        assertTrue(ReleaseParser.parse(json).get(0).prerelease());
    }

    @Test public void digestAloneIsEnoughAndIsLowercased() throws Exception {
        ReleaseInfo r = ReleaseParser.parse(Fixtures.read("update/releases-list.json")).get(3);
        assertEquals("v0.1.4", r.tag());
        assertNull(r.sha256Url());
        assertEquals("a".repeat(64), r.sha256Hex());
    }

    @Test public void singleObjectAnswerOfTheLatestEndpoint() throws Exception {
        assertEquals(List.of("v1.0.0"), tags(ReleaseParser.parse(Fixtures.read("update/release-latest.json"))));
    }

    @Test public void nonHttpsAssetsAreRejected() throws Exception {
        String json = Fixtures.read("update/release-latest.json").replace("https://github.com", "http://github.com");
        assertTrue(ReleaseParser.parse(json).isEmpty());
    }

    @Test public void apkWithAnotherNameIsNotPicked() throws Exception {
        String json = Fixtures.read("update/release-latest.json").replace("MusicPlayer-v1.0.0.apk\"", "other-v1.0.0.apk\"");
        assertTrue(ReleaseParser.parse(json).isEmpty());
    }

    @Test public void malformedJsonThrows() {
        assertThrows(JSONException.class, () -> ReleaseParser.parse("<html>rate limited</html>"));
        assertThrows(JSONException.class, () -> ReleaseParser.parse(""));
    }

    @Test public void emptyListIsFine() throws Exception {
        assertTrue(ReleaseParser.parse("[]").isEmpty());
    }
}
