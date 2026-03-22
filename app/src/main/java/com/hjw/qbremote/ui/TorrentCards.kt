package com.hjw.qbremote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.hjw.qbremote.R
import com.hjw.qbremote.data.ServerCapabilities
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentProperties
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.ui.theme.qbGlassCardColors
import com.hjw.qbremote.ui.theme.qbGlassOutlineColor
import com.hjw.qbremote.ui.theme.qbGlassStrongContainerColor
import com.hjw.qbremote.ui.theme.qbGlassSubtleContainerColor

@Composable
internal fun TorrentCard(
    torrent: TorrentInfo,
    crossSeedCount: Int,
    isPending: Boolean,
    onOpenDetails: () -> Unit,
) {
    val effectiveState = effectiveTorrentState(torrent)
    val stateLabel = localizedTorrentStateLabel(effectiveState)
    val categoryText = normalizeCategoryLabel(
        category = torrent.category,
        noCategoryText = stringResource(R.string.no_category),
    )
    val tagsText = compactTagsLabel(
        tags = torrent.tags,
        noTagsText = stringResource(R.string.no_tags),
    )
    val activeAgoText = formatActiveAgo(torrent.lastActivity)
    val addedOnText = formatAddedOn(torrent.addedOn)
    val savePathText = torrent.savePath.ifBlank { "-" }
    val stateStyle = torrentStateStyle(effectiveState)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isPending) { onOpenDetails() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, stateStyle.borderColor.copy(alpha = 0.58f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = qbGlassSubtleContainerColor(),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = torrent.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp,
                )
            }

            TorrentMetaHeaderRow(
                tagsText = tagsText,
                crossSeedCount = crossSeedCount,
                stateLabel = stateLabel,
                stateStyle = stateStyle,
                addedOnText = addedOnText,
                activeAgoText = activeAgoText,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator(
                    progress = { torrent.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    color = stateStyle.progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = formatPercent(torrent.progress),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = stateStyle.progressColor,
                )
            }

            TorrentQuickStatsRow(
                torrent = torrent,
                categoryText = categoryText,
                savePathText = savePathText,
                minHeight = 96.dp,
            )
        }
    }
}

