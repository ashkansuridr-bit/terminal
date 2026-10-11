package app.terminalssh.secure.model

/**
 * Advanced per-host connection settings (F040).
 *
 * A flaky VPS and a box on the same desk want different patience: the VPS needs a shorter
 * connect timeout and a keep-alive so a NAT idle-timeout does not silently drop the session,
 * while the LAN box is fine with defaults. These live per host rather than globally so tuning
 * one server never surprises another.
 *
 * Values are clamped rather than rejected, because the real input is a small text field on a
 * phone: a user typing "999" for a timeout should get the maximum, not an error dead-end.
 */
data class ConnectionOptions(
    val connectTimeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    /** 0 disables keep-alive. */
    val keepAliveSeconds: Int = 0,
    val compression: Boolean = false,
    val maxReconnectAttempts: Int = HostProfile.DEFAULT_RECONNECT_ATTEMPTS,
) {

    val keepAliveEnabled: Boolean get() = keepAliveSeconds > 0

    /** Every field clamped to a range that cannot wedge a connection. */
    fun normalized(): ConnectionOptions = copy(
        connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
        keepAliveSeconds = keepAliveSeconds.coerceIn(0, MAX_KEEPALIVE_SECONDS),
        maxReconnectAttempts = maxReconnectAttempts.coerceIn(0, MAX_RECONNECT_ATTEMPTS),
    )

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 20
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 120
        const val MAX_KEEPALIVE_SECONDS = 300
        const val MAX_RECONNECT_ATTEMPTS = 20

        val default = ConnectionOptions()
    }
}

/**
 * Persistence for [ConnectionOptions]: a flat string map so it can ride along with the rest of
 * a host's stored fields without a new schema.
 *
 * Decoding is deliberately forgiving — a missing or unparseable value falls back to that
 * field's default instead of failing the whole host load. A settings file written by an older
 * or newer build must never make a server unopenable.
 */
object ConnectionOptionsCodec {

    const val KEY_TIMEOUT = "connection.timeout"
    const val KEY_KEEPALIVE = "connection.keepalive"
    const val KEY_COMPRESSION = "connection.compression"
    const val KEY_RECONNECT = "connection.reconnect"

    fun encode(options: ConnectionOptions): Map<String, String> = mapOf(
        KEY_TIMEOUT to options.connectTimeoutSeconds.toString(),
        KEY_KEEPALIVE to options.keepAliveSeconds.toString(),
        KEY_COMPRESSION to options.compression.toString(),
        KEY_RECONNECT to options.maxReconnectAttempts.toString(),
    )

    fun decode(values: Map<String, String>): ConnectionOptions {
        val base = ConnectionOptions.default
        return ConnectionOptions(
            connectTimeoutSeconds = values[KEY_TIMEOUT]?.toIntOrNull() ?: base.connectTimeoutSeconds,
            keepAliveSeconds = values[KEY_KEEPALIVE]?.toIntOrNull() ?: base.keepAliveSeconds,
            compression = values[KEY_COMPRESSION]?.trim()?.toBooleanStrictOrNull() ?: base.compression,
            maxReconnectAttempts = values[KEY_RECONNECT]?.toIntOrNull() ?: base.maxReconnectAttempts,
        ).normalized()
    }
}
