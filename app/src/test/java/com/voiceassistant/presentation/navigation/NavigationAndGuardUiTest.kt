package com.voiceassistant.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import com.voiceassistant.presentation.components.VaScreenHeader
import com.voiceassistant.presentation.home.AutoGuardSystemTtsDialog
import com.voiceassistant.presentation.theme.VoiceAssistantTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose + Robolectric: 5-tab bottom nav, Settings hides bar, Auto-Guard dialog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationAndGuardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigationExposesFiveTabs() {
        composeRule.setContent {
            VoiceAssistantTheme(darkTheme = true) {
                NavShellUnderTest()
            }
        }

        assertEquals(5, bottomDestinations.size)
        bottomDestinations.forEach { dest ->
            composeRule.onNodeWithTag("nav_${dest.route}").assertIsDisplayed()
        }
    }

    @Test
    fun openingSettingsHidesBottomBar() {
        composeRule.setContent {
            VoiceAssistantTheme(darkTheme = true) {
                NavShellUnderTest()
            }
        }

        composeRule.onNodeWithTag("bottom_nav").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onAllNodesWithTag("bottom_nav").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        assertTrue(shouldHideBottomBar(Dest.Settings.route))
    }

    @Test
    fun autoGuardDialogAppearsWhenModelMissing() {
        composeRule.setContent {
            VoiceAssistantTheme(darkTheme = true) {
                AutoGuardSystemTtsDialog(
                    onDismiss = {},
                    onOpenModelManager = {}
                )
            }
        }

        composeRule.onNodeWithText("Using system voice").assertIsDisplayed()
        composeRule.onNodeWithText("Open packs").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun shouldHideBottomBarForOverlayRoutes() {
        assertTrue(shouldHideBottomBar(Dest.Settings.route))
        assertTrue(shouldHideBottomBar("conversation"))
        assertTrue(shouldHideBottomBar("model_manager"))
        assertTrue(shouldHideBottomBar("project_editor/abc"))
        assertFalse(shouldHideBottomBar(Dest.Home.route))
        assertFalse(shouldHideBottomBar(Dest.Translate.route))
    }
}

@Composable
private fun NavShellUnderTest() {
    var currentRoute by remember { mutableStateOf(Dest.Home.route) }
    val hideBottom = shouldHideBottomBar(currentRoute)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideBottom) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav"),
                    tonalElevation = 0.dp
                ) {
                    bottomDestinations.forEach { dest ->
                        val label = stringResource(dest.labelRes)
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav_${dest.route}"),
                            selected = currentRoute == dest.route,
                            onClick = { currentRoute = dest.route },
                            icon = { Icon(dest.icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VaScreenHeader(onOpenSettings = { currentRoute = Dest.Settings.route }) {
                Text("VoiceAssistant")
            }
            if (currentRoute == Dest.Settings.route) {
                Text(
                    text = "Settings body",
                    modifier = Modifier.testTag("settings_screen")
                )
            }
        }
    }
}
