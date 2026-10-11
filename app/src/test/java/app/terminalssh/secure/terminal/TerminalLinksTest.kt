package app.terminalssh.secure.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalLinksTest {

    @Test fun detectsAnHttpsUrl() {
        val links = TerminalLinks.detect("see https://example.com/deploy?x=1 for details")
        assertEquals(1, links.size)
        assertEquals("https://example.com/deploy?x=1", links.single().text)
        assertTrue(links.single().isDirectlyOpenable)
    }

    @Test fun detectsBareWwwHost() {
        val link = TerminalLinks.detect("visit www.example.org now").single()
        assertEquals("www.example.org", link.text)
        assertEquals("http://www.example.org", TerminalLinks.toUri(link.text))
    }

    @Test fun schemeUrlKeepsItsOwnScheme() {
        assertEquals("ssh://box:2222", TerminalLinks.toUri("ssh://box:2222"))
    }

    @Test fun dangerousSchemesAreRefused() {
        assertEquals(TerminalLinks.Policy.BLOCK, TerminalLinks.policyFor("javascript"))
        assertEquals(TerminalLinks.Policy.BLOCK, TerminalLinks.policyFor("data"))
        val link = TerminalLinks.detect("javascript://alert(1)").single()
        assertFalse(link.isDirectlyOpenable)
    }

    @Test fun fileAndIntentNeedConfirmation() {
        assertEquals(TerminalLinks.Policy.CONFIRM, TerminalLinks.policyFor("file"))
        assertEquals(TerminalLinks.Policy.CONFIRM, TerminalLinks.policyFor("intent"))
        assertEquals(TerminalLinks.Policy.CONFIRM, TerminalLinks.policyFor("content"))
    }

    @Test fun unknownSchemesDefaultToConfirmNotSilentOpen() {
        assertEquals(TerminalLinks.Policy.CONFIRM, TerminalLinks.policyFor("weird"))
    }

    @Test fun trailingProsePunctuationIsTrimmable() {
        assertEquals("https://example.com/x", TerminalLinks.trimTrailing("https://example.com/x)."))
    }

    @Test fun multipleLinksAreOrderedByPosition() {
        val links = TerminalLinks.detect("a https://one.example b https://two.example")
        assertEquals(listOf("https://one.example", "https://two.example"), links.map { it.text })
    }

    @Test fun plainTextHasNoLinks() {
        assertTrue(TerminalLinks.detect("total 4 drwxr-xr-x 2 root root").isEmpty())
    }
}
