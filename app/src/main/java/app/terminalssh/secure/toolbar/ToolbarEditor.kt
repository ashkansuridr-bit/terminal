package app.terminalssh.secure.toolbar

/**
 * Pure editing operations on a [ToolbarProfile].
 *
 * Every operation returns a new profile and never throws on a bad index — a drag that
 * lands one row too far is a no-op, not a crash. Keeping this pure means reordering can be
 * tested without a device, which matters because reordering is exactly where a layout
 * editor goes wrong.
 */
object ToolbarEditor {

    fun addKey(profile: ToolbarProfile, rowIndex: Int, key: ToolKeySpec): ToolbarProfile {
        if (profile.layout.keys.size >= ToolbarLayout.MAX_KEYS) return profile
        if (profile.layout.keyById(key.id) != null) return profile
        val rows = profile.layout.rows.map { it.toMutableList() }.toMutableList()
        if (rows.isEmpty()) return profile
        val row = rowIndex.coerceIn(0, rows.lastIndex)
        rows[row].add(key)
        return profile.copy(layout = profile.layout.redraw(rows))
    }

    fun appendKey(profile: ToolbarProfile, key: ToolKeySpec): ToolbarProfile =
        addKey(profile, profile.layout.rows.lastIndex, key)

    /** Removes a key. The last remaining key in the whole layout cannot be removed. */
    fun removeKey(profile: ToolbarProfile, keyId: String): ToolbarProfile {
        if (profile.layout.keys.size <= 1) return profile
        val rows = profile.layout.rows
            .map { row -> row.filterNot { it.id == keyId } }
        return profile.copy(layout = profile.layout.redraw(rows))
    }

    fun duplicateKey(profile: ToolbarProfile, keyId: String): ToolbarProfile {
        val source = profile.layout.keyById(keyId) ?: return profile
        val newId = uniqueId(profile.layout, "${source.id}_copy")
        val copy = source.copy(id = newId)
        return appendKey(profile, copy)
    }

    /** Moves a key to an absolute position. Invalid targets are ignored. */
    fun moveKey(
        profile: ToolbarProfile,
        fromRow: Int,
        fromIndex: Int,
        toRow: Int,
        toIndex: Int,
    ): ToolbarProfile {
        val rows = profile.layout.rows.map { it.toMutableList() }.toMutableList()
        if (fromRow !in rows.indices) return profile
        if (fromIndex !in rows[fromRow].indices) return profile
        if (toRow !in rows.indices) return profile
        val moved = rows[fromRow].removeAt(fromIndex)
        val target = toIndex.coerceIn(0, rows[toRow].size)
        if (fromRow == toRow && target == fromIndex) return profile
        rows[toRow].add(target, moved)
        return profile.copy(layout = profile.layout.redraw(rows))
    }

    /** Moves a key one slot earlier/later within a flattened ordering. Used by ↑/↓ buttons. */
    fun moveKeyBy(profile: ToolbarProfile, keyId: String, delta: Int): ToolbarProfile {
        val flat = profile.layout.keys
        val from = flat.indexOfFirst { it.id == keyId }
        if (from < 0) return profile
        val to = (from + delta).coerceIn(0, flat.lastIndex)
        if (to == from) return profile
        val reordered = flat.toMutableList().apply { add(to, removeAt(from)) }
        return repack(profile, reordered)
    }

    /** Moves a key to the start of the next row (or the previous row when already last). */
    fun moveKeyToOtherRow(profile: ToolbarProfile, keyId: String): ToolbarProfile {
        val rows = profile.layout.rows
        for (r in rows.indices) {
            val idx = rows[r].indexOfFirst { it.id == keyId }
            if (idx < 0) continue
            val target = if (r < rows.lastIndex) r + 1 else r - 1
            if (target !in rows.indices) return profile
            return moveKey(profile, r, idx, target, 0)
        }
        return profile
    }

    fun renameKey(profile: ToolbarProfile, keyId: String, label: String): ToolbarProfile {
        val trimmed = label.trim().take(ToolKeySpec.MAX_LABEL)
        if (trimmed.isEmpty()) return profile
        return mapKey(profile, keyId) { it.copy(label = trimmed) }
    }

    fun setEnabled(profile: ToolbarProfile, keyId: String, enabled: Boolean): ToolbarProfile =
        mapKey(profile, keyId) { it.copy(enabled = enabled) }

    fun setPinned(profile: ToolbarProfile, keyId: String, pinned: Boolean): ToolbarProfile =
        mapKey(profile, keyId) { it.copy(pinned = pinned) }

