package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolbarEditorTest {

    private fun key(id: String, action: ToolbarAction = TextAction(id)) =
        ToolKeySpec(id = id, label = id, action = action)

    private fun profile(vararg rows: List<ToolKeySpec>): ToolbarProfile = ToolbarProfile(
        id = "p", name = "P", description = "", iconToken = "",
        themeId = ToolbarThemes.default.id,
        layout = ToolbarLayout(rows = rows.toList()),
    )

    private val base = profile(listOf(key("a"), key("b")), listOf(key("c")))

    @Test fun addKeyInsertsIntoRequestedRow() {
        val result = ToolbarEditor.addKey(base, 0, key("z"))
        assertEquals(listOf("a", "b", "z"), result.layout.rows[0].map { it.id })
    }

    @Test fun addKeyRejectsDuplicateIdAndIgnoresOutOfRangeRow() {
        assertEquals(base, ToolbarEditor.addKey(base, 0, key("a")))
        val clamped = ToolbarEditor.addKey(base, 99, key("z"))
        assertEquals(listOf("c", "z"), clamped.layout.rows[1].map { it.id })
    }

    @Test fun appendKeyGoesToLastRow() {
        assertEquals(listOf("c", "z"), ToolbarEditor.appendKey(base, key("z")).layout.rows[1].map { it.id })
    }

    @Test fun removeKeyDropsItAndEmptyRows() {
        val result = ToolbarEditor.removeKey(base, "c")
        assertEquals(1, result.layout.rows.size)
        assertEquals(listOf("a", "b"), result.layout.rows[0].map { it.id })
    }

    @Test fun removeKeyRefusesToEmptyTheWholeLayout() {
        val single = profile(listOf(key("only")))
        assertEquals(single, ToolbarEditor.removeKey(single, "only"))
    }

    @Test fun duplicateKeyGetsAUniqueId() {
        val result = ToolbarEditor.duplicateKey(base, "a")
        val ids = result.layout.keys.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("a_copy" in ids)
    }

    @Test fun moveKeyWithinARowReorders() {
        val two = profile(listOf(key("a"), key("b"), key("c")))
        val result = ToolbarEditor.moveKey(two, 0, 0, 0, 2)
        assertEquals(listOf("b", "c", "a"), result.layout.rows[0].map { it.id })
    }

    @Test fun moveKeyAcrossRows() {
        val result = ToolbarEditor.moveKey(base, 0, 0, 1, 0)
        assertEquals(listOf("b"), result.layout.rows[0].map { it.id })
        assertEquals(listOf("a", "c"), result.layout.rows[1].map { it.id })
    }

    @Test fun moveKeyWithBadIndexIsNoOp() {
        assertEquals(base, ToolbarEditor.moveKey(base, 5, 0, 0, 0))
        assertEquals(base, ToolbarEditor.moveKey(base, 0, 9, 0, 0))
        assertEquals(base, ToolbarEditor.moveKey(base, 0, 0, 9, 0))
    }

    @Test fun moveKeyByWalksFlattenedOrderAndRepacks() {
        val flat = profile(listOf(key("a"), key("b"), key("c"), key("d")))
        val result = ToolbarEditor.moveKeyBy(flat, "a", 1)
        assertEquals(listOf("b", "a", "c", "d"), result.layout.rows[0].map { it.id })
    }

    @Test fun moveKeyToOtherRowMovesForwardThenBack() {
        val forward = ToolbarEditor.moveKeyToOtherRow(base, "a")
        assertEquals(listOf("b"), forward.layout.rows[0].map { it.id })
        assertEquals(listOf("a", "c"), forward.layout.rows[1].map { it.id })
        // Moving a key back lands it at the head of the previous row.
        val back = ToolbarEditor.moveKeyToOtherRow(forward, "c")
        assertEquals(listOf("c", "b"), back.layout.rows[0].map { it.id })
        assertEquals(listOf("a"), back.layout.rows[1].map { it.id })
    }

    @Test fun renameTrimsClampsAndRejectsEmpty() {
        assertEquals("Hi", ToolbarEditor.renameKey(base, "a", "  Hi  ").layout.keyById("a")!!.label)
        val long = ToolbarEditor.renameKey(base, "a", "x".repeat(100)).layout.keyById("a")!!.label
        assertEquals(ToolKeySpec.MAX_LABEL, long.length)
        assertEquals(base, ToolbarEditor.renameKey(base, "a", "   "))
    }

    @Test fun togglesAndActionEditsApply() {
        assertFalse(ToolbarEditor.setEnabled(base, "a", false).layout.keyById("a")!!.enabled)
        assertTrue(ToolbarEditor.setPinned(base, "a", true).layout.keyById("a")!!.pinned)
        assertEquals(KeyWidth.WIDE, ToolbarEditor.setWidth(base, "a", KeyWidth.WIDE).layout.keyById("a")!!.width)
        assertEquals(
            ControlAction('C'),
            ToolbarEditor.setAction(base, "a", ControlAction('C')).layout.keyById("a")!!.action,
        )
    }

    @Test fun gesturesBindAndClear() {
        val bound = ToolbarEditor.bindGesture(base, "a", ToolbarGesture.LONG_PRESS, ControlAction('D'))
        assertEquals(ControlAction('D'), bound.layout.keyById("a")!!.gestures[ToolbarGesture.LONG_PRESS])
        val cleared = ToolbarEditor.clearGesture(bound, "a", ToolbarGesture.LONG_PRESS)
        assertNull(cleared.layout.keyById("a")!!.gestures[ToolbarGesture.LONG_PRESS])
    }

    @Test fun resetRowAndResetRestoreFromFallback() {
        val edited = ToolbarEditor.renameKey(base, "a", "renamed")
        val fallback = profile(listOf(key("a"), key("b")), listOf(key("c")))
        assertEquals("a", ToolbarEditor.resetRow(edited, 0, fallback).layout.keyById("a")!!.label)
        assertEquals(base.layout, ToolbarEditor.reset(edited, base).layout)
    }

    @Test fun editSessionTracksDirtyUndoRedo() {
        val session = ToolbarEditSession(base)
        assertFalse(session.isDirty)
        assertFalse(session.canUndo)
        session.apply { ToolbarEditor.appendKey(it, key("z")) }
        assertTrue(session.isDirty)
        assertTrue(session.canUndo)
        session.undo()
        assertFalse(session.isDirty)
        assertTrue(session.canRedo)
        session.redo()
        assertTrue(session.isDirty)
        session.reset()
        assertEquals(base, session.current)
        assertFalse(session.canUndo)
    }

    @Test fun editSessionUndoStackIsBounded() {
        val session = ToolbarEditSession(base, undoLimit = 3)
        repeat(6) { i -> session.apply { ToolbarEditor.appendKey(it, key("k$i")) } }
        var undos = 0
        while (session.canUndo) {
            session.undo()
            undos++
        }
        assertEquals(3, undos)
    }

    @Test fun applyWithNoChangeDoesNotTouchHistory() {
        val session = ToolbarEditSession(base)
        session.apply { it }
        assertFalse(session.canUndo)
        assertFalse(session.isDirty)
    }

    @Test fun duplicateIdHelperFindsASlot() {
        assertEquals("x", ToolbarEditor.uniqueId(base.layout, "x"))
        assertEquals("a2", ToolbarEditor.uniqueId(base.layout, "a"))
    }

    @Test fun everyShippedProfileIsValid() {
        ToolbarProfiles.all.forEach { profile ->
            val problems = ToolbarValidator.validate(ToolbarConfigState(customProfiles = listOf(profile)))
            assertTrue(problems.isEmpty(), "profile ${profile.id}: $problems")
        }
        assertNotEquals(ToolbarProfiles.all.size, 0)
    }

    @Test fun shippedProfileKeysNeverContainEmptyTextActions() {
        ToolbarProfiles.all.flatMap { it.layout.keys + it.optionalKeys }.forEach { key ->
            val action = key.action
            assertFalse(
                action is TextAction && action.text.isEmpty(),
                "profile key ${key.id} (${key.label}) must not be a no-op empty text action",
            )
        }
    }

    @Test fun functionKeysThroughF12AreAvailableInThePalette() {
        val functionKeys = ToolbarKeyPalette.entries
            .mapNotNull { it.action as? SpecialKeyAction }
            .map { it.key }
            .filter { it.name.startsWith("F") }
        assertTrue(
            ToolbarSpecialKey.entries.filter { it.name.matches(Regex("F(?:[1-9]|1[0-2])")) }
                .all { it in functionKeys },
        )
    }
}
