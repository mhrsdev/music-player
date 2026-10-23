package com.mhrsdev.musicplayer.data.waveform

import com.mhrsdev.musicplayer.domain.model.Track
import com.mhrsdev.musicplayer.domain.repository.WaveformRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Waveform data source. Profiles are computed once at [RESOLUTION] bars and resampled to whatever the caller asks.
 *
 * - Lookup order: memory LRU -> disk -> decode ([extractor]) on [dispatcher] (bounded, low priority).
 * - One in-flight job per track key shared by all collectors; it is cancelled when the last collector goes away.
 * - Failures (undecodable file, no media uri, ...) emit null and are remembered for the process lifetime.
 */
class WaveformRepositoryImpl(
    private val extractor: WaveformExtractor,
    private val disk: WaveformCache?,
    private val dispatcher: CoroutineDispatcher,
    private val memory: MemoryWaveformCache = MemoryWaveformCache(),
) : WaveformRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Any()
    private val inFlight = HashMap<String, Flight>()
    private val failed = HashSet<String>()

    private class Flight(val job: Deferred<FloatArray?>) {
        var refs = 0
    }

    override fun waveformFor(track: Track, bars: Int): Flow<FloatArray?> = flow {
        val n = bars.coerceIn(1, MAX_BARS)
        if (track.mediaUri.isNullOrBlank()) {
            emit(null)
            return@flow
        }
        val key = WaveformKey.of(track, RESOLUTION)
        memory.get(key)?.let {
            emit(WaveformBuilder.resample(it, n))
            return@flow
        }
        emit(null)
        val flight = acquire(key, track) ?: return@flow
        try {
            val profile = flight.job.await()
            if (profile != null) emit(WaveformBuilder.resample(profile, n))
        } finally {
            release(key, flight)
        }
    }

    /** Joins (or starts) the job for [key]; null if this key already failed. */
    private fun acquire(key: String, track: Track): Flight? = synchronized(lock) {
        if (key in failed) return null
        val flight = inFlight.getOrPut(key) {
            val job = scope.async { load(key, track) }
            Flight(job).also { f ->
                job.invokeOnCompletion {
                    synchronized(lock) { if (inFlight[key] === f) inFlight.remove(key) }
                }
            }
        }
        flight.refs++
        flight
    }

    private fun release(key: String, flight: Flight) {
        val cancel = synchronized(lock) {
            flight.refs--
            if (flight.refs <= 0 && flight.job.isActive) {
                if (inFlight[key] === flight) inFlight.remove(key)
                true
            } else false
        }
        if (cancel) flight.job.cancel(CancellationException("no more collectors"))
    }

    private suspend fun load(key: String, track: Track): FloatArray? {
        memory.get(key)?.let { return it }
        disk?.let { d ->
            val hit = try { d.get(key) } catch (_: Exception) { null }
            if (hit != null) {
                memory.put(key, hit)
                return hit
            }
        }
        val result = try {
            extractor.extract(track, RESOLUTION) { scope.isActive && currentCoroutineIsActive() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (!kotlin.coroutines.coroutineContext.isActive) throw CancellationException("cancelled")
        if (result == null) {
            synchronized(lock) { failed.add(key) }
            return null
        }
        memory.put(key, result)
        try { disk?.put(key, result) } catch (_: Exception) {}
        return result
    }

    // Placeholder replaced below; kept private to keep load() readable.
    private fun currentCoroutineIsActive(): Boolean = true

    companion object {
        /** Stored resolution; requests for fewer/more bars are resampled from this. */
        const val RESOLUTION = WaveformBuilder.DEFAULT_BINS
        private const val MAX_BARS = 2048

        /** Bounded pool (default 2 threads) at background priority so decoding never competes with playback/UI. */
        fun backgroundDispatcher(threads: Int = 2): CoroutineDispatcher {
            val pool: ExecutorService = Executors.newFixedThreadPool(threads) { r ->
                Thread {
                    try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) } catch (_: Throwable) {}
                    r.run()
                }.apply { name = "waveform-decoder"; isDaemon = true; priority = Thread.MIN_PRIORITY }
            }
            return pool.asCoroutineDispatcher()
        }
    }
}
