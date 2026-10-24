package fr.randommediapicker.media

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList

class GridResourceManager(context: Context) {
    val historyBackLimit = if (context.getSystemService(ActivityManager::class.java).isLowRamDevice) 5 else 10
    val videoBudget: Int = runCatching {
        if (context.getSystemService(ActivityManager::class.java).isLowRamDevice) 1 else {
            val instances = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.isHardwareAccelerated && "video/avc" in it.supportedTypes }
                .maxOfOrNull { it.getCapabilitiesForType("video/avc").maxSupportedInstances } ?: 1
            instances.coerceIn(1, 2)
        }
    }.getOrDefault(1)
}
