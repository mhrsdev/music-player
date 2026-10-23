package com.mhrsdev.musicplayer.data.lyrics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Resources {
    private Resources() { }

    static String read(String name) throws IOException {
        try (InputStream in = Resources.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IOException("missing test resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
