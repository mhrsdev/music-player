package com.mhrsdev.musicplayer.data.scan

import kotlinx.coroutines.flow.Flow

/** Raw row from the platform audio index, before filtering. Pure data so scanning logic is unit-testable. */
data class ScannedAudio(
    val mediaStoreId: Long,
    val displayName: String?,
    val title: String?,
    val artist: String?,
    val artistId: Long,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    /** MediaStore YEAR; 0 or negative = unknown. */
    val year: Int,
    /** MediaStore TRACK, which may be disc*1000 + track. 0 = unknown. */
    val track: Int,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val contentUri: String,
    /** RELATIVE_PATH (API 29+) or parent directory of DATA; used only for folder exclusion. */
    val folder: String?,
    val isMusic: Boolean = true,
    val isRingtone: Boolean = false,
    val isNotification: Boolean = false,
    val isAlarm: Boolean = false,
    /** True only when the source could read an explicit/advisory tag. MediaStore itself exposes none. */
    val isExplicit: Boolean = false,
)

/** User-tunable scanner options (persisted in DataStore by [ScannerSettingsStore]). */
data class ScannerOptions(
    /** Clips shorter than this are ignored (voice notes, sound effects, ringtones). */
    val minDurationMs: Long = DEFAULT_MIN_DURATION_MS,
    /** Case-insensitive folder fragments (e.g. "WhatsApp", "Recordings"); matching tracks are skipped. */
    val excludedFolders: Set<String> = emptySet(),
    /** Rescan automatically when the platform audio index changes. */
    val autoRescan: Boolean = true,
) {
    companion object {
        const val DEFAULT_MIN_DURATION_MS = 30_000L
    }
}

interface ScannerOptionsSource {
    val options: Flow<ScannerOptions>
}

/** Reads every audio row the app may see. The real implementation is [MediaStoreAudioSource]. */
interface AudioSource {
    suspend fun queryAll(options: ScannerOptions): List<ScannedAudio>
}

object ScanFilter {
    fun accept(a: ScannedAudio, o: ScannerOptions): Boolean {
        if (!a.isMusic || a.isRingtone || a.isNotification || a.isAlarm) return false
        if (a.durationMs < o.minDurationMs) return false
        val folder = a.folder
        if (folder != null && o.excludedFolders.any { it.isNotBlank() && folder.contains(it, ignoreCase = true) }) {
            return false
        }
        return true
    }
}

data class SyncResult(val added: Int, val updated: Int, val removed: Int, val total: Int) {
    val changed: Boolean get() = added + updated + removed > 0
}

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(val result: SyncResult, val finishedAtMs: Long) : ScanState
    /** Typically a missing permission (SecurityException) or a provider failure. */
    data class Failed(val message: String) : ScanState
}
