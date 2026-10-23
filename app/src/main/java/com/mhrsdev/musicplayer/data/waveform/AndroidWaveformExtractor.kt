package com.mhrsdev.musicplayer.data.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.mhrsdev.musicplayer.domain.model.Track
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Blocking decoder that produces a [bins]-value profile for a track, or null if it cannot be decoded. */
fun interface WaveformExtractor {
    /** Must poll [isActive] regularly and return null promptly once it is false. */
    fun extract(track: Track, bins: Int, isActive: () -> Boolean): FloatArray?
}

/**
 * MediaExtractor + MediaCodec decode to PCM (16-bit, float, 8-bit, 24/32-bit; any channel count) fed into a
 * [WaveformBuilder]. Local sources only (content://, file://, absolute path); any failure -> pure-Kotlin WAV
 * fallback, then null. Never throws. Call from a background thread.
 */
class AndroidWaveformExtractor(private val context: Context) : WaveformExtractor {

    override fun extract(track: Track, bins: Int, isActive: () -> Boolean): FloatArray? {
        val raw = track.mediaUri ?: return null
        val uri = toLocalUri(raw) ?: return null
        val decoded = try {
            decodeWithCodec(uri, bins, isActive)
        } catch (_: Exception) {
            null
        }
        if (decoded != null || !isActive()) return decoded
        return try {
            context.contentResolver.openInputStream(uri)?.use { WavDecoder.extract(it, bins, isActive) }
        } catch (_: Exception) {
            null
        }
    }

    private fun toLocalUri(raw: String): Uri? {
        val uri = Uri.parse(raw)
        return when (uri.scheme) {
            "content", "file", "android.resource" -> uri
            null -> Uri.fromFile(File(raw))
            else -> null // http(s) etc.: not decoded locally
        }
    }

    private fun decodeWithCodec(uri: Uri, bins: Int, isActive: () -> Boolean): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var inFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    inFormat = f
                    break
                }
            }
            if (trackIndex < 0 || inFormat == null) return null
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inFormat, null, null, 0)
            codec.start()

            var sampleRate = intOr(inFormat, MediaFormat.KEY_SAMPLE_RATE, 44100)
            var channels = intOr(inFormat, MediaFormat.KEY_CHANNEL_COUNT, 2)
            var encoding = ENCODING_PCM_16BIT
            var builder: WaveformBuilder? = null

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0
            while (!outputDone) {
                if (!isActive()) return null
                var progressed = false
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        sampleRate = intOr(of, MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = intOr(of, MediaFormat.KEY_CHANNEL_COUNT, channels)
                        encoding = intOr(of, MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
                        // Normally fires before any output. A later change keeps the existing builder (rate/channels fixed at first use).
                        if (builder?.windowsCollected == 0) builder = null
                        progressed = true
                    }
                    oi >= 0 -> {
                        if (info.size > 0) {
                            val b = builder ?: WaveformBuilder(sampleRate, channels.coerceAtLeast(1), bins).also { builder = it }
                            val out = codec.getOutputBuffer(oi)
                            if (out != null) {
                                out.position(info.offset)
                                out.limit(info.offset + info.size)
                                feed(out.slice().order(ByteOrder.LITTLE_ENDIAN), encoding, b)
                            }
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        progressed = true
                    }
                }
                // Guard against a codec that never makes progress (TIMEOUT_US per idle loop).
                if (progressed) idleLoops = 0 else if (++idleLoops > MAX_IDLE_LOOPS) return null
            }
            val b = builder ?: return null
            return if (b.windowsCollected == 0) null else b.build()
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun intOr(f: MediaFormat, key: String, default: Int): Int =
        if (f.containsKey(key)) f.getInteger(key) else default

    private fun feed(buf: ByteBuffer, encoding: Int, b: WaveformBuilder) {
        when (encoding) {
            ENCODING_PCM_FLOAT -> { val fb = buf.asFloatBuffer(); while (fb.hasRemaining()) b.addSample(fb.get()) }
            ENCODING_PCM_8BIT -> while (buf.hasRemaining()) b.addSample(((buf.get().toInt() and 0xFF) - 128) / 128f)
            ENCODING_PCM_24BIT_PACKED -> while (buf.remaining() >= 3) {
                val v = (buf.get().toInt() and 0xFF) or ((buf.get().toInt() and 0xFF) shl 8) or (buf.get().toInt() shl 16)
                b.addSample(v / 8388608f)
            }
            ENCODING_PCM_32BIT -> { val ib = buf.asIntBuffer(); while (ib.hasRemaining()) b.addSample(ib.get() / 2147483648f) }
            else -> { val sb = buf.asShortBuffer(); while (sb.hasRemaining()) b.addSample(sb.get() / 32768f) }
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_IDLE_LOOPS = 1000 // ~10 s without any progress

        // android.media.AudioFormat constants, inlined so this file has no extra imports.
        const val ENCODING_PCM_16BIT = 2
        const val ENCODING_PCM_8BIT = 3
        const val ENCODING_PCM_FLOAT = 4
        const val ENCODING_PCM_24BIT_PACKED = 21
        const val ENCODING_PCM_32BIT = 22
    }
}
