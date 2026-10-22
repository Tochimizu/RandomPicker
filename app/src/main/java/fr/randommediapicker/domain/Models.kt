package fr.randommediapicker.domain

enum class MediaKind { IMAGE, ANIMATED, VIDEO }
enum class RandomMode { PURE_RANDOM, SHUFFLE }
enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class VideoMode { COMPLETE, LIMITED }
enum class AudioMode { MUSIC_ONLY, VIDEO_ONLY, VIDEO_PRIORITY, DUCKING, MIX }
enum class ScaleMode { FIT, CROP }
enum class RotationMode { AUTO, PORTRAIT, LANDSCAPE }
enum class SelectionOverride { INHERIT, INCLUDE, EXCLUDE }
enum class FolderCheck { INCLUDED, EXCLUDED, PARTIAL }

data class PlaybackSettings(
    val gridSize: Int = 3,
    val randomMode: RandomMode = RandomMode.SHUFFLE,
    val imageSeconds: Int = 5,
    val gifSeconds: Int = 8,
    val videoMode: VideoMode = VideoMode.COMPLETE,
    val videoMaxSeconds: Int = 30,
    val audioMode: AudioMode = AudioMode.VIDEO_ONLY,
    val automaticAudio: Boolean = true,
    val videoAudio: Boolean = true,
    val masterVolume: Float = 1f,
    val musicVolume: Float = .7f,
    val videoVolume: Float = 1f,
    val musicUri: String? = null,
    val musicEnabled: Boolean = false,
    val keepScreenOn: Boolean = true,
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val fade: Boolean = true,
    val rotation: RotationMode = RotationMode.AUTO,
    val theme: ThemeMode = ThemeMode.DARK,
)

data class FolderNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val depth: Int,
    val directCount: Int = 0,
    val totalCount: Int = 0,
    val selection: SelectionOverride = SelectionOverride.INHERIT,
)

/** Iterative ancestry evaluation: no call-stack limit for deep document trees. */
object FolderSelection {
    fun effective(folders: List<FolderNode>): Map<String, Boolean> {
        val byId = folders.associateBy { it.id }
        val result = mutableMapOf<String, Boolean>()
        for (folder in folders) {
            val path = mutableListOf<FolderNode>()
            val visited = mutableSetOf<String>()
            var cursor: FolderNode? = folder
            var included = true
            while (cursor != null && visited.add(cursor.id)) {
                val cached = result[cursor.id]
                if (cached != null) { included = cached; break }
                if (cursor.selection != SelectionOverride.INHERIT) {
                    included = cursor.selection == SelectionOverride.INCLUDE
                    break
                }
                path.add(cursor)
                cursor = cursor.parentId?.let(byId::get)
            }
            path.forEach { result[it.id] = included }
            result[folder.id] = included
        }
        return result
    }

    fun checks(folders: List<FolderNode>): Map<String, FolderCheck> {
        val selected = effective(folders)
        val checks = selected.mapValues { if (it.value) FolderCheck.INCLUDED else FolderCheck.EXCLUDED }.toMutableMap()
        for (folder in folders.sortedByDescending { it.depth }) {
            val parent = folder.parentId ?: continue
            if (checks[parent] != checks[folder.id]) checks[parent] = FolderCheck.PARTIAL
        }
        return checks
    }

    fun descendants(id: String, folders: List<FolderNode>): Set<String> {
        val children = folders.groupBy { it.parentId }
        val pending = ArrayDeque<String>().apply { add(id) }
        val result = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (result.add(next)) children[next].orEmpty().forEach { pending.add(it.id) }
        }
        return result
    }
}
