package fr.randommediapicker.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fr.randommediapicker.domain.*

/** Owns system focus for the entire mosaic; individual players must not request it. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioCoordinator(
    context: Context,
    private val onFocus: (blocked: Boolean, duck: Float, permanent: Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val music = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError("Musique illisible : choisissez un autre fichier audio.")
            }
        })
    }
    private var source: String? = null
    private var ownsFocus = false
    private var requested = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> { ownsFocus = true; onFocus(false, 1f, false) }
                AudioManager.AUDIOFOCUS_LOSS -> { ownsFocus = false; onFocus(true, 1f, true) }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { ownsFocus = false; onFocus(true, 1f, false) }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocus(false, .2f, false)
            }
        }.build()

    fun sync(settings: PlaybackSettings, levels: AudioLevels, running: Boolean, requestFocus: Boolean): Boolean {
        if (source != settings.musicUri) {
            source = settings.musicUri
            music.stop()
            music.clearMediaItems()
            source?.let { music.setMediaItem(MediaItem.fromUri(it)); music.prepare() }
        }
        if (requestFocus && !requested) {
            ownsFocus = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            requested = ownsFocus
        } else if (!requestFocus && requested) {
            manager.abandonAudioFocusRequest(request)
            requested = false
            ownsFocus = false
        }
        music.volume = levels.music
        music.playWhenReady = running && levels.playMusic && ownsFocus
        return ownsFocus || !requestFocus
    }

    fun release() {
        music.release()
        manager.abandonAudioFocusRequest(request)
    }
}
