package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolbarRepositoryTest {

    private fun repo(prefs: ToolbarPrefs = InMemoryToolbarPrefs()) = ToolbarRepository(prefs) to prefs

    @Test fun freshRepositoryStartsWithDefaults() {
        val (repository, _) = repo()
        assertEquals(ToolbarProfiles.default.id, repository.state.value.activeProfileId)
        assertEquals(ToolbarThemes.default.id, repository.state.value.activeThemeId)
    }

    @Test fun defaultShellToolbarOpensSnippetSheet() {
        val snippetsKey = ToolbarProfiles.default.layout.keyById("snippets")
        assertNotNull(snippetsKey)
        assertEquals(OpenSnippetsAction, snippetsKey.action)
        assertEquals(listOf("ctrl", "alt", "shift"), ToolbarProfiles.default.layout.keys.take(3).map { it.id })
    }

    @Test fun activeProfilePersistsAcrossReload() {
        val (repository, prefs) = repo()
        repository.setActiveProfile("vim")
        val reloaded = ToolbarRepository(prefs)
        assertEquals("vim", reloaded.state.value.activeProfileId)
    }

    @Test fun unknownProfileOrThemeIsIgnored() {
        val (repository, _) = repo()
        repository.setActiveProfile("does_not_exist")
        repository.setActiveTheme("does_not_exist")
        assertEquals(ToolbarProfiles.default.id, repository.state.value.activeProfileId)
        assertEquals(ToolbarThemes.default.id, repository.state.value.activeThemeId)
    }

    @Test fun savedProfileShadowsTheBuiltIn() {
        val (repository, prefs) = repo()
        val edited = ToolbarEditor.renameKey(ToolbarProfiles.shell, "escape", "Esc!")
        repository.saveProfile(edited)
        assertEquals("Esc!", repository.profileById("shell")!!.layout.keyById("escape")!!.label)
        assertEquals("Esc!", ToolbarRepository(prefs).profileById("shell")!!.layout.keyById("escape")!!.label)
    }

    @Test fun gestureBindingsPersistWhenProfileIsSavedAndReloaded() {
        val (repository, prefs) = repo()
        val gesture = ToolbarGesture.SWIPE_LEFT
        val action = ControlAction('C')
        val edited = ToolbarEditor.bindGesture(ToolbarProfiles.shell, "escape", gesture, action)
        repository.saveProfile(edited)

        val saved = ToolbarRepository(prefs).profileById("shell")!!.layout.keyById("escape")!!
        assertEquals(action, saved.gestures[gesture])
    }

    @Test fun deletingAnActiveCustomOnlyProfileFallsBackToDefault() {
        val (repository, _) = repo()
        val custom = ToolbarProfiles.shell.copy(id = "mine", name = "Mine", builtIn = false)
        repository.saveProfile(custom)
        repository.setActiveProfile("mine")
        repository.deleteProfile("mine")
        assertNull(repository.state.value.customProfiles.firstOrNull { it.id == "mine" })
        assertEquals(ToolbarProfiles.default.id, repository.state.value.activeProfileId)
    }

    @Test fun deletingAShadowingCustomProfileRevealsTheBuiltInAndKeepsItActive() {
        val (repository, _) = repo()
        repository.saveProfile(ToolbarEditor.renameKey(ToolbarProfiles.shell, "escape", "Esc!"))
        repository.setActiveProfile("shell")
        repository.deleteProfile("shell")
        assertEquals("Esc", repository.profileById("shell")!!.layout.keyById("escape")!!.label)
        assertEquals("shell", repository.state.value.activeProfileId)
    }

    @Test fun duplicateProfileGetsUniqueIdAndName() {
        val (repository, _) = repo()
        val copy = repository.duplicateProfile("shell")
        assertNotNull(copy)
        assertEquals("shell_copy", copy.id)
        assertFalse(copy.builtIn)
        val second = repository.duplicateProfile("shell")
        assertEquals("shell_copy2", second!!.id)
    }

    @Test fun hostOverrideChangesTheEffectiveProfileAndCanBeCleared() {
        val (repository, _) = repo()
        repository.setActiveProfile("shell")
        repository.setHostOverride("prod-box", "vim")
        assertEquals("vim", repository.effectiveProfile("prod-box").id)
        assertEquals("shell", repository.effectiveProfile("other").id)
        assertEquals("shell", repository.state.value.activeProfileId)
        repository.setHostOverride("prod-box", null)
        assertEquals("shell", repository.effectiveProfile("prod-box").id)
    }

    @Test fun deletingAProfileAlsoClearsOverridesPointingAtIt() {
        val (repository, _) = repo()
        repository.setHostOverride("box", "tmux")
        repository.saveProfile(ToolbarProfiles.tmux.copy(id = "tmux", builtIn = false))
        repository.deleteProfile("tmux")
        assertEquals("tmux", repository.effectiveProfile("box").id) // built-in tmux still exists
        val customOnly = ToolbarProfiles.tmux.copy(id = "custom_tmux", builtIn = false)
        repository.saveProfile(customOnly)
        repository.setHostOverride("box2", "custom_tmux")
        repository.deleteProfile("custom_tmux")
        assertTrue("box2" !in repository.state.value.hostOverrides)
    }

    @Test fun exportThenImportRoundTripsTheConfiguration() {
        val (repository, _) = repo()
        repository.setActiveProfile("power")
        repository.setActiveTheme("nord")
        repository.setModifierLockEnabled(false)
        repository.saveProfile(ToolbarEditor.renameKey(ToolbarProfiles.git, "diff", "change"))
        repository.setHostOverride("h", "docker")
        val exported = repository.export()

        val (other, _) = repo()
        assertTrue(other.applyImport(exported))
        assertEquals(repository.state.value.activeProfileId, other.state.value.activeProfileId)
        assertEquals(repository.state.value.activeThemeId, other.state.value.activeThemeId)
        assertEquals(false, other.state.value.modifierLockEnabled)
        assertEquals(repository.state.value.hostOverrides, other.state.value.hostOverrides)
        assertEquals(
            repository.profileById("git")!!.layout.keyById("diff")!!.label,
            other.profileById("git")!!.layout.keyById("diff")!!.label,
        )
    }

    @Test fun failedImportPersistenceDoesNotPublishImportedState() {
        val backing = InMemoryToolbarPrefs()
        var failWrites = false
        var durableWrites = 0
        val prefs = object : ToolbarPrefs {
            override fun get(key: String): String? = backing.get(key)
            override fun put(key: String, value: String) {
                if (failWrites) error("simulated storage failure")
                backing.put(key, value)
            }
            override fun putDurably(key: String, value: String) {
                durableWrites++
                if (failWrites) error("simulated durable storage failure")
                backing.put(key, value)
            }
        }
        val repository = ToolbarRepository(prefs)
        repository.saveProfile(ToolbarProfiles.shell.copy(name = "Existing"))
        val originalState = repository.state.value
        val originalJson = backing.get(ToolbarRepository.KEY_CONFIG)
        val incoming = ToolbarConfigState(
            customProfiles = listOf(ToolbarProfiles.power.copy(id = "incoming", builtIn = false)),
        )
        failWrites = true

        assertFailsWith<IllegalStateException> { repository.applyImport(ToolbarCodec.encode(incoming)) }
        assertEquals(originalState, repository.state.value)
        assertEquals(originalJson, backing.get(ToolbarRepository.KEY_CONFIG))
        assertEquals(1, durableWrites, "an import must use the durable preference write")
    }

    @Test fun importRejectsOversizeDocumentsAndCredentialLikeMacros() {
        val (repository, _) = repo()
        assertNull(repository.prepareImport(" ".repeat(ToolbarRepository.MAX_IMPORT_BYTES + 1)))
        val state = ToolbarConfigState(
            customMacros = listOf(
                MacroDef("unsafe", "Unsafe", steps = listOf(MacroStep.Text("password=guess-me"))),
            ),
        )
        assertNull(repository.prepareImport(ToolbarCodec.encode(state)))
    }

    @Test fun importPreviewSummarisesAndRejectsGarbage() {
        val (repository, _) = repo()
        repository.saveProfile(ToolbarProfiles.vim.copy(id = "user_vim", builtIn = false))
        val preview = repository.previewImport(repository.export())
        assertNotNull(preview)
        assertEquals(1, preview.profiles)
        assertNull(repository.previewImport("not json at all"))
        assertFalse(repository.applyImport("not json at all"))
    }

    @Test fun corruptStoredDocumentFallsBackWithoutErasingIt() {
        val prefs = InMemoryToolbarPrefs(mapOf(ToolbarRepository.KEY_CONFIG to "{ definitely broken"))
        val repository = ToolbarRepository(prefs)
        assertEquals(ToolbarProfiles.default.id, repository.state.value.activeProfileId)
        assertNotNull(repository.recoveryNotice)
        assertEquals("{ definitely broken", repository.recoverableRaw)

        // A later change must persist cleanly over the broken document.
        repository.setActiveProfile("git")
        assertEquals("git", ToolbarRepository(prefs).state.value.activeProfileId)
        assertNull(repository.recoveryNotice)
        assertNull(repository.recoverableRaw)
    }

    @Test fun resetRestoresDefaultsAndPersists() {
        val (repository, prefs) = repo()
        repository.setActiveProfile("docker")
        repository.saveProfile(ToolbarProfiles.vim.copy(id = "custom", builtIn = false))
        repository.resetToDefaults()
        assertEquals(ToolbarProfiles.default.id, repository.state.value.activeProfileId)
        assertTrue(repository.state.value.customProfiles.isEmpty())
        assertEquals(ToolbarProfiles.default.id, ToolbarRepository(prefs).state.value.activeProfileId)
    }

    @Test fun macroAndThemeSavingRoundTrips() {
        val (repository, prefs) = repo()
        val macro = MacroDef("custom.1", "Hello", steps = listOf(MacroStep.Text("echo hi"), MacroStep.Special(ToolbarSpecialKey.ENTER)))
        val theme = ToolbarThemes.dracula.copy(id = "custom_dracula", name = "Dracula Mine")
        repository.saveMacro(macro)
        repository.saveTheme(theme)
        val reloaded = ToolbarRepository(prefs)
        assertEquals(macro, reloaded.macroById("custom.1"))
        assertEquals(theme, reloaded.themeById("custom_dracula"))
        assertEquals(ToolbarMacros.listFiles, reloaded.macroById("macro.ls"))
    }

    @Test fun deletingAMacroAlsoUnreferencesItFromProfiles() {
        val (repository, _) = repo()
        repository.saveMacro(MacroDef("m1", "M1", steps = listOf(MacroStep.Text("x"))))
        repository.saveProfile(ToolbarProfiles.git.copy(id = "with_macro", macroIds = listOf("m1")))
        repository.deleteMacro("m1")
        assertTrue(repository.profileById("with_macro")!!.macroIds.isEmpty())
        assertNull(repository.macroById("m1"))
    }
}
