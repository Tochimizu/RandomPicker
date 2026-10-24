@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.randommediapicker.ui

import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.drawable.CrossfadeDrawable
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import fr.randommediapicker.domain.*
import fr.randommediapicker.media.GridCellState

@Composable
fun GridScreen(
    model: PickerViewModel, settings: PlaybackSettings, eligibleCount: Int, permissionLost: Boolean,
    immersive: Boolean, toggleImmersive: () -> Unit, openLibrary: () -> Unit, openSettings: () -> Unit,
) {
    val cells by model.playback.cells.collectAsStateWithLifecycle()
    val running by model.playback.running.collectAsStateWithLifecycle()
    val playing by model.playback.playing.collectAsStateWithLifecycle()
    val muted by model.playback.muted.collectAsStateWithLifecycle()
    val focus by model.playback.focus.collectAsStateWithLifecycle()
    val enlarged by model.playback.enlarged.collectAsStateWithLifecycle()
    val historyPaused by model.playback.historyPaused.collectAsStateWithLifecycle()
    val navigationNotice by model.playback.navigationNotice.collectAsStateWithLifecycle()
    val enabled = eligibleCount > 0 && !permissionLost
    var information by remember { mutableStateOf<GridCellState?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item { PlaybackControls(playing, muted, enabled,
                stop = model.playback::stop, resume = model.playback::togglePause,
                previous = { model.playback.navigate(false) }, next = { model.playback.nextAll() },
                reload = { model.playback.restartCycle() }, sound = model.playback::toggleMute) }
            item {
                var gridMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { gridMenu = true }) { Text("${settings.gridSize} × ${settings.gridSize}") }
                    DropdownMenu(expanded = gridMenu, onDismissRequest = { gridMenu = false }) {
                        (1..4).forEach { size -> DropdownMenuItem(text = { Text("$size × $size") }, onClick = {
                            gridMenu = false; model.updateSettings { it.copy(gridSize = size) }
                        }) }
                    }
                }
            }
            item { IconButton(onClick = toggleImmersive) { Icon(if (immersive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (immersive) "Quitter le mode immersif" else "Mode immersif") } }
            if (immersive) {
                item { IconButton(onClick = openLibrary) { Icon(Icons.Default.Folder, "Bibliothèque") } }
                item { IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, "Paramètres") } }
            }
        }
        if (eligibleCount < settings.gridSize * settings.gridSize && eligibleCount > 0) {
            Text("Moins de médias que de cellules : des répétitions sont possibles.",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        if (cells.none { it.media != null } || permissionLost) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Collections, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(if (permissionLost) "Accès au dossier perdu" else "Aucun média disponible", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = openLibrary) { Text("Retourner à la sélection") }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().padding(4.dp)
                .navigationSwipes(previous = { if (enabled) model.playback.navigate(false) },
                    next = { if (enabled) model.playback.nextAll() }), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(settings.gridSize) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(settings.gridSize) { col ->
                            val index = row * settings.gridSize + col
                            val cell = cells.getOrNull(index)
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (cell != null && enlarged != index) MediaCell(cell, model, settings, running && index !in historyPaused, focus == index,
                                    enlarge = { model.playback.setEnlarged(index) }, info = { information = cell })
                            }
                        }
                    }
                }
            }
        }
        navigationNotice?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall) }
        if (!immersive) Text(
            "${if (settings.randomMode == RandomMode.SHUFFLE) "Shuffle" else "Random"} · ${if (running) "Lecture" else "Pause"} · ${model.playback.videoBudget} vidéo(s) active(s) max.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    enlarged?.let { index ->
        Dialog(onDismissRequest = { model.playback.setEnlarged(null) }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        )) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(window) {
                window?.let {
                    val controller = WindowCompat.getInsetsController(it, it.decorView)
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Surface {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Vue agrandie", modifier = Modifier.weight(1f).padding(start = 16.dp))
                                IconButton(onClick = { model.playback.setEnlarged(null) }) {
                                    Icon(Icons.Default.Close, "Fermer la vue agrandie")
                                }
                            }
                            PlaybackControls(playing, muted, enabled,
                                stop = model.playback::stop, resume = model.playback::togglePause,
                                previous = { model.playback.navigate(false, index) }, next = { model.playback.navigate(true, index) },
                                reload = { model.playback.restartCycle() }, sound = model.playback::toggleMute)
                            navigationNotice?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().navigationSwipes(
                        previous = { if (enabled) model.playback.navigate(false, index) },
                        next = { if (enabled) model.playback.navigate(true, index) })) {
                        cells.getOrNull(index)?.let { cell -> MediaCell(cell, model, settings, running && index !in historyPaused, focus == index,
                            enlarge = { model.playback.setEnlarged(null) }, info = { information = cell }, fullScreen = true) }
                    }
                }
            }
        }
    }
    information?.let { MediaInfoDialog(it, onDismiss = { information = null }, exclude = {
        it.media?.id?.let(model.playback::excludeMedia); information = null
    }) }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun MediaCell(
    cell: GridCellState, model: PickerViewModel, settings: PlaybackSettings, running: Boolean,
    focused: Boolean, enlarge: () -> Unit, info: () -> Unit, fullScreen: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val image = cell.media != null && cell.media.kind != MediaKind.VIDEO
    val shape = RoundedCornerShape(if (fullScreen) 0.dp else 6.dp)
    Box(Modifier.fillMaxSize().clip(shape).background(Color.Black)
        .border(BorderStroke(if (focused && !fullScreen) 2.dp else 0.dp,
            if (focused && !fullScreen) MaterialTheme.colorScheme.primary else Color.Transparent), shape)
        .combinedClickable(
            onClick = {
                model.playback.selectFocus(cell.index)
                if (image) enlarge()
            },
            onClickLabel = if (image) {
                if (fullScreen) "Revenir à la mosaïque" else "Afficher l’image en plein écran"
            } else "Choisir cette cellule audio",
            onLongClick = { menu = true }, onDoubleClick = enlarge,
        )
        .semantics { contentDescription = "Cellule ${cell.index + 1}, ${cell.media?.name ?: "vide"}${if (focused) ", priorité audio" else ""}" }) {
        key(cell.token, cell.videoActive) {
            val player = if (cell.videoActive) model.playback.player(cell.index) else null
            if (player != null) {
                AndroidView(factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        this.player = player
                        keepScreenOn = false
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    }
                }, update = { view ->
                    view.player = player
                    view.resizeMode = if (settings.scaleMode == ScaleMode.FIT) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
            } else if (cell.media != null) {
                val context = LocalContext.current
                var animation by remember { mutableStateOf<Animatable?>(null) }
                val request = remember(cell.token, settings.fade) {
                    ImageRequest.Builder(context).data(cell.media.id)
                        .apply { if (cell.media.kind == MediaKind.VIDEO) decoderFactory(VideoFrameDecoder.Factory()) }
                        .crossfade(if (settings.fade) 180 else 0).build()
                }
                AsyncImage(model = request, contentDescription = cell.media.name,
                    contentScale = if (settings.scaleMode == ScaleMode.FIT) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = {
                        val decoded = it.result.drawable
                        animation = (if (decoded is CrossfadeDrawable) decoded.end else decoded) as? Animatable
                        (animation as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        if (running) animation?.start() else animation?.stop()
                        model.playback.ready(cell.index, cell.token, animation != null)
                    },
                    onError = { model.playback.failure(cell.index, cell.token, "Décodage impossible") })
                SideEffect { if (running) animation?.start() else animation?.stop() }
                DisposableEffect(cell.token) { onDispose { animation?.stop() } }
            }
        }
        if (!cell.ready && cell.media != null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
        if (cell.media?.kind == MediaKind.VIDEO && !cell.videoActive) {
            Icon(Icons.Default.PlayCircle, "Aperçu vidéo statique : touchez pour activer", tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        if (focused && !fullScreen) Icon(Icons.AutoMirrored.Filled.VolumeUp, "Cellule audio prioritaire", tint = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .6f)).padding(4.dp).size(16.dp))
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Média suivant") }, onClick = { menu = false; model.playback.next(cell.index) })
            DropdownMenuItem(text = { Text("Revoir le média") }, onClick = { menu = false; model.playback.replay(cell.index) })
            DropdownMenuItem(text = { Text("Exclure du cycle courant") }, onClick = { menu = false; model.playback.exclude(cell.index) })
            DropdownMenuItem(text = { Text("Informations") }, onClick = { menu = false; info() })
        }
    }
}
