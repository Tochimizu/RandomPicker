package fr.randommediapicker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import fr.randommediapicker.domain.*
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: PlaybackSettings, update: ((PlaybackSettings) -> PlaybackSettings) -> Unit,
    chooseMusic: () -> Unit, chooseRoot: () -> Unit, removeMusic: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Les changements sont enregistrés automatiquement.", style = MaterialTheme.typography.bodySmall) }
        item { SectionTitle("Apparence") }
        item {
            SettingChoices("Thème", settings.theme, listOf(ThemeMode.DARK to "Sombre", ThemeMode.LIGHT to "Clair", ThemeMode.SYSTEM to "Système")) {
                value -> update { it.copy(theme = value) }
            }
        }
        item { SectionTitle("Mosaïque et tirage") }
        item { SettingChoices("Disposition", settings.gridSize, (1..4).map { it to "$it × $it" }) { value -> update { it.copy(gridSize = value) } } }
        item {
            SettingChoices("Distribution", settings.randomMode, listOf(RandomMode.SHUFFLE to "Shuffle", RandomMode.PURE_RANDOM to "Random pur")) {
                value -> update { it.copy(randomMode = value) }
            }
        }
        item { Text("Shuffle distribue tous les fichiers avant de recommencer un cycle. Random autorise les répétitions entre les tirages.", style = MaterialTheme.typography.bodySmall) }
        item { SettingSlider("Durée des images", settings.imageSeconds.toFloat(), 1f..120f, "${settings.imageSeconds} s", steps = 118) {
            value -> update { it.copy(imageSeconds = value.roundToInt()) }
        } }
        item { SettingSlider("Durée des animations", settings.gifSeconds.toFloat(), 1f..120f, "${settings.gifSeconds} s", steps = 118) {
            value -> update { it.copy(gifSeconds = value.roundToInt()) }
        } }
        item { SettingChoices("Lecture vidéo", settings.videoMode, listOf(VideoMode.COMPLETE to "Complète", VideoMode.LIMITED to "Durée maximale")) {
            value -> update { it.copy(videoMode = value) }
        } }
        if (settings.videoMode == VideoMode.LIMITED) item {
            SettingSlider("Maximum vidéo", settings.videoMaxSeconds.toFloat(), 1f..120f, "${settings.videoMaxSeconds} s", steps = 118) {
                value -> update { it.copy(videoMaxSeconds = value.roundToInt()) }
            }
        }
        item { SectionTitle("Son et musique") }
        item { SettingSwitch("Son des vidéos", "Une seule cellule sonore à la fois", settings.videoAudio) { value -> update { it.copy(videoAudio = value) } } }
        item { SettingSwitch("Sélection audio automatique", "Désactivez pour verrouiller la cellule choisie par appui court", settings.automaticAudio) {
            value -> update { it.copy(automaticAudio = value) }
        } }
        item { SettingChoices("Mixage", settings.audioMode, listOf(
            AudioMode.MUSIC_ONLY to "Musique seule", AudioMode.VIDEO_ONLY to "Vidéo seule", AudioMode.VIDEO_PRIORITY to "Priorité vidéo",
            AudioMode.DUCKING to "Ducking", AudioMode.MIX to "Mixage",
        )) { value -> update { it.copy(audioMode = value) } } }
        item { Text("Priorité vidéo suspend la musique. Ducking réduit son volume à 20 % pendant le son vidéo. Mixage conserve les deux volumes.", style = MaterialTheme.typography.bodySmall) }
        item {
            MusicSelection(settings, chooseMusic, removeMusic) { value -> update { it.copy(musicEnabled = value) } }
        }
        item { SettingSlider("Volume général", settings.masterVolume, 0f..1f, "${(settings.masterVolume * 100).roundToInt()} %") { value -> update { it.copy(masterVolume = value) } } }
        item { SettingSlider("Volume musique", settings.musicVolume, 0f..1f, "${(settings.musicVolume * 100).roundToInt()} %") { value -> update { it.copy(musicVolume = value) } } }
        item { SettingSlider("Volume vidéo", settings.videoVolume, 0f..1f, "${(settings.videoVolume * 100).roundToInt()} %") { value -> update { it.copy(videoVolume = value) } } }
        item { SectionTitle("Affichage et appareil") }
        item { SettingChoices("Cadrage", settings.scaleMode, listOf(ScaleMode.FIT to "Contenir", ScaleMode.CROP to "Remplir")) { value -> update { it.copy(scaleMode = value) } } }
        item { SettingSwitch("Fondu court", "Transition des images et animations", settings.fade) { value -> update { it.copy(fade = value) } } }
        item { SettingSwitch("Garder l’écran allumé", "Uniquement pendant la lecture", settings.keepScreenOn) { value -> update { it.copy(keepScreenOn = value) } } }
        item { SettingChoices("Rotation", settings.rotation, listOf(RotationMode.AUTO to "Automatique", RotationMode.PORTRAIT to "Portrait", RotationMode.LANDSCAPE to "Paysage")) {
            value -> update { it.copy(rotation = value) }
        } }
        item { OutlinedButton(onClick = chooseRoot) { Text("Remplacer le dossier racine") } }
        item { Text("Lecture locale · Aucun compte · Aucun accès réseau", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun <T> SettingChoices(title: String, value: T, choices: List<Pair<T, String>>, change: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (option, label) -> FilterChip(selected = value == option, onClick = { change(option) }, label = { Text(label) }) }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = change, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, steps: Int = 0, change: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text("$title · $label", style = MaterialTheme.typography.titleSmall)
        Slider(value = draft, onValueChange = { draft = it }, valueRange = range, steps = steps,
            onValueChangeFinished = { change(draft) }, modifier = Modifier.semantics { contentDescription = title })
        if (draft != value) Text(if (range.endInclusive == 1f) "${(draft * 100).roundToInt()} %" else "${draft.roundToInt()} s", style = MaterialTheme.typography.labelSmall)
    }
}
