package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostProfileTest {
    private fun profile(
        label: String = "Prod DB",
        host: String = "db.example.com",
        port: Int = 22,
        username: String = "deploy",
        tags: List<String> = listOf("prod", "postgres"),
    ) = HostProfile(
        id = "1", label = label, host = host, port = port, username = username,
        auth = AuthMethod.Password(""), group = "Production", tags = tags,
    )

    @Test fun rejectsOutOfRangePort() {
        assertFailsWith<IllegalArgumentException> { profile(port = 0) }
        assertFailsWith<IllegalArgumentException> { profile(port = 70000) }
    }

    @Test fun rejectsBlankHostAndUsername() {
        assertFailsWith<IllegalArgumentException> { profile(host = " ") }
        assertFailsWith<IllegalArgumentException> { profile(username = "") }
    }

    @Test fun fallsBackToHostWhenUnlabelled() {
        assertEquals("db.example.com", profile(label = "").displayName)
        assertEquals("Prod DB", profile().displayName)
    }

    @Test fun hidesDefaultPortInSubtitle() {
        assertEquals("deploy@db.example.com", profile().subtitle)
        assertEquals("deploy@db.example.com:2222", profile(port = 2222).subtitle)
    }

    @Test fun searchesLabelHostUserGroupAndTags() {
        val p = profile()
        assertTrue(p.matches(""))
        assertTrue(p.matches("prod"))       // tag and group
        assertTrue(p.matches("DB.EXAMPLE")) // case-insensitive host
        assertTrue(p.matches("deploy"))
        assertFalse(p.matches("staging"))
    }
}
