package io.github.mangi.eta.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.rememberDeviceCapabilities
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaPreferenceDefaults
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.model.AgentToolsAction
import io.github.mangi.eta.ui.model.AgentToolsUiState
import io.github.mangi.eta.ui.model.projectToolGroups
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AgentToolsScreen(state: AgentToolsUiState, onAction: (AgentToolsAction) -> Unit, modifier: Modifier = Modifier) {
    val capabilities = rememberDeviceCapabilities()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val currentGridState = rememberLazyStaggeredGridState()
    val allGridState = rememberLazyStaggeredGridState()
    val groups = projectToolGroups(state.groups, showAll, capabilities.root.isGranted, capabilities.tools.colorOs)
    MiuixScaffoldPage(
        title = stringResource(R.string.ui_tool_ability_9f0f80),
        onBack = { onAction(AgentToolsAction.NavigateBack) },
        modifier = modifier,
        listState = if (showAll) allListState else currentListState,
    ) {
        item(key = "capability-view") {
            TabRow(
                tabs = listOf(stringResource(R.string.capability_current_device), stringResource(R.string.capability_all)),
                selectedTabIndex = if (showAll) 1 else 0,
                onTabSelected = { showAll = it == 1 },
                modifier = Modifier.padding(horizontal = EtaPreferenceDefaults.SidePadding, vertical = 8.dp),
            )
        }
        item(key = "capability-discovery") {
            EtaPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                EtaArrowPreference(
                    title = stringResource(R.string.capability_enhancements),
                    summary = stringResource(R.string.capability_enhancements_summary),
                    onClick = { onAction(AgentToolsAction.OpenEnhancements) },
                )
            }
            item(key = "capability-discovery", span = StaggeredGridItemSpan.FullLine) {
                EtaCard {
                    EtaArrowPreference(title = stringResource(R.string.capability_enhancements),
                        summary = stringResource(R.string.capability_enhancements_summary),
                        onClick = { onAction(AgentToolsAction.OpenEnhancements) })
                }
            }
            groups.forEach { group ->
                item(key = "group-" + group.id, span = StaggeredGridItemSpan.FullLine) {
                    Text(group.title, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(group.tools, key = { group.id + "-" + it.id }) { tool ->
                    ToolCard(tool, capabilities.root.isGranted, capabilities.tools, onAction, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
