package com.nexora.tv

import android.app.Instrumentation
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun remoteOpensAppsAndBackReturnsHome() {
        compose.waitForIdle()
        val device: Instrumentation = InstrumentationRegistry.getInstrumentation()
        device.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        device.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Refresh app list").assertIsDisplayed()
        device.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Favorite Apps").assertExists()
    }
}
