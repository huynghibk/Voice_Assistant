package com.voiceassistant.presentation.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voiceassistant.R
import com.voiceassistant.presentation.components.VaScreenHeader
import com.voiceassistant.presentation.project.ProjectListScreen
import com.voiceassistant.presentation.theme.VaDimens
import com.voiceassistant.presentation.timeline.TimelineScreen

/**
 * Merged Projects + Timeline into one Studio hub.
 */
@Composable
fun StudioHubScreen(
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = VaDimens.screenHorizontal,
                vertical = VaDimens.screenTop
            )
        ) {
            VaScreenHeader(onOpenSettings = onOpenSettings) {
                Column {
                    Text(
                        stringResource(R.string.nav_studio),
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        stringResource(R.string.studio_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[tab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.studio_tab_projects),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.studio_tab_timeline),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
            }
        }

        when (tab) {
            0 -> ProjectListScreen(
                onOpenProject = onOpenProject,
                onCreateProject = onCreateProject,
                embedded = true
            )
            else -> TimelineScreen(embedded = true)
        }
    }
}
