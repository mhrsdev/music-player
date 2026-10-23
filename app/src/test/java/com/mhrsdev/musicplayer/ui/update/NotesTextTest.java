package com.mhrsdev.musicplayer.ui.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotesTextTest {
    @Test public void stripsMarkdownToReadablePlainText() {
        String md = "## Added\r\n- **Update** screen with `SHA-256`\r\n* [Changelog](https://example.com/c)\r\n\r\n\r\n\r\n"
                + "<!-- hidden -->### Install\nSHA-256: `abc`";
        assertEquals("Added\n• Update screen with SHA-256\n• Changelog\n\nInstall\nSHA-256: abc", NotesText.plain(md));
    }

    @Test public void longNotesAreCut() {
        String s = NotesText.plain("x".repeat(10_000));
        assertTrue(s.length() <= 4001);
        assertTrue(s.endsWith("…"));
    }
}
