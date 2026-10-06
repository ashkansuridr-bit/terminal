package app.terminalssh.secure.sftp

import android.content.ComponentName
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FailedSaveActivityTest {
    @Test fun enabledLockRemainsRequiredAcrossRecreationWithoutAuthentication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as app.terminalssh.secure.TerminalApp
        val originalSetting = app.settings.biometricLock
        try {
            app.settings.biometricLock = true
            ActivityScenario.launch(FailedSaveActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(isUnlocked(activity))
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertFalse(isUnlocked(activity))
                }
            }
        } finally { app.settings.biometricLock = originalSetting }
    }

    private fun isUnlocked(activity: FailedSaveActivity): Boolean =
        FailedSaveActivity::class.java.getDeclaredField("unlocked").let {
            it.isAccessible = true
            it.getBoolean(activity)
        }

    @Test fun recoveryActivityIsInternalAndScreenshotProtected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(ComponentName(context, FailedSaveActivity::class.java), 0)
        assertFalse(info.exported)
        ActivityScenario.launch(FailedSaveActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }
}
