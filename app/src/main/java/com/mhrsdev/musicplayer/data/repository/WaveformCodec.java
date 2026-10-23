package com.mhrsdev.musicplayer.data.repository;

/** Waveform values in [0, 1] to one byte per bar and back (a 200-bar envelope costs 200 bytes in the database). */
public final class WaveformCodec {
    public static byte[] encode(float[] bars) {
        byte[] out = new byte[bars.length];
        for (int i = 0; i < bars.length; i++) {
            out[i] = (byte) Math.round(Math.max(0f, Math.min(1f, bars[i])) * 255f);
        }
        return out;
    }

    public static float[] decode(byte[] data) {
        float[] out = new float[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (data[i] & 0xFF) / 255f;
        return out;
    }

    private WaveformCodec() { }
}
