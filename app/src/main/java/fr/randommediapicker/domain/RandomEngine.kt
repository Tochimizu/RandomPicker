package fr.randommediapicker.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

data class RandomSelection(val mediaId: String, val cycleNumber: Long)

interface RandomEngine {
    suspend fun initialize(eligibleMediaIds: List<String>, mode: RandomMode)
    suspend fun next(excludedVisibleIds: Set<String> = emptySet()): RandomSelection?
    suspend fun nextBatch(count: Int, excludedVisibleIds: Set<String> = emptySet()): List<RandomSelection>
    suspend fun reset()
}

/** One synchronized distributor per session. A batch holds the same lock throughout. */
class SessionRandomEngine(private val random: Random = Random.Default) : RandomEngine {
    private val mutex = Mutex()
    private var ids = emptyList<String>()
    private var mode = RandomMode.SHUFFLE
    private var queue = mutableListOf<String>()
    private var epoch = 0L
    private val recent = ArrayDeque<String>()
    private val randomExclusions = mutableSetOf<String>()

    override suspend fun initialize(eligibleMediaIds: List<String>, mode: RandomMode) = mutex.withLock {
        ids = eligibleMediaIds.distinct()
        this.mode = mode
        queue.clear()
        recent.clear()
        randomExclusions.clear()
        epoch = 0
        // Start the cycle now so an exclusion made immediately after a mode change
        // can remove an item before the first subsequent cell transition.
        if (mode == RandomMode.SHUFFLE && ids.isNotEmpty()) {
            queue = ids.shuffled(random).toMutableList()
            epoch = 1
        }
    }

    private fun draw(excluded: Set<String>): RandomSelection? {
        if (ids.isEmpty()) return null
        val id = if (mode == RandomMode.PURE_RANDOM) {
            val pool = ids.filterNot { it in randomExclusions }
            if (pool.isEmpty()) return null
            val alternatives = pool.filterNot { it in excluded }
            (alternatives.ifEmpty { pool }).random(random)
        } else {
            if (queue.isEmpty()) {
                epoch++
                queue = ids.shuffled(random).toMutableList()
                // Favor files absent from the preceding boundary, without dropping entries.
                val memory = recent.toSet()
                queue.sortBy { if (it in memory) 1 else 0 }
            }
            val available = queue.indexOfFirst { it !in excluded }
            // Preserve the once-per-cycle guarantee when the remaining file is already visible.
            queue.removeAt(if (available >= 0) available else 0)
        }
        recent.addLast(id)
        while (recent.size > minOf(4, (ids.size - 1).coerceAtLeast(0))) recent.removeFirst()
        return RandomSelection(id, epoch)
    }

    override suspend fun next(excludedVisibleIds: Set<String>): RandomSelection? = mutex.withLock {
        draw(excludedVisibleIds)
    }

    override suspend fun nextBatch(count: Int, excludedVisibleIds: Set<String>): List<RandomSelection> = mutex.withLock {
        val visible = excludedVisibleIds.toMutableSet()
        buildList {
            repeat(count.coerceAtLeast(0)) {
                val pick = draw(visible) ?: return@repeat
                add(pick)
                visible.add(pick.mediaId)
            }
        }
    }

    /** In Random mode there is no natural epoch; Reset explicitly starts the next cycle. */
    suspend fun excludeForCurrentCycle(id: String) = mutex.withLock {
        if (mode == RandomMode.SHUFFLE) queue.removeAll { it == id }
        else randomExclusions.add(id)
        Unit
    }

    override suspend fun reset() = mutex.withLock { queue.clear(); recent.clear(); randomExclusions.clear(); epoch = 0L }
}
