package com.mhrsdev.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource
import com.mhrsdev.musicplayer.domain.repository.PlayerController
import org.koin.compose.koinInject

/** STUB (T1): proves DI + fakes + Expressive APIs are wired. Replaced by the real Home in T3. */
@Composable
fun PlaceholderHome(modifier: Modifier = Modifier) {
    val feed = koinInject<HomeFeedSource>()
    val player = koinInject<PlayerController>()
    val sections by feed.sections().collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by player.state.collectAsStateWithLifecycle()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text("Music Player", style = MaterialTheme.typography.displaySmall)
            Text("Placeholder Home (T1 scaffold)", style = MaterialTheme.typography.bodyMedium)
            sections.forEach {
                Text("${it.title}: ${it.items.size} items", Modifier.padding(top = 12.dp))
            }
            Text(
                "Now: ${playback.currentTrack?.title ?: "nothing"}",
                Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LinearWavyProgressIndicator(
                progress = { if (playback.durationMs == 0L) 0f else playback.positionMs / playback.durationMs.toFloat() },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
