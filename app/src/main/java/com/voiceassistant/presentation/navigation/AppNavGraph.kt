package com.voiceassistant.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voiceassistant.R
import com.voiceassistant.presentation.camera.CameraScannerScreen
import com.voiceassistant.presentation.home.HomeScreen
import com.voiceassistant.presentation.project.ProjectEditorScreen
import com.voiceassistant.presentation.search.SearchAssistantScreen
import com.voiceassistant.presentation.settings.SettingsScreen
import com.voiceassistant.presentation.settings.manual.UserManualScreen
import com.voiceassistant.presentation.settings.model.ModelManagerScreen
import com.voiceassistant.presentation.studio.StudioHubScreen
import com.voiceassistant.presentation.translate.ConversationScreen
import com.voiceassistant.presentation.translate.TranslateHubScreen
import com.voiceassistant.presentation.translate.TranslateHubViewModel
import com.voiceassistant.presentation.voice.CloneVoiceScreen
import com.voiceassistant.presentation.voice.VoiceLibraryScreen
import kotlinx.coroutines.flow.map

sealed class Dest(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    data object Home : Dest("home", R.string.nav_tts, Icons.Outlined.Home)
    data object Translate : Dest("translate", R.string.nav_translate, Icons.Outlined.Translate)
    data object Studio : Dest("studio", R.string.nav_studio, Icons.Outlined.Timeline)
    data object Voices : Dest("voices", R.string.nav_voices, Icons.Outlined.Mic)
    data object Search : Dest("search", R.string.nav_search, Icons.Outlined.Search)
    data object Settings : Dest("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

private const val CAMERA_SCANNER = "camera_scanner"
private const val CONVERSATION = "conversation"
private const val USER_MANUAL = "user_manual"
private const val MODEL_MANAGER = "model_manager"
private const val PENDING_TTS_TEXT = "pending_tts_text"
private const val PENDING_TRANSLATE_TEXT = "pending_translate_text"

/** Material 3: keep 3–5 bottom destinations. Settings lives in the top header. */
internal val bottomDestinations = listOf(
    Dest.Home, Dest.Translate, Dest.Studio, Dest.Voices, Dest.Search
)

/** Routes that hide the bottom navigation bar (Settings, editors, overlays). */
internal fun shouldHideBottomBar(route: String?): Boolean =
    route?.startsWith("project_editor") == true ||
        route == "clone_voice" ||
        route == CAMERA_SCANNER ||
        route == CONVERSATION ||
        route == USER_MANUAL ||
        route == Dest.Settings.route ||
        route == MODEL_MANAGER

@Composable
fun AppNavGraph(
    navViewModel: AppNavViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val hideBottom = shouldHideBottomBar(current?.route)

    val ingressTts by navViewModel.pendingTextIngress.pendingTts.collectAsStateWithLifecycle()
    val ingressTranslate by navViewModel.pendingTextIngress.pendingTranslate.collectAsStateWithLifecycle()

    LaunchedEffect(ingressTts) {
        val text = navViewModel.pendingTextIngress.consumeTts() ?: return@LaunchedEffect
        runCatching {
            navController.getBackStackEntry(Dest.Home.route)
                .savedStateHandle[PENDING_TTS_TEXT] = text
        }
        navController.navigate(Dest.Home.route) {
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(ingressTranslate) {
        val text = navViewModel.pendingTextIngress.consumeTranslate() ?: return@LaunchedEffect
        runCatching {
            navController.getBackStackEntry(Dest.Translate.route)
                .savedStateHandle[PENDING_TRANSLATE_TEXT] = text
        }
        navController.navigate(Dest.Translate.route) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSettings() {
        navController.navigate(Dest.Settings.route) {
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideBottom) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomDestinations.forEach { dest ->
                        val selected = current?.hierarchy?.any { it.route == dest.route } == true
                        val label = stringResource(dest.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = label) },
                            label = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Home.route) { entry ->
                val pending by entry.savedStateHandle
                    .getStateFlow(PENDING_TTS_TEXT, "")
                    .map { it.ifBlank { null } }
                    .collectAsStateWithLifecycle(initialValue = null)
                HomeScreen(
                    pendingScannerText = pending,
                    onPendingTextConsumed = {
                        entry.savedStateHandle[PENDING_TTS_TEXT] = ""
                    },
                    onOpenSettings = { openSettings() },
                    onOpenModelManager = { navController.navigate(MODEL_MANAGER) }
                )
            }
            composable(Dest.Translate.route) { entry ->
                val pending by entry.savedStateHandle
                    .getStateFlow(PENDING_TRANSLATE_TEXT, "")
                    .map { it.ifBlank { null } }
                    .collectAsStateWithLifecycle(initialValue = null)
                val viewModel: TranslateHubViewModel = hiltViewModel()
                LaunchedEffect(pending) {
                    val incoming = pending
                    if (!incoming.isNullOrBlank()) {
                        viewModel.applyIncomingText(incoming)
                        entry.savedStateHandle[PENDING_TRANSLATE_TEXT] = ""
                    }
                }
                TranslateHubScreen(
                    onOpenCamera = { navController.navigate(CAMERA_SCANNER) },
                    onOpenConversation = { navController.navigate(CONVERSATION) },
                    onSendToTts = { text ->
                        navController
                            .getBackStackEntry(Dest.Home.route)
                            .savedStateHandle[PENDING_TTS_TEXT] = text
                        navController.navigate(Dest.Home.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = { openSettings() },
                    onOpenModelManager = { navController.navigate(MODEL_MANAGER) },
                    viewModel = viewModel
                )
            }
            composable(CONVERSATION) {
                ConversationScreen(onClose = { navController.popBackStack() })
            }
            composable(Dest.Studio.route) {
                StudioHubScreen(
                    onOpenProject = { id -> navController.navigate("project_editor/$id") },
                    onCreateProject = { navController.navigate("project_editor/new") },
                    onOpenSettings = { openSettings() }
                )
            }
            composable(
                route = "project_editor/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                ProjectEditorScreen(
                    projectId = entry.arguments?.getString("projectId") ?: "new",
                    onBack = { navController.popBackStack() },
                    onOpenTimeline = {
                        navController.navigate(Dest.Studio.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Dest.Voices.route) {
                VoiceLibraryScreen(
                    onCloneVoice = { navController.navigate("clone_voice") },
                    onOpenSettings = { openSettings() }
                )
            }
            composable("clone_voice") {
                CloneVoiceScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.Search.route) {
                SearchAssistantScreen(onOpenSettings = { openSettings() })
            }
            composable(Dest.Settings.route) {
                SettingsScreen(
                    onOpenManual = { navController.navigate(USER_MANUAL) },
                    onOpenModelManager = { navController.navigate(MODEL_MANAGER) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(MODEL_MANAGER) {
                ModelManagerScreen(onBack = { navController.popBackStack() })
            }
            composable(USER_MANUAL) {
                UserManualScreen(onBack = { navController.popBackStack() })
            }
            composable(CAMERA_SCANNER) {
                CameraScannerScreen(
                    onBack = { navController.popBackStack() },
                    onSendToTts = { text ->
                        val toTranslate = runCatching {
                            navController.getBackStackEntry(Dest.Translate.route)
                        }.getOrNull()
                        if (toTranslate != null) {
                            toTranslate.savedStateHandle[PENDING_TRANSLATE_TEXT] = text
                            navController.popBackStack(Dest.Translate.route, inclusive = false)
                        } else {
                            navController
                                .getBackStackEntry(Dest.Home.route)
                                .savedStateHandle[PENDING_TTS_TEXT] = text
                            navController.popBackStack(Dest.Home.route, inclusive = false)
                        }
                    }
                )
            }
        }
    }
}
