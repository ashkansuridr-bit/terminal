package app.terminalssh.secure.security

import kotlin.test.Test
import kotlin.test.assertFailsWith

class VaultLimitsTest {
    @Test fun rejectsOversizedPrivateKey() {
        assertFailsWith<IllegalArgumentException> {
            VaultLimits.requirePrivateKeySize(ByteArray(VaultLimits.MAX_PRIVATE_KEY_BYTES + 1))
        }
    }

    @Test fun rejectsOversizedSnippet() {
        assertFailsWith<IllegalArgumentException> {
            VaultLimits.requireSnippetSize(ByteArray(VaultLimits.MAX_SNIPPET_BYTES + 1))
        }
    }
}
