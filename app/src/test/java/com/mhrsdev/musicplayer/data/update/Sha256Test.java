package com.mhrsdev.musicplayer.data.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Sha256Test {
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void hashesAFile() throws Exception {
        File f = tmp.newFile();
        Files.write(f.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals(ABC, Sha256.of(f));
    }

    @Test public void aFlippedByteChangesTheDigest() throws Exception {
        File f = tmp.newFile();
        Files.write(f.toPath(), "abd".getBytes(StandardCharsets.UTF_8));
        assertFalse(ABC.equals(Sha256.of(f)));
    }

    @Test public void parsesSha256sumLines() {
        assertEquals(ABC, Sha256.parseChecksumFile(ABC + "  MusicPlayer-v0.1.0.apk\n"));
        assertEquals(ABC, Sha256.parseChecksumFile(ABC.toUpperCase() + " *MusicPlayer-v0.1.0.apk"));
        assertEquals(ABC, Sha256.parseChecksumFile(ABC));
        assertNull(Sha256.parseChecksumFile(""));
        assertNull(Sha256.parseChecksumFile("<html>Not Found</html>"));
        assertNull(Sha256.parseChecksumFile(ABC.substring(1) + "  x.apk"));
        assertNull(Sha256.parseChecksumFile(null));
    }

    @Test public void copyReportsProgressOnceAndChecksTheLength() throws Exception {
        byte[] data = new byte[100_000];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> seen = new ArrayList<>();
        UrlConnectionUpdateHttp.copy(new ByteArrayInputStream(data), out, data.length, seen::add, Long.MAX_VALUE);
        assertEquals(data.length, out.size());
        assertEquals(Integer.valueOf(0), seen.get(0));
        assertEquals(Integer.valueOf(100), seen.get(seen.size() - 1));
        assertEquals(seen.size(), seen.stream().distinct().count());

        assertThrows(IOException.class, () -> UrlConnectionUpdateHttp.copy(new ByteArrayInputStream(data), new ByteArrayOutputStream(),
                data.length + 1, p -> { }, Long.MAX_VALUE)); // truncated
        assertThrows(IOException.class, () -> UrlConnectionUpdateHttp.copy(new ByteArrayInputStream(data), new ByteArrayOutputStream(),
                -1, p -> { }, 10)); // too large
        assertTrue(seen.size() > 1);
    }
}
