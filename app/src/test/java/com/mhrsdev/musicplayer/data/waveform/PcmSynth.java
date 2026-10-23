package com.mhrsdev.musicplayer.data.waveform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Synthetic interleaved PCM for tests. */
final class PcmSynth {
    static final int RATE = 44_100;

    private PcmSynth() { }

    /** Mono function of time (seconds) -> sample in [-1, 1], returned as 16-bit stereo-or-mono PCM of {@code frames}. */
    interface Signal { double at(double seconds, int channel); }

    static ByteBuffer pcm16(Signal s, int startFrame, int frames, int channels) {
        ByteBuffer b = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder());
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                double v = Math.max(-1, Math.min(1, s.at((startFrame + f) / (double) RATE, c)));
                b.putShort((short) Math.round(v * 32767));
            }
        }
        b.flip();
        return b;
    }

    static ByteBuffer pcmFloat(Signal s, int startFrame, int frames, int channels) {
        ByteBuffer b = ByteBuffer.allocate(frames * channels * 4).order(ByteOrder.nativeOrder());
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) b.putFloat((float) s.at((startFrame + f) / (double) RATE, c));
        }
        b.flip();
        return b;
    }

    static double sine(double seconds, double amplitude) {
        return amplitude * Math.sin(2 * Math.PI * 440 * seconds);
    }
}
