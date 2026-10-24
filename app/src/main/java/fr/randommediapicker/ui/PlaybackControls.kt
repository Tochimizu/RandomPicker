package fr.randommediapicker.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackControls(
    playing: Boolean, muted: Boolean, enabled: Boolean,
    stop: () -> Unit, resume: () -> Unit, previous: () -> Unit, next: () -> Unit,
    reload: () -> Unit, sound: () -> Unit,
) {
    Row {
        IconButton(onClick = if (playing) stop else resume, enabled = enabled) {
            Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) "Stop" else "Reprendre la lecture")
        }
        IconButton(onClick = previous, enabled = enabled) { Icon(Icons.Default.SkipPrevious, "Précédent") }
        IconButton(onClick = next, enabled = enabled) { Icon(Icons.Default.SkipNext, "Next : suivant") }
        IconButton(onClick = reload, enabled = enabled) { Icon(Icons.Default.Refresh, "Reload : recommencer le cycle") }
        IconButton(onClick = sound) {
            Icon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                if (muted) "Son : activer" else "Son : couper")
        }
    }
}

/** Installed only on the media area so swipes cannot steal gestures from the controls. */
@Composable
fun Modifier.navigationSwipes(previous: () -> Unit, next: () -> Unit): Modifier {
    val currentPrevious by rememberUpdatedState(previous)
    val currentNext by rememberUpdatedState(next)
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    return pointerInput(threshold) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                if (distance >= threshold) currentNext()
                else if (distance <= -threshold) currentPrevious()
                distance = 0f
            },
        )
    }
}
