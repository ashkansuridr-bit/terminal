package app.terminalssh.secure.security

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Key generation runs through JSch and the platform's JCE providers, neither of which a
 * JVM test reaches faithfully — in particular whether Ed25519 is actually generable is a
 * property of the device's providers plus how D8 packaged JSch's multi-release jar.
 */
@RunWith(AndroidJUnit4::class)
class KeyGenerationTest {

    @Test fun everyAdvertisedAlgorithmActuallyGenerates() {
        // The real contract: if supported() lists it, generate() must not throw. A
        // mismatch here means the UI offers a key the device cannot produce.
        for (algorithm in KeyAlgorithm.supported()) {
            val key = KeyGeneration.generate(algorithm, "instrumentation-test")
            try {
                assertTrue(
                    "${algorithm.label} produced empty private material",
                    key.privateKey.isNotEmpty(),
                )
                assertTrue(
                    "${algorithm.label} public key was not an SSH public key: ${key.publicKey}",
                    key.publicKey.startsWith("ssh-") || key.publicKey.startsWith("ecdsa-"),
                )
                assertTrue(
                    "${algorithm.label} public key lost its comment",
                    key.publicKey.endsWith("instrumentation-test"),
                )
                assertEquals(algorithm, key.algorithm)
            } finally {
                key.wipe()
            }
        }
    }

    @Test fun ed25519MatchesPlatformSupport() {
        val listed = KeyAlgorithm.ED25519 in KeyAlgorithm.supported()
        assertEquals(
            "Ed25519 availability must track the API level it needs",
            Build.VERSION.SDK_INT >= 33,
            listed,
        )
    }

    @Test fun wipeClearsPrivateMaterial() {
        val key = KeyGeneration.generate(KeyAlgorithm.ECDSA_256, "wipe-test")
        assertTrue(key.privateKey.any { it != 0.toByte() })
        key.wipe()
        assertTrue("private key was not zeroed", key.privateKey.all { it == 0.toByte() })
    }

    @Test fun twoGenerationsProduceDifferentKeys() {
        val first = KeyGeneration.generate(KeyAlgorithm.ECDSA_256, "a")
        val second = KeyGeneration.generate(KeyAlgorithm.ECDSA_256, "b")
        try {
            assertNotEquals(first.publicKey, second.publicKey)
        } finally {
            first.wipe()
            second.wipe()
        }
    }

    @Test fun generatedKeyIsAcceptedByTheFormatGate() {
        // Round-trips generation against the same validation an imported key goes through,
        // so a generated key can never be one the app would refuse to import.
        val key = KeyGeneration.generate(KeyAlgorithm.ECDSA_256, "format-test")
        try {
            val format = PrivateKeyFormat.detect(key.privateKey)
            assertFalse("format gate returned nothing", format.isBlank())
        } finally {
            key.wipe()
        }
    }

    @Test fun unsupportedAlgorithmIsRejectedRatherThanFailingLater() {
        val unsupported = KeyAlgorithm.entries.firstOrNull { it !in KeyAlgorithm.supported() }
            ?: return // Every algorithm is available on this device; nothing to assert.
        try {
            KeyGeneration.generate(unsupported, "should-not-happen").wipe()
            throw AssertionError("expected ${unsupported.label} to be rejected")
        } catch (expected: IllegalArgumentException) {
            // The point: it fails at the gate with a clear reason, not deep inside JSch.
        }
    }
}
