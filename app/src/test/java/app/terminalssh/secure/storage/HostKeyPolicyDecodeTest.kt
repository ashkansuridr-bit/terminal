package app.terminalssh.secure.storage

import app.terminalssh.secure.model.HostKeyPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostKeyPolicyDecodeTest {
    @Test fun legacyAbsentFieldMigratesToTofu() {
        assertEquals(HostKeyPolicy.TRUST_ON_FIRST_USE, decodeHostKeyPolicy(null))
    }
    @Test fun validPoliciesArePreserved() {
        HostKeyPolicy.entries.forEach { assertEquals(it, decodeHostKeyPolicy(it.name)) }
    }
    @Test fun explicitInvalidPolicyNeverFallsBackToTofu() {
        listOf("", "strict", "UNKNOWN", "STRICT ").forEach {
            assertFailsWith<IllegalArgumentException> { decodeHostKeyPolicy(it) }
        }
    }
}
