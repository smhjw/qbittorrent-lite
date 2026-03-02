package com.hjw.qbremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.hjw.qbremote.data.ChartSortMode
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TransferInfo
import java.net.URI

private data class SiteChartEntry(
    val site: String,
    val torrentCount: Int,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val totalSpeed: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val visibleTorrents = remember(
        state.torrents,
        state.selectedFilter,
        state.selectedSort,
        state.sortDescending,
    ) {
        val filtered = state.torrents.filter { state.selectedFilter.matches(it) }
        sortTorrents(filtered, state.selectedSort, state.sortDescending)
    }
    val groupedTorrents = remember(visibleTorrents, state.settings.enableServerGrouping) {
        if (!state.settings.enableServerGrouping) {
            emptyList()
        } else {
            visibleTorrents
                .groupBy { trackerSiteName(it.tracker) }
                .toList()
                .sortedByDescending { (_, torrents) -> torrents.size }
        }
    }
    val chartEntries = remember(state.torrents, state.settings.chartSortMode) {
        buildSiteChartEntries(state.torrents, state.settings.chartSortMode)
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("qB Remote") },
                actions = {
                    TextButton(onClick = { showSettingsDialog = true }) {
                        Text("Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ConnectionCard(
                    state = state,
                    onHostChange = viewModel::updateHost,
                    onPortChange = viewModel::updatePort,
                    onHttpsChange = viewModel::updateUseHttps,
                    onUserChange = viewModel::updateUsername,
                    onPasswordChange = viewModel::updatePassword,
                    onRefreshSecondsChange = viewModel::updateRefreshSeconds,
                    onConnect = viewModel::connect,
                )
            }

            if (state.connected) {
                item {
                    TransferSummaryCard(
                        transferInfo = state.transferInfo,
                        torrentCount = state.torrents.size,
                        refreshing = state.isRefreshing,
                        showTotals = state.settings.showSpeedTotals,
                        onRefresh = viewModel::refresh,
                    )
                }

                if (state.settings.showChartPanel) {
                    item {
                        ChartPanelCard(
                            entries = chartEntries,
                            chartSortMode = state.settings.chartSortMode,
                            showSiteName = state.settings.chartShowSiteName,
                        )
                    }
                }

                item {
                    FilterRow(
                        selected = state.selectedFilter,
                        onSelect = viewModel::setFilter,
                    )
                }

                item {
                    SortRow(
                        selected = state.selectedSort,
                        descending = state.sortDescending,
                        onSelect = viewModel::setSort,
                        onToggleDirection = viewModel::toggleSortDirection,
                    )
                }

                if (state.settings.enableServerGrouping) {
                    groupedTorrents.forEach { (site, siteTorrents) ->
                        item(key = "group_header_$site") {
                            Text(
                                text = "$site (${siteTorrents.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }

                        items(
                            items = siteTorrents,
                            key = { "${site}_${it.hash.ifBlank { it.name }}" },
                        ) { torrent ->
                            TorrentCard(
                                torrent = torrent,
                                isPending = state.pendingHashes.contains(torrent.hash),
                                deleteFilesDefault = state.settings.deleteFilesDefault,
                                deleteFilesWhenNoSeeders = state.settings.deleteFilesWhenNoSeeders,
                                onPause = { viewModel.pauseTorrent(torrent.hash) },
                                onResume = { viewModel.resumeTorrent(torrent.hash) },
                                onDelete = { deleteFiles ->
                                    viewModel.deleteTorrent(torrent.hash, deleteFiles)
                                },
                            )
                        }
                    }
                } else {
                    items(
                        items = visibleTorrents,
                        key = { it.hash.ifBlank { it.name } },
                    ) { torrent ->
                        TorrentCard(
                            torrent = torrent,
                            isPending = state.pendingHashes.contains(torrent.hash),
                            deleteFilesDefault = state.settings.deleteFilesDefault,
                            deleteFilesWhenNoSeeders = state.settings.deleteFilesWhenNoSeeders,
                            onPause = { viewModel.pauseTorrent(torrent.hash) },
                            onResume = { viewModel.resumeTorrent(torrent.hash) },
                            onDelete = { deleteFiles ->
                                viewModel.deleteTorrent(torrent.hash, deleteFiles)
                            },
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "Connect first to load torrent list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = state.settings,
            onDismiss = { showSettingsDialog = false },
            onShowSpeedTotalsChange = viewModel::updateShowSpeedTotals,
            onEnableServerGroupingChange = viewModel::updateEnableServerGrouping,
            onShowChartPanelChange = viewModel::updateShowChartPanel,
            onChartShowSiteNameChange = viewModel::updateChartShowSiteName,
            onChartSortModeChange = viewModel::updateChartSortMode,
            onDeleteFilesWhenNoSeedersChange = viewModel::updateDeleteFilesWhenNoSeeders,
            onDeleteFilesDefaultChange = viewModel::updateDeleteFilesDefault,
        )
    }
}

@Composable
private fun ConnectionCard(
    state: MainUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onHttpsChange: (Boolean) -> Unit,
    onUserChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRefreshSecondsChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Connection", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.host,
                onValueChange = onHostChange,
                singleLine = true,
                label = { Text("Host / IP") },
                placeholder = { Text("Example: 192.168.1.12") },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.width(120.dp),
                    value = if (state.settings.port == 0) "" else state.settings.port.toString(),
                    onValueChange = onPortChange,
                    singleLine = true,
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.settings.refreshSeconds.toString(),
                    onValueChange = onRefreshSecondsChange,
                    singleLine = true,
                    label = { Text("Refresh (s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.username,
                onValueChange = onUserChange,
                singleLine = true,
                label = { Text("Username") },
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.password,
                onValueChange = onPasswordChange,
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HTTPS")
                Switch(
                    checked = state.settings.useHttps,
                    onCheckedChange = onHttpsChange,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onConnect, enabled = !state.isConnecting) {
                        Text(if (state.isConnecting) "Connecting..." else "Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferSummaryCard(
    transferInfo: TransferInfo,
    torrentCount: Int,
    refreshing: Boolean,
    showTotals: Boolean,
    onRefresh: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Global transfer", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRefresh, enabled = !refreshing) {
                        Text(if (refreshing) "Refreshing..." else "Refresh")
                    }
                }
            }
            Text("Down: ${formatSpeed(transferInfo.downloadSpeed)}")
            Text("Up: ${formatSpeed(transferInfo.uploadSpeed)}")
            if (showTotals) {
                Text("Total down: ${formatBytes(transferInfo.downloadedTotal)}")
                Text("Total up: ${formatBytes(transferInfo.uploadedTotal)}")
            }
            Text("DHT: ${transferInfo.dhtNodes}  |  Torrents: $torrentCount")
        }
    }
}

@Composable
private fun ChartPanelCard(
    entries: List<SiteChartEntry>,
    chartSortMode: ChartSortMode,
    showSiteName: Boolean,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Chart panel (${chartSortModeLabel(chartSortMode)})",
                fontWeight = FontWeight.SemiBold,
            )

            if (entries.isEmpty()) {
                Text(
                    text = "No chart data.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val topEntries = entries.take(10)
            val maxMetric = topEntries.maxOfOrNull { chartMetric(it, chartSortMode) }?.coerceAtLeast(1L) ?: 1L
            topEntries.forEachIndexed { index, entry ->
                val metric = chartMetric(entry, chartSortMode)
                val label = if (showSiteName) entry.site else "#${index + 1}"
                val metricText = chartMetricText(entry, chartSortMode)
                val progress = (metric.toFloat() / maxMetric.toFloat()).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Column(modifier = Modifier.weight(0.55f)) {
                        Text(text = metricText, style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: TorrentFilter,
    onSelect: (TorrentFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(TorrentFilter.entries) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun TorrentCard(
    torrent: TorrentInfo,
    isPending: Boolean,
    deleteFilesDefault: Boolean,
    deleteFilesWhenNoSeeders: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: (Boolean) -> Unit,
) {
    var showDeleteDialog by remember(torrent.hash) { mutableStateOf(false) }
    var deleteFilesChecked by remember(torrent.hash) { mutableStateOf(false) }
    val paused = isPausedState(torrent.state)
    val canPause = !paused && isActiveTransferState(torrent.state)
    val canResume = paused

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = torrent.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(stateLabel(torrent.state)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                )

                val tagLabel = torrent.tags.ifBlank { "No tags" }
                AssistChip(
                    onClick = {},
                    label = { Text(tagLabel) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(formatPercent(torrent.progress), fontWeight = FontWeight.Medium)
                }
            }

            LinearProgressIndicator(
                progress = { torrent.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (canPause) {
                    TextButton(onClick = onPause, enabled = !isPending) {
                        Text("Pause")
                    }
                }
                if (canResume) {
                    TextButton(onClick = onResume, enabled = !isPending) {
                        Text("Resume")
                    }
                }
                TextButton(
                    onClick = {
                        deleteFilesChecked = deleteFilesDefault ||
                            (deleteFilesWhenNoSeeders && torrent.seeders <= 0)
                        showDeleteDialog = true
                    },
                    enabled = !isPending,
                ) {
                    Text("Delete")
                }
            }

            HorizontalDivider()
            Text("Site: ${trackerSiteName(torrent.tracker)}")
            Text("Size: ${formatBytes(torrent.size)}  Downloaded: ${formatBytes(torrent.downloaded)}")
            Text("Up ${formatSpeed(torrent.uploadSpeed)}   Down ${formatSpeed(torrent.downloadSpeed)}")
            Text("Seed/Leech: ${torrent.seeders}/${torrent.leechers}   Complete/Incomp: ${torrent.numComplete}/${torrent.numIncomplete}")
            Text("Added: ${formatAddedOn(torrent.addedOn)}")
            if (torrent.lastActivity > 0) {
                Text("Last activity: ${formatAddedOn(torrent.lastActivity)}")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete torrent?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose whether to delete downloaded files together.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteFilesChecked,
                            onCheckedChange = { deleteFilesChecked = it },
                        )
                        Text("Delete files")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(deleteFilesChecked)
                    },
                    enabled = !isPending,
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SortRow(
    selected: TorrentSort,
    descending: Boolean,
    onSelect: (TorrentSort) -> Unit,
    onToggleDirection: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onToggleDirection) {
                    Text(if (descending) "DESC" else "ASC")
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(TorrentSort.entries) { sort ->
                FilterChip(
                    selected = selected == sort,
                    onClick = { onSelect(sort) },
                    label = { Text(sort.label) },
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: ConnectionSettings,
    onDismiss: () -> Unit,
    onShowSpeedTotalsChange: (Boolean) -> Unit,
    onEnableServerGroupingChange: (Boolean) -> Unit,
    onShowChartPanelChange: (Boolean) -> Unit,
    onChartShowSiteNameChange: (Boolean) -> Unit,
    onChartSortModeChange: (ChartSortMode) -> Unit,
    onDeleteFilesWhenNoSeedersChange: (Boolean) -> Unit,
    onDeleteFilesDefaultChange: (Boolean) -> Unit,
) {
    var showChartSortMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingSwitchRow(
                    title = "Show speed totals",
                    checked = settings.showSpeedTotals,
                    onCheckedChange = onShowSpeedTotalsChange,
                )
                SettingSwitchRow(
                    title = "Enable server grouping",
                    checked = settings.enableServerGrouping,
                    onCheckedChange = onEnableServerGroupingChange,
                )
                SettingSwitchRow(
                    title = "Show chart panel",
                    checked = settings.showChartPanel,
                    onCheckedChange = onShowChartPanelChange,
                )
                SettingSwitchRow(
                    title = "Show site name in chart",
                    checked = settings.chartShowSiteName,
                    onCheckedChange = onChartShowSiteNameChange,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Chart sort mode",
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { showChartSortMenu = true }) {
                            Text(chartSortModeLabel(settings.chartSortMode))
                        }
                        DropdownMenu(
                            expanded = showChartSortMenu,
                            onDismissRequest = { showChartSortMenu = false },
                        ) {
                            ChartSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(chartSortModeLabel(mode)) },
                                    onClick = {
                                        onChartSortModeChange(mode)
                                        showChartSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                SettingSwitchRow(
                    title = "Delete files by default when no seeders",
                    checked = settings.deleteFilesWhenNoSeeders,
                    onCheckedChange = onDeleteFilesWhenNoSeedersChange,
                )
                SettingSwitchRow(
                    title = "Delete files by default",
                    checked = settings.deleteFilesDefault,
                    onCheckedChange = onDeleteFilesDefaultChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun isPausedState(state: String): Boolean {
    return state.lowercase() in setOf("pauseddl", "pausedup")
}

private fun isActiveTransferState(state: String): Boolean {
    return state.lowercase() in setOf(
        "downloading", "forceddl", "stalleddl", "metadl",
        "uploading", "forcedup", "stalledup"
    )
}

private fun sortTorrents(
    torrents: List<TorrentInfo>,
    sort: TorrentSort,
    descending: Boolean,
): List<TorrentInfo> {
    val comparator = when (sort) {
        TorrentSort.ACTIVITY_TIME -> compareBy<TorrentInfo> { it.lastActivity }
        TorrentSort.ADDED_TIME -> compareBy<TorrentInfo> { it.addedOn }
        TorrentSort.DOWNLOAD_SPEED -> compareBy<TorrentInfo> { it.downloadSpeed }
        TorrentSort.UPLOAD_SPEED -> compareBy<TorrentInfo> { it.uploadSpeed }
    }
    return if (descending) torrents.sortedWith(comparator.reversed()) else torrents.sortedWith(comparator)
}

private fun buildSiteChartEntries(
    torrents: List<TorrentInfo>,
    mode: ChartSortMode,
): List<SiteChartEntry> {
    val grouped = torrents.groupBy { trackerSiteName(it.tracker) }
    return grouped.map { (site, list) ->
        val down = list.sumOf { it.downloadSpeed }
        val up = list.sumOf { it.uploadSpeed }
        SiteChartEntry(
            site = site,
            torrentCount = list.size,
            downloadSpeed = down,
            uploadSpeed = up,
            totalSpeed = down + up,
        )
    }.sortedByDescending { chartMetric(it, mode) }
}

private fun chartMetric(entry: SiteChartEntry, mode: ChartSortMode): Long {
    return when (mode) {
        ChartSortMode.TOTAL_SPEED -> entry.totalSpeed
        ChartSortMode.DOWNLOAD_SPEED -> entry.downloadSpeed
        ChartSortMode.UPLOAD_SPEED -> entry.uploadSpeed
        ChartSortMode.TORRENT_COUNT -> entry.torrentCount.toLong()
    }
}

private fun chartMetricText(entry: SiteChartEntry, mode: ChartSortMode): String {
    return when (mode) {
        ChartSortMode.TOTAL_SPEED -> "Total: ${formatSpeed(entry.totalSpeed)}"
        ChartSortMode.DOWNLOAD_SPEED -> "Down: ${formatSpeed(entry.downloadSpeed)}"
        ChartSortMode.UPLOAD_SPEED -> "Up: ${formatSpeed(entry.uploadSpeed)}"
        ChartSortMode.TORRENT_COUNT -> "Torrents: ${entry.torrentCount}"
    }
}

private fun chartSortModeLabel(mode: ChartSortMode): String {
    return when (mode) {
        ChartSortMode.TOTAL_SPEED -> "Total speed"
        ChartSortMode.DOWNLOAD_SPEED -> "Download speed"
        ChartSortMode.UPLOAD_SPEED -> "Upload speed"
        ChartSortMode.TORRENT_COUNT -> "Torrent count"
    }
}

private fun trackerSiteName(tracker: String): String {
    val trimmed = tracker.trim()
    if (trimmed.isBlank()) return "Unknown"

    return runCatching {
        URI(trimmed).host.orEmpty().ifBlank { "Unknown" }
    }.getOrElse {
        trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .ifBlank { "Unknown" }
    }
}
