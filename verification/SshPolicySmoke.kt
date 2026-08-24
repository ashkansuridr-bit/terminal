import app.terminalssh.secure.model.HostKeyPolicy
import app.terminalssh.secure.ssh.KnownHostsVerifier
import app.terminalssh.secure.ssh.SshSessionManager
import app.terminalssh.secure.ssh.SshSessionState

fun checkCase(name: String, block: () -> Unit) {
    try { block(); println("PASS $name") }
    catch (t: Throwable) { println("FAIL $name: ${t.message}"); throw t }
}

fun main() {
    val verifier = KnownHostsVerifier()
    val key = "server-key".encodeToByteArray()

    checkCase("strict rejects unknown host") {
        val r = verifier.verify("example.com", 22, "ssh-ed25519", key, null, HostKeyPolicy.STRICT)
        check(r is KnownHostsVerifier.Decision.Reject && r.reason == "unknown_host")
    }
    checkCase("TOFU requires approval") {
        val manager = SshSessionManager()
        manager.begin()
        val r = manager.onServerKey("example.com", 22, "ssh-ed25519", key, null, HostKeyPolicy.TRUST_ON_FIRST_USE)
        check(r is KnownHostsVerifier.Decision.FirstUse)
        check(manager.state is SshSessionState.AwaitingHostKeyApproval)
        manager.approveFirstUse()
        check(manager.state == SshSessionState.Authenticating)
    }
    checkCase("changed host key is rejected") {
        val known = KnownHostsVerifier.KnownHost("example.com", 22, "ssh-ed25519", key)
        val r = verifier.verify("example.com", 22, "ssh-ed25519", "attacker-key".encodeToByteArray(), known, HostKeyPolicy.STRICT)
        check(r is KnownHostsVerifier.Decision.Reject && r.reason == "host_key_changed")
    }
    checkCase("known unchanged key is accepted") {
        val known = KnownHostsVerifier.KnownHost("example.com", 22, "ssh-ed25519", key)
        val r = verifier.verify("example.com", 22, "ssh-ed25519", key, known, HostKeyPolicy.STRICT)
        check(r == KnownHostsVerifier.Decision.Accept)
    }
    println("SSH_POLICY_SMOKE_OK")
}
