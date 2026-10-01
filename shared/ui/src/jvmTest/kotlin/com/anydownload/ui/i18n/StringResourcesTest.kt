package com.anydownload.ui.i18n

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.anydownload.ui.generated.resources.Res
import com.anydownload.ui.generated.resources.sample
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class StringResourcesTest {

    @Test
    fun sampleStringResolvesFromRes() = runComposeUiTest {
        setContent {
            androidx.compose.material3.Text(stringResource(Res.string.sample))
        }
        onNodeWithText("Sample").assertExists()
    }
}
