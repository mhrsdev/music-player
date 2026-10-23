package com.mhrsdev.musicplayer.data.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Fixtures {
    private Fixtures() { }

    static String read(String name) throws IOException {
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IOException("missing test resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
