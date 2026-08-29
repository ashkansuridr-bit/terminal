package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteTrashTest {

    private val home = "/home/deploy"

    @Test fun trashLivesUnderTheUsersHomeSoItSurvivesAReboot() {
        assertEquals("/home/deploy/.terminalssh-trash", RemoteTrash.trashDir(home))
    }

    @Test fun aTrashedPathKeepsTheOriginalNameReadable() {
        val p = RemoteTrash.trashedPath(home, "/var/www/index.html", 1700000000000)
        assertTrue(p.endsWith("1700000000000-index.html"))
        assertTrue(p.startsWith(RemoteTrash.trashDir(home)))
    }

    @Test fun deletingTheSameNameTwiceDoesNotCollide() {
        val a = RemoteTrash.trashedPath(home, "/x/config", 1)
        val b = RemoteTrash.trashedPath(home, "/y/config", 2)
        assertFalse(a == b, "two deletes of the same filename must not overwrite each other")
    }

    @Test fun aTrailingSlashOnADirectoryIsHandled() {
        assertTrue(RemoteTrash.trashedPath(home, "/var/log/", 5).endsWith("5-log"))
    }

    @Test fun theNameParsesBackIntoTimeAndOriginal() {
        val (stamp, name) = RemoteTrash.parseTrashedName("1700000000000-index.html")!!
        assertEquals(1700000000000, stamp)
        assertEquals("index.html", name)
    }

    @Test fun anOriginalNameContainingADashSurvives() {
        val (_, name) = RemoteTrash.parseTrashedName("42-my-long-file-name.txt")!!
        assertEquals("my-long-file-name.txt", name, "only the first dash is the separator")
    }

    @Test fun somethingElseInTheTrashDirectoryIsNotMistakenForAnEntry() {
        assertNull(RemoteTrash.parseTrashedName("notes.txt"))
        assertNull(RemoteTrash.parseTrashedName("abc-notes.txt"))
        assertNull(RemoteTrash.parseTrashedName("-leading"))
        assertNull(RemoteTrash.parseTrashedName("42-"))
    }

    @Test fun deletingFromInsideTheTrashIsRecognisedSoItReallyDeletes() {
        assertTrue(RemoteTrash.isInTrash("/home/deploy/.terminalssh-trash/1-a.txt", home))
        assertFalse(RemoteTrash.isInTrash("/var/www/a.txt", home))
        assertFalse(RemoteTrash.isInTrash("/home/deploy/notes.txt", home))
    }
}
