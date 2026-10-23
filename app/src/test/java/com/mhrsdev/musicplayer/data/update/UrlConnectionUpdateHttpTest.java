package com.mhrsdev.musicplayer.data.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The real HTTP client against a tiny local server: redirects (renamed repo, asset hosts), ETag, User-Agent, download. */
public class UrlConnectionUpdateHttpTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private ServerSocket server;
    private String base;
    private final List<String> seen = Collections.synchronizedList(new ArrayList<>());
    private final UrlConnectionUpdateHttp http = new UrlConnectionUpdateHttp("MusicPlayer/9.9 (test)");

    @Before public void start() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        base = "http://127.0.0.1:" + server.getLocalPort();
        Thread t = new Thread(() -> {
            try {
                while (true) serve(server.accept());
            } catch (IOException closed) {
                // test finished
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @After public void stop() throws IOException {
        server.close();
    }

    private void serve(Socket s) throws IOException {
        try (Socket socket = s) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String path = in.readLine().split(" ")[1];
            Map<String, String> headers = new HashMap<>();
            for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                int i = line.indexOf(':');
                headers.put(line.substring(0, i).toLowerCase(), line.substring(i + 1).trim());
            }
            String head;
            byte[] body = new byte[0];
            switch (path) {
                case "/old": head = "301 Moved Permanently\r\nLocation: /new/hop1"; break;
                case "/new/hop1": head = "302 Found\r\nLocation: " + base + "/final"; break;
                case "/asset": head = "302 Found\r\nLocation: /apk"; break;
                case "/loop": head = "302 Found\r\nLocation: /loop"; break;
                case "/apk":
                    body = new byte[70_000];
                    head = "200 OK";
                    break;
                case "/final":
                    seen.add(headers.get("user-agent") + "|" + headers.get("if-none-match"));
                    if ("\"e1\"".equals(headers.get("if-none-match"))) {
                        head = "304 Not Modified";
                    } else {
                        body = "[{\"ok\":true}]".getBytes(StandardCharsets.UTF_8);
                        head = "200 OK\r\nETag: \"e1\"";
                    }
                    break;
                default: head = "404 Not Found";
            }
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 " + head + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        }
    }

    @Test public void followsRedirectsAndSendsUserAgentAndEtag() throws Exception {
        UpdateHttp.Response r = http.get(base + "/old", null);
        assertEquals(200, r.code());
        assertEquals("\"e1\"", r.etag());
        assertEquals("[{\"ok\":true}]", r.body());
        assertEquals("MusicPlayer/9.9 (test)|null", seen.get(0));

        UpdateHttp.Response again = http.get(base + "/old", "\"e1\"");
        assertEquals(304, again.code());
        assertNull(again.body());
        assertEquals("MusicPlayer/9.9 (test)|\"e1\"", seen.get(1));
    }

    @Test public void redirectLoopsGiveUp() {
        assertThrows(IOException.class, () -> http.get(base + "/loop", null));
    }

    @Test public void notFoundIsAnAnswerNotAnException() throws Exception {
        assertEquals(404, http.get(base + "/missing", null).code());
    }

    @Test public void downloadFollowsRedirectsAndWritesTheFile() throws Exception {
        File dest = tmp.newFile();
        List<Integer> percents = new ArrayList<>();
        http.download(base + "/asset", dest, percents::add);
        assertEquals(70_000, Files.size(dest.toPath()));
        assertEquals(Integer.valueOf(100), percents.get(percents.size() - 1));
    }

    @Test public void downloadOfAMissingFileFails() {
        assertThrows(IOException.class, () -> http.download(base + "/missing", new File(tmp.getRoot(), "x"), p -> { }));
    }
}