@Composable
internal fun TorrentOperationDetailCard(
    torrent: TorrentInfo,
    crossSeedCount: Int,
    isPending: Boolean,
    capabilities: ServerCapabilities,
    detailLoading: Boolean,
    detailProperties: TorrentProperties?,
    detailFiles: List<TorrentFileInfo>,
    detailTrackers: List<TorrentTracker>,
    magnetUri: String,
    categoryOptions: List<String>,
    tagOptions: List<String>,
    deleteFilesDefault: Boolean,
    deleteFilesWhenNoSeeders: Boolean,
    onCopyHash: () -> Unit,
    onCopyMagnet: (String) -> Unit,
    onExportTorrent: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onSetLocation: (String) -> Unit,
    onSetCategory: (String) -> Unit,
    onSetTags: (String, String) -> Unit,
    onSetSpeedLimit: (String, String) -> Unit,
    onSetShareRatio: (String) -> Unit,
    onReannounce: () -> Unit,
    onRecheck: () -> Unit,
    onCopyTracker: (TorrentTracker) -> Unit,
    onEditTracker: (TorrentTracker, String) -> Unit,
    onDeleteTracker: (TorrentTracker) -> Unit,
) {
    var showDeleteDialog by remember(torrent.hash) { mutableStateOf(false) }
    var deleteFilesChecked by remember(torrent.hash) { mutableStateOf(false) }
    var renameText by remember(torrent.hash) { mutableStateOf(torrent.name) }
    var locationText by remember(torrent.hash) {
        mutableStateOf(detailProperties?.savePath?.takeIf { it.isNotBlank() } ?: torrent.savePath)
    }
    var categoryTextInput by remember(torrent.hash) { mutableStateOf(torrent.category) }
    var tagsTextInput by remember(torrent.hash) { mutableStateOf(torrent.tags) }
    var downloadLimitText by remember(torrent.hash) { mutableStateOf("") }
    var uploadLimitText by remember(torrent.hash) { mutableStateOf("") }
    var ratioText by remember(torrent.hash) { mutableStateOf(formatRatio(torrent.ratio)) }
    var selectedTab by rememberSaveable(torrent.hash) { mutableIntStateOf(0) }
    var trackersPasskeyVisible by rememberSaveable(torrent.hash) { mutableStateOf(false) }
    var editingTracker by remember(torrent.hash) { mutableStateOf<TorrentTracker?>(null) }
    var editingTrackerUrl by remember(torrent.hash) { mutableStateOf("") }
    var pendingDeleteTracker by remember(torrent.hash) { mutableStateOf<TorrentTracker?>(null) }
    var fileBrowserPath by rememberSaveable(torrent.hash) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(torrent.hash, detailProperties?.downloadLimit, detailProperties?.uploadLimit) {
        val dl = detailProperties?.downloadLimit ?: 0L
        val up = detailProperties?.uploadLimit ?: 0L
        downloadLimitText = if (dl > 0L) (dl / 1024L).toString() else ""
        uploadLimitText = if (up > 0L) (up / 1024L).toString() else ""
    }
    LaunchedEffect(torrent.hash, detailProperties?.shareRatio) {
        val ratio = detailProperties?.shareRatio
        if (ratio != null && ratio >= 0.0 && ratio.isFinite()) {
            ratioText = formatRatio(ratio)
        }
    }

    val fileTree = remember(torrent.hash, detailFiles) { buildTorrentFileTree(detailFiles) }
    val currentFileTreeNode = remember(fileTree, fileBrowserPath) {
        resolveTorrentFileTreeNode(fileTree, fileBrowserPath) ?: fileTree
    }
    LaunchedEffect(torrent.hash, detailFiles) {
        if (resolveTorrentFileTreeNode(fileTree, fileBrowserPath) == null) {
            fileBrowserPath = emptyList()
        }
    }

    val effectiveState = effectiveTorrentState(torrent)
    val paused = isPausedState(effectiveState)
    val peerOverviewItems = listOf(
        stringResource(R.string.detail_peer_seeders_label) to
            stringResource(R.string.detail_peer_seeders_value, torrent.seeders, torrent.numComplete),
        stringResource(R.string.detail_peer_leechers_label) to
            stringResource(R.string.detail_peer_leechers_value, torrent.leechers, torrent.numIncomplete),
        stringResource(R.string.detail_peer_cross_seed_label) to
            stringResource(R.string.detail_peer_cross_seed_value, crossSeedCount),
        stringResource(R.string.detail_peer_ratio_label) to formatRatio(torrent.ratio),
        stringResource(R.string.detail_peer_activity_label) to formatActiveAgo(torrent.lastActivity),
    )
    val hasMutableTrackers = detailTrackers.any { isMutableTrackerUrl(it.url) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.42f)),
        colors = qbGlassCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = torrent.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            TabRow(selectedTabIndex = selectedTab) {
                listOf(
                    stringResource(R.string.tab_info),
                    stringResource(R.string.tab_trackers),
                    stringResource(R.string.tab_peers),
                    stringResource(R.string.tab_files),
                ).forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.detail_section_basic),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DetailReadonlyActionRow(
                            label = stringResource(R.string.detail_hash_label),
                            value = torrent.hash.ifBlank { "-" },
                            actionText = stringResource(R.string.copy),
                            enabled = !isPending && torrent.hash.isNotBlank(),
                            onAction = onCopyHash,
                        )
                        DetailReadonlyActionRow(
                            label = stringResource(R.string.detail_magnet_label),
                            value = magnetUri.ifBlank { "-" },
                            actionText = stringResource(R.string.copy),
                            enabled = !isPending && magnetUri.isNotBlank(),
                            onAction = { onCopyMagnet(magnetUri) },
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            if (capabilities.supportsExportTorrent) {
                                item {
                                    DetailInlineActionButton(
                                        text = stringResource(R.string.detail_export_torrent),
                                        enabled = !isPending && torrent.hash.isNotBlank(),
                                        accentColor = MaterialTheme.colorScheme.secondary,
                                        onClick = onExportTorrent,
                                    )
                                }
                            }
                            if (capabilities.supportsReannounce) {
                                item {
                                    DetailInlineActionButton(
                                        text = stringResource(R.string.detail_reannounce),
                                        enabled = !isPending && torrent.hash.isNotBlank(),
                                        accentColor = Color(0xFF4C8DFF),
                                        onClick = onReannounce,
                                    )
                                }
                            }
                            if (capabilities.supportsRecheck) {
                                item {
                                    DetailInlineActionButton(
                                        text = stringResource(R.string.detail_recheck),
                                        enabled = !isPending && torrent.hash.isNotBlank(),
                                        accentColor = Color(0xFFF3A53C),
                                        onClick = onRecheck,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        if (capabilities.supportsRename) {
                            Text(
                                stringResource(R.string.detail_section_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ActionInputRow(
                                label = stringResource(R.string.detail_new_name_label),
                                value = renameText,
                                onValueChange = { renameText = it },
                                actionText = stringResource(R.string.detail_action_change),
                                enabled = !isPending,
                                onAction = { onRename(renameText.trim()) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        }
                        Text(
                            stringResource(R.string.detail_section_path),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.detail_set_path_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ActionInputRow(
                            label = stringResource(R.string.detail_save_path_label),
                            value = locationText,
                            onValueChange = { locationText = it },
                            actionText = stringResource(R.string.detail_action_change),
                            enabled = !isPending,
                            onAction = { onSetLocation(locationText.trim()) },
                        )
                        if (capabilities.supportsCategories) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                            Text(
                                stringResource(R.string.detail_section_category),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (categoryOptions.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    items(categoryOptions, key = { it }) { option ->
                                        TorrentMetaChip(
                                            text = option,
                                            containerColor = if (option == categoryTextInput) Color(0xFF5D7CFF) else Color(0xFF4D4D4D),
                                            contentColor = Color(0xFFEAF0FF),
                                            onClick = { categoryTextInput = option },
                                        )
                                    }
                                }
                            }
                            ActionInputRow(
                                label = stringResource(R.string.detail_category_label),
                                value = categoryTextInput,
                                onValueChange = { categoryTextInput = it },
                                actionText = stringResource(R.string.detail_action_change),
                                enabled = !isPending,
                                onAction = { onSetCategory(categoryTextInput.trim()) },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        Text(
                            stringResource(R.string.detail_section_tags),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (tagOptions.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                items(tagOptions, key = { it }) { option ->
                                    val selected = parseTags(tagsTextInput).contains(option)
                                    TorrentMetaChip(
                                        text = option,
                                        containerColor = if (selected) Color(0xFF5D7CFF) else Color(0xFF4D4D4D),
                                        contentColor = Color(0xFFEAF0FF),
                                        onClick = { tagsTextInput = toggleTag(tagsTextInput, option) },
                                    )
                                }
                            }
                        }
                        ActionInputRow(
                            label = stringResource(R.string.detail_tags_label),
                            value = tagsTextInput,
                            onValueChange = { tagsTextInput = it },
                            actionText = stringResource(R.string.detail_action_change),
                            enabled = !isPending,
                            onAction = { onSetTags(torrent.tags, tagsTextInput.trim()) },
                        )
                        if (capabilities.supportsPerTorrentSpeedLimit) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                            Text(
                                stringResource(R.string.detail_section_speed_limit),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = downloadLimitText,
                                    onValueChange = { downloadLimitText = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.detail_download_kb_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    enabled = !isPending,
                                )
                                OutlinedTextField(
                                    value = uploadLimitText,
                                    onValueChange = { uploadLimitText = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.detail_upload_kb_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    enabled = !isPending,
                                )
                                TextButton(
                                    onClick = { onSetSpeedLimit(downloadLimitText, uploadLimitText) },
                                    enabled = !isPending,
                                    modifier = Modifier.background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                                ) {
                                    Text(stringResource(R.string.detail_action_apply))
                                }
                            }
                        }
                        if (capabilities.supportsShareRatio) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                            Text(
                                stringResource(R.string.detail_section_ratio),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ActionInputRow(
                                label = stringResource(R.string.detail_ratio_label),
                                value = ratioText,
                                onValueChange = { ratioText = it },
                                actionText = stringResource(R.string.detail_action_apply),
                                enabled = !isPending,
                                onAction = { onSetShareRatio(ratioText.trim()) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { if (paused) onResume() else onPause() },
                                enabled = !isPending,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                            ) {
                                Text(if (paused) stringResource(R.string.resume) else stringResource(R.string.pause))
                            }
                            TextButton(
                                onClick = {
                                    deleteFilesChecked = deleteFilesDefault ||
                                        (deleteFilesWhenNoSeeders && torrent.seeders <= 0)
                                    showDeleteDialog = true
                                },
                                enabled = !isPending,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
                1 -> {
                    if (detailLoading && detailTrackers.isEmpty()) {
                        Text(
                            stringResource(R.string.loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TorrentMetaChip(
                            text = pluralStringResource(
                                R.plurals.detail_tracker_count,
                                detailTrackers.size,
                                detailTrackers.size,
                            ),
                            containerColor = Color(0xFF6C3FD3),
                            contentColor = Color.White,
                        )
                        if (hasMutableTrackers) {
                            TextButton(
                                onClick = { trackersPasskeyVisible = !trackersPasskeyVisible },
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (trackersPasskeyVisible) R.drawable.ic_password_hidden else R.drawable.ic_password_visible
                                    ),
                                    contentDescription = stringResource(
                                        if (trackersPasskeyVisible) R.string.detail_hide_passkey else R.string.detail_show_passkey
                                    ),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (detailTrackers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_tracker_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        detailTrackers.forEach { tracker ->
                            val isMutableTracker = isMutableTrackerUrl(tracker.url)
                            TrackerInfoCard(
                                tracker = tracker,
                                displayUrl = when {
                                    !isMutableTracker -> tracker.url.ifBlank { "-" }
                                    trackersPasskeyVisible -> tracker.url.ifBlank { "-" }
                                    else -> maskTrackerUrl(tracker.url)
                                },
                                allowMutation = capabilities.supportsTrackerMutation && isMutableTracker,
                                onCopy = if (!isPending && isMutableTracker && tracker.url.isNotBlank()) {
                                    { onCopyTracker(tracker) }
                                } else {
                                    null
                                },
                                onEdit = if (
                                    !isPending &&
                                    capabilities.supportsTrackerMutation &&
                                    isMutableTracker &&
                                    tracker.url.isNotBlank()
                                ) {
                                    {
                                        editingTracker = tracker
                                        editingTrackerUrl = tracker.url
                                    }
                                } else {
                                    null
                                },
                                onDelete = if (
                                    !isPending &&
                                    capabilities.supportsTrackerMutation &&
                                    isMutableTracker &&
                                    tracker.url.isNotBlank()
                                ) {
                                    { pendingDeleteTracker = tracker }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                2 -> {
                    TorrentUnifiedInfoPanel(items = peerOverviewItems)
                }
                3 -> {
                    if (detailLoading) {
                        Text(
                            stringResource(R.string.loading_files),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (detailFiles.isEmpty()) {
                        Text(
                            stringResource(R.string.no_file_details),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            item {
                                TorrentMetaChip(
                                    text = stringResource(R.string.detail_files_root),
                                    containerColor = if (fileBrowserPath.isEmpty()) Color(0xFF4469FF) else Color(0xFF2E3340),
                                    contentColor = Color.White,
                                    onClick = { fileBrowserPath = emptyList() },
                                )
                            }
                            fileBrowserPath.forEachIndexed { index, segment ->
                                item {
                                    Text(
                                        text = "/",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 5.dp),
                                    )
                                }
                                item {
                                    TorrentMetaChip(
                                        text = segment,
                                        containerColor = if (index == fileBrowserPath.lastIndex) Color(0xFF5D7CFF) else Color(0xFF2E3340),
                                        contentColor = Color.White,
                                        onClick = { fileBrowserPath = fileBrowserPath.take(index + 1) },
                                    )
                                }
                            }
                        }
                        currentFileTreeNode.children.forEach { node ->
                            TorrentFileBrowserNodeCard(
                                node = node,
                                onOpenDirectory = {
                                    if (node.isDirectory) {
                                        fileBrowserPath = node.pathSegments
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = PanelShape,
            containerColor = qbGlassStrongContainerColor(),
            title = { Text(stringResource(R.string.delete_torrent_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.delete_torrent_desc))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteFilesChecked,
                            onCheckedChange = { deleteFilesChecked = it },
                        )
                        Text(stringResource(R.string.delete_files))
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
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val editingTrackerState = editingTracker
    if (editingTrackerState != null) {
        AlertDialog(
            onDismissRequest = { editingTracker = null },
            shape = PanelShape,
            containerColor = qbGlassStrongContainerColor(),
            title = { Text(stringResource(R.string.detail_tracker_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editingTrackerUrl,
                    onValueChange = { editingTrackerUrl = it },
                    label = { Text(stringResource(R.string.detail_tracker_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isPending,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingTracker = null
                        onEditTracker(editingTrackerState, editingTrackerUrl.trim())
                    },
                    enabled = !isPending && editingTrackerUrl.trim().isNotBlank(),
                ) {
                    Text(stringResource(R.string.server_save_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTracker = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val deleteTrackerState = pendingDeleteTracker
    if (deleteTrackerState != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTracker = null },
            shape = PanelShape,
            containerColor = qbGlassStrongContainerColor(),
            title = { Text(stringResource(R.string.detail_tracker_delete_title)) },
            text = { Text(stringResource(R.string.detail_tracker_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteTracker = null
                        onDeleteTracker(deleteTrackerState)
                    },
                    enabled = !isPending,
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTracker = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private data class TorrentFileTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val progress: Float,
    val fileCount: Int,
    val orderIndex: Int,
    val pathSegments: List<String>,
    val children: List<TorrentFileTreeNode> = emptyList(),
)

private class MutableTorrentFileTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val orderIndex: Int,
    val pathSegments: List<String>,
) {
    val children = linkedMapOf<String, MutableTorrentFileTreeNode>()
    var size: Long = 0L
    var weightedProgress: Double = 0.0
    var fileCount: Int = 0
}

private fun buildTorrentFileTree(files: List<TorrentFileInfo>): TorrentFileTreeNode {
    val root = MutableTorrentFileTreeNode(
        name = "",
        fullPath = "",
        isDirectory = true,
        orderIndex = Int.MIN_VALUE,
        pathSegments = emptyList(),
    )
    files.forEachIndexed { fallbackIndex, file ->
        val cleanPath = file.name.trim().replace('\\', '/')
        if (cleanPath.isBlank()) return@forEachIndexed
        val segments = cleanPath.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEachIndexed
        var current = root
        current.size += file.size
        current.weightedProgress += file.size * file.progress.coerceIn(0f, 1f)
        current.fileCount += 1
        segments.forEachIndexed { index, segment ->
            val isFile = index == segments.lastIndex
            val currentPathSegments = segments.take(index + 1)
            val fullPath = currentPathSegments.joinToString("/")
            val child = current.children.getOrPut(segment) {
                MutableTorrentFileTreeNode(
                    name = segment,
                    fullPath = fullPath,
                    isDirectory = !isFile,
                    orderIndex = if (file.index >= 0) file.index else fallbackIndex,
                    pathSegments = currentPathSegments,
                )
            }
            if (isFile) {
                child.size = file.size
                child.weightedProgress = file.size.toDouble() * file.progress.coerceIn(0f, 1f).toDouble()
                child.fileCount = 1
            } else {
                child.size += file.size
                child.weightedProgress += file.size.toDouble() * file.progress.coerceIn(0f, 1f).toDouble()
                child.fileCount += 1
            }
            current = child
        }
    }
    return root.toImmutableNode()
}

private fun MutableTorrentFileTreeNode.toImmutableNode(): TorrentFileTreeNode {
    val safeSize = size.coerceAtLeast(0L)
    val progress = if (safeSize > 0L) {
        (weightedProgress / safeSize.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    return TorrentFileTreeNode(
        name = name,
        fullPath = fullPath,
        isDirectory = isDirectory,
        size = safeSize,
        progress = progress,
        fileCount = fileCount.coerceAtLeast(if (isDirectory) 0 else 1),
        orderIndex = orderIndex,
        pathSegments = pathSegments,
        children = children.values
            .sortedWith(
                compareBy<MutableTorrentFileTreeNode> { !it.isDirectory }
                    .thenBy { it.orderIndex }
                    .thenBy { it.name.lowercase() },
            )
            .map { it.toImmutableNode() },
    )
}

private fun resolveTorrentFileTreeNode(
    root: TorrentFileTreeNode,
    pathSegments: List<String>,
): TorrentFileTreeNode? {
    var current = root
    pathSegments.forEach { segment ->
        current = current.children.firstOrNull { it.name == segment } ?: return null
    }
    return current
}

@Composable
private fun TorrentFileBrowserNodeCard(
    node: TorrentFileTreeNode,
    onOpenDirectory: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = node.isDirectory, onClick = onOpenDirectory),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.28f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = qbGlassSubtleContainerColor(),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TorrentMetaChip(
                    text = stringResource(
                        if (node.isDirectory) {
                            R.string.detail_files_folder_chip
                        } else {
                            R.string.detail_files_file_chip
                        }
                    ),
                    containerColor = if (node.isDirectory) Color(0xFF2D74F7) else Color(0xFF3E4656),
                    contentColor = Color.White,
                )
                Text(
                    text = node.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (node.isDirectory) {
                    pluralStringResource(
                        R.plurals.detail_files_folder_summary,
                        node.fileCount,
                        node.fileCount,
                        formatBytes(node.size),
                        formatPercent(node.progress),
                    )
                } else {
                    stringResource(
                        R.string.detail_files_file_summary,
                        formatBytes(node.size),
                        formatPercent(node.progress),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { node.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (node.isDirectory) Color(0xFF4C8DFF) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailInlineActionButton(
    text: String,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.background(
            color = accentColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp),
        ),
    ) {
        Text(
            text = text,
            color = accentColor,
        )
    }
}

@Composable
internal fun ActionInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    actionText: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(label) },
            enabled = enabled,
        )
        TextButton(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
            ),
        ) {
            Text(actionText)
        }
    }
}

@Composable
internal fun DetailReadonlyActionRow(
    label: String,
    value: String,
    actionText: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.weight(1f),
            singleLine = true,
            readOnly = true,
            label = { Text(label) },
        )
        TextButton(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
            ),
        ) {
            Text(actionText)
        }
    }
}
