package app.terminalssh.secure.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Existence probes must never turn permission or transport errors into overwrite permission. */
class SftpExistenceTest {
    @Test fun existingPathReturnsTrue() = assertTrue(client().exists("/data/report"))
    @Test fun onlyExplicitMissingReturnsFalse() {
        assertFalse(client(SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "missing")).exists("/data/report"))
    }
    @Test fun permissionDeniedPropagates() = assertSftpFailure(ChannelSftp.SSH_FX_PERMISSION_DENIED)
    @Test fun genericFailurePropagates() = assertSftpFailure(ChannelSftp.SSH_FX_FAILURE)
    @Test fun disconnectedPropagates() = assertSftpFailure(ChannelSftp.SSH_FX_NO_CONNECTION)
    @Test fun connectionLostPropagates() = assertSftpFailure(ChannelSftp.SSH_FX_CONNECTION_LOST)
    @Test fun timeoutPropagates() {
        val failure = SocketTimeoutException("test timeout")
        assertSame(failure, assertFailsWith<SocketTimeoutException> { client(failure).exists("/data/report") })
    }
    private fun assertSftpFailure(code: Int) {
        val failure = SftpException(code, "test failure")
        assertSame(failure, assertFailsWith<SftpException> { client(failure).exists("/data/report") })
    }
    private fun client(failure: Exception? = null): SftpClient {
        val client = SftpClient(JSch().getSession("unused", "localhost"))
        val channel = object : ChannelSftp() {
            override fun isConnected() = true
            override fun stat(path: String): SftpATTRS {
                if (failure != null) throw failure
                return SftpATTRS()
            }
        }
        SftpClient::class.java.getDeclaredField("channel").apply {
            isAccessible = true
            set(client, channel)
        }
        return client
    }
}
