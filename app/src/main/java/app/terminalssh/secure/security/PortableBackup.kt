package app.terminalssh.secure.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A passphrase-protected, self-describing backup envelope (F048).
 *
 * The on-device vault key lives in the AndroidKeyStore and cannot leave the device, so a
 * backup that must be restorable elsewhere needs its own key — derived from a passphrase the
 * user chooses. This is that envelope: PBKDF2-HMAC-SHA256 with a per-file random salt and a
 * high iteration count, then AES-256-GCM with the header bound as AAD.
 *
 * Binding the header as AAD means iterations, salt and nonce cannot be downgraded or swapped
 * without the tag failing, so an attacker cannot strip the KDF cost. Wrong passphrase and
 * tampering are reported as distinct outcomes so the UI never says "corrupt" for a typo.
 *
 * Android-free (only `javax.crypto`), so the round-trip is unit-tested on the JVM.
 */
object PortableBackup {

    private val MAGIC = byteArrayOf(0x54, 0x53, 0x42, 0x4B) // "TSBK"
    const val VERSION = 1
    const val DEFAULT_ITERATIONS = 210_000
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    class WrongPassphrase : IllegalStateException("passphrase did not decrypt the backup")
    class MalformedBackup(message: String) : IllegalArgumentException(message)

    data class Header(val iterations: Int, val salt: ByteArray, val nonce: ByteArray)

    fun seal(
        payload: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        require(iterations > 0) { "iterations must be positive" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val nonce = cipher.iv
            val aad = encodeHeader(iterations, salt, nonce)
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(payload)
            return aad + ciphertext
        } finally {
            key.fill(0)
        }
    }

    fun open(blob: ByteArray, passphrase: CharArray): ByteArray {
        val header = peek(blob)
        val key = deriveKey(passphrase, header.salt, header.iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, header.nonce),
            )
            cipher.updateAAD(encodeHeader(header.iterations, header.salt, header.nonce))
            val offset = encodeHeader(header.iterations, header.salt, header.nonce).size
            return try {
                cipher.doFinal(blob, offset, blob.size - offset)
            } catch (e: AEADBadTagException) {
                throw WrongPassphrase()
            }
        } finally {
            key.fill(0)
        }
    }

    fun isEnvelope(blob: ByteArray): Boolean =
        blob.size > headerSize() && blob[0] == MAGIC[0] && blob[1] == MAGIC[1] &&
            blob[2] == MAGIC[2] && blob[3] == MAGIC[3]

    fun peekIterations(blob: ByteArray): Int? = runCatching { peek(blob).iterations }.getOrNull()

    private fun peek(blob: ByteArray): Header {
        if (!isEnvelope(blob)) throw MalformedBackup("not a Terminal-SSH backup")
        val buffer = ByteBuffer.wrap(blob)
        buffer.position(MAGIC.size)
        val version = buffer.get().toInt()
        if (version != VERSION) throw MalformedBackup("unsupported backup version $version")
        val iterations = buffer.int
        if (iterations <= 0) throw MalformedBackup("invalid iteration count")
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        return Header(iterations, salt, nonce)
    }

    private fun encodeHeader(iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray =
        ByteBuffer.allocate(headerSize() + MAGIC.size).apply {
            put(MAGIC)
            put(VERSION.toByte())
            putInt(iterations)
            put(salt)
            put(nonce)
        }.array()

    private fun headerSize(): Int = 1 + 4 + SALT_BYTES + NONCE_BYTES

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
