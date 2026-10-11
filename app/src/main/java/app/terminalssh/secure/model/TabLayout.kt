package app.terminalssh.secure.model

/**
 * Tab layout operations for the terminal tab strip (F019).
 *
 * Sessions themselves live elsewhere; this is the pure, reorderable list the strip renders:
 * rename, pin, reorder and close. Pinned tabs float to the front so the one session a user
 * always needs stays under their thumb while the rest churn.
 *
 * The pinned-first invariant is enforced by [normalize] after every mutation, so the render
 * path never has to remember to sort, and a restore from a stale saved order is corrected on
 * load instead of trusting whatever was on disk.
 */
object TabLayout {

    data class Tab(val id: String, val title: String, val pinned: Boolean = false)

    const val MAX_TITLE = 40

    /** Pinned first, then input order — a stable sort so untouched tabs never jump around. */
    fun normalize(tabs: List<Tab>): List<Tab> =
        tabs.sortedByDescending { it.pinned }

    /**
     * Renames a tab. A blank name is rejected (keeps the old one) rather than producing an
     * untappable empty chip, and the title is length-capped.
     */
    fun rename(tabs: List<Tab>, id: String, newTitle: String): List<Tab> {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return tabs
        val capped = if (trimmed.length > MAX_TITLE) trimmed.substring(0, MAX_TITLE) else trimmed
        return tabs.map { if (it.id == id) it.copy(title = capped) else it }
    }

    fun togglePin(tabs: List<Tab>, id: String): List<Tab> =
        normalize(tabs.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })

    /**
     * Moves the tab at [from] so that it lands at [to], then re-establishes the pinned-first
     * invariant. Bad indices are a no-op.
     */
    fun move(tabs: List<Tab>, from: Int, to: Int): List<Tab> {
        if (from !in tabs.indices) return tabs
        val mutable = tabs.toMutableList()
        val moved = mutable.removeAt(from)
        val target = to.coerceIn(0, mutable.size)
        mutable.add(target, moved)
        return normalize(mutable)
    }

    fun close(tabs: List<Tab>, id: String): List<Tab> = tabs.filterNot { it.id == id }

    /**
     * Which tab should be selected after [closedId] is removed. Prefers the tab that slid into
     * the closed slot (the natural "next" tab), falling back to the previous one, then null.
     */
    fun selectionAfterClose(tabs: List<Tab>, closedId: String, currentlySelected: String?): String? {
        if (currentlySelected != closedId) return currentlySelected
        val index = tabs.indexOfFirst { it.id == closedId }
        if (index < 0) return currentlySelected
        val remaining = tabs.filterNot { it.id == closedId }
        if (remaining.isEmpty()) return null
        return remaining.getOrNull(index)?.id ?: remaining.last().id
    }

    /** Whether [id] may be moved to [targetIndex] without breaking the pinned groups. */
    fun canMoveWithinGroup(tabs: List<Tab>, id: String, targetIndex: Int): Boolean {
        val from = tabs.indexOfFirst { it.id == id }
        if (from < 0 || targetIndex !in tabs.indices) return false
        return tabs[from].pinned == tabs[targetIndex].pinned
    }
}
