package fr.randommediapicker

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import fr.randommediapicker.data.*

class PickerApplication : Application(), ImageLoaderFactory {
    val database by lazy { Room.databaseBuilder(this, LibraryDatabase::class.java, "library.db").build() }
    val library by lazy { LibraryRepository(this, database) }
    val settings by lazy { SettingsRepository(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()); add(VideoFrameDecoder.Factory()) }
        .respectCacheHeaders(false)
        .build()
}
