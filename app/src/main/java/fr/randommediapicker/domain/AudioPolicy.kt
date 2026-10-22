package fr.randommediapicker.domain

data class AudioLevels(val music: Float, val video: Float, val playMusic: Boolean)

object AudioPolicy {
    fun levels(settings: PlaybackSettings, hasVideo: Boolean, muted: Boolean, duck: Float = 1f): AudioLevels {
        val master = if (muted) 0f else settings.masterVolume * duck
        val video = hasVideo && settings.videoAudio && settings.audioMode != AudioMode.MUSIC_ONLY
        val music = settings.musicEnabled && settings.musicUri != null && settings.audioMode != AudioMode.VIDEO_ONLY
        val suspendMusic = video && settings.audioMode == AudioMode.VIDEO_PRIORITY
        val musicDuck = if (video && settings.audioMode == AudioMode.DUCKING) .2f else 1f
        return AudioLevels(
            if (music && !suspendMusic) master * settings.musicVolume * musicDuck else 0f,
            if (video) master * settings.videoVolume else 0f,
            music && !suspendMusic,
        )
    }
}
