package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolbarCodecTest {

    private fun roundTrip(state: ToolbarConfigState): ToolbarConfigState {
        val decoded = ToolbarCodec.decode(ToolbarCodec.encode(state))
        assertTrue(decoded is ToolbarDecode.Ok, "expected decode to succeed: $decoded")
        return decoded.state
    }

    @Test fun defaultStateRoundTrips() {
        val state = ToolbarConfigState.default()
        val decoded = roundTrip(state)
        assertEquals(state.activeProfileId, decoded.activeProfileId)
        assertEquals(state.activeThemeId, decoded.activeThemeId)
        assertEquals(state.modifierLockEnabled, decoded.modifierLockEnabled)
        assertTrue(decoded.customProfiles.isEmpty())
    }

    @Test fun everyBuiltInProfileRoundTripsExactly() {
        ToolbarProfiles.all.forEach { profile ->
            val decoded = roundTrip(ToolbarConfigState(customProfiles = listOf(profile)))
            assertEquals(profile, decoded.customProfiles.single(), "profile ${profile.id} changed on round trip")
        }
    }

    @Test fun everyBuiltInThemeRoundTripsExactly() {
        ToolbarThemes.all.forEach { theme ->
            val decoded = roundTrip(ToolbarConfigState(customThemes = listOf(theme)))
            assertEquals(theme, decoded.customThemes.single(), "theme ${theme.id} changed on round trip")
        }
    }

    @Test fun everyBuiltInMacroRoundTripsExactly() {
        ToolbarMacros.all.forEach { macro ->
            val decoded = roundTrip(ToolbarConfigState(customMacros = listOf(macro)))
            assertEquals(macro, decoded.customMacros.single(), "macro ${macro.id} changed on round trip")
        }
    }

    @Test fun everyActionTypeRoundTrips() {
        val actions = listOf(
            SpecialKeyAction(ToolbarSpecialKey.F7),
            ControlAction('K'),
            TextAction("git commit -m \"x\"\n"),
            ModifierAction(ToolbarModifier.CTRL, lock = true),
            SnippetAction("snippet.1"),
            MacroAction("macro.ls"),
            PasteAction,
            ReconnectAction,
            OpenKeyboardAction,
            HideKeyboardAction,
            ToggleComposeAction,
            OpenSnippetsAction,
            OpenAgentsAction,
            OpenPortForwardAction,
            OpenSettingsAction,
            OpenToolbarEditorAction,
            ChangeFontSizeAction(-2),
            SwitchProfileAction("vim"),
        )
        val keys = actions.mapIndexed { index, action ->
            ToolKeySpec(id = "k$index", label = "K$index", action = action)
        }
        val profile = ToolbarProfile(
            id = "p", name = "P", description = "", iconToken = "", themeId = ToolbarThemes.default.id,
            layout = ToolbarLayout(rows = listOf(keys)),
        )
        val decoded = roundTrip(ToolbarConfigState(customProfiles = listOf(profile)))
        assertEquals(actions, decoded.customProfiles.single().layout.keys.map { it.action })
    }

    @Test fun gestureBindingsRoundTrip() {
        val key = ToolKeySpec(
            id = "enter", label = "Enter", action = SpecialKeyAction(ToolbarSpecialKey.ENTER),
            gestures = mapOf(
                ToolbarGesture.DOUBLE_TAP to ControlAction('D'),
                ToolbarGesture.LONG_PRESS to ControlAction('C'),
                ToolbarGesture.SWIPE_UP to ChangeFontSizeAction(1),
                ToolbarGesture.SWIPE_DOWN to TextAction("x"),
                ToolbarGesture.SWIPE_LEFT to ControlAction('K'),
                ToolbarGesture.SWIPE_RIGHT to OpenSettingsAction,
            ),
            pinned = true, width = KeyWidth.WIDE, style = KeyStyle.ACCENT,
        )
        val profile = ToolbarProfile(
            id = "p", name = "P", description = "d", iconToken = "i", themeId = ToolbarThemes.default.id,
            layout = ToolbarLayout(rows = listOf(listOf(key))),
        )
        val decoded = roundTrip(ToolbarConfigState(customProfiles = listOf(profile)))
        assertEquals(key, decoded.customProfiles.single().layout.keys.single())
    }

    @Test fun unknownActionTypeDropsOnlyThatKey() {
        val good = ToolKeySpec("a", "A", TextAction("x"))
        val bad = Json.obj(
            "id" to Json.str("future"),
            "label" to Json.str("?"),
            "action" to Json.obj("type" to Json.str("from_the_future"), "blob" to Json.str("z")),
        )
        val profileJson = Json.obj(
            "id" to Json.str("p"),
            "name" to Json.str("P"),
            "rows" to Json.arr(listOf(Json.arr(listOf(CustomJson.encodeKey(good), bad)))),
        )
        val document = Json.obj(
            "schema" to Json.num(ToolbarCodec.SCHEMA_VERSION),
            "customProfiles" to Json.arr(listOf(profileJson)),
        )
        val decoded = ToolbarCodec.decode(document.encode())
        assertTrue(decoded is ToolbarDecode.Ok)
        val keys = decoded.state.customProfiles.single().layout.keys
        assertEquals(listOf("a"), keys.map { it.id })
    }

    @Test fun malformedDocumentFails() {
        assertTrue(ToolbarCodec.decode("{not json") is ToolbarDecode.Failed)
        assertTrue(ToolbarCodec.decode("[]") is ToolbarDecode.Failed)
        assertTrue(ToolbarCodec.decode("{}") is ToolbarDecode.Failed) // no schema
        assertTrue(ToolbarCodec.decode("""{"schema":0}""") is ToolbarDecode.Failed)
    }

    @Test fun newerSchemaIsReadBestEffort() {
        val document = Json.obj(
            "schema" to Json.num(ToolbarCodec.SCHEMA_VERSION + 5),
            "activeProfile" to Json.str("vim"),
            "somethingNew" to Json.str("ignored"),
        )
        val decoded = ToolbarCodec.decode(document.encode())
        assertTrue(decoded is ToolbarDecode.Ok)
        assertEquals("vim", decoded.state.activeProfileId)
    }

    @Test fun hostOverridesRoundTrip() {
        val state = ToolbarConfigState(hostOverrides = mapOf("host-a" to "vim", "host-b" to "docker"))
        assertEquals(state.hostOverrides, roundTrip(state).hostOverrides)
    }

    @Test fun outOfRangeThemeValuesAreRejectedForThatThemeOnly() {
        val good = ToolbarThemes.dracula
        val bad = Json.obj(
            "id" to Json.str("bad"),
            "name" to Json.str("Bad"),
            "cornerRadius" to Json.num(999),
        )
        val document = Json.obj(
            "schema" to Json.num(ToolbarCodec.SCHEMA_VERSION),
            "customThemes" to Json.arr(listOf(CustomJson.encodeTheme(good), bad)),
        )
        val decoded = ToolbarCodec.decode(document.encode())
        assertTrue(decoded is ToolbarDecode.Ok)
        assertNotNull(decoded.state.customThemes.firstOrNull { it.id == "dracula" })
        assertFalse(decoded.state.customThemes.any { it.id == "bad" })
    }

    @Test fun encodedDocumentNeverContainsCredentialShapedFields() {
        val text = ToolbarCodec.encode(
            ToolbarConfigState(customProfiles = listOf(ToolbarProfiles.power)),
        )
        listOf("password", "passphrase", "privatekey", "secret", "token", "credential").forEach { forbidden ->
            assertFalse(text.contains(forbidden, ignoreCase = true), "config leaked '$forbidden'")
        }
    }
}

