import app.terminalssh.secure.security.AesGcmVaultCodec
import app.terminalssh.secure.security.VaultAad
import app.terminalssh.secure.security.VaultLimits
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

fun case(name: String, block: () -> Unit) {
    try { block(); println("PASS $name") }
    catch (t: Throwable) { println("FAIL $name: ${t.message}"); throw t }
}

fun main() {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    val codec = AesGcmVaultCodec()
    val secret = "correct horse battery staple".encodeToByteArray()

    case("password round trip") {
        val sealed = codec.seal(key, secret, VaultAad.PASSWORD)
        check(codec.open(key, sealed, VaultAad.PASSWORD).contentEquals(secret))
        check(sealed.nonce.size == 12)
    }
    case("AAD type confusion is rejected") {
        val sealed = codec.seal(key, secret, VaultAad.PASSWORD)
        var rejected = false
        try { codec.open(key, sealed, VaultAad.PRIVATE_KEY) }
        catch (_: AEADBadTagException) { rejected = true }
        check(rejected)
    }
    case("tampered ciphertext is rejected") {
        val sealed = codec.seal(key, secret, VaultAad.PASSPHRASE)
        val tampered = sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        var rejected = false
        try { codec.open(key, AesGcmVaultCodec.Sealed(sealed.nonce, tampered), VaultAad.PASSPHRASE) }
        catch (_: AEADBadTagException) { rejected = true }
        check(rejected)
    }
    case("private key and snippet limits") {
        VaultLimits.requirePrivateKeySize(ByteArray(VaultLimits.MAX_PRIVATE_KEY_BYTES))
        VaultLimits.requireSnippetSize(ByteArray(VaultLimits.MAX_SNIPPET_BYTES))
        check(runCatching { VaultLimits.requirePrivateKeySize(ByteArray(VaultLimits.MAX_PRIVATE_KEY_BYTES + 1)) }.isFailure)
        check(runCatching { VaultLimits.requireSnippetSize(ByteArray(VaultLimits.MAX_SNIPPET_BYTES + 1)) }.isFailure)
    }
    println("VAULT_SMOKE_OK")
}
