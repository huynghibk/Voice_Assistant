package com.voiceassistant.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voiceassistant.presentation.home.HomeScreen
import com.voiceassistant.presentation.project.ProjectEditorScreen
import com.voiceassistant.presentation.project.ProjectListScreen
import com.voiceassistant.presentation.search.SearchAssistantScreen
import com.voiceassistant.presentation.settings.SettingsScreen
import com.voiceassistant.presentation.timeline.TimelineScreen
import com.voiceassistant.presentation.voice.CloneVoiceScreen
import com.voiceassistant.presentation.voice.VoiceLibraryScreen

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "TTS", Icons.Outlined.Home)
    data object Search : Dest("search", "Search", Icons.Outlined.Search)
    data object Projects : Dest("projects", "Projects", Icons.Outlined.Folder)
    data object Voices : Dest("voices", "Voices", Icons.Outlined.Mic)
    data object Timeline : Dest("timeline", "Studio", Icons.Outlined.Timeline)
    data object Settings : Dest("settings", "More", Icons.Outlined.Settings)
}

private val bottomDestinations = listOf(
    Dest.Home, Dest.Search, Dest.Projects, Dest.Voices, Dest.Timeline, Dest.Settings
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val hideBottom = current?.route?.startsWith("project_editor") == true ||
        current?.route == "clone_voice"

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
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = {
                                Text(
                                    dest.label,
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
            composable(Dest.Home.route) { HomeScreen() }
            composable(Dest.Search.route) { SearchAssistantScreen() }
            composable(Dest.Projects.route) {
                ProjectListScreen(
                    onOpenProject = { id -> navController.navigate("project_editor/$id") },
                    onCreateProject = { navController.navigate("project_editor/new") }
                )
            }
            composable(
                route = "project_editor/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                ProjectEditorScreen(
                    projectId = entry.arguments?.getString("projectId") ?: "new",
                    onBack = { navController.popBackStack() },
                    onOpenTimeline = { navController.navigate(Dest.Timeline.route) }
                )
            }
            composable(Dest.Voices.route) {
                VoiceLibraryScreen(onCloneVoice = { navController.navigate("clone_voice") })
            }
            composable("clone_voice") {
                CloneVoiceScreen(onBack = { navController.popBackStack() })
            }
            composable(Dest.Timeline.route) { TimelineScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
