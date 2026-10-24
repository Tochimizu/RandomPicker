package fr.randommediapicker.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.randommediapicker.domain.PlaybackSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MusicSelection(
    settings: PlaybackSettings, chooseMusic: () -> Unit, removeMusic: () -> Unit,
    enableMusic: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val name by produceState<String?>(null, settings.musicUri) {
        value = settings.musicUri?.let { source ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.query(Uri.parse(source), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull() ?: Uri.parse(source).lastPathSegment ?: "Fichier audio local"
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Son de fond", style = MaterialTheme.typography.titleMedium)
        Text(if (settings.musicUri == null) "Aucun son de fond" else name ?: "Fichier audio sélectionné",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = chooseMusic) {
            Text(if (settings.musicUri == null) "Choisir un son de fond" else "Changer le son de fond")
        }
        if (settings.musicUri != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Musique en boucle", modifier = Modifier.weight(1f))
                Switch(checked = settings.musicEnabled, onCheckedChange = enableMusic,
                    modifier = Modifier.semantics { contentDescription = "Musique en boucle" })
            }
            TextButton(onClick = removeMusic) { Text("Retirer le son de fond") }
        }
    }
}
