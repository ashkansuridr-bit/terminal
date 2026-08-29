package app.terminalssh.secure.sftp

import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The provider's registration and its security contract.
 *
 * The behaviour that matters most here is what it does *not* expose: a DocumentsProvider
 * is reachable from other processes, so a root for a host with no live session would mean
 * authenticating outside any UI the user can see.
 */
@RunWith(AndroidJUnit4::class)
class SftpDocumentsProviderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = "${context.packageName.removeSuffix(".debug")}.documents"

    @Test fun theProviderIsRegisteredAndDiscoverable() {
        val info = context.packageManager.resolveContentProvider(providerAuthority(), 0)
        assertNotNull("the documents provider must be registered in the manifest", info)
    }

    @Test fun theProviderIsGatedBehindTheSystemOnlyPermission() {
        val info = context.packageManager.resolveContentProvider(providerAuthority(), 0)!!
        // Exported is required for DocumentsUI to reach it, so the permission is the
        // whole defence: MANAGE_DOCUMENTS is signature-level and only the platform holds it.
        assertTrue("a documents provider must be exported to be usable", info.exported)
        assertEquals(
            "an exported provider without MANAGE_DOCUMENTS would be open to any app",
            "android.permission.MANAGE_DOCUMENTS",
            info.readPermission ?: info.writePermission,
        )
    }

    @Test fun thisAppDoesNotHoldManageDocumentsItself() {
        // Proves the gate is real: even the owning app cannot bind to it without the
        // platform signature, which is why the test below queries through the provider
        // object rather than the resolver.
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission("android.permission.MANAGE_DOCUMENTS"),
        )
    }

    @Test fun noLiveSessionMeansNoRootsAreExposed() {
        val provider = attachedProvider()
        provider.queryRoots(null).use { cursor ->
            assertEquals(
                "a host with no connected session must not appear as a root",
                0, cursor.count,
            )
        }
    }

    @Test fun anUnknownDocumentIdIsRejectedRatherThanGuessed() {
        val provider = attachedProvider()
        for (bad in listOf("", "no-separator", "missing::/path")) {
            var threw = false
            try {
                provider.queryDocument(bad, null)
            } catch (expected: Exception) {
                threw = true
            }
            assertTrue("document id '$bad' should not resolve", threw)
        }
    }

    @Test fun theRootUriCanBeBuiltForTheAuthority() {
        val uri: Uri = DocumentsContract.buildRootsUri(providerAuthority())
        assertEquals(providerAuthority(), uri.authority)
        assertFalse(uri.toString().isEmpty())
    }

    /** DocumentsProvider.attachInfo dereferences ProviderInfo.authority, so it must be real. */
    private fun attachedProvider(): SftpDocumentsProvider =
        SftpDocumentsProvider().apply {
            // attachInfo enforces the same contract the manifest declares: authority set
            // and exported true, with grantUriPermissions. Mirror the real declaration.
            attachInfo(
                context,
                ProviderInfo().apply {
                    authority = providerAuthority()
                    exported = true
                    grantUriPermissions = true
                    readPermission = "android.permission.MANAGE_DOCUMENTS"
                    writePermission = "android.permission.MANAGE_DOCUMENTS"
                },
            )
        }

    private fun providerAuthority(): String =
        // The debug build carries an applicationIdSuffix; the authority does not.
        "app.terminalssh.secure.documents"
}
