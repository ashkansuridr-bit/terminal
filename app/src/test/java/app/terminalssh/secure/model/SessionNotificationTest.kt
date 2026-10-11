package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionNotificationTest {

    private val host = HostProfile(
        id = "h1",
        label = "prod-web",
        host = "web.example.com",
        username = "topsecret-user",
        auth = AuthMethod.Password(vaultRef = "vault://super-secret-ref"),
    )

    @Test fun contentCarriesOnlyTheHostLabelAndAddress() {
        val content = SessionNotification.build(SessionNotification.Event.DISCONNECTED, host, attempt = 3)
        assertEquals("prod-web", content.hostLabel)
        assertEquals("web.example.com", content.hostAddress)
        assertEquals(3, content.attempt)
        // The username and the vault reference must never reach a notification payload.
        val rendered = "${content.hostLabel} ${content.hostAddress} ${content.tag} ${content.attempt}"
        assertFalse(rendered.contains("topsecret-user"))
        assertFalse(rendered.contains("vault"))
        assertFalse(rendered.contains("super-secret-ref"))
    }

    @Test fun aStableTagLetsAnUpdateReplaceTheSameNotification() {
        assertEquals(SessionNotification.tagFor("h1"), SessionNotification.build(SessionNotification.Event.RECONNECTING, host).tag)
        assertTrue(SessionNotification.tagFor("h1").contains("h1"))
    }

    @Test fun titleAndBodyAreLocalizationKeysNotEnglishProse() {
        for (event in SessionNotification.Event.entries) {
            assertTrue(SessionNotification.titleKey(event).startsWith("notif.title."), SessionNotification.titleKey(event))
            assertTrue(SessionNotification.bodyKey(event).startsWith("notif.body."), SessionNotification.bodyKey(event))
        }
    }

    @Test fun disabledNotificationsAreNeverSent() {
        for (event in SessionNotification.Event.entries) {
            assertFalse(SessionNotification.shouldNotify(event, enabled = false, foreground = false))
        }
    }

    @Test fun disconnectAndFailureNotifyByDefault() {
        assertTrue(SessionNotification.shouldNotify(SessionNotification.Event.DISCONNECTED, true, foreground = false))
        assertTrue(SessionNotification.shouldNotify(SessionNotification.Event.RECONNECT_FAILED, true, foreground = false))
    }

    @Test fun reconnectSuccessIsSilentByDefault() {
        assertFalse(SessionNotification.shouldNotify(SessionNotification.Event.RECONNECTED, true, foreground = false))
    }

    @Test fun reconnectSuccessCanBeOptedIntoWhenBackgrounded() {
        val policy = SessionNotification.Policy(notifyOnReconnectSuccess = true)
        assertTrue(SessionNotification.shouldNotify(SessionNotification.Event.RECONNECTED, true, foreground = false, policy = policy))
        assertFalse(SessionNotification.shouldNotify(SessionNotification.Event.RECONNECTED, true, foreground = true, policy = policy))
    }

    @Test fun reconnectInProgressIsNeverNotified() {
        val policy = SessionNotification.Policy(notifyOnReconnectSuccess = true)
        assertFalse(SessionNotification.shouldNotify(SessionNotification.Event.RECONNECTING, true, foreground = false, policy = policy))
    }

    @Test fun disconnectNotificationsCanBeTurnedOff() {
        val policy = SessionNotification.Policy(notifyOnDisconnect = false)
        assertFalse(SessionNotification.shouldNotify(SessionNotification.Event.DISCONNECTED, true, foreground = false, policy = policy))
    }
}
