package com.mhrsdev.musicplayer.data.waveform;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;

/**
 * Android decode glue: MediaExtractor + MediaCodec (mp3, aac, flac, ogg, opus, wav ... whatever the device decodes)
 * feeding {@link EnvelopeBuilder} according to a {@link SamplingPlan}. Decoded buffers are consumed immediately, so
 * RAM stays at one codec buffer; input outside the plan is skipped with {@code advance()} (small gaps) or
 * {@code seekTo()} (gaps over {@link #SEEK_GAP_US}) and never decoded.
 */
public final class WaveformExtractor implements EnvelopeSource {
    private static final String TAG = "WaveformExtractor";
    private static final long DEQUEUE_TIMEOUT_US = 5_000;
    private static final long SEEK_GAP_US = 2_000_000;
    /** Consecutive idle dequeue rounds (~10 ms each) before a silent codec is declared stuck. */
    private static final int MAX_IDLE_ROUNDS = 300;
    private static final int ENCODING_PCM_16BIT = 2;
    private static final int ENCODING_PCM_FLOAT = 4;
    /** Fewer measured buckets than this (of all) means the decoder timestamps are unusable. */
    private static final float MIN_COVERAGE = 0.125f;

    private final Context context;

    public WaveformExtractor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public @Nullable float[] extract(Track track, BooleanSupplier cancelled) {
        if (track.mediaUri() == null) return null;
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, Uri.parse(track.mediaUri()), null);
            int index = firstAudioTrack(extractor);
            if (index < 0) return null;
            MediaFormat format = extractor.getTrackFormat(index);
            extractor.selectTrack(index);
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : track.durationMs() * 1000;
            if (durationUs <= 0) return null;
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();
            SamplingPlan plan = new SamplingPlan(durationUs, WaveformMath.MASTER_BUCKETS);
            EnvelopeBuilder builder = new EnvelopeBuilder(plan);
            if (!decode(extractor, codec, format, plan, builder, cancelled) || builder.coverage() < MIN_COVERAGE) {
                return null;
            }
            return WaveformMath.toEnvelope(builder.rms());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "cannot extract waveform of " + track.id(), e);
            return null;
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (RuntimeException ignored) { /* already stopped / failed */ }
                codec.release();
            }
            extractor.release();
        }
    }

    private static int firstAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    /**
     * Runs the decode loop. Input is fed in runs; at the end of a run an EOS buffer drains the codec (so the tail of
     * the window is not lost), then the extractor skips ahead and the codec is flushed. Flushing at every jump
     * matters: several decoders derive output timestamps from the first input after a flush.
     * @return false = cancelled, stuck, or unsupported PCM encoding
     */
    private static boolean decode(MediaExtractor extractor, MediaCodec codec, MediaFormat inputFormat,
            SamplingPlan plan, EnvelopeBuilder builder, BooleanSupplier cancelled) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int channels = Math.max(1, inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        boolean floatPcm = false;
        boolean eosQueued = false, lastRun = false, seekAllowed = true;
        int idle = 0;
        while (true) {
            if (cancelled.getAsBoolean() || idle > MAX_IDLE_ROUNDS) return false;
            boolean progressed = false;
            if (!eosQueued) {
                long t = extractor.getSampleTime();
                long next = t < 0 ? -1 : plan.nextFeedUs(t);
                int in = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                if (in >= 0) {
                    progressed = true;
                    ByteBuffer buf = next == t ? codec.getInputBuffer(in) : null;
                    int size = buf == null ? -1 : extractor.readSampleData(buf, 0);
                    if (size < 0) { // end of this run: drain the codec
                        codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eosQueued = true;
                        lastRun = next < 0;
                    } else {
                        codec.queueInputBuffer(in, 0, size, t, 0);
                        extractor.advance();
                    }
                }
            }
            int out = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
            if (out >= 0) {
                progressed = true;
                ByteBuffer pcm = info.size > 0 ? codec.getOutputBuffer(out) : null;
                if (pcm != null) {
                    pcm.position(info.offset).limit(info.offset + info.size);
                    builder.add(pcm, channels, floatPcm, sampleRate, info.presentationTimeUs);
                }
                codec.releaseOutputBuffer(out, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (lastRun) return true;
                    seekAllowed = skipAhead(extractor, plan, seekAllowed);
                    codec.flush();
                    eosQueued = false;
                }
            } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                progressed = true;
                MediaFormat f = codec.getOutputFormat();
                channels = Math.max(1, f.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int encoding = f.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ? f.getInteger(MediaFormat.KEY_PCM_ENCODING) : ENCODING_PCM_16BIT;
                if (encoding != ENCODING_PCM_16BIT && encoding != ENCODING_PCM_FLOAT) return false;
                floatPcm = encoding == ENCODING_PCM_FLOAT;
            }
            idle = progressed ? 0 : idle + 1;
        }
    }

    /**
     * Moves the extractor to the next feed time: seeks for big gaps (sync points are earlier, so it advances the
     * rest without decoding), otherwise just advances. Returns whether seeking is still worth trying; a seek that
     * did not land ahead of the old position (no seek table) is not retried.
     */
    private static boolean skipAhead(MediaExtractor extractor, SamplingPlan plan, boolean seekAllowed) {
        long t = extractor.getSampleTime();
        long next = t < 0 ? -1 : plan.nextFeedUs(t);
        if (next < 0) return seekAllowed;
        if (seekAllowed && next - t > SEEK_GAP_US) {
            extractor.seekTo(next, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            seekAllowed = extractor.getSampleTime() > t;
        }
        for (long now = extractor.getSampleTime(); now >= 0 && now < next; now = extractor.getSampleTime()) {
            extractor.advance();
        }
        return seekAllowed;
    }
}
