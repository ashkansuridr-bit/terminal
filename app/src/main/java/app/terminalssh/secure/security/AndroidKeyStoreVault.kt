package app.terminalssh.secure.security

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Persistent encrypted vault. Only AES ciphertext is stored in SharedPreferences;
 * the wrapping key is non-exportable and lives in AndroidKeyStore.
 */
class AndroidKeyStoreVault(
    context: Context,
    private val codec: AesGcmVaultCodec = AesGcmVaultCodec(),
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var durabilityUncertain = false

    @Synchronized
    fun put(ref: String, value: ByteArray, aad: VaultAad) {
        check(!durabilityUncertain) { "vault durability uncertain" }
        require(ref.isNotBlank())
        when (aad) {
            VaultAad.PRIVATE_KEY -> VaultLimits.requirePrivateKeySize(value)
            VaultAad.SNIPPET -> VaultLimits.requireSnippetSize(value)
            else -> Unit
        }
        val sealed = codec.seal(getOrCreateKey(), value, aad)
        val packed = byteArrayOf(VERSION) + sealed.nonce + sealed.ciphertext
        val record = keyName(ref, aad)
        val previous = prefs.getString(record, null)
        if (!prefs.edit().putString(record, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            val restored = prefs.edit().putString(record, previous).commit()
            durabilityUncertain = !restored
            check(restored) { "vault rollback failed" }
            error("vault write failed")
        }
    }

    @Synchronized
    fun get(ref: String, aad: VaultAad): ByteArray? {
        check(!durabilityUncertain) { "vault durability uncertain" }
        val encoded = prefs.getString(keyName(ref, aad), null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size >= 1 + AesGcmVaultCodec.NONCE_BYTES + AesGcmVaultCodec.TAG_BYTES) { "invalid vault record" }
        require(packed[0] == VERSION) { "unsupported vault record version" }
        val nonceStart = 1
        val cipherStart = nonceStart + AesGcmVaultCodec.NONCE_BYTES
        return codec.open(
            getOrCreateKey(),
            AesGcmVaultCodec.Sealed(
                nonce = packed.copyOfRange(nonceStart, cipherStart),
                ciphertext = packed.copyOfRange(cipherStart, packed.size),
            ),
            aad,
        )
    }

    @Synchronized
    fun delete(ref: String, aad: VaultAad) {
        check(!durabilityUncertain) { "vault durability uncertain" }
        val record = keyName(ref, aad)
        val previous = prefs.getString(record, null)
        if (!prefs.edit().remove(record).commit()) {
            val restored = prefs.edit().putString(record, previous).commit()
            durabilityUncertain = !restored
            check(restored) { "vault rollback failed" }
            error("vault deletion failed")
        }
    }

    @Synchronized
    fun clearEncryptedRecords() {
        check(!durabilityUncertain) { "vault durability uncertain" }
        val previous = prefs.all.mapValues { (_, value) ->
            check(value is String) { "invalid vault record" }
            value
        }
        if (!prefs.edit().clear().commit()) {
            val rollback = prefs.edit().clear()
            previous.forEach { (key, value) -> rollback.putString(key, value) }
            val restored = rollback.commit()
            durabilityUncertain = !restored
            error(if (restored) "vault clear failed" else "vault clear rollback failed")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyName(ref: String, aad: VaultAad) = "${aad.wireValue}:$ref"

    companion object {
        private const val PREFS = "vault_v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "terminalssh.vault.v1"
        private const val VERSION: Byte = 1
    }
}