/** Reaches into the codec's private encoders so a test can inject a hand-built key/theme. */
private object CustomJson {
    fun encodeKey(key: ToolKeySpec): JsonValue {
        // Round-trip through the public codec to reuse its (private) key encoder.
        val profile = ToolbarProfile(
            id = "wrap", name = "wrap", description = "", iconToken = "",
            themeId = ToolbarThemes.default.id, layout = ToolbarLayout(rows = listOf(listOf(key))),
        )
        val document = ToolbarCodec.decode(
            ToolbarCodec.encode(ToolbarConfigState(customProfiles = listOf(profile))),
        )
        // Re-encode just this key by encoding the profile and digging out the row entry.
        val encoded = Json.parse(
            ToolbarCodec.encode(
                ToolbarConfigState(customProfiles = listOf((document as ToolbarDecode.Ok).state.customProfiles.single())),
            ),
        ) as JsonValue.Obj
        val profileObj = (encoded["customProfiles"] as JsonValue.Arr).items.single() as JsonValue.Obj
        return ((profileObj["rows"] as JsonValue.Arr).items.single() as JsonValue.Arr).items.single()
    }

    fun encodeTheme(theme: ToolbarTheme): JsonValue {
        val encoded = Json.parse(
            ToolbarCodec.encode(ToolbarConfigState(customThemes = listOf(theme))),
        ) as JsonValue.Obj
        return (encoded["customThemes"] as JsonValue.Arr).items.single()
    }
}
