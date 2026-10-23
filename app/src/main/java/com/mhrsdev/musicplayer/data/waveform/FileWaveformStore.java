package com.mhrsdev.musicplayer.data.waveform;

import androidx.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Simple file-backed {@link WaveformStore}: one small file per track ({@code id, fingerprint, blob}) in a directory.
 * The file name is a hash of the id; the id inside the file guards against hash collisions.
 */
public final class FileWaveformStore implements WaveformStore {
    private final File dir;

    public FileWaveformStore(File dir) {
        this.dir = dir;
    }

    private File fileFor(String trackId) {
        return new File(dir, Integer.toHexString(trackId.hashCode()) + ".wf");
    }

    @Override public @Nullable byte[] get(String trackId, String fingerprint) {
        File f = fileFor(trackId);
        if (!f.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            if (!trackId.equals(in.readUTF()) || !fingerprint.equals(in.readUTF())) return null;
            byte[] blob = new byte[in.readInt()];
            in.readFully(blob);
            return blob;
        } catch (IOException | RuntimeException e) {
            return null; // corrupt or truncated: behave as a miss, put() will overwrite it
        }
    }

    @Override public void put(String trackId, String fingerprint, byte[] blob) {
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        File target = fileFor(trackId);
        File tmp = new File(dir, target.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
            out.writeUTF(trackId);
            out.writeUTF(fingerprint);
            out.writeInt(blob.length);
            out.write(blob);
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(target)) tmp.delete();
    }
}
