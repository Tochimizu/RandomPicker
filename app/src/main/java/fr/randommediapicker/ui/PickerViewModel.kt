package fr.randommediapicker.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.randommediapicker.PickerApplication
import fr.randommediapicker.data.*
import fr.randommediapicker.domain.*
import fr.randommediapicker.media.PlaybackCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class LibraryUiState(
    val root: LibraryRoot? = null,
    val folders: List<FolderNode> = emptyList(),
    val checks: Map<String, FolderCheck> = emptyMap(),
    val eligibleCount: Int = 0,
)

class PickerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PickerApplication
    private val repository = app.library
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    fun notify(message: String) { messages.trySend(message) }
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettings())
    val scanning = MutableStateFlow(false)
    val progress = MutableStateFlow(ScanProgress())
    val permissionLost = MutableStateFlow(false)
    private var scanJob: Job? = null
    val playback = PlaybackCoordinator(app, viewModelScope, repository::invalidate, ::notify)

    val library = combine(repository.root, repository.folders, repository.media) { root, entities, media ->
        val nodes = entities.map { it.node() }
        val effective = FolderSelection.effective(nodes)
        LibraryUiState(root, nodes, FolderSelection.checks(nodes), media.count { effective[it.folderId] == true })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    init {
        viewModelScope.launch { settings.collect { playback.updateSettings(it) } }
        viewModelScope.launch {
            combine(repository.folders, repository.media) { folders, files ->
                val selected = FolderSelection.effective(folders.map { it.node() })
                files.filter { selected[it.folderId] == true }.distinctBy { it.id }
            }.flowOn(Dispatchers.Default).distinctUntilChanged().collect { playback.updateLibrary(it) }
        }
        viewModelScope.launch {
            repository.root.collect { root -> permissionLost.value = root != null && !repository.hasPermission(root.treeUri) }
        }
    }

    fun checkPermission() {
        library.value.root?.let { permissionLost.value = !repository.hasPermission(it.treeUri) }
        if (permissionLost.value) playback.setForeground(false)
    }

    fun chooseRoot(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!repository.hasPermission(uri.toString())) error("L’autorisation de ce fournisseur ne peut pas être conservée.")
            scan(uri)
        } catch (error: Exception) {
            notify(error.message ?: "Impossible de conserver l’accès à ce dossier.")
        }
    }

    fun refresh() { library.value.root?.let { scan(Uri.parse(it.treeUri)) } }

    private fun scan(uri: Uri) {
        val previousJob = scanJob
        previousJob?.cancel()
        scanJob = viewModelScope.launch {
            previousJob?.join()
            scanning.value = true
            progress.value = ScanProgress()
            playback.setForeground(false)
            try {
                val oldRoot = repository.dao.root()?.treeUri
                repository.scan(uri) { progress.value = it }
                permissionLost.value = false
                if (oldRoot != null && oldRoot != uri.toString()) {
                    runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(oldRoot), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                notify("Analyse terminée : ${progress.value.media} médias, ${progress.value.errors} dossiers ignorés.")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                checkPermission()
                notify(error.message ?: "Impossible d’analyser ce dossier.")
            } finally {
                scanning.value = false
                // The lifecycle/visible state is refreshed by the UI after a scan.
            }
        }
    }

    fun selectFolder(id: String?, include: Boolean) = viewModelScope.launch {
        runCatching { repository.select(id, include) }.onFailure { notify("Sélection non enregistrée : ${it.message}") }
    }

    fun updateSettings(transform: (PlaybackSettings) -> PlaybackSettings) = viewModelScope.launch {
        runCatching { app.settings.update(transform) }.onFailure { notify("Réglage non enregistré : ${it.message}") }
    }

    fun chooseMusic(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            updateSettings { it.copy(musicUri = uri.toString(), musicEnabled = true,
                audioMode = if (it.audioMode == AudioMode.VIDEO_ONLY) AudioMode.DUCKING else it.audioMode) }
        } catch (error: Exception) { notify("Impossible de conserver l’accès à ce fichier audio.") }
    }

    override fun onCleared() { playback.release(); super.onCleared() }
}
