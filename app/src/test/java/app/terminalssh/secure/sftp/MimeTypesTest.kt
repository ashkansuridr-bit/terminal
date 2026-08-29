package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals

class MimeTypesTest {

    @Test fun commonDeveloperFilesMapToUsefulTypes() {
        assertEquals("application/json", MimeTypes.forFileName("package.json"))
        assertEquals("text/yaml", MimeTypes.forFileName("docker-compose.yml"))
        assertEquals("text/x-shellscript", MimeTypes.forFileName("deploy.sh"))
        assertEquals("text/plain", MimeTypes.forFileName("nginx.conf"))
        assertEquals("application/pdf", MimeTypes.forFileName("invoice.pdf"))
    }

    @Test fun matchingIsCaseInsensitive() {
        assertEquals("image/png", MimeTypes.forFileName("SCREENSHOT.PNG"))
    }

    @Test fun aDotfileHasNoExtension() {
        // ".bashrc" is not a "bashrc" file; treating it as one mislabels every dotfile.
        assertEquals(MimeTypes.FALLBACK, MimeTypes.forFileName(".bashrc"))
        assertEquals(MimeTypes.FALLBACK, MimeTypes.forFileName(".env"))
    }

    @Test fun aDottedDotfileStillResolves() {
        assertEquals("text/yaml", MimeTypes.forFileName(".github.yml"))
    }

    @Test fun unknownAndMissingExtensionsFallBackToBinary() {
        // Not text/plain: an editor opening a binary as text corrupts it on save.
        assertEquals(MimeTypes.FALLBACK, MimeTypes.forFileName("Makefile"))
        assertEquals(MimeTypes.FALLBACK, MimeTypes.forFileName("core.dump"))
        assertEquals(MimeTypes.FALLBACK, MimeTypes.forFileName("archive."))
        assertEquals(MimeTypes.FALLBACK, MimeTypes.forFileName(""))
    }

    @Test fun privateKeyMaterialIsNotAdvertisedAsText() {
        assertEquals(MimeTypes.FALLBACK, MimeTypes.forFileName("id_ed25519.key"))
    }
}
