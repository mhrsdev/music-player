package com.mhrsdev.musicplayer.data.scan

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Queries `MediaStore.Audio.Media` and returns content:// URIs. Throws SecurityException without permission. */
class MediaStoreAudioSource(context: Context) : AudioSource {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun queryAll(options: ScannerOptions): List<ScannedAudio> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.IS_MUSIC)
            add(MediaStore.Audio.Media.IS_RINGTONE)
            add(MediaStore.Audio.Media.IS_NOTIFICATION)
            add(MediaStore.Audio.Media.IS_ALARM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION") add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        // Coarse pre-filter in SQL; [ScanFilter] re-applies the full rules (including folders) in Kotlin.
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} >= ?")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) append(" AND ${MediaStore.Audio.Media.IS_PENDING} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) append(" AND ${MediaStore.Audio.Media.IS_TRASHED} = 0")
        }
        val args = arrayOf(options.minDurationMs.toString())

        val out = ArrayList<ScannedAudio>()
        resolver.query(collection, projection, selection, args, "${MediaStore.Audio.Media._ID} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val audio = ScannedAudio(
                    mediaStoreId = id,
                    displayName = c.str(MediaStore.Audio.Media.DISPLAY_NAME),
                    title = c.str(MediaStore.Audio.Media.TITLE),
                    artist = c.str(MediaStore.Audio.Media.ARTIST),
                    artistId = c.long(MediaStore.Audio.Media.ARTIST_ID),
                    album = c.str(MediaStore.Audio.Media.ALBUM),
                    albumId = c.long(MediaStore.Audio.Media.ALBUM_ID),
                    durationMs = c.long(MediaStore.Audio.Media.DURATION),
                    year = c.long(MediaStore.Audio.Media.YEAR).toInt(),
                    track = c.long(MediaStore.Audio.Media.TRACK).toInt(),
                    dateAddedMs = c.long(MediaStore.Audio.Media.DATE_ADDED) * 1000L,
                    dateModifiedMs = c.long(MediaStore.Audio.Media.DATE_MODIFIED) * 1000L,
                    contentUri = ContentUris.withAppendedId(collection, id).toString(),
                    folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.str(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else {
                        @Suppress("DEPRECATION") c.str(MediaStore.Audio.Media.DATA)?.let { File(it).parent }
                    },
                    isMusic = c.long(MediaStore.Audio.Media.IS_MUSIC) != 0L,
                    isRingtone = c.long(MediaStore.Audio.Media.IS_RINGTONE) != 0L,
                    isNotification = c.long(MediaStore.Audio.Media.IS_NOTIFICATION) != 0L,
                    isAlarm = c.long(MediaStore.Audio.Media.IS_ALARM) != 0L,
                )
                if (ScanFilter.accept(audio, options)) out += audio
            }
        }
        out
    }

    private fun Cursor.str(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

    private fun Cursor.long(column: String): Long =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L
}

/** Registers a [ContentObserver] on the audio collection; [onChange] fires on the main looper, un-debounced. */
class MediaStoreObserver(context: Context, private val onChange: () -> Unit) {
    private val resolver = context.applicationContext.contentResolver
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = onChange()
        override fun onChange(selfChange: Boolean, uri: Uri?) = onChange()
    }
    private var registered = false

    fun register() {
        if (registered) return
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        resolver.unregisterContentObserver(observer)
        registered = false
    }
}
