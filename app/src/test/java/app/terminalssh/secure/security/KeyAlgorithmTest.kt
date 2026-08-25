package app.terminalssh.secure.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyAlgorithmTest {

    @Test fun ed25519IsHiddenBelowApi33() {
        val supported = KeyAlgorithm.supported(sdkInt = 26)
        assertFalse(KeyAlgorithm.ED25519 in supported)
        assertTrue(KeyAlgorithm.ECDSA_256 in supported)
        assertTrue(KeyAlgorithm.RSA_3072 in supported)
    }

    @Test fun ed25519IsOfferedFromApi33() {
        assertTrue(KeyAlgorithm.ED25519 in KeyAlgorithm.supported(sdkInt = 33))
        assertTrue(KeyAlgorithm.ED25519 in KeyAlgorithm.supported(sdkInt = 36))
    }

    @Test fun defaultIsTheStrongestAvailableForTheDevice() {
        assertEquals(KeyAlgorithm.ECDSA_256, KeyAlgorithm.default(sdkInt = 26))
        assertEquals(KeyAlgorithm.ECDSA_256, KeyAlgorithm.default(sdkInt = 32))
        assertEquals(KeyAlgorithm.ED25519, KeyAlgorithm.default(sdkInt = 33))
    }

    @Test fun everyDeviceHasAtLeastOneUsableAlgorithm() {
        for (sdk in 26..36) {
            assertTrue(KeyAlgorithm.supported(sdk).isNotEmpty(), "no algorithm for API $sdk")
        }
    }

    @Test fun labelsAreDistinctSoTheUiCannotConflateThem() {
        val labels = KeyAlgorithm.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
