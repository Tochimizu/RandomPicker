package fr.randommediapicker.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import fr.randommediapicker.domain.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("playback_settings")

class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    private inline fun <reified E : Enum<E>> Preferences.enum(key: String, default: E): E =
        this[stringPreferencesKey(key)]?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: default

    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        PlaybackSettings(
            gridSize = (p[intPreferencesKey("grid")] ?: 3).coerceIn(1, 4),
            randomMode = p.enum("random", RandomMode.SHUFFLE),
            imageSeconds = (p[intPreferencesKey("image")] ?: 5).coerceIn(1, 120),
            gifSeconds = (p[intPreferencesKey("gif")] ?: 8).coerceIn(1, 120),
            videoMode = p.enum("video_mode", VideoMode.COMPLETE),
            videoMaxSeconds = (p[intPreferencesKey("video_seconds")] ?: 30).coerceIn(1, 120),
            audioMode = p.enum("audio_mode", AudioMode.VIDEO_ONLY),
            automaticAudio = p[booleanPreferencesKey("auto_audio")] ?: true,
            videoAudio = p[booleanPreferencesKey("video_audio")] ?: true,
            masterVolume = (p[floatPreferencesKey("master")] ?: 1f).coerceIn(0f, 1f),
            musicVolume = (p[floatPreferencesKey("music_volume")] ?: .7f).coerceIn(0f, 1f),
            videoVolume = (p[floatPreferencesKey("video_volume")] ?: 1f).coerceIn(0f, 1f),
            musicUri = p[stringPreferencesKey("music_uri")],
            musicEnabled = p[booleanPreferencesKey("music_enabled")] ?: false,
            keepScreenOn = p[booleanPreferencesKey("screen")] ?: true,
            scaleMode = p.enum("scale", ScaleMode.FIT),
            fade = p[booleanPreferencesKey("fade")] ?: true,
            rotation = p.enum("rotation", RotationMode.AUTO),
            theme = p.enum("theme", ThemeMode.DARK),
        )
    }

    /** Transform inside DataStore's transaction so concurrent edits cannot lose preferences. */
    suspend fun update(transform: (PlaybackSettings) -> PlaybackSettings) {
        store.edit { p ->
            val current = PlaybackSettings(
                gridSize = p[intPreferencesKey("grid")] ?: 3,
                randomMode = p.enum("random", RandomMode.SHUFFLE),
                imageSeconds = p[intPreferencesKey("image")] ?: 5,
                gifSeconds = p[intPreferencesKey("gif")] ?: 8,
                videoMode = p.enum("video_mode", VideoMode.COMPLETE),
                videoMaxSeconds = p[intPreferencesKey("video_seconds")] ?: 30,
                audioMode = p.enum("audio_mode", AudioMode.VIDEO_ONLY),
                automaticAudio = p[booleanPreferencesKey("auto_audio")] ?: true,
                videoAudio = p[booleanPreferencesKey("video_audio")] ?: true,
                masterVolume = p[floatPreferencesKey("master")] ?: 1f,
                musicVolume = p[floatPreferencesKey("music_volume")] ?: .7f,
                videoVolume = p[floatPreferencesKey("video_volume")] ?: 1f,
                musicUri = p[stringPreferencesKey("music_uri")],
                musicEnabled = p[booleanPreferencesKey("music_enabled")] ?: false,
                keepScreenOn = p[booleanPreferencesKey("screen")] ?: true,
                scaleMode = p.enum("scale", ScaleMode.FIT),
                fade = p[booleanPreferencesKey("fade")] ?: true,
                rotation = p.enum("rotation", RotationMode.AUTO),
                theme = p.enum("theme", ThemeMode.DARK),
            )
            val s = transform(current)
            p[intPreferencesKey("grid")] = s.gridSize.coerceIn(1, 4)
            p[stringPreferencesKey("random")] = s.randomMode.name
            p[intPreferencesKey("image")] = s.imageSeconds.coerceIn(1, 120)
            p[intPreferencesKey("gif")] = s.gifSeconds.coerceIn(1, 120)
            p[stringPreferencesKey("video_mode")] = s.videoMode.name
            p[intPreferencesKey("video_seconds")] = s.videoMaxSeconds.coerceIn(1, 120)
            p[stringPreferencesKey("audio_mode")] = s.audioMode.name
            p[booleanPreferencesKey("auto_audio")] = s.automaticAudio
            p[booleanPreferencesKey("video_audio")] = s.videoAudio
            p[floatPreferencesKey("master")] = s.masterVolume.coerceIn(0f, 1f)
            p[floatPreferencesKey("music_volume")] = s.musicVolume.coerceIn(0f, 1f)
            p[floatPreferencesKey("video_volume")] = s.videoVolume.coerceIn(0f, 1f)
            if (s.musicUri == null) p.remove(stringPreferencesKey("music_uri")) else p[stringPreferencesKey("music_uri")] = s.musicUri
            p[booleanPreferencesKey("music_enabled")] = s.musicEnabled
            p[booleanPreferencesKey("screen")] = s.keepScreenOn
            p[stringPreferencesKey("scale")] = s.scaleMode.name
            p[booleanPreferencesKey("fade")] = s.fade
            p[stringPreferencesKey("rotation")] = s.rotation.name
            p[stringPreferencesKey("theme")] = s.theme.name
        }
    }
}
