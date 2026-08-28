package app.terminalssh.secure.ssh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalInputTest {
    private val controller = TerminalInputController()

    @Test fun initialStateHasNoModifiersActive() {
        assertEquals(TerminalModifiers(), controller.modifiers.value)
        assertFalse(controller.isCtrlActive())
        assertFalse(controller.isAltActive())
        assertFalse(controller.isShiftActive())
    }

    @Test fun initialModifierMaskIsZero() {
        assertEquals(0, controller.modifierMask())
    }

    @Test fun toggleCtrlSetsIsCtrlActiveOnly() {
        controller.toggle(TerminalModifier.CTRL)
        assertTrue(controller.isCtrlActive())
        assertFalse(controller.isAltActive())
        assertFalse(controller.isShiftActive())
        assertEquals(TerminalModifiers(ctrl = true), controller.modifiers.value)
    }

    @Test fun toggleAltSetsIsAltActiveOnly() {
        controller.toggle(TerminalModifier.ALT)
        assertFalse(controller.isCtrlActive())
        assertTrue(controller.isAltActive())
        assertFalse(controller.isShiftActive())
        assertEquals(TerminalModifiers(alt = true), controller.modifiers.value)
    }

    @Test fun toggleShiftSetsIsShiftActiveOnly() {
        controller.toggle(TerminalModifier.SHIFT)
        assertFalse(controller.isCtrlActive())
        assertFalse(controller.isAltActive())
        assertTrue(controller.isShiftActive())
        assertEquals(TerminalModifiers(shift = true), controller.modifiers.value)
    }

    @Test fun modifierMaskForNoModifiers() {
        assertEquals(0, controller.modifierMask())
    }

    @Test fun modifierMaskForShiftOnly() {
        controller.toggle(TerminalModifier.SHIFT)
        assertEquals(TerminalInputController.VTERM_MOD_SHIFT, controller.modifierMask())
    }

    @Test fun modifierMaskForAltOnly() {
        controller.toggle(TerminalModifier.ALT)
        assertEquals(TerminalInputController.VTERM_MOD_ALT, controller.modifierMask())
    }

    @Test fun modifierMaskForCtrlOnly() {
        controller.toggle(TerminalModifier.CTRL)
        assertEquals(TerminalInputController.VTERM_MOD_CTRL, controller.modifierMask())
    }

    @Test fun modifierMaskForShiftAlt() {
        controller.toggle(TerminalModifier.SHIFT)
        controller.toggle(TerminalModifier.ALT)
        assertEquals(
            TerminalInputController.VTERM_MOD_SHIFT or TerminalInputController.VTERM_MOD_ALT,
            controller.modifierMask(),
        )
    }

    @Test fun modifierMaskForShiftCtrl() {
        controller.toggle(TerminalModifier.SHIFT)
        controller.toggle(TerminalModifier.CTRL)
        assertEquals(
            TerminalInputController.VTERM_MOD_SHIFT or TerminalInputController.VTERM_MOD_CTRL,
            controller.modifierMask(),
        )
    }

    @Test fun modifierMaskForAltCtrl() {
        controller.toggle(TerminalModifier.ALT)
        controller.toggle(TerminalModifier.CTRL)
        assertEquals(
            TerminalInputController.VTERM_MOD_ALT or TerminalInputController.VTERM_MOD_CTRL,
            controller.modifierMask(),
        )
    }

    @Test fun modifierMaskForShiftAltCtrl() {
        controller.toggle(TerminalModifier.SHIFT)
        controller.toggle(TerminalModifier.ALT)
        controller.toggle(TerminalModifier.CTRL)
        assertEquals(
            TerminalInputController.VTERM_MOD_SHIFT or
                TerminalInputController.VTERM_MOD_ALT or
                TerminalInputController.VTERM_MOD_CTRL,
            controller.modifierMask(),
        )
    }

    @Test fun togglingTwiceReturnsToInactive() {
        controller.toggle(TerminalModifier.CTRL)
        controller.toggle(TerminalModifier.CTRL)
        assertFalse(controller.isCtrlActive())
        assertEquals(TerminalModifiers(), controller.modifiers.value)
    }

    @Test fun togglingThriceLeavesActive() {
        controller.toggle(TerminalModifier.SHIFT)
        controller.toggle(TerminalModifier.SHIFT)
        controller.toggle(TerminalModifier.SHIFT)
        assertTrue(controller.isShiftActive())
        assertEquals(TerminalModifiers(shift = true), controller.modifiers.value)
    }

    @Test fun togglingOneModifierDoesNotAffectOthers() {
        controller.toggle(TerminalModifier.ALT)
        controller.toggle(TerminalModifier.CTRL)
        controller.toggle(TerminalModifier.CTRL)
        assertTrue(controller.isAltActive())
        assertFalse(controller.isCtrlActive())
        assertFalse(controller.isShiftActive())
        assertEquals(TerminalModifiers(alt = true), controller.modifiers.value)
    }

    @Test fun clearTransientsResetsAllModifiers() {
        controller.toggle(TerminalModifier.CTRL)
        controller.toggle(TerminalModifier.ALT)
        controller.toggle(TerminalModifier.SHIFT)
        controller.clearTransients()
        assertEquals(TerminalModifiers(), controller.modifiers.value)
        assertFalse(controller.isCtrlActive())
        assertFalse(controller.isAltActive())
        assertFalse(controller.isShiftActive())
        assertEquals(0, controller.modifierMask())
    }

    @Test fun clearTransientsOnAlreadyClearStateIsNoop() {
        controller.clearTransients()
        assertEquals(TerminalModifiers(), controller.modifiers.value)
    }

    @Test fun terminalModifierHasExactlyThreeValues() {
        assertEquals(
            listOf(TerminalModifier.CTRL, TerminalModifier.ALT, TerminalModifier.SHIFT),
            TerminalModifier.values().toList(),
        )
    }

    @Test fun everyTerminalModifierValueTogglesItsOwnFlagIndependently() {
        for (modifier in TerminalModifier.values()) {
            val fresh = TerminalInputController()
            fresh.toggle(modifier)
            val modifiers = fresh.modifiers.value
            when (modifier) {
                TerminalModifier.CTRL -> assertEquals(TerminalModifiers(ctrl = true), modifiers)
                TerminalModifier.ALT -> assertEquals(TerminalModifiers(alt = true), modifiers)
                TerminalModifier.SHIFT -> assertEquals(TerminalModifiers(shift = true), modifiers)
            }
        }
    }
}
