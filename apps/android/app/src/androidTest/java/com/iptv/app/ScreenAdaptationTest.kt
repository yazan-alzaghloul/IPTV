package com.iptv.app

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenAdaptationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun arabicRtlLoginSupportsLargeSystemTextAndReachablePrimaryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SecureCredentialStore(context).clear()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var submitted = false
            scenario.onActivity { activity ->
                val configuration = Configuration(activity.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("ar")); setLayoutDirection(java.util.Locale.forLanguageTag("ar")) }
                val arabic = activity.createConfigurationContext(configuration)
                activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalContext provides arabic, LocalLayoutDirection provides LayoutDirection.Rtl,
                        LocalDensity provides Density(density.density, 1.8f)) {
                        IptvTheme { Surface { LoginScreen(false, null) { _, _, _, _, _ -> submitted = true } } }
                    }
                }
            }
            compose.onNodeWithText("قنواتك. في مكان واحد.").assertExists()
            compose.onNodeWithText("اسم المستخدم").performScrollTo().performTextInput("viewer")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("اتصال", substring = false).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { submitted }
            val directory = java.io.File(context.filesDir, "screenshots").apply { mkdirs() }
            java.io.File(directory, "arabic-large-text.png").outputStream().use {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
