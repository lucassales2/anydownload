package com.anydownload.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.anydownload.core.AppGraph
import com.anydownload.core.BrowserChoice
import com.anydownload.core.BrowserCookieImport
import com.anydownload.core.CookieErrorReason
import com.anydownload.core.CookieOperationResult
import com.anydownload.core.CookieStatus
import com.anydownload.core.CookieStore
import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.AppSettingsDefaults
import com.anydownload.core.domain.ThemePreference
import com.anydownload.core.fake.InMemoryAppGraph
import com.anydownload.core.fake.InMemorySettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless click-through of the Settings screen. It stands in for the T-031
 * desktop click-through where the host cannot send OS input.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsUiTest {

    @Test
    fun sectionsThemeTemplateConcurrencyAndFolderThroughTheUi() = runComposeUiTest {
        val settingsRepository = InMemorySettingsRepository()
        val base = InMemoryAppGraph(settings = settingsRepository)
        val graph = object : AppGraph by base {
            override val pickFolder: () -> String? = { "/tmp/anydownlod-downloads" }
        }
        setContent { App(graph) }

        onNodeWithText("Settings").performClick()
        listOf("Storage", "Filenames", "Queue", "Cookies", "Presets", "Appearance", "Tools").forEach { section ->
            onNodeWithText(section).assertExists()
        }
        onNodeWithTag("settings-tool-ytdlp").assertExists()
        onNodeWithTag("settings-tool-ffmpeg").assertExists()
        onNodeWithTag("settings-tool-kotlin").assertTextContains("2026.08.19", substring = true)

        // A bad template stays inline and is not written.
        onNodeWithTag("settings-template-output").performScrollTo()
            .performTextReplacement("../%(title)s.%(ext)s")
        onNodeWithText("The template cannot contain a \"..\" segment.").assertExists()
        assertEquals(AppSettingsDefaults.OUTPUT_TEMPLATE, settingsRepository.settings.value.outputTemplate)

        // A valid template saves.
        onNodeWithTag("settings-template-output").performTextReplacement("%(title)s-%(id)s.%(ext)s")
        assertEquals("%(title)s-%(id)s.%(ext)s", settingsRepository.settings.value.outputTemplate)

        // Concurrency below 1 is rejected; 2 is accepted.
        onNodeWithTag("settings-concurrency").performScrollTo().performTextReplacement("0")
        onNodeWithText("Concurrent downloads must be 1 or more.").assertExists()
        assertEquals(3, settingsRepository.settings.value.maxConcurrentDownloads)
        onNodeWithTag("settings-concurrency").performTextReplacement("2")
        assertEquals(2, settingsRepository.settings.value.maxConcurrentDownloads)

        // The host folder picker stores the chosen directory.
        onNodeWithTag("settings-choose-folder").performScrollTo().performClick()
        assertEquals("/tmp/anydownlod-downloads", settingsRepository.settings.value.downloadRoot)

        // Theme writes through the shared settings and reaches the header.
        onNodeWithTag("settings-theme-dark").performScrollTo().performClick()
        assertEquals(ThemePreference.DARK, settingsRepository.settings.value.theme)
        onNodeWithTag("settings-close").performClick()
        onNodeWithText("Dark").assertIsSelected()
    }

    @Test
    fun cookiesImportAndDeleteGoThroughTheHostStoreWithoutContents() = runComposeUiTest {
        val settingsRepository = InMemorySettingsRepository()
        var importedPath: String? = null
        var deleted = false
        var hostStatus: CookieStatus = CookieStatus.NotConfigured
        val base = InMemoryAppGraph(settings = settingsRepository)
        val graph = object : AppGraph by base {
            override val pickCookieFile: suspend () -> String? = { "/tmp/fixture-cookies.txt" }
            override val cookieStore: CookieStore = object : CookieStore {
                override fun import(sourcePath: String): CookieOperationResult {
                    importedPath = sourcePath
                    hostStatus = CookieStatus.Configured
                    return CookieOperationResult(success = true)
                }

                override fun importText(text: String): CookieOperationResult {
                    hostStatus = CookieStatus.Configured
                    return CookieOperationResult(success = true)
                }

                override fun delete(): CookieOperationResult {
                    deleted = true
                    hostStatus = CookieStatus.NotConfigured
                    return CookieOperationResult(success = true)
                }

                override fun storedFilePath(): String? = null

                override fun status(): CookieStatus = hostStatus
            }
        }
        setContent { App(graph) }

        onNodeWithText("Settings").performClick()
        onNodeWithText("Status: Not configured").assertExists()

        onNodeWithTag("settings-cookies-import").performScrollTo().performClick()
        waitForIdle()
        assertEquals("/tmp/fixture-cookies.txt", importedPath)
        assertTrue(settingsRepository.settings.value.cookiesConfigured)
        onNodeWithText("Cookie file imported.").assertExists()
        onNodeWithText("Status: Configured").assertExists()

        onNodeWithTag("settings-cookies-delete").performScrollTo().performClick()
        onNodeWithTag("settings-confirm-cookie-delete").performClick()
        assertTrue(deleted)
        assertFalse(settingsRepository.settings.value.cookiesConfigured)
        onNodeWithText("Status: Not configured").assertExists()
    }

    @Test
    fun anErrorStatusShowsTheReasonAndEnablesDelete() = runComposeUiTest {
        val settingsRepository = InMemorySettingsRepository(AppSettings(cookiesConfigured = true))
        val base = InMemoryAppGraph(settings = settingsRepository)
        val graph = object : AppGraph by base {
            override val cookieStore: CookieStore = object : CookieStore {
                override fun import(sourcePath: String): CookieOperationResult =
                    CookieOperationResult(success = true)

                override fun importText(text: String): CookieOperationResult =
                    CookieOperationResult(success = true)

                override fun delete(): CookieOperationResult = CookieOperationResult(success = true)

                override fun storedFilePath(): String? = null

                override fun status(): CookieStatus = CookieStatus.Error(CookieErrorReason.ALL_EXPIRED)
            }
        }
        setContent { App(graph) }

        onNodeWithText("Settings").performClick()
        onNodeWithText(
            "Status: Error: the stored file has no unexpired cookies. Replace or delete it.",
        ).assertExists()
        onNodeWithTag("settings-cookies-delete").performScrollTo().assertIsEnabled()
        assertFalse(settingsRepository.settings.value.cookiesConfigured)
    }

    @Test
    fun browserImportNeedsTheConsentStepAndUsesTheChosenBrowser() = runComposeUiTest {
        val settingsRepository = InMemorySettingsRepository()
        var chosen: BrowserChoice? = null
        var hostStatus: CookieStatus = CookieStatus.NotConfigured
        val base = InMemoryAppGraph(settings = settingsRepository)
        val graph = object : AppGraph by base {
            override val cookieStore: CookieStore = object : CookieStore {
                override fun import(sourcePath: String): CookieOperationResult =
                    CookieOperationResult(success = true)

                override fun importText(text: String): CookieOperationResult {
                    hostStatus = CookieStatus.Configured
                    return CookieOperationResult(success = true)
                }

                override fun delete(): CookieOperationResult = CookieOperationResult(success = true)

                override fun storedFilePath(): String? = null

                override fun status(): CookieStatus = hostStatus
            }
            override val browserCookieImport: BrowserCookieImport = BrowserCookieImport { browser ->
                chosen = browser
                hostStatus = CookieStatus.Configured
                CookieOperationResult(success = true)
            }
        }
        setContent { App(graph) }

        onNodeWithText("Settings").performClick()
        onNodeWithTag("settings-cookies-browser").performScrollTo().performClick()

        // Consent comes first: nothing is read before the user chooses.
        assertNull(chosen)
        onNodeWithText("Copy cookies from a browser?").assertExists()

        onNodeWithTag("settings-browser-firefox").performClick()
        waitForIdle()

        assertEquals(BrowserChoice.FIREFOX, chosen)
        assertTrue(settingsRepository.settings.value.cookiesConfigured)
        onNodeWithText("Cookie file imported.").assertExists()
        onNodeWithText("Status: Configured").assertExists()
    }

    @Test
    fun presetsAddReorderAndRemoveThroughTheUi() = runComposeUiTest {
        val settingsRepository = InMemorySettingsRepository()
        setContent { App(InMemoryAppGraph(settings = settingsRepository)) }
        onNodeWithText("Settings").performClick()

        onNodeWithTag("settings-add-preset").performScrollTo().performClick()
        onNodeWithTag("settings-preset-name").performTextInput("Small")
        onNodeWithTag("settings-preset-option-writeMetadata").performClick()
        onNodeWithTag("settings-preset-save").performClick()

        val small = settingsRepository.settings.value.presets.single()
        assertEquals("Small", small.name)
        assertEquals(mapOf("writeMetadata" to "true"), small.options)

        onNodeWithTag("settings-add-preset").performScrollTo().performClick()
        onNodeWithTag("settings-preset-name").performTextInput("Large")
        onNodeWithTag("settings-preset-save").performClick()

        val large = settingsRepository.settings.value.presets.first { it.name == "Large" }
        onNodeWithTag("settings-preset-up-${large.id}").performScrollTo().performClick()
        assertEquals(listOf("Large", "Small"), settingsRepository.settings.value.presets.map { it.name })

        onNodeWithTag("settings-preset-remove-${large.id}").performScrollTo().performClick()
        assertEquals(listOf("Small"), settingsRepository.settings.value.presets.map { it.name })
    }

    @Test
    fun restoreDefaultsKeepsFolderCookiesAndPresets() = runComposeUiTest {
        val settingsRepository = InMemorySettingsRepository(
            AppSettings(downloadRoot = "/tmp/keep-me", cookiesConfigured = true),
        )
        settingsRepository.addPreset("Keep")
        val base = InMemoryAppGraph(settings = settingsRepository)
        // A configured host file: Settings syncs the flag from this status.
        val graph = object : AppGraph by base {
            override val cookieStore: CookieStore = object : CookieStore {
                override fun import(sourcePath: String): CookieOperationResult =
                    CookieOperationResult(success = true)

                override fun importText(text: String): CookieOperationResult =
                    CookieOperationResult(success = true)

                override fun delete(): CookieOperationResult = CookieOperationResult(success = true)

                override fun storedFilePath(): String? = null

                override fun status(): CookieStatus = CookieStatus.Configured
            }
        }
        setContent { App(graph) }

        onNodeWithText("Settings").performClick()
        onNodeWithTag("settings-restore-defaults").performScrollTo().performClick()
        onNodeWithText(
            "Templates, queue limits, subscription interval, and theme return to their defaults. " +
                "The download folder, cookie status, and presets stay.",
        ).assertExists()
        onNodeWithTag("settings-confirm-restore").performClick()

        val settings = settingsRepository.settings.value
        assertEquals(AppSettingsDefaults.OUTPUT_TEMPLATE, settings.outputTemplate)
        assertEquals(AppSettingsDefaults.MAX_CONCURRENT_DOWNLOADS, settings.maxConcurrentDownloads)
        assertEquals("/tmp/keep-me", settings.downloadRoot)
        assertTrue(settings.cookiesConfigured)
        assertEquals(listOf("Keep"), settings.presets.map { it.name })
    }
}
