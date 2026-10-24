package fr.randommediapicker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.randommediapicker.data.ScanProgress
import fr.randommediapicker.domain.*

@Composable
fun LibraryScreen(
    state: LibraryUiState, scanning: Boolean, progress: ScanProgress, permissionLost: Boolean,
    chooseRoot: () -> Unit, refresh: () -> Unit, select: (String?, Boolean) -> Unit, play: () -> Unit,
    settings: PlaybackSettings, chooseMusic: () -> Unit, removeMusic: () -> Unit, enableMusic: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val children = remember(state.folders) { state.folders.groupBy { it.parentId } }
    val rows = remember(state.folders, expanded) {
        buildList {
            val pending = ArrayDeque<FolderNode>()
            children[null].orEmpty().asReversed().forEach(pending::addLast)
            while (pending.isNotEmpty()) {
                val folder = pending.removeLast()
                add(folder)
                if (folder.id in expanded) children[folder.id].orEmpty().asReversed().forEach(pending::addLast)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Collections, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text(state.root?.displayName ?: "Vos médias, autrement.", style = MaterialTheme.typography.headlineSmall)
                    Text(if (state.root == null) "Choisissez un dossier pour composer une mosaïque d’images, d’animations et de vidéos. Tout reste sur votre appareil."
                        else "${state.eligibleCount} médias dans le Random Set", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = chooseRoot) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp))
                        Text(if (state.root == null) "Choisir un dossier" else "Changer de dossier")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(20.dp)) { MusicSelection(settings, chooseMusic, removeMusic, enableMusic) }
            }
        }
        if (permissionLost) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("L’autorisation du dossier a été révoquée ou le stockage est déconnecté.")
                    TextButton(onClick = chooseRoot) { Text("Autoriser à nouveau") }
                }
            }
        }
        if (scanning) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Analyse… ${progress.folders} dossiers · ${progress.media} médias · ${progress.errors} erreurs")
            }
        }
        if (state.root != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = play, enabled = state.eligibleCount > 0 && !scanning && !permissionLost,
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null); Text("Lancer la mosaïque")
                    }
                    IconButton(onClick = refresh, enabled = !scanning) { Icon(Icons.Default.Refresh, "Actualiser la bibliothèque") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dossiers", style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = { select(null, true) }, enabled = !scanning) { Text("Tout inclure") }
                        TextButton(onClick = { select(null, false) }, enabled = !scanning) { Text("Tout exclure") }
                    }
                }
            }
            items(rows, key = { it.id }) { folder ->
                val check = state.checks[folder.id] ?: FolderCheck.EXCLUDED
                Row(Modifier.fillMaxWidth().padding(start = (folder.depth.coerceAtMost(7) * 16).dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!children[folder.id].isNullOrEmpty()) IconButton(onClick = {
                        expanded = if (folder.id in expanded) expanded - folder.id else expanded + folder.id
                    }) { Icon(if (folder.id in expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        if (folder.id in expanded) "Replier ${folder.name}" else "Déplier ${folder.name}") }
                    else Spacer(Modifier.width(48.dp))
                    TriStateCheckbox(state = when (check) {
                        FolderCheck.INCLUDED -> ToggleableState.On
                        FolderCheck.EXCLUDED -> ToggleableState.Off
                        FolderCheck.PARTIAL -> ToggleableState.Indeterminate
                    }, onClick = { select(folder.id, check != FolderCheck.INCLUDED) }, enabled = !scanning,
                        modifier = Modifier.semantics { contentDescription = "Inclure ${folder.name}" })
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${folder.directCount} directs · ${folder.totalCount} au total", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.eligibleCount == 0 && !scanning) item {
                Text("Aucun média disponible. Incluez un dossier contenant des fichiers compatibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("Android limite l’accès à certaines racines, au dossier Download et aux dossiers privés. Choisissez un sous-dossier accessible avec le sélecteur système.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
