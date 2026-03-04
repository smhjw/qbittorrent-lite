package com.hjw.qbremote.ui
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.hjw.qbremote.data.AppLanguage
import com.hjw.qbremote.data.AppTheme
import com.hjw.qbremote.R
import com.hjw.qbremote.data.ChartSortMode
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ServerProfile
import com.hjw.qbremote.data.model.AddTorrentFile
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentProperties
import com.hjw.qbremote.data.model.TransferInfo
import kotlinx.coroutines.launch
import java.net.URI

private data class SiteChartEntry(
    val site: String,
    val torrentCount: Int,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val totalSpeed: Long,
)

private data class DashboardStateSummary(
    val uploadingCount: Int = 0,
    val downloadingCount: Int = 0,
    val pausedUploadCount: Int = 0,
    val pausedDownloadCount: Int = 0,
    val errorCount: Int = 0,
    val checkingCount: Int = 0,
    val waitingCount: Int = 0,
)

private enum class AppPage {
    DASHBOARD,
    TORRENT_LIST,
    TORRENT_DETAIL,
    SETTINGS,
}

private enum class TorrentListSortField(val label: String) {
    ADDED_ON("添加时间"),
    UPLOAD_SPEED("上传速度"),
    DOWNLOAD_SPEED("下载速度"),
    RATIO("分享比率"),
    UPLOADED_TOTAL("总计上传"),
    DOWNLOADED_TOTAL("总计下载"),
    SIZE("种子大小"),
    LAST_ACTIVITY("活动时间"),
    SEEDERS("做种人数"),
    LEECHERS("下载人数"),
    CROSS_SEED_COUNT("辅种数量"),
}

