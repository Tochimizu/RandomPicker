package fr.randommediapicker.domain

/** A bounded timeline. Callers store identifiers only, never decoded media or players. */
class NavigationHistory<T>(private val backLimit: Int) {
    init { require(backLimit > 0) }
    private val entries = mutableListOf<T>()
    private var cursor = -1
    val size get() = entries.size
    val current get() = entries.getOrNull(cursor)
    val isPast get() = cursor >= 0 && cursor < entries.lastIndex

    fun clear() { entries.clear(); cursor = -1 }

    fun record(value: T) {
        if (current == value) return
        while (entries.lastIndex > cursor) entries.removeAt(entries.lastIndex)
        entries.add(value)
        while (entries.size > backLimit + 1) entries.removeAt(0)
        cursor = entries.lastIndex
    }

    data class Move<T>(val value: T?, val skipped: Int)

    /** Invalid states are discarded without disturbing the currently displayed state. */
    fun move(forward: Boolean, valid: (T) -> Boolean = { true }): Move<T> {
        val direction = if (forward) 1 else -1
        var candidate = cursor + direction
        var skipped = 0
        while (candidate in entries.indices) {
            val value = entries[candidate]
            if (valid(value)) {
                cursor = candidate
                return Move(value, skipped)
            }
            entries.removeAt(candidate)
            if (candidate < cursor) cursor--
            if (!forward) candidate--
            skipped++
        }
        return Move(null, skipped)
    }
}
