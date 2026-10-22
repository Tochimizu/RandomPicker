package fr.randommediapicker.media

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import fr.randommediapicker.data.MediaFile
import fr.randommediapicker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GridCellState(
    val index: Int,
    val media: MediaFile?,
    val token: Long,
    val ready: Boolean = false,
    val elapsedMs: Long = 0,
    val loadingMs: Long = 0,
    val videoActive: Boolean = false,
    val animated: Boolean = media?.kind == MediaKind.ANIMATED,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val invalidate: suspend (String) -> Unit,
    private val message: (String) -> Unit,
) {
    private val engine = SessionRandomEngine()
    private val transitions = Mutex()
    private val resources = GridResourceManager(context)
    val videoBudget get() = resources.videoBudget
    private val _cells = MutableStateFlow<List<GridCellState>>(emptyList())
    val cells = _cells.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()
    private val _focus = MutableStateFlow(0)
    val focus = _focus.asStateFlow()
    private val _playing = MutableStateFlow(true)
    val playing = _playing.asStateFlow()
    private val _muted = MutableStateFlow(false)
    val muted = _muted.asStateFlow()
    private val players = mutableMapOf<Int, ExoPlayer>()
    private val playerTokens = mutableMapOf<Int, Long>()
    private var eligible = emptyList<MediaFile>()
    private val failed = mutableSetOf<String>()
    private var settings = PlaybackSettings()
    private var foreground = false
    private var visible = false
    private var focusBlocked = false
    private var duck = 1f
    private var generation = 0L
    private val replacing = mutableSetOf<Int>()
    private val audio = AudioCoordinator(context, onFocus = { blocked, level, permanent ->
        focusBlocked = blocked
        duck = level
        if (permanent) _playing.value = false
        syncPlayback()
    }, onError = message)

    init {
        scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val delta = (now - last).coerceAtMost(500)
                last = now
                if (!_running.value) continue
                val expired = mutableListOf<GridCellState>()
                val failed = mutableListOf<GridCellState>()
                _cells.value = _cells.value.map { cell ->
                    if (cell.media == null) return@map cell
                    if (!cell.ready) {
                        val next = cell.copy(loadingMs = cell.loadingMs + delta)
                        if (next.loadingMs >= 20_000) failed.add(next)
                        next
                    } else {
                        val advances = !cell.videoActive || players[cell.index]?.isPlaying == true
                        val next = cell.copy(elapsedMs = cell.elapsedMs + if (advances) delta else 0)
                        val duration = when {
                            cell.videoActive && settings.videoMode == VideoMode.COMPLETE -> Long.MAX_VALUE
                            cell.videoActive -> settings.videoMaxSeconds * 1000L
                            cell.animated -> settings.gifSeconds * 1000L
                            else -> settings.imageSeconds * 1000L
                        }
                        if (next.elapsedMs >= duration) expired.add(next)
                        // Recover a decoder that remains buffering after previously becoming ready.
                        if (cell.videoActive && players[cell.index]?.playbackState == Player.STATE_BUFFERING) {
                            val buffering = next.copy(loadingMs = cell.loadingMs + delta)
                            if (buffering.loadingMs >= 20_000) failed.add(buffering)
                            buffering
                        } else next.copy(loadingMs = 0)
                    }
                }
                expired.forEach { next(it.index, it.token) }
                failed.forEach { failure(it.index, it.token, "Délai de chargement dépassé") }
                syncPlayback()
            }
        }
    }

    suspend fun updateLibrary(files: List<MediaFile>) = transitions.withLock {
        val old = eligible.map { it.id }
        // Invalidation removes the URI from Room's accessible flow; a later scan may retry it.
        failed.retainAll(files.map { it.id }.toSet())
        eligible = files.filterNot { it.id in failed }
        if (old != eligible.map { it.id }) engine.initialize(eligible.map { it.id }, settings.randomMode)
        if (eligible.isEmpty()) {
            _cells.value = emptyList()
            reconcileVideos()
        } else if (_cells.value.isEmpty() && visible) resizeLocked()
    }

    suspend fun updateSettings(value: PlaybackSettings) = transitions.withLock {
        val old = settings
        settings = value
        if (old.randomMode != value.randomMode) engine.initialize(eligible.map { it.id }, value.randomMode)
        if (old.gridSize != value.gridSize || (_cells.value.isEmpty() && visible && eligible.isNotEmpty())) resizeLocked()
        reconcileVideos()
    }

    fun setForeground(value: Boolean) { foreground = value; syncPlayback() }
    fun setVisible(value: Boolean) {
        visible = value
        if (value) scope.launch { transitions.withLock { if (_cells.value.isEmpty()) resizeLocked() } }
        syncPlayback()
    }
    fun togglePause() {
        _playing.value = !_playing.value
        if (_playing.value) focusBlocked = false
        syncPlayback()
    }
    fun toggleMute() { _muted.value = !_muted.value; syncPlayback() }
    fun selectFocus(index: Int) { _focus.value = index; reconcileVideos() }
    fun player(index: Int): ExoPlayer? = players[index]

    private suspend fun resizeLocked() {
        val count = settings.gridSize * settings.gridSize
        val current = _cells.value.take(count).toMutableList()
        while (current.size < count && eligible.isNotEmpty()) {
            val pick = engine.next(current.mapNotNull { it.media?.id }.toSet())
            current.add(newCell(current.size, pick?.mediaId))
        }
        _cells.value = current
        if (_focus.value >= count) _focus.value = 0
        reconcileVideos()
    }

    private fun newCell(index: Int, id: String?) = GridCellState(index, eligible.firstOrNull { it.id == id }, ++generation)

    fun next(index: Int, token: Long? = null) {
        if (!replacing.add(index)) return
        scope.launch {
            try {
                transitions.withLock {
                    val old = _cells.value.getOrNull(index) ?: return@withLock
                    if (token != null && old.token != token) return@withLock
                    val visible = _cells.value.filter { it.index != index }.mapNotNull { it.media?.id }.toSet()
                    val pick = engine.next(visible)
                    _cells.value = _cells.value.map { if (it.index == index) newCell(index, pick?.mediaId) else it }
                    reconcileVideos()
                }
            } finally { replacing.remove(index) }
        }
    }

    fun nextAll() = scope.launch {
        transitions.withLock {
            val picks = engine.nextBatch(settings.gridSize * settings.gridSize)
            _cells.value = picks.mapIndexed { index, pick -> newCell(index, pick.mediaId) }
            reconcileVideos()
        }
    }

    fun restartCycle() = scope.launch {
        transitions.withLock {
            engine.reset()
            val picks = engine.nextBatch(settings.gridSize * settings.gridSize)
            _cells.value = picks.mapIndexed { index, pick -> newCell(index, pick.mediaId) }
            reconcileVideos()
        }
    }

    fun replay(index: Int) {
        _cells.value.getOrNull(index) ?: return
        _cells.value = _cells.value.map { if (it.index == index) it.copy(token = ++generation, ready = false, elapsedMs = 0, loadingMs = 0) else it }
        reconcileVideos()
    }

    fun exclude(index: Int) {
        _cells.value.getOrNull(index)?.media?.id?.let(::excludeMedia)
    }

    fun excludeMedia(id: String) = scope.launch {
        transitions.withLock {
            engine.excludeForCurrentCycle(id)
        }
        _cells.value.filter { it.media?.id == id }.forEach { next(it.index) }
        message("Fichier exclu du cycle courant.")
    }

    fun ready(index: Int, token: Long, animated: Boolean? = null) {
        _cells.value = _cells.value.map { if (it.index == index && it.token == token) it.copy(ready = true, loadingMs = 0, animated = animated ?: it.animated) else it }
    }

    fun failure(index: Int, token: Long, reason: String) {
        val cell = _cells.value.getOrNull(index)?.takeIf { it.token == token } ?: return
        val id = cell.media?.id ?: return
        if (id in failed) return
        failed.add(id)
        Log.w("MediaPlayback", "${cell.media.name}: $reason")
        scope.launch {
            transitions.withLock {
                eligible = eligible.filterNot { it.id == id }
                engine.initialize(eligible.map { it.id }, settings.randomMode)
            }
            invalidate(id)
            message("Fichier ignoré : ${cell.media.name}")
            _cells.value.filter { it.media?.id == id }.forEach { next(it.index, it.token) }
        }
    }

    private fun reconcileVideos() {
        val videos = _cells.value.filter { it.media?.kind == MediaKind.VIDEO }
        val wanted = videos.sortedBy { if (it.index == _focus.value) 0 else 1 }
            .take(resources.videoBudget).map { it.index }.toSet()
        players.keys.toList().forEach { index ->
            if (index !in wanted || playerTokens[index] != _cells.value.getOrNull(index)?.token) {
                players.remove(index)?.release()
                playerTokens.remove(index)
            }
        }
        _cells.value = _cells.value.map { cell ->
            if (cell.index !in wanted && cell.videoActive) cell.copy(videoActive = false, ready = false, elapsedMs = 0, loadingMs = 0)
            else if (cell.index in wanted && !cell.videoActive) cell.copy(videoActive = true, ready = false, elapsedMs = 0, loadingMs = 0)
            else cell
        }
        for (cell in _cells.value.filter { it.index in wanted }) {
            if (players[cell.index] != null) continue
            try {
                val player = ExoPlayer.Builder(context).build()
                players[cell.index] = player
                playerTokens[cell.index] = cell.token
                player.volume = 0f
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready(cell.index, cell.token)
                        if (state == Player.STATE_ENDED) next(cell.index, cell.token)
                    }
                    override fun onPlayerError(error: PlaybackException) = failure(cell.index, cell.token, error.errorCodeName)
                })
                player.setMediaItem(MediaItem.fromUri(cell.media!!.id))
                player.prepare()
            } catch (error: Exception) {
                players.remove(cell.index)?.release()
                failure(cell.index, cell.token, error.message ?: "Décodeur indisponible")
            }
        }
        syncPlayback()
    }

    private fun syncPlayback() {
        if (settings.automaticAudio) {
            val current = players[_focus.value]
            if (current == null || current.playbackState == Player.STATE_ENDED) {
                players.entries.firstOrNull { it.value.playbackState == Player.STATE_READY }?.let { _focus.value = it.key }
            }
        }
        val focusedPlayer = players[_focus.value]
        val hasVideo = focusedPlayer?.let {
            it.playbackState == Player.STATE_READY && it.currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_AUDIO && group.isSelected }
        } == true
        val levels = AudioPolicy.levels(settings, hasVideo, _muted.value, duck)
        val wantsPlayback = _playing.value && foreground && visible && _cells.value.any { it.media != null }
        val wantsSound = wantsPlayback && (levels.music > 0f || levels.video > 0f)
        val focusGranted = audio.sync(settings, levels, wantsPlayback && !focusBlocked, wantsSound)
        if (wantsSound && !focusGranted && !focusBlocked) {
            _playing.value = false
            message("Lecture en pause : une autre application conserve la priorité audio.")
        }
        _running.value = wantsPlayback && !focusBlocked && focusGranted
        players.forEach { (index, player) ->
            player.volume = if (index == _focus.value && _running.value) levels.video else 0f
            player.playWhenReady = _running.value
        }
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
        audio.release()
    }
}
