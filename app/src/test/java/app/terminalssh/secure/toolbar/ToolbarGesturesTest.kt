package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolbarGesturesTest {

    private fun key(
        action: ToolbarAction = SpecialKeyAction(ToolbarSpecialKey.ENTER),
        gestures: Map<ToolbarGesture, ToolbarAction> = emptyMap(),
        repeat: Boolean = false,
        enabled: Boolean = true,
    ) = ToolKeySpec(id = "k", label = "K", action = action, gestures = gestures, repeatWhileHeld = repeat, enabled = enabled)

    @Test fun unboundGestureResolvesToNull() {
        assertNull(ToolbarGestures.resolve(key(), ToolbarGesture.LONG_PRESS))
    }

    @Test fun boundGestureResolvesToItsAction() {
        val bound = key(gestures = mapOf(ToolbarGesture.SWIPE_LEFT to ControlAction('C')))
        assertEquals(ControlAction('C'), ToolbarGestures.resolve(bound, ToolbarGesture.SWIPE_LEFT))
        assertEquals(SpecialKeyAction(ToolbarSpecialKey.ENTER), ToolbarGestures.tapAction(bound))
    }

    @Test fun repeatIsAllowedOnlyForPlainTerminalInput() {
        assertTrue(ToolbarGestures.canRepeat(SpecialKeyAction(ToolbarSpecialKey.LEFT)))
        assertTrue(ToolbarGestures.canRepeat(TextAction("x")))
        assertFalse(ToolbarGestures.canRepeat(MacroAction("m")))
        assertFalse(ToolbarGestures.canRepeat(PasteAction))
        assertFalse(ToolbarGestures.canRepeat(OpenSettingsAction))
        assertFalse(ToolbarGestures.canRepeat(ChangeFontSizeAction(1)))
    }

    @Test fun gesturesMatchingThePrimaryActionAreFlagged() {
        val conflicts = ToolbarGestures.conflicts(
            key(gestures = mapOf(ToolbarGesture.DOUBLE_TAP to SpecialKeyAction(ToolbarSpecialKey.ENTER))),
        )
        assertTrue(conflicts.any { it.contains("repeats the primary") })
    }

    @Test fun pickerRejectsPrimaryActionAndLongPressOnRepeatingKey() {
        val primary = SpecialKeyAction(ToolbarSpecialKey.ENTER)
        val key = key(action = primary)
        assertFalse(ToolbarGestures.canBind(key, ToolbarGesture.DOUBLE_TAP, primary))
        assertTrue(ToolbarGestures.canBind(key, ToolbarGesture.DOUBLE_TAP, ControlAction('C')))

        val repeating = key(action = SpecialKeyAction(ToolbarSpecialKey.LEFT), repeat = true)
        assertFalse(ToolbarGestures.canBind(repeating, ToolbarGesture.LONG_PRESS, ControlAction('C')))
        assertTrue(ToolbarGestures.canBind(repeating, ToolbarGesture.SWIPE_UP, ControlAction('C')))
    }

    @Test fun repeatPlusLongPressIsFlagged() {
        val conflicts = ToolbarGestures.conflicts(
            key(
                action = SpecialKeyAction(ToolbarSpecialKey.LEFT),
                gestures = mapOf(ToolbarGesture.LONG_PRESS to ControlAction('C')),
                repeat = true,
            ),
        )
        assertTrue(conflicts.any { it.contains("repeat while held") })
    }

    @Test fun repeatingANonRepeatingActionIsFlagged() {
        val conflicts = ToolbarGestures.conflicts(key(action = OpenSettingsAction, repeat = true))
        assertTrue(conflicts.any { it.contains("cannot repeat") })
    }

    @Test fun coherentKeyHasNoConflicts() {
        assertTrue(ToolbarGestures.conflicts(key(gestures = mapOf(ToolbarGesture.LONG_PRESS to ControlAction('C')))).isEmpty())
    }

    @Test fun disabledKeyIsNotPoliced() {
        assertTrue(ToolbarGestures.conflicts(key(action = OpenSettingsAction, repeat = true, enabled = false)).isEmpty())
    }

    @Test fun validatorCatchesMissingReferences() {
        val ghostProfile = ToolbarProfile(
            id = "p", name = "P", description = "", iconToken = "", themeId = "no_theme",
            macroIds = listOf("no_macro"),
            layout = ToolbarLayout(rows = listOf(listOf(ToolKeySpec("k", "K", MacroAction("no_macro"))))),
        )
        val state = ToolbarConfigState(
            activeProfileId = "ghost",
            customProfiles = listOf(ghostProfile),
            hostOverrides = mapOf("host" to "also_ghost"),
        )
        val problems = ToolbarValidator.validate(state)
        assertTrue(problems.any { it.contains("active profile") })
        assertTrue(problems.any { it.contains("missing theme") })
        assertTrue(problems.any { it.contains("missing macro") || it.contains("uses missing macro") })
        assertTrue(problems.any { it.contains("host override") })
    }

    @Test fun validatorAcceptsTheDefaultState() {
        assertTrue(ToolbarValidator.validate(ToolbarConfigState.default()).isEmpty())
    }

    @Test fun validatorFlagsDuplicateCustomIds() {
        val dup = ToolbarProfiles.shell
        val state = ToolbarConfigState(customProfiles = listOf(dup, dup))
        assertTrue(ToolbarValidator.validate(state).any { it.contains("duplicate custom profile") })
    }
}
