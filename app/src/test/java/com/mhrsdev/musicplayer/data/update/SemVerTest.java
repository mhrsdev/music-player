package com.mhrsdev.musicplayer.data.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SemVerTest {
    private static SemVer v(String s) {
        SemVer v = SemVer.parse(s);
        if (v == null) throw new AssertionError("not a version: " + s);
        return v;
    }

    private static void assertLess(String lower, String higher) {
        assertTrue(lower + " < " + higher, v(lower).compareTo(v(higher)) < 0);
        assertTrue(higher + " > " + lower, v(higher).compareTo(v(lower)) > 0);
    }

    @Test public void parsesTagsWithAndWithoutV() {
        assertEquals(new SemVer(1, 20, 3, null), v("v1.20.3"));
        assertEquals(new SemVer(0, 1, 0, null), v("0.1.0"));
        assertEquals(new SemVer(0, 2, 0, "rc.1"), v("v0.2.0-rc.1"));
        assertEquals(new SemVer(0, 2, 0, null), v("v0.2.0+build.5"));
    }

    @Test public void garbageIsIgnored() {
        for (String s : new String[] {null, "", "latest", "nightly", "v1", "v1.2", "v1.2.x", "1.2.3.4", "v1.2.3-", "release-1.2.3"}) {
            assertNull(String.valueOf(s), SemVer.parse(s));
        }
    }

    @Test public void numbersCompareAsNumbersNotText() {
        assertLess("v0.9.0", "v0.10.0");
        assertLess("v1.9.9", "v2.0.0");
        assertLess("v0.1.9", "v0.1.10");
    }

    @Test public void preReleaseIsLowerThanItsRelease() {
        assertLess("v1.0.0-rc.1", "v1.0.0");
        assertLess("v0.2.0-beta", "v0.2.0");
        assertLess("v0.1.0", "v0.2.0-alpha"); // but a pre-release of a later version beats an older release
    }

    @Test public void preReleaseIdentifiersFollowSemverRules() {
        // the example chain from the semver 2.0 spec
        String[] chain = {"1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11",
                "1.0.0-rc.1", "1.0.0"};
        for (int i = 0; i + 1 < chain.length; i++) assertLess(chain[i], chain[i + 1]);
    }

    @Test public void buildMetadataIsIgnoredAndEqualVersionsCompareEqual() {
        assertEquals(0, v("v1.2.3+a").compareTo(v("1.2.3+b")));
        assertEquals(0, v("1.0.0-rc.01").compareTo(v("1.0.0-rc.1")));
    }
}
