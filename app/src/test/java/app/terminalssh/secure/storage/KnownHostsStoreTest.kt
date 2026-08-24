package app.terminalssh.secure.storage

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class KnownHostsStoreTest {
    @Test
    fun identityIsStableAcrossDefaultLocales() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val turkishIdentity = knownHostIdentity("INTERNAL.EXAMPLE", 22)

            Locale.setDefault(Locale.ENGLISH)
            val englishIdentity = knownHostIdentity("INTERNAL.EXAMPLE", 22)

            assertEquals("host.internal.example:22", turkishIdentity)
            assertEquals(englishIdentity, turkishIdentity)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun identityPreservesIpv6AddressAndSeparatesPort() {
        assertEquals("host.2001:db8::1:2222", knownHostIdentity("2001:DB8::1", 2222))
    }
}
