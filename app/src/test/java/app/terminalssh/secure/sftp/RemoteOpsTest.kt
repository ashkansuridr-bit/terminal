package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteOpsTest {

    // ---- quoting is the security boundary for every command here ----

    @Test fun everyCommandQuotesAPathWithAShellMetacharacter() {
        val evil = "/tmp/x'; rm -rf ~; '"
        val commands = listOf(
            RemoteOps.tailCommand(evil),
            RemoteOps.tailSinceCommand(evil, 10),
            RemoteOps.grepCommand(evil, "needle", false),
            RemoteOps.extractCommand("/tmp/a.zip", evil)!!,
            RemoteOps.symlinkCommand(evil, evil),
            RemoteOps.hardLinkCommand(evil, evil),
            RemoteOps.touchCommand(evil),
            RemoteOps.duplicateCommand(evil, evil),
            RemoteOps.chownCommand(evil, "root", null, false)!!,
        )
        // The property that matters is not "the text is absent" — inside quotes it is
        // harmless — but that the path appears only in its fully quoted form, so the
        // shell can never see the closing quote the attacker supplied.
        val quoted = RemoteCommands.shellQuote(evil)
        for (c in commands) {
            assertTrue(c.contains(quoted), "path was not passed in quoted form: $c")
            assertEquals(
                c.split(quoted).size - 1,
                Regex(Regex.escape("rm -rf ~")).findAll(c).count(),
                "every occurrence of the payload must be inside the quoted path: $c",
            )
        }
    }

    @Test fun aSearchNeedleIsQuotedToo() {
        val c = RemoteOps.grepCommand("/var", "\$(id)", false)
        assertTrue(c.contains("'\$(id)'"), "the needle must not be evaluated by the shell")
    }

    // ---- 14: follow ----

    @Test fun tailClampsAnAbsurdLineCount() {
        assertTrue(RemoteOps.tailCommand("/x", 10_000_000).contains("-n ${RemoteOps.MAX_TAIL_LINES}"))
        assertTrue(RemoteOps.tailCommand("/x", 0).contains("-n 1"))
    }

    @Test fun tailSinceUsesOneBasedByteOffset() {
        // tail -c +N is 1-based; asking from byte 0 must be +1, not +0.
        assertTrue(RemoteOps.tailSinceCommand("/x", 0).contains("-c +1"))
        assertTrue(RemoteOps.tailSinceCommand("/x", 100).contains("-c +101"))
    }

    // ---- 16: search ----

    @Test fun grepIsFixedStringNotRegex() {
        val c = RemoteOps.grepCommand("/var", "a.b[c]", false)
        assertTrue(c.contains("-rnIF"), "without -F the user's literal becomes a regex")
    }

    @Test fun caseInsensitiveSearchAddsTheFlag() {
        assertTrue(RemoteOps.grepCommand("/v", "x", true).contains("-rnIFi"))
        assertFalse(RemoteOps.grepCommand("/v", "x", false).contains("-rnIFi"))
    }

    @Test fun parsesGrepHits() {
        val out = "/var/log/app.log:42:connection refused\n/etc/nginx.conf:7:listen 80;"
        val hits = RemoteOps.parseGrepOutput(out)
        assertEquals(2, hits.size)
        assertEquals(RemoteOps.SearchHit("/var/log/app.log", 42, "connection refused"), hits[0])
        assertEquals(7, hits[1].line)
    }

    @Test fun aPathContainingAColonDoesNotLoseItsName() {
        // The reason this parses from the right instead of split(":").
        val hits = RemoteOps.parseGrepOutput("/tmp/a:b/file.txt:12:hit here")
        assertEquals(1, hits.size)
        assertEquals("/tmp/a:b/file.txt", hits[0].path)
        assertEquals(12, hits[0].line)
        assertEquals("hit here", hits[0].text)
    }

    @Test fun matchTextContainingAColonSurvives() {
        val hits = RemoteOps.parseGrepOutput("/etc/hosts:3:127.0.0.1 localhost:8080")
        assertEquals("127.0.0.1 localhost:8080", hits[0].text)
    }

    @Test fun garbageLinesAreSkippedNotCrashed() {
        assertTrue(RemoteOps.parseGrepOutput("grep: /root: Permission denied").isEmpty())
        assertTrue(RemoteOps.parseGrepOutput("").isEmpty())
        assertTrue(RemoteOps.parseGrepOutput("/a/b:notanumber:text").isEmpty())
    }

    // ---- 17: run against a selection ----

    @Test fun placeholderIsSubstitutedPerPath() {
        val c = RemoteOps.buildSelectionCommand("gzip {}", listOf("/a", "/b"))!!
        assertEquals("gzip '/a' && gzip '/b'", c)
    }

    @Test fun aTemplateWithoutAPlaceholderGetsThePathsAppended() {
        assertEquals("chmod +x '/a' '/b'", RemoteOps.buildSelectionCommand("chmod +x", listOf("/a", "/b")))
    }

    @Test fun commandsAreChainedSoAFailureStopsTheRest() {
        assertTrue(RemoteOps.buildSelectionCommand("rm {}", listOf("/a", "/b"))!!.contains("&&"))
    }

    @Test fun anEmptyTemplateOrSelectionProducesNothing() {
        assertNull(RemoteOps.buildSelectionCommand("", listOf("/a")))
        assertNull(RemoteOps.buildSelectionCommand("   ", listOf("/a")))
        assertNull(RemoteOps.buildSelectionCommand("ls", emptyList()))
    }

    // ---- 18: extract ----

    @Test fun recognisesEveryArchiveFormItSupports() {
        for (n in listOf("a.tar.gz", "a.tgz", "a.tar.bz2", "a.tbz2", "a.tar.xz", "a.txz", "a.tar", "a.zip")) {
            assertTrue(RemoteOps.isExtractable(n), "$n should be extractable")
        }
    }

    @Test fun refusesWhatItCannotUnpack() {
        for (n in listOf("a.txt", "a.rar", "a.7z", "archive", "a.gz")) {
            assertFalse(RemoteOps.isExtractable(n), "$n should not be extractable")
            assertNull(RemoteOps.extractCommand("/tmp/$n", "/dest"))
        }
    }

    @Test fun extensionMatchingIsCaseInsensitive() {
        assertTrue(RemoteOps.isExtractable("BACKUP.TAR.GZ"))
    }

    @Test fun eachArchiveKindGetsTheRightDecompressor() {
        assertTrue(RemoteOps.extractCommand("/a.tar.gz", "/d")!!.contains("tar -xzf"))
        assertTrue(RemoteOps.extractCommand("/a.tar.bz2", "/d")!!.contains("tar -xjf"))
        assertTrue(RemoteOps.extractCommand("/a.tar.xz", "/d")!!.contains("tar -xJf"))
        assertTrue(RemoteOps.extractCommand("/a.zip", "/d")!!.contains("unzip -o -q"))
    }

    @Test fun extractAlwaysTargetsTheDestinationExplicitly() {
        // -C / -d rather than a cd, so a failed directory change cannot unpack in place.
        val c = RemoteOps.extractCommand("/a.tar.gz", "/dest")!!
        assertTrue(c.startsWith("mkdir -p '/dest' &&"))
        assertTrue(c.contains("-C '/dest'"))
    }

    // ---- 19: ownership ----

    @Test fun ownerOnlyAndOwnerGroupBothWork() {
        assertTrue(RemoteOps.chownCommand("/v", "www-data", null, false)!!.contains("'www-data' '/v'"))
        assertTrue(RemoteOps.chownCommand("/v", "www-data", "www-data", false)!!.contains("'www-data:www-data'"))
    }

    @Test fun recursiveAddsTheFlag() {
        assertTrue(RemoteOps.chownCommand("/v", "root", null, true)!!.contains("chown -R "))
    }

    @Test fun anInvalidPrincipalIsRejectedBeforeItReachesTheShell() {
        assertNull(RemoteOps.chownCommand("/v", "root; rm -rf /", null, false))
        assertNull(RemoteOps.chownCommand("/v", "", null, false))
        assertNull(RemoteOps.chownCommand("/v", "a".repeat(33), null, false))
        assertNull(RemoteOps.chownCommand("/v", "ok", "bad group", false))
    }

    @Test fun ordinaryPrincipalNamesAreAccepted() {
        for (n in listOf("root", "www-data", "user_1", "deploy.svc")) {
            assertTrue(RemoteOps.isValidPrincipal(n), "$n should be valid")
        }
    }

    // ---- 21: links ----

    @Test fun symlinkAndHardLinkDifferOnlyByTheFlag() {
        assertTrue(RemoteOps.symlinkCommand("/t", "/l").startsWith("ln -s "))
        assertTrue(RemoteOps.hardLinkCommand("/t", "/l").startsWith("ln '"))
    }

    @Test fun duplicatePreservesAttributes() {
        assertTrue(RemoteOps.duplicateCommand("/a", "/b").startsWith("cp -a "))
    }
}
