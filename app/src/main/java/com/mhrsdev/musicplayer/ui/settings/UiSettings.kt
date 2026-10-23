package com.mhrsdev.musicplayer.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mhrsdev.musicplayer.ui.components.SeekBarStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** App theme mode (DESIGN.md §2.1). [Black] = dark scheme with pure-black surfaces (AMOLED). */
enum class ThemeMode { System, Light, Dark, Black }

/** Purely visual preferences owned by the UI layer. Defaults follow DESIGN.md + STATUS decisions K6. */
data class UiSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Android 12+ wallpaper colors for app chrome instead of the lapis base scheme. */
    val useWallpaperColors: Boolean = false,
    /** Player / mini-player take their colors from the playing artwork (DESIGN.md §2.2). */
    val colorsFromArtwork: Boolean = true,
    val seekBarStyle: SeekBarStyle = SeekBarStyle.Wavy,
    /** App-level reduced motion (in addition to the system animator scale). */
    val reduceMotion: Boolean = false,
)

/** Read/write access to [UiSettings]. Injected with Koin; screens never touch DataStore directly. */
interface UiSettingsRepository {
    val settings: Flow<UiSettings>
    suspend fun update(transform: (UiSettings) -> UiSettings)
}

private val Context.uiSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "ui_settings")

/** DataStore-backed implementation (file `datastore/ui_settings.preferences_pb`). */
class DataStoreUiSettingsRepository(context: Context) : UiSettingsRepository {
    private val store = context.applicationContext.uiSettingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val wallpaper = booleanPreferencesKey("wallpaper_colors")
        val artwork = booleanPreferencesKey("colors_from_artwork")
        val seek = stringPreferencesKey("seek_bar_style")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
    }

    override val settings: Flow<UiSettings> = store.data.map { p ->
        val d = UiSettings()
        UiSettings(
            themeMode = p[Keys.theme].toEnum(d.themeMode),
            useWallpaperColors = p[Keys.wallpaper] ?: d.useWallpaperColors,
            colorsFromArtwork = p[Keys.artwork] ?: d.colorsFromArtwork,
            seekBarStyle = p[Keys.seek].toEnum(d.seekBarStyle),
            reduceMotion = p[Keys.reduceMotion] ?: d.reduceMotion,
        )
    }

    override suspend fun update(transform: (UiSettings) -> UiSettings) {
        store.edit { p ->
            val d = UiSettings()
            val current = UiSettings(
                themeMode = p[Keys.theme].toEnum(d.themeMode),
                useWallpaperColors = p[Keys.wallpaper] ?: d.useWallpaperColors,
                colorsFromArtwork = p[Keys.artwork] ?: d.colorsFromArtwork,
                seekBarStyle = p[Keys.seek].toEnum(d.seekBarStyle),
                reduceMotion = p[Keys.reduceMotion] ?: d.reduceMotion,
            )
            val next = transform(current)
            p[Keys.theme] = next.themeMode.name
            p[Keys.wallpaper] = next.useWallpaperColors
            p[Keys.artwork] = next.colorsFromArtwork
            p[Keys.seek] = next.seekBarStyle.name
            p[Keys.reduceMotion] = next.reduceMotion
        }
    }
}

/** In-memory implementation for previews and tests. */
class InMemoryUiSettingsRepository(initial: UiSettings = UiSettings()) : UiSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<UiSettings> = state.asStateFlow()
    override suspend fun update(transform: (UiSettings) -> UiSettings) = state.update(transform)
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
