package app.terminalssh.secure.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRegistryTest {

    /** Stands in for resource lookup so search can be tested without Android. */
    private val labels = mapOf(
        SettingsRegistry.fontSize.titleRes to "Font size",
        SettingsRegistry.theme.titleRes to "Terminal theme",
        SettingsRegistry.biometricLock.titleRes to "Biometric lock",
        SettingsRegistry.clipboardClearSeconds.titleRes to "Clear clipboard automatically",
        SettingsRegistry.keepAlive.titleRes to "Send keepalive packets",
        SettingsRegistry.hapticKeys.titleRes to "Vibrate on key press",
        SettingsRegistry.hapticKeys.summaryRes!! to "Haptic feedback on the special-key bar",
    )
    private val resolve: (Int) -> String = { labels[it] ?: "" }

    @Test fun everySettingKeyIsUnique() {
        val keys = SettingsRegistry.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate keys: ${keys.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    @Test fun everySettingBelongsToExactlyOneGroup() {
        val grouped = SettingGroup.entries.flatMap { SettingsRegistry.byGroup(it) }
        assertEquals(SettingsRegistry.all.size, grouped.size)
    }

    @Test fun lookupByKeyRoundTrips() {
        SettingsRegistry.all.forEach { spec ->
            assertEquals(spec, SettingsRegistry.byKey(spec.key), "byKey failed for ${spec.key}")
        }
        assertEquals(null, SettingsRegistry.byKey("no-such-setting"))
    }

    @Test fun keysMatchWhatEarlierVersionsWrote() {
        // Changing any of these silently resets that setting on upgrade.
        assertEquals("theme", SettingsRegistry.theme.key)
        assertEquals("font_size", SettingsRegistry.fontSize.key)
        assertEquals("biometric", SettingsRegistry.biometricLock.key)
        assertEquals("paste_confirm", SettingsRegistry.confirmMultilinePaste.key)
        assertEquals("keepalive", SettingsRegistry.keepAlive.key)
        assertEquals("clipboard_clear_seconds", SettingsRegistry.clipboardClearSeconds.key)
        assertEquals("xfer_limit_kbps", SettingsRegistry.transferLimitKbPerSecond.key)
        assertEquals("xfer_wifi_only", SettingsRegistry.transfersWifiOnly.key)
    }

    @Test fun recoverableSettingsIncludeTransferPreferences() {
        assertTrue(SettingsRegistry.transferLimitKbPerSecond in SettingsRegistry.all)
        assertTrue(SettingsRegistry.transfersWifiOnly in SettingsRegistry.all)
        assertEquals(0, SettingsRegistry.transferLimitKbPerSecond.default)
        assertEquals(100_000, SettingsRegistry.transferLimitKbPerSecond.max)
        assertEquals(false, SettingsRegistry.transfersWifiOnly.default)
    }

    @Test fun visibleSettingsAreExactlyTheSettingsWithRuntimeConsumers() {
        assertEquals(
            listOf(
                SettingsRegistry.theme,
                SettingsRegistry.fontSize,
                SettingsRegistry.terminalType,
                SettingsRegistry.hapticKeys,
                SettingsRegistry.keepScreenOn,
                SettingsRegistry.keepAlive,
                SettingsRegistry.confirmMultilinePaste,
                SettingsRegistry.biometricLock,
                SettingsRegistry.clipboardClearSeconds,
                // Redacts secret-shaped terminal output; consumed by SshSession.maskChunk.
                SettingsRegistry.maskSecretsInOutput,
                SettingsRegistry.transferLimitKbPerSecond,
                // Holds the SFTP queue on metered networks; consumed by
                // SftpController.mayStartTransfers via AppViewModel.onUnmeteredNetwork.
                SettingsRegistry.transfersWifiOnly,
            ),
            SettingsRegistry.visible,
        )
    }

    @Test fun blankQueryReturnsNothingRatherThanEverything() {
        assertTrue(SettingsRegistry.search("", resolve).isEmpty())
        assertTrue(SettingsRegistry.search("   ", resolve).isEmpty())
    }

    @Test fun searchFindsSettingsByTitle() {
        val hits = SettingsRegistry.search("font", resolve)
        assertTrue(SettingsRegistry.fontSize in hits, "font size was not found")
    }

    @Test fun searchAlsoMatchesTheSummary() {
        // "haptic" appears only in the summary, not the title.
        val hits = SettingsRegistry.search("haptic", resolve)
        assertTrue(SettingsRegistry.hapticKeys in hits, "summary text was not searched")
    }

    @Test fun titleMatchOutranksSummaryMatch() {
        // "key" is in hapticKeys' title ("key press") and its summary ("special-key bar").
        val hits = SettingsRegistry.search("keepalive", resolve)
        assertEquals(SettingsRegistry.keepAlive, hits.first())
    }

    @Test fun searchIsFuzzyNotJustSubstring() {
        // "clipbrd" is not a substring of "Clear clipboard automatically".
        val hits = SettingsRegistry.search("clipbrd", resolve)
        assertTrue(SettingsRegistry.clipboardClearSeconds in hits)
    }

    @Test fun nonsenseQueryReturnsNothing() {
        assertTrue(SettingsRegistry.search("zzzqqq", resolve).isEmpty())
    }

    @Test fun defaultViewIsShortEnoughToScan() {
        // The whole point of advanced mode: the default list stays scannable.
        val basic = SettingsRegistry.visible.count { !it.advanced }
        assertTrue(basic <= 14, "$basic non-advanced settings is too many for the default view")
        assertTrue(SettingsRegistry.visible.any { it.advanced }, "advanced mode hides nothing")
    }
}
