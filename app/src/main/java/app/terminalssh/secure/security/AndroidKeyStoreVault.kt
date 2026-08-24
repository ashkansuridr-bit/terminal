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

    fun put(ref: String, value: ByteArray, aad: VaultAad) {
        require(ref.isNotBlank())
        when (aad) {
            VaultAad.PRIVATE_KEY -> VaultLimits.requirePrivateKeySize(value)
            VaultAad.SNIPPET -> VaultLimits.requireSnippetSize(value)
            else -> Unit
        }
        val sealed = codec.seal(getOrCreateKey(), value, aad)
        val packed = byteArrayOf(VERSION) + sealed.nonce + sealed.ciphertext
        prefs.edit().putString(keyName(ref, aad), Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(ref: String, aad: VaultAad): ByteArray? {
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

    fun delete(ref: String, aad: VaultAad) {
        prefs.edit().remove(keyName(ref, aad)).apply()
    }

    fun clearEncryptedRecords() {
        prefs.edit().clear().apply()
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
