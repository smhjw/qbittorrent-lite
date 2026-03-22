package com.hjw.qbremote.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ServerCapabilities
import com.hjw.qbremote.data.ServerBackendType
import com.hjw.qbremote.data.ServerProfile
import com.hjw.qbremote.data.model.AddTorrentFile
import com.hjw.qbremote.ui.theme.qbGlassCardColors
import com.hjw.qbremote.ui.theme.qbGlassOutlineColor
import com.hjw.qbremote.ui.theme.qbGlassSubtleContainerColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AddTorrentSheet(
    context: Context,
    capabilities: ServerCapabilities,
    categoryOptions: List<String>,
    tagOptions: List<String>,
    pathOptions: List<String>,
    onCancel: () -> Unit,
    onAdd: (
        urls: String,
        files: List<AddTorrentFile>,
        autoTmm: Boolean,
        category: String,
        tags: String,
        savePath: String,
        paused: Boolean,
        skipChecking: Boolean,
        sequentialDownload: Boolean,
        firstLastPiecePrio: Boolean,
        uploadLimitKb: String,
        downloadLimitKb: String,
    ) -> Unit,
) {
    val supportsCategories = capabilities.supportsCategories
    val supportsSpeedLimits = capabilities.supportsPerTorrentSpeedLimit
    val supportsAdvancedAddOptions = capabilities.supportsAdvancedAddOptions
    var urls by remember { mutableStateOf("") }
    var selectedFiles by remember { mutableStateOf(listOf<AddTorrentFile>()) }
    var autoTmm by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var savePath by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var skipChecking by remember { mutableStateOf(false) }
    var sequentialDownload by remember { mutableStateOf(false) }
    var firstLastPiecePrio by remember { mutableStateOf(false) }
    var uploadLimitKb by remember { mutableStateOf("") }
    var downloadLimitKb by remember { mutableStateOf("") }
    val canAdd = urls.trim().isNotBlank() || selectedFiles.isNotEmpty()
    val suggestedCategoryOptions = remember(categoryOptions) {
        categoryOptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val suggestedPathOptions = remember(pathOptions) {
        pathOptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val pickTorrentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val newFiles = uris.mapNotNull { readTorrentFile(context, it) }
        if (newFiles.isNotEmpty()) {
            selectedFiles = (selectedFiles + newFiles).distinctBy { file ->
                "${file.name}|${file.bytes.size}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 700.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.add_torrent_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = qbGlassSubtleContainerColor(),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.torrent_links_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.torrent_links_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = urls,
                    onValueChange = { urls = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("magnet:?xt=...") },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = qbGlassSubtleContainerColor(),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.torrent_files_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(
                        onClick = { pickTorrentLauncher.launch(arrayOf("*/*")) },
                    ) {
                        Text(
                            text = "+",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (selectedFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.torrent_files_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    selectedFiles.take(5).forEach { file ->
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selectedFiles.size > 5) {
                        Text(
                            text = pluralStringResource(
                                id = R.plurals.more_files_count,
                                count = selectedFiles.size - 5,
                                selectedFiles.size - 5,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = qbGlassSubtleContainerColor(),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (supportsAdvancedAddOptions) {
                    SettingSwitchRow(
                        title = stringResource(R.string.auto_torrent_management),
                        checked = autoTmm,
                        onCheckedChange = { autoTmm = it },
                    )
                }
                if (supportsCategories) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_category_label)) },
                        placeholder = { Text(stringResource(R.string.leave_empty_hint)) },
                        singleLine = true,
                    )
                    if (suggestedCategoryOptions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(suggestedCategoryOptions, key = { it }) { option ->
                                val selected = category.equals(option, ignoreCase = true)
                                TorrentMetaChip(
                                    text = option,
                                    containerColor = if (selected) Color(0xFF5D7CFF) else Color(0xFF4D4D4D),
                                    contentColor = Color(0xFFEAF0FF),
                                    onClick = { category = option },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.add_tags_label)) },
                    placeholder = { Text(stringResource(R.string.tags_split_hint)) },
                    singleLine = true,
                )
                if (tagOptions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tagOptions, key = { it }) { option ->
                            val selected = parseTags(tags).any { it.equals(option, ignoreCase = true) }
                            TorrentMetaChip(
                                text = option,
                                containerColor = if (selected) Color(0xFF5D7CFF) else Color(0xFF4D4D4D),
                                contentColor = Color(0xFFEAF0FF),
                                onClick = { tags = toggleTag(tags, option) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = savePath,
                    onValueChange = { savePath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.save_path_manual_label)) },
                    placeholder = { Text("/mnt/usb2_2-1/download") },
                    singleLine = true,
                )
                if (suggestedPathOptions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestedPathOptions, key = { it }) { option ->
                            val selected = savePath.equals(option, ignoreCase = true)
                            TorrentMetaChip(
                                text = option,
                                containerColor = if (selected) Color(0xFF5D7CFF) else Color(0xFF4D4D4D),
                                contentColor = Color(0xFFEAF0FF),
                                onClick = { savePath = option },
                            )
                        }
                    }
                }
                SettingSwitchRow(
                    title = stringResource(R.string.pause_after_add),
                    checked = paused,
                    onCheckedChange = { paused = it },
                )
            }
        }

        if (supportsAdvancedAddOptions || supportsSpeedLimits) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.35f)),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = qbGlassSubtleContainerColor(),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (supportsAdvancedAddOptions) {
                        SettingSwitchRow(
                            title = stringResource(R.string.skip_hash_check),
                            checked = skipChecking,
                            onCheckedChange = { skipChecking = it },
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.sequential_download),
                            checked = sequentialDownload,
                            onCheckedChange = { sequentialDownload = it },
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.first_last_piece_prio),
                            checked = firstLastPiecePrio,
                            onCheckedChange = { firstLastPiecePrio = it },
                        )
                    }

                    if (supportsSpeedLimits) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uploadLimitKb,
                                onValueChange = { uploadLimitKb = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.upload_limit_kb_label)) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                            )
                            OutlinedTextField(
                                value = downloadLimitKb,
                                onValueChange = { downloadLimitKb = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.download_limit_kb_label)) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            TextButton(
                enabled = canAdd,
                onClick = {
                    onAdd(
                        urls,
                        selectedFiles,
                        autoTmm,
                        category,
                        tags,
                        savePath,
                        paused,
                        skipChecking,
                        sequentialDownload,
                        firstLastPiecePrio,
                        uploadLimitKb,
                        downloadLimitKb,
                    )
                },
            ) {
                Text(stringResource(R.string.add))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ServerProfileSheet(
    profiles: List<ServerProfile>,
    activeProfileId: String?,
    initialEditingProfileId: String?,
    onSwitchProfile: (String) -> Unit,
    onAddProfile: (
        name: String,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
    ) -> Unit,
    onUpdateProfile: (
        profileId: String,
        name: String,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
    ) -> Unit,
    onRequestDeleteProfile: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var editingProfileId by remember(initialEditingProfileId, profiles) {
        mutableStateOf(
            initialEditingProfileId?.takeIf { requestedId ->
                profiles.any { it.id == requestedId }
            }.orEmpty()
        )
    }
    val editingProfile = remember(editingProfileId, profiles) {
        profiles.firstOrNull { it.id == editingProfileId }
    }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var useHttps by remember { mutableStateOf(false) }
    var backendType by remember { mutableStateOf(ServerBackendType.QBITTORRENT) }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable(editingProfileId) { mutableStateOf(false) }
    var refreshSeconds by remember { mutableStateOf("5") }
    val scrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    fun defaultPort(type: ServerBackendType): String {
        return when (type) {
            ServerBackendType.QBITTORRENT -> "8080"
            ServerBackendType.TRANSMISSION -> "9091"
        }
    }

    fun defaultUsername(type: ServerBackendType): String {
        return when (type) {
            ServerBackendType.QBITTORRENT -> "admin"
            ServerBackendType.TRANSMISSION -> "admin"
        }
    }

    LaunchedEffect(editingProfileId, profiles) {
        val profile = profiles.firstOrNull { it.id == editingProfileId }
        if (profile != null) {
            name = profile.name
            host = profile.host
            port = profile.port.toString()
            useHttps = profile.useHttps
            backendType = profile.backendType
            username = profile.username
            password = ""
            refreshSeconds = profile.refreshSeconds.toString()
        } else {
            name = ""
            backendType = ServerBackendType.QBITTORRENT
            host = ""
            port = defaultPort(backendType)
            useHttps = false
            username = defaultUsername(backendType)
            password = ""
            refreshSeconds = "5"
        }
    }

    val editing = editingProfile != null
    val canSubmit = host.trim().isNotBlank() && username.trim().isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 760.dp)
            .verticalScroll(scrollState)
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.server_manage_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.no_saved_servers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            profiles.forEach { profile ->
                val active = profile.id == activeProfileId
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSwitchProfile(profile.id) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary else qbGlassOutlineColor(defaultAlpha = 0.35f)
                    ),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (active) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            qbGlassSubtleContainerColor()
                        }
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSwitchProfile(profile.id) },
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = buildServerAddressText(
                                        ConnectionSettings(
                                            host = profile.host,
                                            port = profile.port,
                                            useHttps = profile.useHttps,
                                        ),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (active) {
                                        stringResource(R.string.server_profile_active)
                                    } else {
                                        stringResource(R.string.server_profile_tap_to_connect)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Row {
                                TextButton(onClick = { editingProfileId = profile.id }) {
                                    Text(stringResource(R.string.edit))
                                }
                                TextButton(onClick = { onRequestDeleteProfile(profile.id) }) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (editing) {
                    stringResource(R.string.edit_server_title)
                } else {
                    stringResource(R.string.add_server_title)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (editing) {
                TextButton(onClick = { editingProfileId = "" }) {
                    Text(stringResource(R.string.server_switch_to_add))
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.server_name_optional_label)) },
            placeholder = { Text("my seedbox") },
            singleLine = true,
        )
        Text(
            text = stringResource(R.string.connection_backend_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackendTypeOption(
                label = stringResource(R.string.backend_qbittorrent),
                iconRes = R.drawable.ic_backend_qbittorrent,
                selected = backendType == ServerBackendType.QBITTORRENT,
                onClick = {
                    backendType = ServerBackendType.QBITTORRENT
                    if (!editing) {
                        port = defaultPort(backendType)
                        useHttps = false
                        username = defaultUsername(backendType)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            BackendTypeOption(
                label = stringResource(R.string.backend_transmission),
                iconRes = R.drawable.ic_backend_transmission,
                selected = backendType == ServerBackendType.TRANSMISSION,
                onClick = {
                    backendType = ServerBackendType.TRANSMISSION
                    if (!editing) {
                        port = defaultPort(backendType)
                        useHttps = false
                        username = defaultUsername(backendType)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = host,
            onValueChange = { value ->
                host = value
                parseHostInputHints(value)?.let { hints ->
                    hints.port?.let { parsedPort -> port = parsedPort.toString() }
                    hints.useHttps?.let { parsedHttps -> useHttps = parsedHttps }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(150)
                            val broughtIntoView = runCatching {
                                bringIntoViewRequester.bringIntoView()
                                true
                            }.getOrDefault(false)
                            if (!broughtIntoView) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    }
                },
            label = { Text(stringResource(R.string.connection_host_label)) },
            placeholder = { Text("192.168.0.10") },
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                modifier = Modifier.width(120.dp),
                label = { Text(stringResource(R.string.connection_port_label)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
            )
            OutlinedTextField(
                value = refreshSeconds,
                onValueChange = { refreshSeconds = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.connection_refresh_label)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.connection_https_label))
            androidx.compose.material3.Switch(
                checked = useHttps,
                onCheckedChange = { useHttps = it },
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.connection_username_label)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.connection_password_label)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                PasswordVisibilityTrailingIcon(
                    passwordVisible = passwordVisible,
                    onToggle = { passwordVisible = !passwordVisible },
                )
            },
            placeholder = if (editing) {
                { Text(stringResource(R.string.server_password_preserve_hint)) }
            } else {
                null
            },
        )
        if (editing) {
            Text(
                text = stringResource(R.string.server_password_preserve_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            TextButton(
                enabled = canSubmit,
                onClick = {
                    if (editingProfile != null) {
                        onUpdateProfile(
                            editingProfile.id,
                            name.trim(),
                            backendType,
                            host.trim(),
                            port.trim(),
                            useHttps,
                            username.trim(),
                            password,
                            refreshSeconds.trim(),
                        )
                    } else {
                        onAddProfile(
                            name.trim(),
                            backendType,
                            host.trim(),
                            port.trim(),
                            useHttps,
                            username.trim(),
                            password,
                            refreshSeconds.trim(),
                        )
                    }
                },
            ) {
                Text(
                    if (editing) {
                        stringResource(R.string.server_save_action)
                    } else {
                        stringResource(R.string.add)
                    }
                )
            }
        }
    }
}

@Composable
private fun BackendTypeOption(
    label: String,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        qbGlassOutlineColor(defaultAlpha = 0.35f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        qbGlassSubtleContainerColor()
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    OutlinedCard(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun readTorrentFile(context: Context, uri: Uri): AddTorrentFile? {
    val displayName = readDisplayName(context, uri)
    if (displayName.isBlank()) return null
    return runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        AddTorrentFile(name = displayName, bytes = bytes)
    }.getOrNull()
}

internal fun readDisplayName(context: Context, uri: Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")
}
