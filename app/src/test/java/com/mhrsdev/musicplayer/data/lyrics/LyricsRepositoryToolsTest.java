package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.LyricsCandidate;
import com.mhrsdev.musicplayer.domain.model.LyricsSearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import org.junit.Rule;
import org.junit.Test;

/** Search again (fresh), manual search, picking a candidate and importing a file (kept and preferred). */
public class LyricsRepositoryToolsTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private final Queue<Runnable> queue = new ArrayDeque<>();
    private int remoteCalls;
    private String remoteText;
    private final LyricsSource remote = (t, c) -> {
        remoteCalls++;
        return remoteText == null ? null : new LyricsSource.Found(remoteText, "LRCLIB");
    };
    private final List<String> urls = new ArrayList<>();
    private String searchBody = "[]";
    private final LrclibClient lrclib = new LrclibClient((url, c) -> { urls.add(url); return searchBody; }, "https://x/api");
    private final File dir;
    private final UserLyricsStore store;
    private final InMemoryLyricsCacheStore cache = new InMemoryLyricsCacheStore();
    private final LyricsRepositoryImpl repo;
    private final Track track = TestTracks.track("t1", "Ghalbam", "Reza Shiri @RozMusic.com", "", 200_000, null);
    private final LyricsSource embeddedJunk = (t, c) -> new LyricsSource.Found("RozMusic.com", "Embedded");

    public LyricsRepositoryToolsTest() throws IOException {
        dir = Files.createTempDirectory("lyr").toFile();
        store = new UserLyricsStore(dir);
        LyricsResolver resolver = new LyricsResolver(Arrays.asList(store, embeddedJunk), remote, cache, () -> 1_000L);
        repo = new LyricsRepositoryImpl(resolver, queue::add, lrclib, store,
                uri -> uri.equals("content://doc/1") ? "[00:01.00]from file\n[00:03.00]second" : "only junk?");
    }

    private <T> T run(LiveData<T> live) {
        List<T> out = new ArrayList<>();
        live.observeForever(out::add);
        Runnable r;
        while ((r = queue.poll()) != null) r.run();
        return out.get(out.size() - 1);
    }

    @Test public void searchAgainIgnoresNegativeCache() {
        assertTrue(run(repo.lyricsFor(track)).isEmpty()); // embedded junk rejected, online none (cached)
        assertEquals(1, remoteCalls);
        run(repo.lyricsFor(track));
        assertEquals(1, remoteCalls);
        remoteText = "[00:01.00]real\n[00:02.00]lyrics";
        Lyrics l = run(repo.searchAgain(track));
        assertEquals(2, remoteCalls);
        assertEquals("LRCLIB", l.source());
    }

    @Test public void manualSearchReturnsCandidatesBestFirst() {
        searchBody = "[{\"duration\":200,\"trackName\":\"Other\",\"artistName\":\"X\",\"plainLyrics\":\"p\"},"
                + "{\"duration\":200,\"trackName\":\"Ghalbam\",\"artistName\":\"Reza Shiri\",\"syncedLyrics\":\"[00:01.00]s\"}]";
        LyricsSearchResult r = run(repo.search("Reza Shiri @RozMusic.com", "Ghalbam"));
        assertFalse(r.failed());
        assertEquals(2, r.items().size());
        assertEquals("Ghalbam", r.items().get(0).title());
        assertTrue(r.items().get(0).synced());
        assertEquals("https://x/api/search?q=Ghalbam+Reza+Shiri", urls.get(0));
    }

    @Test public void searchFailureIsReported() {
        LyricsRepositoryImpl failing = new LyricsRepositoryImpl(
                new LyricsResolver(List.of(), remote, cache, () -> 1L), queue::add,
                new LrclibClient((u, c) -> { throw new IOException("timeout"); }, "https://x/api"), store, null);
        assertTrue(run(failing.search("a", "b")).failed());
    }

    @Test public void pickedCandidateIsKeptAndPreferred() {
        LyricsCandidate c = new LyricsCandidate("Ghalbam", "Reza Shiri", "", 200_000, true, "[00:01.00]one");
        Lyrics l = run(repo.useCandidate(track, c));
        assertEquals(UserLyricsStore.LABEL, l.source());
        assertEquals(1, l.lines().size()); // the user's choice is never junk-filtered
        Lyrics again = run(repo.lyricsFor(track));
        assertEquals(UserLyricsStore.LABEL, again.source());
    }

    @Test public void importedFileIsCopiedAndPreferred() {
        Lyrics l = run(repo.importFile(track, "content://doc/1"));
        assertEquals("from file", l.lines().get(0).text());
        assertTrue(store.fileFor(track.id()).isFile());
        assertEquals(UserLyricsStore.LABEL, run(repo.lyricsFor(track)).source());
    }

    @Test public void unreadableFileKeepsWhatTheTrackHad() {
        LyricsRepositoryImpl noReader = new LyricsRepositoryImpl(
                new LyricsResolver(List.of(store), remote, cache, () -> 1L), queue::add, lrclib, store,
                uri -> { throw new IOException("denied"); });
        assertTrue(run(noReader.importFile(track, "content://x")).failed());
        assertFalse(store.fileFor(track.id()).exists());
    }
}
