package app.terminalssh.secure.terminal

/**
 * The pure half of split terminal panes (F031).
 *
 * The emulator can only render one buffer today, so the feature as a whole is still blocked: this
 * is the layout tree and the focus arithmetic, not the second buffer. It exists as a tested engine
 * so the eventual renderer has something correct to draw — split and close that keep the tree
 * valid, a resize that cannot collapse a pane to zero, and focus moves that always land on a real
 * pane.
 *
 * A split is binary. Repeating "split in two" gives the same result as a grid, and a binary tree
 * is what a resizable layout wants anyway: dragging a divider only ever changes one ratio.
 */
sealed interface PaneNode {

    /** Pane ids in visual order (depth-first, first before second). */
    val leaves: List<String>

    data class Leaf(val id: String) : PaneNode {
        override val leaves: List<String> get() = listOf(id)
    }

    data class Split(
        val orientation: Orientation,
        /** Share of the first child, always clamped to `[MIN_RATIO, 1 - MIN_RATIO]`. */
        val ratio: Float,
        val first: PaneNode,
        val second: PaneNode,
    ) : PaneNode {
        override val leaves: List<String> get() = first.leaves + second.leaves
    }

    enum class Orientation {
        /** Children side by side (a vertical divider). */
        HORIZONTAL,

        /** Children stacked (a horizontal divider). */
        VERTICAL,
    }
}

object PaneLayout {

    /** Neither side of a divider may be dragged below this share of the space. */
    const val MIN_RATIO = 0.15f

    /** Beyond a handful, panes are too small to be readable on a phone. */
    const val MAX_PANES = 6

    /**
     * Splits [targetId] in two, placing [newId] after the (new) first half.
     * Returns null when the target is absent, the id is already used, or [MAX_PANES] is reached.
     */
    fun split(
        root: PaneNode,
        targetId: String,
        newId: String,
        orientation: PaneNode.Orientation,
    ): PaneNode? {
        if (newId.isBlank() || newId == targetId) return null
        if (newId in root.leaves) return null
        if (root.leaves.size >= MAX_PANES) return null
        return replace(root, targetId) { leaf ->
            PaneNode.Split(orientation, 0.5f, leaf, PaneNode.Leaf(newId))
        }
    }

    /**
     * Removes a pane; its sibling takes the free space. Returns null when [id] is absent or is the
     * only pane left (closing the last pane would leave nothing to show).
     */
    fun close(root: PaneNode, id: String): PaneNode? {
        if (id !in root.leaves) return null
        if (root.leaves.size <= 1) return null
        return remove(root, id)
    }

    /**
     * Sets the ratio of the split selected by [path] (`true` = first child, `false` = second;
     * empty = this node), clamped so no pane vanishes. A path that lands on a leaf has no effect.
     */
    fun resize(root: PaneNode, path: List<Boolean>, newRatio: Float): PaneNode =
        updateAt(root, path) { node ->
            if (node is PaneNode.Split) node.copy(ratio = clampRatio(newRatio)) else node
        }

    fun clampRatio(ratio: Float): Float = ratio.coerceIn(MIN_RATIO, 1f - MIN_RATIO)

    /** Next pane in visual order, wrapping around. An unknown [currentId] lands on the first pane. */
    fun focusNext(root: PaneNode, currentId: String): String {
        val ids = root.leaves
        if (ids.isEmpty()) return currentId
        val index = ids.indexOf(currentId)
        return if (index < 0) ids.first() else ids[(index + 1) % ids.size]
    }

    /** Previous pane in visual order, wrapping around. An unknown [currentId] lands on the last pane. */
    fun focusPrev(root: PaneNode, currentId: String): String {
        val ids = root.leaves
        if (ids.isEmpty()) return currentId
        val index = ids.indexOf(currentId)
        return if (index < 0) ids.last() else ids[(index - 1 + ids.size) % ids.size]
    }

    private fun replace(
        node: PaneNode,
        targetId: String,
        transform: (PaneNode.Leaf) -> PaneNode,
    ): PaneNode? = when (node) {
        is PaneNode.Leaf -> if (node.id == targetId) transform(node) else null
        is PaneNode.Split ->
            replace(node.first, targetId, transform)?.let { node.copy(first = it) }
                ?: replace(node.second, targetId, transform)?.let { node.copy(second = it) }
    }

    private fun remove(node: PaneNode, id: String): PaneNode? = when (node) {
        is PaneNode.Leaf -> null
        is PaneNode.Split -> when {
            node.first is PaneNode.Leaf && node.first.id == id -> node.second
            node.second is PaneNode.Leaf && node.second.id == id -> node.first
            else ->
                remove(node.first, id)?.let { node.copy(first = it) }
                    ?: remove(node.second, id)?.let { node.copy(second = it) }
        }
    }

    private fun updateAt(node: PaneNode, path: List<Boolean>, transform: (PaneNode) -> PaneNode): PaneNode {
        if (path.isEmpty()) return transform(node)
        if (node !is PaneNode.Split) return node
        val intoFirst = path.first()
        val child = if (intoFirst) node.first else node.second
        val updated = updateAt(child, path.drop(1), transform)
        return if (intoFirst) node.copy(first = updated) else node.copy(second = updated)
    }
}