    fun setWidth(profile: ToolbarProfile, keyId: String, width: KeyWidth): ToolbarProfile =
        mapKey(profile, keyId) { it.copy(width = width) }

    fun setAction(profile: ToolbarProfile, keyId: String, action: ToolbarAction): ToolbarProfile =
        mapKey(profile, keyId) { it.copy(action = action) }

    fun bindGesture(
        profile: ToolbarProfile,
        keyId: String,
        gesture: ToolbarGesture,
        action: ToolbarAction,
    ): ToolbarProfile = mapKey(profile, keyId) { it.copy(gestures = it.gestures + (gesture to action)) }

    fun clearGesture(profile: ToolbarProfile, keyId: String, gesture: ToolbarGesture): ToolbarProfile =
        mapKey(profile, keyId) { it.copy(gestures = it.gestures - gesture) }

    fun resetRow(profile: ToolbarProfile, rowIndex: Int, fallback: ToolbarProfile): ToolbarProfile {
        if (rowIndex !in profile.layout.rows.indices) return profile
        if (rowIndex !in fallback.layout.rows.indices) return profile
        val rows = profile.layout.rows.map { it.toMutableList() }.toMutableList()
        rows[rowIndex] = fallback.layout.rows[rowIndex].toMutableList()
        return profile.copy(layout = profile.layout.redraw(rows))
    }

    fun reset(profile: ToolbarProfile, fallback: ToolbarProfile): ToolbarProfile =
        profile.copy(layout = fallback.layout, themeId = fallback.themeId)

    private fun mapKey(
        profile: ToolbarProfile,
        keyId: String,
        transform: (ToolKeySpec) -> ToolKeySpec,
    ): ToolbarProfile {
        var found = false
        val rows = profile.layout.rows.map { row ->
            row.map { key ->
                if (key.id == keyId) {
                    found = true
                    transform(key)
                } else {
                    key
                }
            }
        }
        return if (found) profile.copy(layout = profile.layout.redraw(rows)) else profile
    }

    /** Redistributes a flat key list back into the profile's current row shape. */
    private fun repack(profile: ToolbarProfile, flat: List<ToolKeySpec>): ToolbarProfile {
        val shape = profile.layout.rows.map { it.size }
        val rows = ArrayList<List<ToolKeySpec>>(shape.size)
        var cursor = 0
        shape.forEach { size ->
            val take = flat.drop(cursor).take(size)
            if (take.isNotEmpty()) rows.add(take)
            cursor += size
        }
        val leftover = flat.drop(cursor)
        if (leftover.isNotEmpty()) rows.add(leftover)
        return profile.copy(layout = profile.layout.redraw(rows))
    }

    fun uniqueId(layout: ToolbarLayout, base: String): String {
        val existing = layout.keys.map { it.id }.toSet()
        if (base !in existing) return base
        var n = 2
        while ("${base}$n" in existing) n++
        return "$base$n"
    }
}

/** Shared guard for starting and completing a SAF import around editor draft changes. */
object ToolbarImportPolicy {
    fun canImport(
        isDirty: Boolean,
        hasPendingMacros: Boolean,
        hasPendingThemes: Boolean,
    ): Boolean = !isDirty && !hasPendingMacros && !hasPendingThemes
}

/**
 * An editor session with undo/redo.
 *
 * Undo/redo keeps whole immutable profiles rather than diffs, so an undo can never leave a
 * half-applied edit behind. The stack is bounded so a long editing session cannot grow
 * without limit.
 */
class ToolbarEditSession(
    private val original: ToolbarProfile,
    private val undoLimit: Int = 40,
) {
    private val undoStack = ArrayDeque<ToolbarProfile>()
    private val redoStack = ArrayDeque<ToolbarProfile>()

    var current: ToolbarProfile = original
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isDirty: Boolean get() = current != original

    fun apply(transform: (ToolbarProfile) -> ToolbarProfile): ToolbarProfile {
        val next = transform(current)
        if (next == current) return current
        undoStack.addLast(current)
        while (undoStack.size > undoLimit) undoStack.removeFirst()
        redoStack.clear()
        current = next
        return current
    }

    fun undo(): ToolbarProfile {
        val previous = undoStack.removeLastOrNull() ?: return current
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): ToolbarProfile {
        val next = redoStack.removeLastOrNull() ?: return current
        undoStack.addLast(current)
        current = next
        return current
    }

    fun reset(): ToolbarProfile {
        undoStack.clear()
        redoStack.clear()
        current = original
        return current
    }
}
