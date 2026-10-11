package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionOptionsTest {

    @Test fun defaultsAreSane() {
        val defaults = ConnectionOptions.default
        assertEquals(ConnectionOptions.DEFAULT_TIMEOUT_SECONDS, defaults.connectTimeoutSeconds)
        assertEquals(0, defaults.keepAliveSeconds)
        assertFalse(defaults.compression)
        assertEquals(HostProfile.DEFAULT_RECONNECT_ATTEMPTS, defaults.maxReconnectAttempts)
        assertFalse(defaults.keepAliveEnabled)
    }

    @Test fun normalizationClampsTheTimeout() {
        assertEquals(
            ConnectionOptions.MIN_TIMEOUT_SECONDS,
            ConnectionOptions(connectTimeoutSeconds = 0).normalized().connectTimeoutSeconds,
        )
        assertEquals(
            ConnectionOptions.MAX_TIMEOUT_SECONDS,
            ConnectionOptions(connectTimeoutSeconds = 999).normalized().connectTimeoutSeconds,
        )
    }

    @Test fun normalizationClampsKeepAliveAndReconnect() {
        val clamped = ConnectionOptions(keepAliveSeconds = -5, maxReconnectAttempts = 999).normalized()
        assertEquals(0, clamped.keepAliveSeconds)
        assertEquals(ConnectionOptions.MAX_RECONNECT_ATTEMPTS, clamped.maxReconnectAttempts)
        assertFalse(clamped.keepAliveEnabled)
        assertTrue(ConnectionOptions(keepAliveSeconds = 30).keepAliveEnabled)
    }

    @Test fun encodeDecodeRoundTrips() {
        val options = ConnectionOptions(
            connectTimeoutSeconds = 45,
            keepAliveSeconds = 30,
            compression = true,
            maxReconnectAttempts = 7,
        )
        assertEquals(options, ConnectionOptionsCodec.decode(ConnectionOptionsCodec.encode(options)))
    }

    @Test fun decodeFallsBackToDefaultsForGarbageValues() {
        val decoded = ConnectionOptionsCodec.decode(
            mapOf(ConnectionOptionsCodec.KEY_TIMEOUT to "not-a-number"),
        )
        assertEquals(ConnectionOptions.default, decoded)
    }

    @Test fun decodeNormalizesOutOfRangeValues() {
        val decoded = ConnectionOptionsCodec.decode(
            mapOf(
                ConnectionOptionsCodec.KEY_TIMEOUT to "999",
                ConnectionOptionsCodec.KEY_KEEPALIVE to "-3",
            ),
        )
        assertEquals(ConnectionOptions.MAX_TIMEOUT_SECONDS, decoded.connectTimeoutSeconds)
        assertEquals(0, decoded.keepAliveSeconds)
    }

    @Test fun decodeReadsBooleansStrictly() {
        assertTrue(
            ConnectionOptionsCodec.decode(mapOf(ConnectionOptionsCodec.KEY_COMPRESSION to "true")).compression,
        )
        // "yes" is not a strict boolean, so the field keeps its default rather than guessing.
        assertFalse(
            ConnectionOptionsCodec.decode(mapOf(ConnectionOptionsCodec.KEY_COMPRESSION to "yes")).compression,
        )
    }
}
