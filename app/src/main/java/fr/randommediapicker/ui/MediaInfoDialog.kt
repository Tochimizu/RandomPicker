package fr.randommediapicker.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.randommediapicker.domain.MediaKind
import fr.randommediapicker.media.GridCellState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun MediaInfoDialog(cell: GridCellState, onDismiss: () -> Unit, exclude: () -> Unit) {
    val media = cell.media ?: return
    val context = LocalContext.current
    val details by produceState("Chargement des métadonnées…", media.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (media.kind == MediaKind.VIDEO) {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(context, Uri.parse(media.id))
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        "$width × $height px · ${duration?.div(1000) ?: "?"} s"
                    }
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(Uri.parse(media.id)).use { BitmapFactory.decodeStream(it, null, options) }
                    if (options.outWidth > 0) "${options.outWidth} × ${options.outHeight} px" else "Dimensions indisponibles"
                }
            }.getOrDefault("Métadonnées indisponibles")
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(media.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chemin : ${media.relativePath}")
            Text("Type : ${media.mimeType.ifEmpty { media.kind.name }}")
            Text(details)
            Text("Taille : ${android.text.format.Formatter.formatFileSize(context, media.sizeBytes)}")
            if (media.lastModified > 0) Text("Modifié : ${DateFormat.getDateTimeInstance().format(Date(media.lastModified))}")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        dismissButton = { TextButton(onClick = exclude) { Text("Exclure temporairement") } })
}
