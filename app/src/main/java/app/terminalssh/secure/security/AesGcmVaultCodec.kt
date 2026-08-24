package app.terminalssh.secure.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmVaultCodec {
    data class Sealed(val nonce: ByteArray, val ciphertext: ByteArray) {
        init {
            require(nonce.size == NONCE_BYTES) { "invalid GCM nonce length" }
            require(ciphertext.size >= TAG_BYTES) { "ciphertext too short" }
        }
    }

    /**
     * The nonce is taken from the cipher rather than supplied to it.
     *
     * The vault key is generated with setRandomizedEncryptionRequired(true), so
     * AndroidKeyStore rejects a caller-provided IV on encrypt with
     * "Caller-provided IV not permitted" — it insists on generating one itself precisely
     * so a nonce can never be reused under the same key. Passing our own nonce here made
     * every vault write throw. Decryption still supplies the stored nonce, which is allowed.
     */
    fun seal(key: SecretKey, plaintext: ByteArray, aad: VaultAad): Sealed {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad.bytes())
        val ciphertext = cipher.doFinal(plaintext)
        return Sealed(cipher.iv.copyOf(), ciphertext)
    }

    fun open(key: SecretKey, sealed: Sealed, aad: VaultAad): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed.nonce))
        cipher.updateAAD(aad.bytes())
        return cipher.doFinal(sealed.ciphertext)
    }

    companion object {
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
    }
}