private val PanelShape = RoundedCornerShape(20.dp)
private val DarkBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF060A12),
        Color(0xFF0B1422),
        Color(0xFF08131E),
        Color(0xFF060A12),
    ),
)
private val LightBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF5FAFF),
        Color(0xFFEAF3FC),
        Color(0xFFE4F0F9),
        Color(0xFFF6FAFF),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current

    var currentPage by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    var previousPage by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    var showAddTorrentSheet by rememberSaveable { mutableStateOf(false) }
    var showServerProfilesSheet by rememberSaveable { mutableStateOf(false) }
    var showTorrentSortMenu by rememberSaveable { mutableStateOf(false) }
    var showTorrentSearchBar by rememberSaveable { mutableStateOf(false) }
    var torrentSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTorrentIdentity by rememberSaveable { mutableStateOf("") }
    var torrentSortField by rememberSaveable { mutableStateOf(TorrentListSortField.ADDED_ON) }
    var torrentSortAscending by rememberSaveable { mutableStateOf(false) }
    var startMotion by remember { mutableStateOf(false) }
    val localContext = LocalContext.current
    val contentReveal by animateFloatAsState(
        targetValue = if (startMotion) 1f else 0f,
        animationSpec = tween(
            durationMillis = 700,
            delayMillis = 80,
            easing = FastOutSlowInEasing,
        ),
        label = "contentReveal",
    )
    val appBackgroundGradient = if (state.settings.appTheme == AppTheme.DARK) {
        DarkBackgroundGradient
    } else {
        LightBackgroundGradient
    }
    val crossSeedCounts = remember(state.torrents) {
        buildCrossSeedCountMap(state.torrents)
    }
    val normalizedSearchQuery = remember(torrentSearchQuery) { torrentSearchQuery.trim() }
    val visibleTorrents = remember(
        state.torrents,
        crossSeedCounts,
        normalizedSearchQuery,
        torrentSortField,
        torrentSortAscending,
    ) {
        val filtered = state.torrents.filter { torrent ->
            matchesTorrentSearch(torrent, normalizedSearchQuery)
        }
        sortTorrents(
            torrents = filtered,
            crossSeedCounts = crossSeedCounts,
            field = torrentSortField,
            ascending = torrentSortAscending,
        )
    }
    val categoryOptionsForAdd = remember(state.categoryOptions) {
        state.categoryOptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val tagOptionsForAdd = remember(state.tagOptions) {
        state.tagOptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val pathOptionsForAdd = remember(state.torrents) {
        state.torrents
            .map { it.savePath.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val selectedTorrent = remember(state.torrents, selectedTorrentIdentity) {
        state.torrents.firstOrNull { torrentIdentityKey(it) == selectedTorrentIdentity }
    }
    val contentListState = rememberLazyListState()

    fun closeDrawer(action: () -> Unit) {
        action()
        scope.launch { drawerState.close() }
    }

    fun openSettings() {
        if (currentPage != AppPage.SETTINGS) {
            previousPage = currentPage
        }
        currentPage = AppPage.SETTINGS
    }

    fun openTorrentList() {
        if (currentPage != AppPage.TORRENT_LIST) {
            previousPage = currentPage
        }
        currentPage = AppPage.TORRENT_LIST
    }

    fun openTorrentDetail(torrent: TorrentInfo) {
        selectedTorrentIdentity = torrentIdentityKey(torrent)
        if (currentPage != AppPage.TORRENT_DETAIL) {
            previousPage = currentPage
        }
        currentPage = AppPage.TORRENT_DETAIL
    }

    fun backToPreviousPage() {
        currentPage = if (previousPage == currentPage) AppPage.DASHBOARD else previousPage
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    LaunchedEffect(Unit) {
        startMotion = true
    }

    LaunchedEffect(currentPage, selectedTorrent?.hash) {
        val hash = selectedTorrent?.hash.orEmpty()
        val refreshScene = when (currentPage) {
            AppPage.DASHBOARD -> RefreshScene.DASHBOARD
            AppPage.TORRENT_LIST -> RefreshScene.DASHBOARD
            AppPage.TORRENT_DETAIL -> RefreshScene.TORRENT_DETAIL
            AppPage.SETTINGS -> RefreshScene.SETTINGS
        }
        viewModel.updateRefreshScene(refreshScene)
        if (currentPage == AppPage.TORRENT_DETAIL && hash.isNotBlank()) {
            viewModel.loadTorrentDetail(hash)
        }
    }

    BackHandler(enabled = currentPage != AppPage.DASHBOARD) {
        backToPreviousPage()
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                DrawerThemeItem(
                    darkTheme = state.settings.appTheme == AppTheme.DARK,
                    onThemeChange = { enabled ->
                        viewModel.updateAppTheme(if (enabled) AppTheme.DARK else AppTheme.LIGHT)
                    },
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentPage) {
                    val edgeWidthPx = with(density) { 36.dp.toPx() }
                    val triggerDistancePx = with(density) { 90.dp.toPx() }
                    var trackingFromEdge = false
                    var dragDistance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            trackingFromEdge = offset.x <= edgeWidthPx
                            dragDistance = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!trackingFromEdge) return@detectHorizontalDragGestures
                            if (dragAmount > 0f) {
                                dragDistance += dragAmount
                            }
                            if (dragDistance >= triggerDistancePx && currentPage != AppPage.DASHBOARD) {
                                backToPreviousPage()
                                trackingFromEdge = false
                                dragDistance = 0f
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            trackingFromEdge = false
                            dragDistance = 0f
                        },
                        onDragCancel = {
                            trackingFromEdge = false
                            dragDistance = 0f
                        },
                    )
                }
                .background(appBackgroundGradient),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        navigationIcon = {
                            TextButton(
                                onClick = {
                                    if (currentPage == AppPage.DASHBOARD) {
                                        scope.launch { drawerState.open() }
                                    } else {
                                        backToPreviousPage()
                                    }
                                },
                            ) {
                                Text(
                                    text = if (currentPage == AppPage.DASHBOARD) "≡" else "←",
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.top_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                            )
                        },
                        actions = {
                            when (currentPage) {
                                AppPage.DASHBOARD -> {
                                    TextButton(
                                        onClick = {
                                            showServerProfilesSheet = true
                                        },
                                    ) {
                                        Text("+", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
                                    }
                                    TextButton(onClick = { openSettings() }) {
                                        Text("设置", color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }

                                AppPage.TORRENT_LIST -> {
                                    Box {
                                        TextButton(onClick = { showTorrentSortMenu = true }) {
                                            Text(
                                                text = if (torrentSortAscending) "排序↑" else "排序↓",
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showTorrentSortMenu,
                                            onDismissRequest = { showTorrentSortMenu = false },
                                        ) {
                                            TorrentListSortField.entries.forEach { field ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (field == torrentSortField) {
                                                            "✓ ${field.label}"
                                                            } else {
                                                                field.label
                                                            }
                                                        )
                                                    },
                                                    onClick = {
                                                        torrentSortField = field
                                                        showTorrentSortMenu = false
                                                        scope.launch {
                                                            contentListState.animateScrollToItem(0)
                                                        }
                                                    },
                                                )
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(if (torrentSortAscending) "当前：顺序" else "当前：逆序") },
                                                onClick = {},
                                                enabled = false,
                                            )
                                            DropdownMenuItem(
                                                text = { Text("顺序") },
                                                onClick = {
                                                    torrentSortAscending = true
                                                    showTorrentSortMenu = false
                                                    scope.launch {
                                                        contentListState.animateScrollToItem(0)
                                                    }
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("逆序") },
                                                onClick = {
                                                    torrentSortAscending = false
                                                    showTorrentSortMenu = false
                                                    scope.launch {
                                                        contentListState.animateScrollToItem(0)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            val nextShowSearchBar = !showTorrentSearchBar
                                            showTorrentSearchBar = nextShowSearchBar
                                            if (nextShowSearchBar) {
                                                scope.launch {
                                                    contentListState.animateScrollToItem(0)
                                                }
                                            }
                                            if (!nextShowSearchBar) {
                                                torrentSearchQuery = ""
                                            }
                                        },
                                    ) {
                                        Text(
                                            text = if (showTorrentSearchBar) "取消搜索" else "搜索",
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.loadGlobalSelectionOptions()
                                            showAddTorrentSheet = true
                                        },
                                    ) {
                                        Text("+", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
                                    }
                                }

                                AppPage.SETTINGS -> {
                                    TextButton(
                                        onClick = {
                                            if (state.connected) viewModel.refresh() else viewModel.connect()
                                        },
                                    ) {
                                        Text(
                                            text = if (state.connected) "刷新" else "连接",
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                }

                                AppPage.TORRENT_DETAIL -> {
                                    // Keep detail view focused on torrent actions in content body.
                                }
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                LazyColumn(
                    state = contentListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = contentReveal
                            translationY = (1f - contentReveal) * 36f
                        }
                        .padding(innerPadding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (currentPage) {
                        AppPage.DASHBOARD -> {
                            if (state.connected) {
                                item {
                                    ServerOverviewCard(
                                        serverVersion = state.serverVersion,
                                        transferInfo = state.transferInfo,
                                        torrents = state.torrents,
                                        torrentCount = state.torrents.size,
                                        showTotals = state.settings.showSpeedTotals,
                                        onRefresh = viewModel::refresh,
                                        onOpenTorrentList = ::openTorrentList,
                                    )
                                }
                            } else {
                                item {
                                    NeedConnectionCard(
                                        onOpenConnection = { openSettings() },
                                    )
                                }
                            }
                        }

                        AppPage.TORRENT_LIST -> {
                            if (state.connected) {
                                if (showTorrentSearchBar) {
                                    item {
                                        TorrentSearchInputCard(
                                            query = torrentSearchQuery,
                                            onQueryChange = { torrentSearchQuery = it },
                                            onClear = { torrentSearchQuery = "" },
                                        )
                                    }
                                }
                                items(
                                    items = visibleTorrents,
                                    key = { it.hash.ifBlank { it.name } },
                                ) { torrent ->
                                    TorrentCard(
                                        torrent = torrent,
                                        crossSeedCount = crossSeedCounts[torrentIdentityKey(torrent)] ?: 0,
                                        isPending = state.pendingHashes.contains(torrent.hash),
                                        onOpenDetails = { openTorrentDetail(torrent) },
                                    )
                                }
                                if (visibleTorrents.isEmpty()) {
                                    item {
                                        Text(
                                            text = if (normalizedSearchQuery.isNotBlank()) "未找到匹配种子" else "暂无种子数据",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            } else {
                                item {
                                    NeedConnectionCard(
                                        onOpenConnection = { openSettings() },
                                    )
                                }
                            }
                        }

                        AppPage.TORRENT_DETAIL -> {
                            val torrent = selectedTorrent
                            if (torrent == null) {
                                item {
                                    Text(
                                        text = stringResource(R.string.torrent_detail_not_found),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                item {
                                    TorrentOperationDetailCard(
                                        torrent = torrent,
                                        crossSeedCount = crossSeedCounts[torrentIdentityKey(torrent)] ?: 0,
                                        isPending = state.pendingHashes.contains(torrent.hash),
                                        detailLoading = state.detailLoading && state.detailHash == torrent.hash,
                                        detailProperties = if (state.detailHash == torrent.hash) state.detailProperties else null,
                                        detailFiles = if (state.detailHash == torrent.hash) state.detailFiles else emptyList(),
                                        detailTrackers = if (state.detailHash == torrent.hash) state.detailTrackers else emptyList(),
                                        categoryOptions = state.categoryOptions,
                                        tagOptions = state.tagOptions,
                                        deleteFilesDefault = state.settings.deleteFilesDefault,
                                        deleteFilesWhenNoSeeders = state.settings.deleteFilesWhenNoSeeders,
                                        onPause = { viewModel.pauseTorrent(torrent.hash) },
                                        onResume = { viewModel.resumeTorrent(torrent.hash) },
                                        onDelete = { deleteFiles ->
                                            viewModel.deleteTorrent(torrent.hash, deleteFiles)
                                        },
                                        onRename = { viewModel.renameTorrent(torrent.hash, it) },
                                        onSetLocation = { viewModel.setTorrentLocation(torrent.hash, it) },
                                        onSetCategory = { viewModel.setTorrentCategory(torrent.hash, it) },
                                        onSetTags = { oldTags, newTags ->
                                            viewModel.setTorrentTags(torrent.hash, oldTags, newTags)
                                        },
                                        onSetSpeedLimit = { dl, up ->
                                            viewModel.setTorrentSpeedLimit(torrent.hash, dl, up)
                                        },
                                        onSetShareRatio = { ratio ->
                                            viewModel.setTorrentShareRatio(torrent.hash, ratio)
                                        },
                                    )
                                }
                            }
                        }

                        AppPage.SETTINGS -> {
                            item {
                                SettingsPageContent(
                                    settings = state.settings,
                                    onShowSpeedTotalsChange = viewModel::updateShowSpeedTotals,
                                    onEnableServerGroupingChange = viewModel::updateEnableServerGrouping,
                                    onDeleteFilesWhenNoSeedersChange = viewModel::updateDeleteFilesWhenNoSeeders,
                                    onDeleteFilesDefaultChange = viewModel::updateDeleteFilesDefault,
                                )
                            }
                            item {
                                ConnectionCard(
                                    state = state,
                                    onHostChange = viewModel::updateHost,
                                    onPortChange = viewModel::updatePort,
                                    onHttpsChange = viewModel::updateUseHttps,
                                    onUserChange = viewModel::updateUsername,
                                    onPasswordChange = viewModel::updatePassword,
                                    onRefreshSecondsChange = viewModel::updateRefreshSeconds,
                                    onConnect = {
                                        viewModel.connect()
                                        currentPage = AppPage.DASHBOARD
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 72.dp)
                    .height(56.dp)
                    .pointerInput(contentListState) {
                        detectTapGestures(
                            onDoubleTap = {
                                scope.launch {
                                    if (contentListState.firstVisibleItemIndex > 0 ||
                                        contentListState.firstVisibleItemScrollOffset > 0
                                    ) {
                                        contentListState.animateScrollToItem(0)
                                    }
                                }
                            },
                        )
                    },
            )

            if (showServerProfilesSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showServerProfilesSheet = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = PanelShape,
                ) {
                    ServerProfilesSheet(
                        profiles = state.serverProfiles,
                        activeProfileId = state.activeServerProfileId,
                        currentSettings = state.settings,
                        onDismiss = { showServerProfilesSheet = false },
                        onConnectProfile = { profileId ->
                            viewModel.connectServerProfile(profileId)
                            showServerProfilesSheet = false
                        },
                        onDeleteProfile = { profileId ->
                            viewModel.deleteServerProfile(profileId)
                        },
                        onAddProfile = { name, host, port, useHttps, username, password, refreshSeconds, connectNow ->
                            viewModel.addServerProfile(
                                name = name,
                                host = host,
                                portText = port,
                                useHttps = useHttps,
                                username = username,
                                password = password,
                                refreshSecondsText = refreshSeconds,
                                connectNow = connectNow,
                            )
                            if (connectNow) {
                                showServerProfilesSheet = false
                            }
                        },
                    )
                }
            }

            if (showAddTorrentSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAddTorrentSheet = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = PanelShape,
                ) {
                    AddTorrentSheet(
                        context = localContext,
                        categoryOptions = categoryOptionsForAdd,
                        tagOptions = tagOptionsForAdd,
                        pathOptions = pathOptionsForAdd,
                        onCancel = { showAddTorrentSheet = false },
                        onAdd = { urls, files, autoTmm, category, tags, savePath, paused, skipChecking, sequential, firstLast, upKb, dlKb ->
                            viewModel.addTorrent(
                                urls = urls,
                                files = files,
                                autoTmm = autoTmm,
                                category = category,
                                tags = tags,
                                savePath = savePath,
                                paused = paused,
                                skipChecking = skipChecking,
                                sequentialDownload = sequential,
                                firstLastPiecePrio = firstLast,
                                uploadLimitKb = upKb,
                                downloadLimitKb = dlKb,
                            )
                            showAddTorrentSheet = false
                        },
                    )
                }
            }

        }
    }
}

@Composable
private fun TorrentSearchInputCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索种子") },
                placeholder = { Text("按名称/标签/分类/Hash 搜索") },
            )
            if (query.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClear) {
                        Text("清空")
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerProfilesSheet(
    profiles: List<ServerProfile>,
    activeProfileId: String?,
    currentSettings: ConnectionSettings,
    onDismiss: () -> Unit,
    onConnectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onAddProfile: (
        name: String,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
        connectNow: Boolean,
    ) -> Unit,
) {
    var nameInput by remember { mutableStateOf("") }
    var hostInput by remember { mutableStateOf(currentSettings.host) }
    var portInput by remember { mutableStateOf(currentSettings.port.coerceIn(1, 65535).toString()) }
    var useHttps by remember { mutableStateOf(currentSettings.useHttps) }
    var usernameInput by remember { mutableStateOf(currentSettings.username.ifBlank { "admin" }) }
    var passwordInput by remember { mutableStateOf(currentSettings.password) }
    var refreshSecondsInput by remember { mutableStateOf(currentSettings.refreshSeconds.coerceIn(10, 120).toString()) }
    var connectNow by remember { mutableStateOf(true) }

    val canSave = hostInput.trim().isNotBlank() && usernameInput.trim().isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 740.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "服务器管理",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "已保存服务器",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (profiles.isEmpty()) {
                    Text(
                        text = "暂无服务器配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    profiles.forEach { profile ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.resolvedName(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = buildServerAddressText(
                                            ConnectionSettings(
                                                host = profile.host,
                                                port = profile.port,
                                                useHttps = profile.useHttps,
                                            )
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                if (activeProfileId == profile.id) {
                                    Text(
                                        text = "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                }

                                TextButton(onClick = { onConnectProfile(profile.id) }) {
                                    Text("连接")
                                }
                                TextButton(onClick = { onDeleteProfile(profile.id) }) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "新增服务器",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("显示名称（可选）") },
                    placeholder = { Text("例如：家里 qB") },
                )
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("服务器地址") },
                    placeholder = { Text("192.168.1.12 或 qb.example.com") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("端口") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = refreshSecondsInput,
                        onValueChange = { refreshSecondsInput = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("刷新间隔(秒)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("用户名") },
                )
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                SettingSwitchRow(
                    title = "使用 HTTPS",
                    checked = useHttps,
                    onCheckedChange = { useHttps = it },
                )
                SettingSwitchRow(
                    title = "保存后立即连接",
                    checked = connectNow,
                    onCheckedChange = { connectNow = it },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("关闭") }
            TextButton(
                enabled = canSave,
                onClick = {
                    onAddProfile(
                        nameInput,
                        hostInput,
                        portInput,
                        useHttps,
                        usernameInput,
                        passwordInput,
                        refreshSecondsInput,
                        connectNow,
                    )
                    if (!connectNow) {
                        nameInput = ""
                        hostInput = ""
                        usernameInput = "admin"
                        passwordInput = ""
                    }
                },
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun AddTorrentSheet(
    context: Context,
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
    var urls by remember { mutableStateOf("") }
    var selectedFiles by remember { mutableStateOf(listOf<AddTorrentFile>()) }
    var autoTmm by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var savePath by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var skipChecking by remember { mutableStateOf(false) }
    var sequentialDownload by remember { mutableStateOf(true) }
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
            text = "添加种子",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "种子链接",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "添加多个时请换行",
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                        text = "种子文件",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(
                        onClick = { pickTorrentLauncher.launch(arrayOf("*/*")) },
                    ) {
                        Text("+")
                    }
                }
                if (selectedFiles.isEmpty()) {
                    Text(
                        text = "点击右侧 + 按钮添加 .torrent 文件",
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
                            text = "还有 ${selectedFiles.size - 5} 个文件",
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingSwitchRow(
                    title = "自动种子管理",
                    checked = autoTmm,
                    onCheckedChange = { autoTmm = it },
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("添加分类") },
                    placeholder = { Text("不设置则留空") },
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
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("添加标签") },
                    placeholder = { Text("多个标签用逗号分隔") },
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
                    label = { Text("保存路径（可手动输入）") },
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
                    title = "添加后暂停",
                    checked = paused,
                    onCheckedChange = { paused = it },
                )
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingSwitchRow(
                    title = "跳过哈希校验",
                    checked = skipChecking,
                    onCheckedChange = { skipChecking = it },
                )
                SettingSwitchRow(
                    title = "按顺序下载",
                    checked = sequentialDownload,
                    onCheckedChange = { sequentialDownload = it },
                )
                SettingSwitchRow(
                    title = "先下载首尾文件块",
                    checked = firstLastPiecePrio,
                    onCheckedChange = { firstLastPiecePrio = it },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uploadLimitKb,
                        onValueChange = { uploadLimitKb = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("上传限速 KB/s") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = downloadLimitKb,
                        onValueChange = { downloadLimitKb = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("下载限速 KB/s") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
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
                Text("添加")
            }
        }
    }
}

private fun readTorrentFile(context: Context, uri: Uri): AddTorrentFile? {
    return runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val name = readDisplayName(context, uri).ifBlank { "upload.torrent" }
        AddTorrentFile(name = name, bytes = bytes)
    }.getOrNull()
}

private fun readDisplayName(context: Context, uri: Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")
}

@Composable
private fun DrawerThemeItem(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.menu_theme),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (darkTheme) {
                    stringResource(R.string.theme_dark)
                } else {
                    stringResource(R.string.theme_light)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = darkTheme,
            onCheckedChange = onThemeChange,
        )
    }
}

@Composable
private fun NeedConnectionCard(
    onOpenConnection: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.connect_first_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onOpenConnection,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) {
                    Text(stringResource(R.string.go_to_settings))
                }
            }

        }
    }
}

@Composable
private fun CrossSeedDetailSummaryCard(
    sourceName: String,
    count: Int,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.cross_seed_detail_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (sourceName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.cross_seed_detail_source_fmt, sourceName),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.cross_seed_detail_count_fmt, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CrossSeedDetailCard(torrent: TorrentInfo) {
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
    val savePathText = torrent.savePath.ifBlank { "-" }
    val siteText = trackerSiteName(torrent.tracker)
    val stateStyle = torrentStateStyle(effectiveState)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = torrent.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = formatAddedOn(torrent.addedOn),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = siteText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    item {
                        TorrentMetaChip(
                            text = tagsText,
                            containerColor = Color(0xFF0B8F6F),
                            contentColor = Color(0xFFE1FFF4),
                        )
                    }
                    item {
                        TorrentStateTag(
                            label = stateLabel,
                            style = stateStyle,
                        )
                    }
                }
                Text(
                    text = formatActiveAgo(torrent.lastActivity),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            TorrentInfoCell(
                text = "↑ ${formatSpeed(torrent.uploadSpeed)}    ↓ ${formatSpeed(torrent.downloadSpeed)}",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TorrentInfoCell(
                    text = "已传 ${formatBytes(torrent.uploaded)}    已下 ${formatBytes(torrent.downloaded)}",
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = "大小 ${formatBytes(torrent.size)}",
                    modifier = Modifier.weight(1f),
                )
            }

            TorrentInfoCell(
                text = "分类 $categoryText",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TorrentInfoCell(
                    text = stringResource(R.string.torrent_ratio_fmt, formatRatio(torrent.ratio)),
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = stringResource(R.string.torrent_seed_count_fmt, torrent.seeders, torrent.numComplete),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TorrentInfoCell(
                    text = stringResource(R.string.torrent_peer_count_fmt, torrent.leechers, torrent.numIncomplete),
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = stringResource(R.string.cross_seed_detail_site_fmt, siteText),
                    modifier = Modifier.weight(1f),
                )
            }

            TorrentInfoCell(
                text = "路径 $savePathText",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsPanelCard(
    content: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = { content() },
        )
    }
}

@Composable
private fun SettingsPageContent(
    settings: ConnectionSettings,
    onShowSpeedTotalsChange: (Boolean) -> Unit,
    onEnableServerGroupingChange: (Boolean) -> Unit,
    onDeleteFilesWhenNoSeedersChange: (Boolean) -> Unit,
    onDeleteFilesDefaultChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsPanelCard {
            SettingSwitchRow(
                title = stringResource(R.string.settings_show_speed_totals),
                checked = settings.showSpeedTotals,
                onCheckedChange = onShowSpeedTotalsChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_enable_server_grouping),
                checked = settings.enableServerGrouping,
                onCheckedChange = onEnableServerGroupingChange,
            )
        }
        SettingsPanelCard {
            SettingSwitchRow(
                title = stringResource(R.string.settings_delete_when_no_seeders),
                checked = settings.deleteFilesWhenNoSeeders,
                onCheckedChange = onDeleteFilesWhenNoSeedersChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_delete_by_default),
                checked = settings.deleteFilesDefault,
                onCheckedChange = onDeleteFilesDefaultChange,
            )
        }
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
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.connection_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.host,
                onValueChange = onHostChange,
                singleLine = true,
                label = { Text(stringResource(R.string.connection_host_label)) },
                placeholder = { Text(stringResource(R.string.connection_host_hint)) },
                shape = RoundedCornerShape(14.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.width(120.dp),
                    value = if (state.settings.port == 0) "" else state.settings.port.toString(),
                    onValueChange = onPortChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.connection_port_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )

                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.settings.refreshSeconds.toString(),
                    onValueChange = onRefreshSecondsChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.connection_refresh_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.username,
                onValueChange = onUserChange,
                singleLine = true,
                label = { Text(stringResource(R.string.connection_username_label)) },
                shape = RoundedCornerShape(14.dp),
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.password,
                onValueChange = onPasswordChange,
                singleLine = true,
                label = { Text(stringResource(R.string.connection_password_label)) },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.connection_https_label))
                Switch(
                    checked = state.settings.useHttps,
                    onCheckedChange = onHttpsChange,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onConnect,
                        enabled = !state.isConnecting,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Text(
                            if (state.isConnecting) {
                                stringResource(R.string.connecting)
                            } else {
                                stringResource(R.string.connect)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerOverviewCard(
    serverVersion: String,
    transferInfo: TransferInfo,
    torrents: List<TorrentInfo>,
    torrentCount: Int,
    showTotals: Boolean,
    onRefresh: () -> Unit,
    onOpenTorrentList: () -> Unit,
) {
    val stateSummary = remember(torrents) { buildDashboardStateSummary(torrents) }
    val uploadLimitText = formatRateLimit(transferInfo.uploadRateLimit)
    val downloadLimitText = formatRateLimit(transferInfo.downloadRateLimit)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTorrentList() },
        shape = PanelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_qbremote_foreground),
                    contentDescription = "QB Remote",
                    modifier = Modifier.size(34.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "QB Remote",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "版本 $serverVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TorrentInfoCell(
                    text = "↓ ${formatSpeed(transferInfo.downloadSpeed)}",
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = "↑ ${formatSpeed(transferInfo.uploadSpeed)}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TorrentInfoCell(
                    text = "正在上传 ${stateSummary.uploadingCount}",
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = "正在下载 ${stateSummary.downloadingCount}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TorrentInfoCell(
                    text = "暂停上传 ${stateSummary.pausedUploadCount}",
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = "暂停下载 ${stateSummary.pausedDownloadCount}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TorrentInfoCell(
                    text = "错误状态 ${stateSummary.errorCount}",
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = "校验状态 ${stateSummary.checkingCount}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TorrentInfoCell(
                    text = "等待状态 ${stateSummary.waitingCount}",
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = "种子数 $torrentCount",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TorrentInfoCell(
                    text = "上传限速 $uploadLimitText",
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = "下载限速 $downloadLimitText",
                    modifier = Modifier.weight(1f),
                )
            }
            if (showTotals) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TorrentInfoCell(
                        text = "总下载 ${formatBytes(transferInfo.downloadedTotal)}",
                        modifier = Modifier.weight(1f),
                    )
                    TorrentInfoCell(
                        text = "总上传 ${formatBytes(transferInfo.uploadedTotal)}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChartPanelCard(
    entries: List<SiteChartEntry>,
    chartSortMode: ChartSortMode,
    showSiteName: Boolean,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "标签统计（${chartSortModeLabel(chartSortMode)}）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.chart_no_data),
                    color = MaterialTheme.colorScheme.onSurface,
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
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentCard(
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.24f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "▲ ${formatSpeed(torrent.uploadSpeed)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6E8DFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "▼ ${formatSpeed(torrent.downloadSpeed)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF5B95),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.weight(0.44f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "📤 ${formatBytes(torrent.uploaded)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "📥 ${formatBytes(torrent.downloaded)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "🏷️ $categoryText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "📁 $savePathText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.weight(0.32f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "⚖ ${formatRatio(torrent.ratio)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "💾 ${formatBytes(torrent.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "🌱 ${torrent.seeders}/${torrent.numComplete}  👥 ${torrent.leechers}/${torrent.numIncomplete}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

        }
    }
}

@Composable
private fun TorrentOperationDetailCard(
    torrent: TorrentInfo,
    crossSeedCount: Int,
    isPending: Boolean,
    detailLoading: Boolean,
    detailProperties: TorrentProperties?,
    detailFiles: List<TorrentFileInfo>,
    detailTrackers: List<com.hjw.qbremote.data.model.TorrentTracker>,
    categoryOptions: List<String>,
    tagOptions: List<String>,
    deleteFilesDefault: Boolean,
    deleteFilesWhenNoSeeders: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onSetLocation: (String) -> Unit,
    onSetCategory: (String) -> Unit,
    onSetTags: (String, String) -> Unit,
    onSetSpeedLimit: (String, String) -> Unit,
    onSetShareRatio: (String) -> Unit,
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

    var selectedTab by remember(torrent.hash) { mutableStateOf(0) }

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

    val effectiveState = effectiveTorrentState(torrent)
    val paused = isPausedState(effectiveState)
    val stateLabel = localizedTorrentStateLabel(effectiveState)
    val stateStyle = torrentStateStyle(effectiveState)
    val tagsText = compactTagsLabel(
        tags = torrent.tags,
        noTagsText = stringResource(R.string.no_tags),
    )
    val addedOnText = formatAddedOn(torrent.addedOn)
    val activeAgoText = formatActiveAgo(torrent.lastActivity)
    val categoryText = normalizeCategoryLabel(
        category = torrent.category,
        noCategoryText = stringResource(R.string.no_category),
    )
    val savePathText = torrent.savePath.ifBlank { "-" }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        ),
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
                listOf("信息", "服务器", "用户", "文件").forEachIndexed { index, label ->
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
                        TorrentMetaHeaderRow(
                            tagsText = tagsText,
                            crossSeedCount = crossSeedCount,
                            stateLabel = stateLabel,
                            stateStyle = stateStyle,
                            addedOnText = addedOnText,
                            activeAgoText = activeAgoText,
                        )
                        TorrentInfoCell(
                            text = formatPercent(torrent.progress),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LinearProgressIndicator(
                            progress = { torrent.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = stateStyle.progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(0.24f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "▲ ${formatSpeed(torrent.uploadSpeed)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6E8DFF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "▼ ${formatSpeed(torrent.downloadSpeed)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF5B95),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Column(
                                modifier = Modifier.weight(0.44f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = "📤 ${formatBytes(torrent.uploaded)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "📥 ${formatBytes(torrent.downloaded)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "🏷️ $categoryText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "📁 $savePathText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Column(
                                modifier = Modifier.weight(0.32f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = "⚖ ${formatRatio(torrent.ratio)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "💾 ${formatBytes(torrent.size)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "🌱 ${torrent.seeders}/${torrent.numComplete}  👥 ${torrent.leechers}/${torrent.numIncomplete}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

                        Text("名称", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ActionInputRow(
                            label = "新名称",
                            value = renameText,
                            onValueChange = { renameText = it },
                            actionText = "更改",
                            enabled = !isPending,
                            onAction = { onRename(renameText.trim()) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        Text("路径", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "手动修改路径后，会自动关闭“自动种子管理”",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ActionInputRow(
                            label = "保存路径",
                            value = locationText,
                            onValueChange = { locationText = it },
                            actionText = "更改",
                            enabled = !isPending,
                            onAction = { onSetLocation(locationText.trim()) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        Text("分类", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            label = "分类",
                            value = categoryTextInput,
                            onValueChange = { categoryTextInput = it },
                            actionText = "更改",
                            enabled = !isPending,
                            onAction = { onSetCategory(categoryTextInput.trim()) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        Text("标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            label = "标签",
                            value = tagsTextInput,
                            onValueChange = { tagsTextInput = it },
                            actionText = "更改",
                            enabled = !isPending,
                            onAction = { onSetTags(torrent.tags, tagsTextInput.trim()) },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        Text("种子限速", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = downloadLimitText,
                                onValueChange = { downloadLimitText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("下载 KB/s") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !isPending,
                            )
                            OutlinedTextField(
                                value = uploadLimitText,
                                onValueChange = { uploadLimitText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("上传 KB/s") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !isPending,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onSetSpeedLimit(downloadLimitText, uploadLimitText) }, enabled = !isPending) {
                                Text("应用")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        Text("分享比率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ActionInputRow(
                            label = "比率",
                            value = ratioText,
                            onValueChange = { ratioText = it },
                            actionText = "应用",
                            enabled = !isPending,
                            onAction = { onSetShareRatio(ratioText.trim()) },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    if (paused) onResume() else onPause()
                                },
                                enabled = !isPending,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                            ) {
                                Text(if (paused) "继续" else "暂停")
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
                                Text("删除")
                            }
                        }
                    }
                }

                1 -> {
                    if (detailLoading) {
                        Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TorrentMetaChip(
                            text = "🌐 ${detailTrackers.size}",
                            containerColor = Color(0xFF6C3FD3),
                            contentColor = Color.White,
                        )
                    }
                    if (detailTrackers.isEmpty()) {
                        Text(
                            text = "暂无 Tracker 信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        detailTrackers.forEach { tracker ->
                            TrackerInfoCard(tracker = tracker)
                        }
                    }
                }

                2 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TorrentInfoCell(
                            text = stringResource(R.string.torrent_seed_count_fmt, torrent.seeders, torrent.numComplete),
                            modifier = Modifier.weight(1f),
                        )
                        TorrentInfoCell(
                            text = stringResource(R.string.torrent_peer_count_fmt, torrent.leechers, torrent.numIncomplete),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TorrentInfoCell(
                            text = stringResource(R.string.torrent_cross_seed_chip_fmt, crossSeedCount),
                            modifier = Modifier.weight(1f),
                        )
                        TorrentInfoCell(
                            text = stringResource(R.string.torrent_ratio_fmt, formatRatio(torrent.ratio)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TorrentInfoCell(
                        text = "最近活跃 ${formatActiveAgo(torrent.lastActivity)}",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                3 -> {
                    if (detailLoading) {
                        Text("加载文件中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (detailFiles.isEmpty()) {
                        Text("暂无文件详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        detailFiles.take(120).forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = file.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatBytes(file.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = formatPercent(file.progress),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
}

@Composable
private fun ActionInputRow(
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
private fun TrackerInfoCard(tracker: com.hjw.qbremote.data.model.TorrentTracker) {
    val status = trackerStatusLabel(tracker.status)
    val statusColor = trackerStatusColor(tracker.status)
    val message = tracker.message.trim().ifBlank { "正常工作" }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TorrentMetaChip(
                    text = status,
                    containerColor = statusColor.copy(alpha = 0.22f),
                    contentColor = statusColor,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = tracker.url.ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Peers ${tracker.numPeers}  Seeds ${tracker.numSeeds}  Leeches ${tracker.numLeeches}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun trackerStatusLabel(status: Int): String {
    return when (status) {
        0 -> "禁用"
        1 -> "未联系"
        2 -> "工作中"
        3 -> "更新中"
        4 -> "不可用"
        else -> "未知"
    }
}

private fun trackerStatusColor(status: Int): Color {
    return when (status) {
        0 -> Color(0xFF9E9E9E)
        1 -> Color(0xFF90A4AE)
        2 -> Color(0xFF4CAF50)
        3 -> Color(0xFFFFC107)
        4 -> Color(0xFFE53935)
        else -> Color(0xFF607D8B)
    }
}

private fun parseTags(input: String): List<String> {
    return input
        .split(',', ';', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun toggleTag(current: String, option: String): String {
    val tags = parseTags(current).toMutableList()
    val idx = tags.indexOfFirst { it.equals(option, ignoreCase = false) }
    if (idx >= 0) {
        tags.removeAt(idx)
    } else {
        tags.add(option)
    }
    return tags.joinToString(",")
}

private data class TorrentStateStyle(
    val borderColor: Color,
    val progressColor: Color,
    val tagContainer: Color,
    val tagContent: Color,
)

@Composable
private fun torrentStateStyle(state: String): TorrentStateStyle {
    val normalized = normalizeTorrentState(state)
    val base = when (normalized) {
        "error", "missingfiles" -> Color(0xFFD32F2F)
        "downloading", "stalleddl", "forceddl" -> Color(0xFF1E88E5)
        "uploading", "stalledup", "forcedup" -> Color(0xFF2E7D32)
        "pauseddl", "pausedup", "stoppeddl", "stoppedup" -> Color(0xFF6D6D6D)
        "queueddl", "queuedup", "checkingdl", "checkingup", "checkingresumedata", "metadl", "forcedmetadl", "allocating", "moving" -> Color(0xFFF9A825)
        else -> Color(0xFF607D8B)
    }
    return TorrentStateStyle(
        borderColor = base,
        progressColor = base,
        tagContainer = base.copy(alpha = 0.20f),
        tagContent = base,
    )
}

@Composable
private fun TorrentStateTag(
    label: String,
    style: TorrentStateStyle,
) {
    Box(
        modifier = Modifier
            .background(style.tagContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = style.tagContent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TorrentMetaChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TorrentInfoCell(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                shape = RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsDialog(
    settings: ConnectionSettings,
    onDismiss: () -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onShowSpeedTotalsChange: (Boolean) -> Unit,
    onEnableServerGroupingChange: (Boolean) -> Unit,
    onShowChartPanelChange: (Boolean) -> Unit,
    onChartShowSiteNameChange: (Boolean) -> Unit,
    onChartSortModeChange: (ChartSortMode) -> Unit,
    onDeleteFilesWhenNoSeedersChange: (Boolean) -> Unit,
    onDeleteFilesDefaultChange: (Boolean) -> Unit,
) {
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showChartSortMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PanelShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { showLanguageMenu = true }) {
                            Text(appLanguageLabel(settings.appLanguage))
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                        ) {
                            AppLanguage.entries.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(appLanguageLabel(language)) },
                                    onClick = {
                                        onAppLanguageChange(language)
                                        showLanguageMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                SettingSwitchRow(
                    title = stringResource(R.string.settings_show_speed_totals),
                    checked = settings.showSpeedTotals,
                    onCheckedChange = onShowSpeedTotalsChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_enable_server_grouping),
                    checked = settings.enableServerGrouping,
                    onCheckedChange = onEnableServerGroupingChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_show_chart_panel),
                    checked = settings.showChartPanel,
                    onCheckedChange = onShowChartPanelChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_show_site_name),
                    checked = settings.chartShowSiteName,
                    onCheckedChange = onChartShowSiteNameChange,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_chart_sort_mode),
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
                    title = stringResource(R.string.settings_delete_when_no_seeders),
                    checked = settings.deleteFilesWhenNoSeeders,
                    onCheckedChange = onDeleteFilesWhenNoSeedersChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_delete_by_default),
                    checked = settings.deleteFilesDefault,
                    onCheckedChange = onDeleteFilesDefaultChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
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

@Composable
private fun localizedTorrentStateLabel(state: String): String {
    return when (normalizeTorrentState(state)) {
        "downloading", "stalleddl" -> stringResource(R.string.state_downloading)
        "uploading", "stalledup" -> stringResource(R.string.state_seeding)
        "pauseddl", "pausedup" -> stringResource(R.string.state_paused)
        "error", "missingfiles" -> stringResource(R.string.state_error)
        "queueddl", "queuedup" -> stringResource(R.string.state_queued)
        "metadl", "forcedmetadl" -> stringResource(R.string.state_metadata)
        "checkingdl", "checkingup", "checkingresumedata" -> stringResource(R.string.state_checking)
        "allocating", "moving" -> stringResource(R.string.state_preparing)
        "stoppeddl", "stoppedup" -> stringResource(R.string.state_stopped)
        "forceddl", "forcedup" -> stringResource(R.string.state_forced)
        "unknown", "" -> stringResource(R.string.state_unknown)
        else -> stringResource(R.string.state_unknown)
    }
}

private fun isPausedState(state: String): Boolean {
    return normalizeTorrentState(state) in setOf("pauseddl", "pausedup", "stoppeddl", "stoppedup")
}

private fun isActiveTransferState(state: String): Boolean {
    return normalizeTorrentState(state) in setOf(
        "downloading", "forceddl", "stalleddl", "metadl", "forcedmetadl",
        "checkingdl", "queueddl", "allocating", "moving", "checkingresumedata",
        "uploading", "forcedup", "stalledup", "checkingup", "queuedup"
    )
}

private fun effectiveTorrentState(torrent: TorrentInfo): String {
    val normalized = normalizeTorrentState(torrent.state)
    if (normalized.isNotBlank() && normalized != "unknown") return normalized
    if (torrent.uploadSpeed > 0L) return "uploading"
    if (torrent.downloadSpeed > 0L) return "downloading"
    if (torrent.progress >= 1f && (torrent.uploaded > 0L || torrent.downloaded > 0L || torrent.size > 0L)) {
        return "stalledup"
    }
    if (torrent.progress > 0f || torrent.downloaded > 0L) return "stalleddl"
    return if (normalized.isBlank()) "unknown" else normalized
}

private fun normalizeTorrentState(state: String): String {
    return state.trim().lowercase()
}

private fun buildDashboardStateSummary(torrents: List<TorrentInfo>): DashboardStateSummary {
    if (torrents.isEmpty()) return DashboardStateSummary()

    var uploading = 0
    var downloading = 0
    var pausedUpload = 0
    var pausedDownload = 0
    var error = 0
    var checking = 0
    var waiting = 0

    torrents.forEach { torrent ->
        when (normalizeTorrentState(effectiveTorrentState(torrent))) {
            "uploading", "forcedup", "stalledup" -> uploading++
            "downloading", "forceddl", "stalleddl", "metadl", "forcedmetadl", "allocating", "moving" -> downloading++
            "pausedup", "stoppedup" -> pausedUpload++
            "pauseddl", "stoppeddl" -> pausedDownload++
            "error", "missingfiles" -> error++
            "checkingdl", "checkingup", "checkingresumedata" -> checking++
            "queueddl", "queuedup" -> waiting++
        }
    }

    return DashboardStateSummary(
        uploadingCount = uploading,
        downloadingCount = downloading,
        pausedUploadCount = pausedUpload,
        pausedDownloadCount = pausedDownload,
        errorCount = error,
        checkingCount = checking,
        waitingCount = waiting,
    )
}

private fun formatRateLimit(value: Long): String {
    return if (value <= 0L) {
        "不限"
    } else {
        formatSpeed(value)
    }
}

@Composable
private fun TorrentMetaHeaderRow(
    tagsText: String,
    crossSeedCount: Int,
    stateLabel: String,
    stateStyle: TorrentStateStyle,
    addedOnText: String,
    activeAgoText: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            item {
                TorrentMetaChip(
                    text = tagsText,
                    containerColor = Color(0xFF0B8F6F),
                    contentColor = Color(0xFFE1FFF4),
                )
            }
            item {
                TorrentMetaChip(
                    text = stringResource(R.string.torrent_cross_seed_chip_fmt, crossSeedCount),
                    containerColor = Color(0xFF1F7AE0),
                    contentColor = Color(0xFFE4F0FF),
                )
            }
            item {
                TorrentStateTag(
                    label = stateLabel,
                    style = stateStyle,
                )
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 170.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TorrentTimestampLabel(icon = "🕓", text = addedOnText)
            TorrentTimestampLabel(icon = "⏱", text = activeAgoText)
        }
    }
}

@Composable
private fun TorrentTimestampLabel(
    icon: String,
    text: String,
) {
    Text(
        text = "$icon $text",
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun normalizeCategoryLabel(category: String, noCategoryText: String): String {
    val normalized = category.trim()
    if (normalized.isBlank()) return noCategoryText
    if (normalized == "-" || normalized.equals("null", ignoreCase = true)) return noCategoryText
    return normalized
}

private fun compactTagsLabel(tags: String, noTagsText: String): String {
    val normalizedTags = tags
        .split(',', ';', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }

    if (normalizedTags.isEmpty()) return noTagsText
    if (normalizedTags.size <= 2) return normalizedTags.joinToString(",")

    val preview = normalizedTags.take(2).joinToString(",")
    return "$preview +${normalizedTags.size - 2}"
}

private data class CrossSeedGroupKey(
    val savePath: String,
    val size: Long,
    val uniqueIdentity: String = "",
)

private fun torrentIdentityKey(torrent: TorrentInfo): String {
    return torrent.hash.ifBlank {
        "${torrent.name}|${torrent.addedOn}|${torrent.savePath}|${torrent.size}"
    }
}

private fun matchesTorrentSearch(torrent: TorrentInfo, query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return true

    val q = normalized.lowercase()
    return torrent.name.lowercase().contains(q) ||
        torrent.hash.lowercase().contains(q) ||
        torrent.category.lowercase().contains(q) ||
        torrent.tags.lowercase().contains(q) ||
        torrent.savePath.lowercase().contains(q) ||
        torrent.tracker.lowercase().contains(q)
}

private fun sortTorrents(
    torrents: List<TorrentInfo>,
    crossSeedCounts: Map<String, Int>,
    field: TorrentListSortField,
    ascending: Boolean,
): List<TorrentInfo> {
    val comparator = Comparator<TorrentInfo> { a, b ->
        val comparison = when (field) {
            TorrentListSortField.ADDED_ON -> compareValues(a.addedOn, b.addedOn)
            TorrentListSortField.UPLOAD_SPEED -> compareValues(a.uploadSpeed, b.uploadSpeed)
            TorrentListSortField.DOWNLOAD_SPEED -> compareValues(a.downloadSpeed, b.downloadSpeed)
            TorrentListSortField.RATIO -> compareValues(a.ratio, b.ratio)
            TorrentListSortField.UPLOADED_TOTAL -> compareValues(a.uploaded, b.uploaded)
            TorrentListSortField.DOWNLOADED_TOTAL -> compareValues(a.downloaded, b.downloaded)
            TorrentListSortField.SIZE -> compareValues(a.size, b.size)
            TorrentListSortField.LAST_ACTIVITY -> compareValues(a.lastActivity, b.lastActivity)
            TorrentListSortField.SEEDERS -> compareValues(a.seeders, b.seeders)
            TorrentListSortField.LEECHERS -> compareValues(a.leechers, b.leechers)
            TorrentListSortField.CROSS_SEED_COUNT -> compareValues(
                crossSeedCounts[torrentIdentityKey(a)] ?: 0,
                crossSeedCounts[torrentIdentityKey(b)] ?: 0,
            )
        }
        if (comparison != 0) comparison else a.name.compareTo(b.name, ignoreCase = true)
    }

    val sorted = torrents.sortedWith(comparator)
    return if (ascending) sorted else sorted.reversed()
}

private fun buildCrossSeedCountMap(torrents: List<TorrentInfo>): Map<String, Int> {
    val grouped = torrents.groupBy { crossSeedGroupKey(it) }
    val result = mutableMapOf<String, Int>()

    torrents.forEach { torrent ->
        val key = crossSeedGroupKey(torrent)
        val groupCount = grouped[key]?.size ?: 1
        result[torrentIdentityKey(torrent)] = (groupCount - 1).coerceAtLeast(0)
    }
    return result
}

private fun crossSeedGroupKey(torrent: TorrentInfo): CrossSeedGroupKey {
    val normalizedPath = torrent.savePath.trim().lowercase()
    val normalizedSize = torrent.size.coerceAtLeast(0L)
    if (normalizedPath.isBlank() || normalizedSize <= 0L) {
        return CrossSeedGroupKey(
            savePath = "__invalid__",
            size = -1L,
            uniqueIdentity = torrent.hash.ifBlank { torrentIdentityKey(torrent) },
        )
    }
    return CrossSeedGroupKey(
        savePath = normalizedPath,
        size = normalizedSize,
    )
}

private fun buildTagChartEntries(
    torrents: List<TorrentInfo>,
    mode: ChartSortMode,
): List<SiteChartEntry> {
    val grouped = mutableMapOf<String, MutableList<TorrentInfo>>()
    torrents.forEach { torrent ->
        val tags = torrent.tags
            .split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }
            .ifEmpty { listOf("无标签") }
        tags.forEach { tag ->
            grouped.getOrPut(tag) { mutableListOf() }.add(torrent)
        }
    }

    return grouped.map { (tag, list) ->
        val down = list.sumOf { it.downloadSpeed }
        val up = list.sumOf { it.uploadSpeed }
        SiteChartEntry(
            site = tag,
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

@Composable
private fun chartMetricText(entry: SiteChartEntry, mode: ChartSortMode): String {
    return when (mode) {
        ChartSortMode.TOTAL_SPEED -> stringResource(
            R.string.chart_metric_total_fmt,
            formatSpeed(entry.totalSpeed)
        )
        ChartSortMode.DOWNLOAD_SPEED -> stringResource(
            R.string.chart_metric_down_fmt,
            formatSpeed(entry.downloadSpeed)
        )
        ChartSortMode.UPLOAD_SPEED -> stringResource(
            R.string.chart_metric_up_fmt,
            formatSpeed(entry.uploadSpeed)
        )
        ChartSortMode.TORRENT_COUNT -> stringResource(
            R.string.chart_metric_torrents_fmt,
            entry.torrentCount
        )
    }
}

@Composable
private fun chartSortModeLabel(mode: ChartSortMode): String {
    return when (mode) {
        ChartSortMode.TOTAL_SPEED -> stringResource(R.string.chart_sort_total_speed)
        ChartSortMode.DOWNLOAD_SPEED -> stringResource(R.string.chart_sort_download_speed)
        ChartSortMode.UPLOAD_SPEED -> stringResource(R.string.chart_sort_upload_speed)
        ChartSortMode.TORRENT_COUNT -> stringResource(R.string.chart_sort_torrent_count)
    }
}

@Composable
private fun appLanguageLabel(language: AppLanguage): String {
    return when (language) {
        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
        AppLanguage.ZH_CN -> stringResource(R.string.settings_language_zh_cn)
        AppLanguage.EN -> stringResource(R.string.settings_language_en)
    }
}

private fun trackerSiteName(tracker: String): String {
    val trimmed = tracker.trim()
    if (trimmed.isBlank()) return "未知"

    return runCatching {
        URI(trimmed).host.orEmpty().ifBlank { "未知" }
    }.getOrElse {
        trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .ifBlank { "未知" }
    }
}

private fun buildServerAddressText(settings: ConnectionSettings): String {
    val host = settings.host.trim().ifBlank { "-" }
    if (host.startsWith("http://", ignoreCase = true) || host.startsWith("https://", ignoreCase = true)) {
        return host
    }
    val scheme = if (settings.useHttps) "https" else "http"
    return "$scheme://$host:${settings.port}"
}







