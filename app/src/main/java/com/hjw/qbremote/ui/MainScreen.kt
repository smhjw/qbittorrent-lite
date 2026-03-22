package com.hjw.qbremote.ui
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.material3.Icon
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.hjw.qbremote.data.AppLanguage
import com.hjw.qbremote.data.AppTheme
import com.hjw.qbremote.R
import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ServerBackendType
import com.hjw.qbremote.data.ServerDashboardPreferences
import com.hjw.qbremote.data.ServerProfile
import com.hjw.qbremote.data.defaultCapabilitiesFor
import com.hjw.qbremote.data.model.AddTorrentFile
import com.hjw.qbremote.data.model.CountryUploadRecord
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentProperties
import com.hjw.qbremote.data.model.TransferInfo
import com.hjw.qbremote.ui.theme.qbGlassCardColors
import com.hjw.qbremote.ui.theme.qbGlassChipColor
import com.hjw.qbremote.ui.theme.qbGlassOutlineColor
import com.hjw.qbremote.ui.theme.qbGlassStrongContainerColor
import com.hjw.qbremote.ui.theme.qbGlassSubtleContainerColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

private data class DashboardStateSummary(
    val uploadingCount: Int = 0,
    val downloadingCount: Int = 0,
    val pausedUploadCount: Int = 0,
    val pausedDownloadCount: Int = 0,
    val errorCount: Int = 0,
    val checkingCount: Int = 0,
    val waitingCount: Int = 0,
)

private data class DashboardStatusPillItem(
    val label: String,
    val count: Int,
    val accentColor: Color,
)

private data class VerticalReorderSession<T>(
    val items: List<T>,
    val indexByItem: Map<T, Int>,
    val startIndex: Int,
    val slotTops: List<Float>,
    val slotCenters: List<Float>,
    val minOffset: Float,
    val maxOffset: Float,
)

data class PieLegendEntry(
    val label: String,
    val value: Long,
    val valueText: String,
)

private val HomeServerStackExpandedCardHeight = 188.dp
private val HomeServerStackCollapsedCardHeight = 90.dp
private val HomeServerStackExposedHeight = 60.dp
private val DashboardCardSpacing = 10.dp
internal const val ReorderDraggedScale = 1.018f
internal const val ReorderDraggedShadow = 18f
internal const val ReorderSelectedShadow = 22f
internal const val ReorderCollapsedShadow = 12f
private const val ServerCardClickSuppressionWindowMs = 140L
private val ServerCardClickSuppressionDragThreshold = 6.dp

private enum class AppPage {
    DASHBOARD,
    SERVER_DASHBOARD,
    TORRENT_LIST,
    TORRENT_DETAIL,
    SETTINGS,
}

private data class PageAnimationState(
    val page: AppPage,
    val dashboardSessionKey: String = "",
)

private enum class TorrentListSortOption {
    ADDED_TIME,
    UPLOAD_SPEED,
    DOWNLOAD_SPEED,
    SHARE_RATIO,
    TOTAL_UPLOADED,
    TOTAL_DOWNLOADED,
    TORRENT_SIZE,
    ACTIVITY_TIME,
    SEEDERS,
    LEECHERS,
    CROSS_SEED_COUNT,
}

enum class DashboardChartCard(
    val storageKey: String,
) {
    COUNTRY_FLOW("country_flow"),
    CATEGORY_SHARE("category_share"),
    DAILY_UPLOAD("daily_upload"),
    TAG_UPLOAD("tag_upload"),
    TORRENT_STATE("torrent_state"),
    TRACKER_SITE("tracker_site"),
}

val PanelShape = RoundedCornerShape(20.dp)
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
val DashboardPiePalette = listOf(
    Color(0xFF4C8DFF),
    Color(0xFF33BC84),
    Color(0xFFF3A53C),
    Color(0xFFA77AF2),
    Color(0xFFEF6D5E),
    Color(0xFF19B1C3),
    Color(0xFF8F9FB7),
    Color(0xFFFFCF5C),
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class,
    ExperimentalAnimationApi::class,
)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    var currentPage by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    var previousPage by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    var showAddTorrentSheet by rememberSaveable { mutableStateOf(false) }
    var showServerProfileSheet by rememberSaveable { mutableStateOf(false) }
    var serverSheetEditingProfileId by rememberSaveable { mutableStateOf("") }
    var pendingDeleteProfileId by rememberSaveable { mutableStateOf("") }
    var pendingTorrentExportHash by rememberSaveable { mutableStateOf("") }
    var pendingTorrentExportName by rememberSaveable { mutableStateOf("") }
    var selectedTorrentIdentity by rememberSaveable { mutableStateOf("") }
    var pendingTorrentReturnIdentity by rememberSaveable { mutableStateOf("") }
    var showDashboardCardManagerSheet by rememberSaveable { mutableStateOf(false) }
    var showTorrentSortMenu by remember { mutableStateOf(false) }
    var showTorrentSearchBar by rememberSaveable { mutableStateOf(false) }
    var torrentSearchQuery by rememberSaveable { mutableStateOf("") }
    var torrentListSortOption by rememberSaveable { mutableStateOf(TorrentListSortOption.UPLOAD_SPEED) }
    var torrentListSortDescending by rememberSaveable { mutableStateOf(true) }
    var sortScrollRequestId by remember { mutableIntStateOf(0) }
    val addTorrentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val serverProfileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dashboardCardManagerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val openDrawerDescription = stringResource(R.string.menu_open_drawer)
    val backDescription = stringResource(R.string.back)
    val manageServersDescription = stringResource(R.string.menu_manage_servers)
    val sortDescription = stringResource(R.string.menu_sort)
    val searchDescription = stringResource(R.string.menu_search)
    val addTorrentDescription = stringResource(R.string.menu_add_torrent)
    val localContext = LocalContext.current
    val exportTorrentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-bittorrent"),
    ) { uri ->
        val exportHash = pendingTorrentExportHash
        pendingTorrentExportHash = ""
        pendingTorrentExportName = ""
        if (uri == null || exportHash.isBlank()) return@rememberLauncherForActivityResult
        viewModel.exportTorrentFile(exportHash) { bytes ->
            runCatching {
                localContext.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                } ?: error("Unable to open export target.")
            }.onSuccess {
                scope.launch {
                    snackbarHostState.showSnackbar(localContext.getString(R.string.detail_export_success))
                }
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar(localContext.getString(R.string.detail_export_failed))
                }
            }
        }
    }
    val backgroundTargetSizePx = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density,
    ) {
        val widthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        val heightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
        (maxOf(widthPx, heightPx) * 1.18f).roundToInt().coerceAtLeast(1)
    }
    val appBackgroundBrush = when (state.settings.appTheme) {
        AppTheme.DARK -> DarkBackgroundGradient
        AppTheme.LIGHT -> LightBackgroundGradient
        AppTheme.CUSTOM -> DarkBackgroundGradient
    }
    val customBackgroundState = rememberCustomBackgroundImageState(
        path = state.settings.customBackgroundImagePath,
        targetMaxDimensionPx = backgroundTargetSizePx,
    )
    val customBackgroundImage = customBackgroundState.image
    val showCustomBackgroundImage = state.settings.appTheme == AppTheme.CUSTOM && customBackgroundImage != null
    val customBackgroundScrim = if (state.settings.customBackgroundToneIsLight) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.18f)
    }
    val crossSeedCounts = remember(state.torrents) {
        buildCrossSeedCountMap(state.torrents)
    }
    val activePendingProfileId = remember(state.activeServerProfileId) {
        state.activeServerProfileId.orEmpty()
    }
    fun isPendingAction(hash: String): Boolean {
        if (activePendingProfileId.isBlank()) return false
        return state.pendingActionKeys.contains(buildPendingActionKey(activePendingProfileId, hash))
    }
    val filteredTorrents = remember(state.torrents, torrentSearchQuery) {
        val query = torrentSearchQuery.trim()
        if (query.isBlank()) {
            state.torrents
        } else {
            state.torrents.filter { torrent ->
                matchesTorrentSearch(torrent = torrent, query = query)
            }
        }
    }
    val visibleTorrents = remember(
        filteredTorrents,
        torrentListSortOption,
        torrentListSortDescending,
        crossSeedCounts,
    ) {
        sortTorrentList(
            torrents = filteredTorrents,
            sortOption = torrentListSortOption,
            descending = torrentListSortDescending,
            crossSeedCounts = crossSeedCounts,
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
    val multiServerDashboard = state.serverProfiles.size > 1
    val selectedDashboardProfileId = state.selectedDashboardProfileId
        ?: state.activeServerProfileId
        ?: state.serverProfiles.firstOrNull()?.id
    val serverDashboardSessionKey = remember(
        selectedDashboardProfileId,
        state.dashboardSessionToken,
    ) {
        "${selectedDashboardProfileId.orEmpty()}:${state.dashboardSessionToken}"
    }
    val selectedDashboardSnapshot = remember(
        state.dashboardServerSnapshots,
        selectedDashboardProfileId,
    ) {
        state.dashboardServerSnapshots.firstOrNull { it.profileId == selectedDashboardProfileId }
    }
    val selectedServerProfile = remember(state.serverProfiles, selectedDashboardProfileId) {
        state.serverProfiles.firstOrNull { it.id == selectedDashboardProfileId }
    }
    val pendingDeleteProfile = remember(pendingDeleteProfileId, state.serverProfiles) {
        state.serverProfiles.firstOrNull { it.id == pendingDeleteProfileId }
    }
    val selectedDashboardBackendType = selectedServerProfile?.backendType ?: state.settings.serverBackendType
    val serverDashboardCapabilities = remember(selectedDashboardBackendType) {
        defaultCapabilitiesFor(selectedDashboardBackendType)
    }
    val serverDashboardVersion = remember(selectedDashboardSnapshot) {
        selectedDashboardSnapshot?.serverVersion?.ifBlank { "-" } ?: "-"
    }
    val serverDashboardTransferInfo = remember(selectedDashboardSnapshot) {
        selectedDashboardSnapshot?.transferInfo ?: TransferInfo()
    }
    val serverDashboardTorrents = remember(selectedDashboardSnapshot) {
        selectedDashboardSnapshot?.torrents.orEmpty()
    }
    val serverDashboardTorrentCount = remember(serverDashboardTorrents) {
        serverDashboardTorrents.size
    }
    val serverDashboardTagUploadDate = remember(selectedDashboardSnapshot) {
        selectedDashboardSnapshot?.dailyTagUploadDate.orEmpty()
    }
    val serverDashboardTagUploadStats: List<DailyTagUploadStat> = remember(selectedDashboardSnapshot) {
        selectedDashboardSnapshot?.dailyTagUploadStats?.map { stat ->
            DailyTagUploadStat(
                tag = stat.tag,
                uploadedBytes = stat.uploadedBytes,
                torrentCount = stat.torrentCount,
                isNoTag = stat.isNoTag,
            )
        }.orEmpty()
    }
    val serverDashboardCountryUploadStats: List<CountryUploadRecord> = remember(selectedDashboardSnapshot) {
        selectedDashboardSnapshot?.dailyCountryUploadStats.orEmpty()
    }
    val serverDashboardHasContent = remember(selectedDashboardSnapshot) {
        selectedDashboardSnapshot != null
    }
    val serverDashboardShowContent = serverDashboardHasContent
    val availableDashboardCards = remember(selectedDashboardBackendType) {
        dashboardCardsForBackend(selectedDashboardBackendType)
    }
    val currentDashboardPreferences = remember(
        state.serverDashboardPreferences,
        selectedServerProfile?.id,
        state.settings,
        selectedDashboardBackendType,
    ) {
        resolveServerDashboardPreferences(
            preferences = selectedServerProfile?.id?.let(state.serverDashboardPreferences::get),
            backendType = selectedDashboardBackendType,
        )
    }
    val showServerStackHint = multiServerDashboard &&
        !state.settings.hasSeenServerStackReorderHint
    val showDashboardSwipeHint = selectedServerProfile != null &&
        !state.settings.hasSeenServerDashboardSwipeHint
    val showDashboardCardHint = selectedServerProfile != null &&
        !state.settings.hasSeenServerDashboardCardHint
    val dashboardListState = rememberLazyListState()
    val serverDashboardListState = rememberLazyListState()
    val torrentListState = rememberLazyListState()
    val torrentDetailListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    var localDashboardCardOrder by remember(selectedServerProfile?.id, selectedDashboardBackendType) {
        mutableStateOf(parseDashboardCardOrder(currentDashboardPreferences.cardOrder, availableDashboardCards))
    }
    var localVisibleDashboardCardKeys by remember(selectedServerProfile?.id, selectedDashboardBackendType) {
        mutableStateOf(currentDashboardPreferences.visibleCards.toSet())
    }
    val displayVisibleDashboardCards = remember(
        localDashboardCardOrder,
        localVisibleDashboardCardKeys,
    ) {
        localDashboardCardOrder.filter { card ->
            card.storageKey in localVisibleDashboardCardKeys
        }
    }
    val dashboardDragGestureKey = remember(displayVisibleDashboardCards) {
        displayVisibleDashboardCards.joinToString(separator = "|") { it.storageKey }
    }
    val displayDashboardPreferences = remember(
        currentDashboardPreferences,
        localDashboardCardOrder,
        localVisibleDashboardCardKeys,
        displayVisibleDashboardCards,
    ) {
        currentDashboardPreferences.copy(
            cardOrder = serializeDashboardCardOrder(localDashboardCardOrder, availableDashboardCards),
            visibleCards = displayVisibleDashboardCards.map { it.storageKey },
        )
    }
    var draggingDashboardCard by remember { mutableStateOf<DashboardChartCard?>(null) }
    var draggingDashboardOffsetY by remember { mutableFloatStateOf(0f) }
    var draggingDashboardTargetIndex by remember { mutableIntStateOf(-1) }
    var draggingDashboardSession by remember { mutableStateOf<VerticalReorderSession<DashboardChartCard>?>(null) }
    var revealedDashboardHideCardKey by remember(selectedServerProfile?.id, currentPage) {
        mutableStateOf<String?>(null)
    }
    val dashboardCardHeights = remember { mutableStateMapOf<DashboardChartCard, Int>() }
    var draggingServerProfileId by remember { mutableStateOf<String?>(null) }
    var draggingServerOffsetY by remember { mutableFloatStateOf(0f) }
    var draggingServerTargetIndex by remember { mutableIntStateOf(-1) }
    var draggingServerSession by remember { mutableStateOf<VerticalReorderSession<String>?>(null) }

    fun listStateForPage(page: AppPage): LazyListState {
        return when (page) {
            AppPage.DASHBOARD -> dashboardListState
            AppPage.SERVER_DASHBOARD -> serverDashboardListState
            AppPage.TORRENT_LIST -> torrentListState
            AppPage.TORRENT_DETAIL -> torrentDetailListState
            AppPage.SETTINGS -> settingsListState
        }
    }

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

    fun openServerProfileSheet(editingProfileId: String? = null) {
        serverSheetEditingProfileId = editingProfileId.orEmpty()
        showServerProfileSheet = true
    }

    fun requestDeleteServerProfile(profileId: String) {
        pendingDeleteProfileId = profileId
    }

    fun copyToClipboard(value: String, successMessageRes: Int) {
        val text = value.trim()
        if (text.isBlank()) return
        clipboardManager.setText(AnnotatedString(text))
        scope.launch {
            snackbarHostState.showSnackbar(localContext.getString(successMessageRes))
        }
    }

    fun requestTorrentExport(hash: String, torrentName: String) {
        val normalizedHash = hash.trim()
        if (normalizedHash.isBlank()) return
        pendingTorrentExportHash = normalizedHash
        pendingTorrentExportName = buildTorrentExportFileName(
            torrentName = torrentName,
            hash = normalizedHash,
        )
        exportTorrentLauncher.launch(pendingTorrentExportName)
    }

    fun openTorrentList() {
        if (currentPage != AppPage.TORRENT_LIST) {
            previousPage = currentPage
        }
        currentPage = AppPage.TORRENT_LIST
    }

    fun openTorrentListFromDashboard() {
        if (!state.settings.homeTorrentEntryHintDismissed) {
            viewModel.dismissHomeTorrentEntryHint()
        }
        openTorrentList()
    }

    fun openServerDashboard(profileId: String) {
        if (profileId.isBlank()) return
        if (currentPage != AppPage.SERVER_DASHBOARD) {
            previousPage = currentPage
        }
        currentPage = AppPage.SERVER_DASHBOARD
        viewModel.switchServerProfile(profileId)
    }

    fun openTorrentDetail(torrent: TorrentInfo) {
        val torrentIdentity = torrentIdentityKey(torrent)
        selectedTorrentIdentity = torrentIdentity
        pendingTorrentReturnIdentity = if (currentPage == AppPage.TORRENT_LIST) {
            torrentIdentity
        } else {
            ""
        }
        if (currentPage != AppPage.TORRENT_DETAIL) {
            previousPage = currentPage
        }
        currentPage = AppPage.TORRENT_DETAIL
    }

    fun backToPreviousPage() {
        val nextPage = if (previousPage == currentPage) AppPage.DASHBOARD else previousPage
        if (currentPage == AppPage.TORRENT_DETAIL && nextPage != AppPage.TORRENT_LIST) {
            pendingTorrentReturnIdentity = ""
        }
        currentPage = nextPage
    }

    fun requestScrollToFirstTorrentAfterSort() {
        sortScrollRequestId += 1
    }

    fun persistDashboardCardOrderIfChanged(
        nextOrder: List<DashboardChartCard>,
        rollbackOrder: List<DashboardChartCard> = nextOrder,
    ) {
        val profileId = selectedServerProfile?.id ?: return
        val serialized = serializeDashboardCardOrder(nextOrder, availableDashboardCards)
        if (serialized != currentDashboardPreferences.cardOrder) {
            viewModel.updateServerDashboardCardOrder(profileId, nextOrder) { success ->
                if (!success && state.activeServerProfileId == profileId) {
                    localDashboardCardOrder = rollbackOrder
                }
            }
        }
    }

    fun startDashboardCardDrag(card: DashboardChartCard) {
        val visibleCards = displayVisibleDashboardCards
        val startIndex = visibleCards.indexOf(card)
        if (startIndex < 0) return
        revealedDashboardHideCardKey = null
        val itemSpacingPx = with(density) { DashboardCardSpacing.toPx() }
        val defaultCardHeightPx = with(density) { 180.dp.toPx() }
        val slotHeights = visibleCards.map { dashboardCard ->
            dashboardCardHeights[dashboardCard]?.toFloat() ?: defaultCardHeightPx
        }
        val slotTops = buildList {
            var currentTop = 0f
            visibleCards.forEachIndexed { index, _ ->
                add(currentTop)
                currentTop += slotHeights[index] + itemSpacingPx
            }
        }
        val edgeSlackPx = with(density) { 24.dp.toPx() }
        draggingDashboardCard = card
        draggingDashboardOffsetY = 0f
        draggingDashboardTargetIndex = startIndex
        draggingDashboardSession = buildVerticalReorderSession(
            items = visibleCards,
            startIndex = startIndex,
            slotTops = slotTops,
            slotHeights = slotHeights,
            edgeSlackPx = edgeSlackPx,
        )
    }

    fun updateDashboardCardDrag(card: DashboardChartCard, deltaY: Float) {
        val dragSession = draggingDashboardSession ?: return
        if (draggingDashboardCard != card) return
        draggingDashboardOffsetY = (draggingDashboardOffsetY + deltaY)
            .coerceIn(dragSession.minOffset, dragSession.maxOffset)
        draggingDashboardTargetIndex = resolveVerticalReorderTargetIndex(
            dragSession,
            draggingDashboardOffsetY,
        )
    }

    fun endDashboardCardDrag() {
        val draggedCard = draggingDashboardCard ?: return
        val dragSession = draggingDashboardSession
        val previousOrder = localDashboardCardOrder
        if (dragSession != null && draggingDashboardTargetIndex >= 0) {
            localDashboardCardOrder = reorderDashboardCardOrder(
                order = localDashboardCardOrder,
                visibleCards = dragSession.items,
                card = draggedCard,
                targetIndex = draggingDashboardTargetIndex,
            )
        }
        draggingDashboardCard = null
        draggingDashboardOffsetY = 0f
        draggingDashboardTargetIndex = -1
        draggingDashboardSession = null
        persistDashboardCardOrderIfChanged(localDashboardCardOrder, previousOrder)
    }

    fun hideDashboardCard(card: DashboardChartCard) {
        val profileId = selectedServerProfile?.id ?: return
        val previousVisibleKeys = localVisibleDashboardCardKeys
        val nextVisibleKeys = previousVisibleKeys - card.storageKey
        localVisibleDashboardCardKeys = nextVisibleKeys
        revealedDashboardHideCardKey = null
        viewModel.updateServerDashboardCardVisibility(profileId, card, false) { success ->
            if (!success && state.activeServerProfileId == profileId) {
                localVisibleDashboardCardKeys = previousVisibleKeys
            }
        }
    }

    fun startServerStackDrag(profileId: String) {
        val orderedIds = state.dashboardServerSnapshots.map { it.profileId }
        val startIndex = orderedIds.indexOf(profileId)
        if (startIndex < 0 || orderedIds.size < 2) return
        val exposedStepPx = with(density) { HomeServerStackExposedHeight.toPx() }
        val expandedHeightPx = with(density) { HomeServerStackExpandedCardHeight.toPx() }
        val collapsedHeightPx = with(density) { HomeServerStackCollapsedCardHeight.toPx() }
        val slotTops = orderedIds.indices.map { index ->
            if (index == 0) {
                (orderedIds.lastIndex * exposedStepPx)
            } else {
                (orderedIds.lastIndex - index) * exposedStepPx
            }
        }
        val slotHeights = orderedIds.indices.map { index ->
            if (index == 0) expandedHeightPx else collapsedHeightPx
        }
        val edgeSlackPx = with(density) { 24.dp.toPx() }
        draggingServerProfileId = profileId
        draggingServerOffsetY = 0f
        draggingServerTargetIndex = startIndex
        draggingServerSession = buildVerticalReorderSession(
            items = orderedIds,
            startIndex = startIndex,
            slotTops = slotTops,
            slotHeights = slotHeights,
            edgeSlackPx = edgeSlackPx,
        )
    }

    fun updateServerStackDrag(profileId: String, deltaY: Float) {
        val dragSession = draggingServerSession ?: return
        if (draggingServerProfileId != profileId) return
        draggingServerOffsetY = (draggingServerOffsetY + deltaY)
            .coerceIn(dragSession.minOffset, dragSession.maxOffset)
        draggingServerTargetIndex = resolveVerticalReorderTargetIndex(
            dragSession,
            draggingServerOffsetY,
        )
    }

    fun endServerStackDrag() {
        val profileId = draggingServerProfileId ?: return
        val dragSession = draggingServerSession
        if (dragSession != null && draggingServerTargetIndex >= 0) {
            val reorderedIds = reorderServerProfileIds(
                current = dragSession.items,
                profileId = profileId,
                targetIndex = draggingServerTargetIndex,
            )
            if (reorderedIds != dragSession.items) {
                viewModel.reorderServerProfiles(reorderedIds)
            }
        }
        draggingServerProfileId = null
        draggingServerOffsetY = 0f
        draggingServerTargetIndex = -1
        draggingServerSession = null
    }

    fun scrollToTopOfCurrentPage(animated: Boolean) {
        scope.launch {
            val targetListState = listStateForPage(currentPage)
            if (animated) {
                targetListState.animateScrollToItem(0)
            } else {
                targetListState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(sortScrollRequestId) {
        if (sortScrollRequestId <= 0) return@LaunchedEffect
        if (currentPage != AppPage.TORRENT_LIST) return@LaunchedEffect
        val targetIndex = if (showTorrentSearchBar && visibleTorrents.isNotEmpty()) 1 else 0
        torrentListState.scrollToItem(targetIndex)
        // Guard against LazyList position restore after data reordering.
        yield()
        torrentListState.scrollToItem(targetIndex)
    }

    LaunchedEffect(
        currentPage,
        pendingTorrentReturnIdentity,
        visibleTorrents,
        showTorrentSearchBar,
    ) {
        if (currentPage != AppPage.TORRENT_LIST) return@LaunchedEffect
        val anchorIdentity = pendingTorrentReturnIdentity.ifBlank { return@LaunchedEffect }
        val targetListIndex = visibleTorrents.indexOfFirst { torrentIdentityKey(it) == anchorIdentity }
        pendingTorrentReturnIdentity = ""
        if (targetListIndex < 0) return@LaunchedEffect
        val targetIndex = targetListIndex + if (showTorrentSearchBar) 1 else 0
        torrentListState.scrollToItem(targetIndex)
        yield()
        torrentListState.scrollToItem(targetIndex)
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    LaunchedEffect(currentDashboardPreferences.cardOrder, selectedServerProfile?.id, selectedDashboardBackendType) {
        localDashboardCardOrder = parseDashboardCardOrder(
            currentDashboardPreferences.cardOrder,
            availableDashboardCards,
        )
    }

    LaunchedEffect(currentDashboardPreferences.visibleCards, selectedServerProfile?.id, selectedDashboardBackendType) {
        localVisibleDashboardCardKeys = currentDashboardPreferences.visibleCards.toSet()
    }

    LaunchedEffect(revealedDashboardHideCardKey, selectedServerProfile?.id, currentPage) {
        val currentKey = revealedDashboardHideCardKey ?: return@LaunchedEffect
        if (currentPage != AppPage.SERVER_DASHBOARD) return@LaunchedEffect
        delay(5_000)
        if (revealedDashboardHideCardKey == currentKey) {
            revealedDashboardHideCardKey = null
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage != AppPage.SERVER_DASHBOARD) {
            showDashboardCardManagerSheet = false
            revealedDashboardHideCardKey = null
            draggingDashboardCard = null
            draggingDashboardOffsetY = 0f
            draggingDashboardTargetIndex = -1
            draggingDashboardSession = null
        }
        if (currentPage != AppPage.DASHBOARD) {
        }
    }

    LaunchedEffect(currentPage, selectedTorrent?.hash) {
        if (currentPage != AppPage.TORRENT_LIST) {
            showTorrentSortMenu = false
        }
        val hash = selectedTorrent?.hash.orEmpty()
        val refreshScene = when (currentPage) {
            AppPage.DASHBOARD -> RefreshScene.DASHBOARD
            AppPage.SERVER_DASHBOARD -> RefreshScene.SERVER
            AppPage.TORRENT_LIST -> RefreshScene.SERVER
            AppPage.TORRENT_DETAIL -> RefreshScene.TORRENT_DETAIL
            AppPage.SETTINGS -> RefreshScene.SETTINGS
        }
        viewModel.updateRefreshScene(refreshScene)
        if (currentPage == AppPage.TORRENT_DETAIL && hash.isNotBlank()) {
            viewModel.loadTorrentDetail(hash)
        }
    }

    LaunchedEffect(currentPage, serverDashboardSessionKey) {
        if (currentPage == AppPage.SERVER_DASHBOARD) {
            serverDashboardListState.scrollToItem(0)
        }
    }

    BackHandler(enabled = currentPage != AppPage.DASHBOARD) {
        backToPreviousPage()
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                drawerContainerColor = qbGlassStrongContainerColor(),
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
                    settings = state.settings,
                    onThemeChange = { theme ->
                        closeDrawer { viewModel.updateAppTheme(theme) }
                    },
                    onApplyCustomTheme = { imagePath, toneIsLight ->
                        closeDrawer {
                            viewModel.applyCustomThemeBackground(imagePath, toneIsLight)
                        }
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
                },
        ) {
            if (showCustomBackgroundImage) {
                customBackgroundImage?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(customBackgroundScrim),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appBackgroundBrush),
                )
            }
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    val compactTorrentTopBar = currentPage == AppPage.TORRENT_LIST
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        navigationIcon = {
                            TextButton(
                                modifier = Modifier.semantics {
                                    contentDescription = if (currentPage == AppPage.DASHBOARD) {
                                        openDrawerDescription
                                    } else {
                                        backDescription
                                    }
                                },
                                onClick = {
                                    if (currentPage == AppPage.DASHBOARD) {
                                        scope.launch { drawerState.open() }
                                    } else {
                                        backToPreviousPage()
                                    }
                                },
                                contentPadding = if (compactTorrentTopBar) {
                                    PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                } else {
                                    ButtonDefaults.TextButtonContentPadding
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (compactTorrentTopBar) {
                                            Modifier.offset(x = (-4).dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .pointerInput(currentPage) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                scrollToTopOfCurrentPage(animated = true)
                                            },
                                        )
                                    },
                            ) {
                                TopBrandTitle(
                                    modifier = Modifier.fillMaxWidth(),
                                    compact = compactTorrentTopBar,
                                )
                            }
                        },
                        actions = {
                            when (currentPage) {
                                AppPage.DASHBOARD -> {
                                    Row(
                                        modifier = Modifier.padding(end = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            modifier = Modifier.semantics {
                                                contentDescription = manageServersDescription
                                            },
                                            onClick = { openServerProfileSheet() },
                                        ) {
                                            Text(
                                                text = stringResource(R.string.menu_servers),
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                        }
                                        TextButton(onClick = { openSettings() }) {
                                            Text(
                                                text = stringResource(R.string.menu_settings),
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                        }
                                    }
                                }

                                AppPage.SERVER_DASHBOARD -> {
                                    TextButton(
                                        onClick = {
                                            viewModel.markServerDashboardCardHintSeen()
                                            showDashboardCardManagerSheet = true
                                        },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.dashboard_manage_cards_action),
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                }

                                AppPage.SETTINGS -> {
                                    TextButton(
                                        onClick = {
                                            if (state.connected) viewModel.refresh(manual = true) else viewModel.connect()
                                        },
                                    ) {
                                        Text(
                                            text = if (state.connected) {
                                                if (state.isManualRefreshing) {
                                                    stringResource(R.string.refreshing)
                                                } else {
                                                    stringResource(R.string.refresh)
                                                }
                                            } else {
                                                stringResource(R.string.connect)
                                            },
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                }

                                AppPage.TORRENT_DETAIL -> {
                                    TextButton(
                                        onClick = {
                                            if (state.connected) viewModel.refresh(manual = true) else viewModel.connect()
                                        },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.refresh),
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                }

                                AppPage.TORRENT_LIST -> {
                                    Row(
                                        modifier = Modifier
                                            .padding(end = 2.dp)
                                            .offset(x = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box {
                                            TextButton(
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
                                                    .semantics {
                                                        contentDescription = sortDescription
                                                    },
                                                onClick = { showTorrentSortMenu = true },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.menu_sort),
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    style = MaterialTheme.typography.labelLarge,
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showTorrentSortMenu,
                                                onDismissRequest = { showTorrentSortMenu = false },
                                            ) {
                                                TorrentListSortOption.entries.forEach { option ->
                                                    val isSelected = option == torrentListSortOption
                                                    DropdownMenuItem(
                                                        text = {
                                                            val prefix = if (isSelected) "✓ " else ""
                                                            Text("$prefix${torrentListSortLabel(option)}")
                                                        },
                                                        onClick = {
                                                            torrentListSortOption = option
                                                            showTorrentSortMenu = false
                                                            requestScrollToFirstTorrentAfterSort()
                                                        },
                                                    )
                                                }
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                        text = {
                                                            val prefix = if (torrentListSortDescending) "✓ " else ""
                                                            Text("${prefix}${stringResource(R.string.sort_descending)}")
                                                        },
                                                    onClick = {
                                                        torrentListSortDescending = true
                                                        showTorrentSortMenu = false
                                                        requestScrollToFirstTorrentAfterSort()
                                                    },
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                            val prefix = if (!torrentListSortDescending) "✓ " else ""
                                                            Text("${prefix}${stringResource(R.string.sort_ascending)}")
                                                        },
                                                    onClick = {
                                                        torrentListSortDescending = false
                                                        showTorrentSortMenu = false
                                                        requestScrollToFirstTorrentAfterSort()
                                                    },
                                                )
                                            }
                                        }
                                        TextButton(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
                                                .semantics {
                                                    contentDescription = searchDescription
                                                },
                                            onClick = {
                                                showTorrentSearchBar = !showTorrentSearchBar
                                                if (!showTorrentSearchBar) {
                                                    torrentSearchQuery = ""
                                                }
                                                scope.launch {
                                                    torrentListState.scrollToItem(0)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        ) {
                                            Text(
                                                text = if (showTorrentSearchBar) {
                                                    stringResource(R.string.menu_collapse)
                                                } else {
                                                    stringResource(R.string.menu_search)
                                                },
                                                color = MaterialTheme.colorScheme.onBackground,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                        TextButton(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
                                                .semantics {
                                                    contentDescription = addTorrentDescription
                                                },
                                            onClick = {
                                                viewModel.loadGlobalSelectionOptions()
                                                showAddTorrentSheet = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        ) {
                                            Text(
                                                text = "+",
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontSize = 22.sp,
                                            )
                                        }
                                    }
                                }

                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                val hasSavedConnection = remember(
                    state.serverProfiles,
                    state.settings.host,
                    state.settings.username,
                ) {
                    state.serverProfiles.isNotEmpty() ||
                        (state.settings.host.trim().isNotBlank() && state.settings.username.trim().isNotBlank())
                }
                val showRestorePlaceholder = !state.startupRestoreComplete ||
                    (hasSavedConnection && !state.dashboardCacheHydrated && !state.connected)
                val showTorrentListContent = state.connected || state.hasDashboardSnapshot
                val showTorrentDetailRestorePlaceholder = selectedTorrent == null &&
                    selectedTorrentIdentity.isNotBlank() &&
                    showRestorePlaceholder
                val showServerDashboardSkeleton = selectedServerProfile != null &&
                    selectedDashboardSnapshot == null &&
                    (state.isConnecting || showRestorePlaceholder)
                val showDashboardSnapshot = if (multiServerDashboard) {
                    state.serverProfiles.isNotEmpty()
                } else {
                    state.connected || state.hasDashboardSnapshot || state.dashboardServerSnapshots.isNotEmpty()
                }
                val showDashboardSkeleton = !showDashboardSnapshot && showRestorePlaceholder
                val contentModifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)

                val contentList: @Composable (AppPage) -> Unit = { page ->
                    LazyColumn(
                        state = listStateForPage(page),
                        modifier = contentModifier,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (page) {
                            AppPage.DASHBOARD -> {
                                if (showDashboardSnapshot) {
                                    item {
                                        if (multiServerDashboard) {
                                            MultiServerDashboardSection(
                                                aggregate = state.dashboardAggregate,
                                                snapshots = state.dashboardServerSnapshots,
                                                draggingProfileId = draggingServerProfileId,
                                                draggingOffsetY = draggingServerOffsetY,
                                                draggingTargetIndex = draggingServerTargetIndex,
                                                dragSession = draggingServerSession,
                                                showReorderHint = showServerStackHint,
                                                onDismissReorderHint = {
                                                    viewModel.markServerStackReorderHintSeen()
                                                },
                                                onStartDrag = { profileId ->
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.markServerStackReorderHintSeen()
                                                    startServerStackDrag(profileId)
                                                },
                                                onDragDelta = ::updateServerStackDrag,
                                                onDragEnd = ::endServerStackDrag,
                                                onOpenServerDashboard = ::openServerDashboard,
                                            )
                                        } else {
                                            val activeProfileId = state.activeServerProfileId
                                                ?: state.serverProfiles.firstOrNull()?.id
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                ServerOverviewCard(
                                                    overviewTitle = when (
                                                        state.serverProfiles.firstOrNull { it.id == activeProfileId }?.backendType
                                                            ?: state.settings.serverBackendType
                                                    ) {
                                                        com.hjw.qbremote.data.ServerBackendType.TRANSMISSION -> "Transmission"
                                                        com.hjw.qbremote.data.ServerBackendType.QBITTORRENT -> stringResource(
                                                            R.string.brand_qbittorrent
                                                        )
                                                    },
                                                    serverProfiles = state.serverProfiles,
                                                    activeProfileId = state.activeServerProfileId,
                                                    serverVersion = state.serverVersion,
                                                    transferInfo = state.transferInfo,
                                                    torrents = state.torrents,
                                                    torrentCount = state.torrents.size,
                                                    showTotals = true,
                                                    showEntryHint = !state.settings.homeTorrentEntryHintDismissed,
                                                    backendType = state.serverProfiles.firstOrNull { it.id == activeProfileId }?.backendType
                                                        ?: state.settings.serverBackendType,
                                                    onCardClick = {
                                                        activeProfileId?.let(::openServerDashboard)
                                                    },
                                                    onDismissEntryHint = viewModel::dismissHomeTorrentEntryHint,
                                                    onOpenTorrentList = ::openTorrentListFromDashboard,
                                                    onSwitchServerProfile = viewModel::switchServerProfile,
                                                    onEditServerProfile = { profileId -> openServerProfileSheet(profileId) },
                                                    onRequestDeleteServerProfile = ::requestDeleteServerProfile,
                                                    swipeActionsEnabled = false,
                                                    showSwipeHint = false,
                                                    onDismissSwipeHint = {},
                                                )
                                            }
                                        }
                                    }
                                } else if (showDashboardSkeleton) {
                                    item {
                                        DashboardHomeSkeleton(
                                            showCharts = false,
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

                            AppPage.SERVER_DASHBOARD -> {
                                if (selectedServerProfile != null && serverDashboardShowContent) {
                                    item(key = "server_dashboard_overview_$serverDashboardSessionKey") {
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(animationSpec = tween(120)),
                                            exit = fadeOut(animationSpec = tween(90)),
                                        ) {
                                            ServerOverviewCard(
                                                overviewTitle = when (selectedServerProfile.backendType) {
                                                    com.hjw.qbremote.data.ServerBackendType.TRANSMISSION -> "Transmission"
                                                    com.hjw.qbremote.data.ServerBackendType.QBITTORRENT -> stringResource(
                                                        R.string.brand_qbittorrent
                                                    )
                                                },
                                                backendType = selectedServerProfile.backendType,
                                                serverProfiles = listOf(selectedServerProfile),
                                                activeProfileId = selectedServerProfile.id,
                                                serverVersion = serverDashboardVersion,
                                                transferInfo = serverDashboardTransferInfo,
                                                torrents = serverDashboardTorrents,
                                                torrentCount = serverDashboardTorrentCount,
                                                showTotals = true,
                                                showEntryHint = false,
                                                onCardClick = null,
                                                onDismissEntryHint = viewModel::dismissHomeTorrentEntryHint,
                                                onOpenTorrentList = ::openTorrentListFromDashboard,
                                                onSwitchServerProfile = viewModel::switchServerProfile,
                                                onEditServerProfile = { profileId -> openServerProfileSheet(profileId) },
                                                onRequestDeleteServerProfile = ::requestDeleteServerProfile,
                                                swipeActionsEnabled = true,
                                                showSwipeHint = showDashboardSwipeHint,
                                                onDismissSwipeHint = {
                                                    viewModel.markServerDashboardSwipeHintSeen()
                                                },
                                            )
                                        }
                                    }
                                    if (showDashboardCardHint) {
                                        item(key = "server_dashboard_chart_hint_$serverDashboardSessionKey") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                DashboardEntryHintBubble(
                                                    text = stringResource(R.string.dashboard_chart_reorder_hint),
                                                    dismissDescription = stringResource(R.string.dismiss_hint),
                                                    onDismiss = {
                                                        viewModel.markServerDashboardCardHintSeen()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    if (displayVisibleDashboardCards.isEmpty()) {
                                        item(key = "server_dashboard_chart_empty_$serverDashboardSessionKey") {
                                            DashboardManagementEmptyCard(
                                                onOpenManager = {
                                                    viewModel.markServerDashboardCardHintSeen()
                                                    showDashboardCardManagerSheet = true
                                                },
                                            )
                                        }
                                    } else {
                                        items(
                                            items = displayVisibleDashboardCards,
                                            key = { "$serverDashboardSessionKey:${it.storageKey}" },
                                        ) { card ->
                                            ReorderableDashboardCard(
                                                card = card,
                                                gestureKey = dashboardDragGestureKey,
                                                isDragging = draggingDashboardCard == card,
                                                dragOffsetY = if (draggingDashboardCard == card) {
                                                    draggingDashboardOffsetY
                                                } else {
                                                    0f
                                                },
                                                siblingOffsetY = calculateDashboardSiblingOffset(
                                                    card = card,
                                                    draggingCard = draggingDashboardCard,
                                                    draggingTargetIndex = draggingDashboardTargetIndex,
                                                    dragSession = draggingDashboardSession,
                                                ),
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.markServerDashboardCardHintSeen()
                                                    startDashboardCardDrag(card)
                                                },
                                                onDragDelta = { deltaY -> updateDashboardCardDrag(card, deltaY) },
                                                onDragEnd = { endDashboardCardDrag() },
                                                onMeasured = { height -> dashboardCardHeights[card] = height },
                                            ) {
                                                when (card) {
                                                    DashboardChartCard.COUNTRY_FLOW -> {
                                                        CountryFlowMapCard(
                                                            stats = if (serverDashboardCapabilities.supportsCountryDistribution) {
                                                                serverDashboardCountryUploadStats
                                                            } else {
                                                                emptyList()
                                                            },
                                                            coverageNote = "",
                                                            showHideButton = revealedDashboardHideCardKey == card.storageKey,
                                                            onRevealHide = { revealedDashboardHideCardKey = card.storageKey },
                                                            onHide = { hideDashboardCard(card) },
                                                        )
                                                    }

                                                    DashboardChartCard.CATEGORY_SHARE -> {
                                                        if (selectedDashboardBackendType == ServerBackendType.TRANSMISSION) {
                                                            TransmissionLabelCategoryPieCard(
                                                                torrents = serverDashboardTorrents,
                                                                showHideButton = revealedDashboardHideCardKey == card.storageKey,
                                                                onRevealHide = { revealedDashboardHideCardKey = card.storageKey },
                                                                onHide = { hideDashboardCard(card) },
                                                            )
                                                        } else {
                                                            CategorySharePieCard(
                                                                torrents = if (serverDashboardCapabilities.supportsCategories) {
                                                                    serverDashboardTorrents
                                                                } else {
                                                                    emptyList()
                                                                },
                                                                coverageNote = "",
                                                                showHideButton = revealedDashboardHideCardKey == card.storageKey,
                                                                onRevealHide = { revealedDashboardHideCardKey = card.storageKey },
                                                                onHide = { hideDashboardCard(card) },
                                                            )
                                                        }
                                                    }

                                                    DashboardChartCard.DAILY_UPLOAD -> {
                                                        DailyTagUploadPieCard(
                                                            date = serverDashboardTagUploadDate,
                                                            stats = serverDashboardTagUploadStats,
                                                            showHideButton = revealedDashboardHideCardKey == card.storageKey,
                                                            onRevealHide = { revealedDashboardHideCardKey = card.storageKey },
                                                            onHide = { hideDashboardCard(card) },
                                                        )
                                                    }

                                                    DashboardChartCard.TAG_UPLOAD -> {
                                                        DailyTagUploadPieCard(
                                                            date = serverDashboardTagUploadDate,
                                                            stats = serverDashboardTagUploadStats,
                                                            titleOverride = stringResource(R.string.dashboard_tag_upload_share_title),
                                                            showHideButton = revealedDashboardHideCardKey == card.storageKey,
                                                            onRevealHide = { revealedDashboardHideCardKey = card.storageKey },
                                                            onHide = { hideDashboardCard(card) },
                                                        )
                                                    }

                                                    DashboardChartCard.TORRENT_STATE -> {
                                                        TransmissionStatePieCard(
                                                            torrents = serverDashboardTorrents,
                                                            showHideButton = revealedDashboardHideCardKey == card.storageKey,
                                                            onRevealHide = { revealedDashboardHideCardKey = card.storageKey },
                                                            onHide = { hideDashboardCard(card) },
                                                        )
                                                    }

                                                    DashboardChartCard.TRACKER_SITE -> {
                                                        TransmissionTrackerSitePieCard(
                                                            torrents = serverDashboardTorrents,
                                                            showHideButton = revealedDashboardHideCardKey == card.storageKey,
                                                            onRevealHide = { revealedDashboardHideCardKey = card.storageKey },
                                                            onHide = { hideDashboardCard(card) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (showServerDashboardSkeleton || showRestorePlaceholder) {
                                    item(key = "server_dashboard_loading_$serverDashboardSessionKey") {
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(animationSpec = tween(120)),
                                            exit = fadeOut(animationSpec = tween(90)),
                                        ) {
                                            ServerDashboardSkeleton()
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

                            AppPage.TORRENT_LIST -> {
                                if (showTorrentListContent) {
                                    if (showTorrentSearchBar) {
                                        stickyHeader(key = "torrent_search_bar") {
                                            OutlinedTextField(
                                                value = torrentSearchQuery,
                                                onValueChange = { torrentSearchQuery = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(qbGlassStrongContainerColor())
                                                    .padding(bottom = 8.dp),
                                                label = { Text(stringResource(R.string.search_torrent_label)) },
                                                placeholder = { Text(stringResource(R.string.search_torrent_placeholder)) },
                                                singleLine = true,
                                            )
                                        }
                                    }
                                    items(
                                        items = visibleTorrents,
                                        key = { it.hash.ifBlank { it.name } },
                                    ) { torrent ->
                                        Box(
                                            modifier = Modifier.animateItemPlacement(
                                                animationSpec = spring(
                                                    dampingRatio = 0.78f,
                                                    stiffness = 380f,
                                                )
                                            )
                                        ) {
                                            TorrentCard(
                                                torrent = torrent,
                                                crossSeedCount = crossSeedCounts[torrentIdentityKey(torrent)] ?: 0,
                                                isPending = isPendingAction(torrent.hash),
                                                onOpenDetails = { openTorrentDetail(torrent) },
                                            )
                                        }
                                    }
                                    if (visibleTorrents.isEmpty()) {
                                        item {
                                            Text(
                                                text = stringResource(R.string.no_torrent_data),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                } else if (showRestorePlaceholder) {
                                    item {
                                        PageRestorePlaceholder()
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
                                    if (showTorrentDetailRestorePlaceholder) {
                                        item {
                                            PageRestorePlaceholder()
                                        }
                                    } else {
                                        item {
                                            Text(
                                                text = stringResource(R.string.torrent_detail_not_found),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                } else {
                                    item(key = "torrent_detail_card") {
                                        AnimatedContent(
                                            targetState = torrent.hash,
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                                                    fadeOut(animationSpec = tween(durationMillis = 120))
                                            },
                                            label = "torrentDetailCard",
                                        ) { targetHash ->
                                            TorrentOperationDetailCard(
                                                torrent = torrent,
                                                crossSeedCount = crossSeedCounts[torrentIdentityKey(torrent)] ?: 0,
                                                isPending = isPendingAction(targetHash),
                                                capabilities = state.activeCapabilities,
                                                detailLoading = state.detailLoading && state.detailHash == targetHash,
                                                detailProperties = if (state.detailHash == targetHash) state.detailProperties else null,
                                                detailFiles = if (state.detailHash == targetHash) state.detailFiles else emptyList(),
                                                detailTrackers = if (state.detailHash == targetHash) state.detailTrackers else emptyList(),
                                                magnetUri = buildMagnetUri(
                                                    hash = targetHash,
                                                    name = torrent.name,
                                                    trackerUrls = if (state.detailHash == targetHash) {
                                                        state.detailTrackers.map { it.url }
                                                    } else {
                                                        emptyList()
                                                    },
                                                ),
                                                categoryOptions = state.categoryOptions,
                                                tagOptions = state.tagOptions,
                                                deleteFilesDefault = state.settings.deleteFilesDefault,
                                                deleteFilesWhenNoSeeders = state.settings.deleteFilesWhenNoSeeders,
                                                onCopyHash = {
                                                    copyToClipboard(targetHash, R.string.detail_hash_copied)
                                                },
                                                onCopyMagnet = { magnetUri ->
                                                    copyToClipboard(magnetUri, R.string.detail_magnet_copied)
                                                },
                                                onExportTorrent = {
                                                    requestTorrentExport(
                                                        hash = targetHash,
                                                        torrentName = torrent.name,
                                                    )
                                                },
                                                onPause = { viewModel.pauseTorrent(targetHash) },
                                                onResume = { viewModel.resumeTorrent(targetHash) },
                                                onDelete = { deleteFiles ->
                                                    viewModel.deleteTorrent(targetHash, deleteFiles)
                                                },
                                                onRename = { viewModel.renameTorrent(targetHash, it) },
                                                onSetLocation = { viewModel.setTorrentLocation(targetHash, it) },
                                                onSetCategory = { viewModel.setTorrentCategory(targetHash, it) },
                                                onSetTags = { oldTags, newTags ->
                                                    viewModel.setTorrentTags(targetHash, oldTags, newTags)
                                                },
                                                onSetSpeedLimit = { dl, up ->
                                                    viewModel.setTorrentSpeedLimit(targetHash, dl, up)
                                                },
                                                onSetShareRatio = { ratio ->
                                                    viewModel.setTorrentShareRatio(targetHash, ratio)
                                                },
                                                onReannounce = { viewModel.reannounceTorrent(targetHash) },
                                                onRecheck = { viewModel.recheckTorrent(targetHash) },
                                                onCopyTracker = { tracker ->
                                                    copyToClipboard(tracker.url, R.string.detail_tracker_copied)
                                                },
                                                onEditTracker = { tracker, newUrl ->
                                                    viewModel.editTracker(targetHash, tracker, newUrl)
                                                },
                                                onDeleteTracker = { tracker ->
                                                    viewModel.removeTracker(targetHash, tracker)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            AppPage.SETTINGS -> {
                                item {
                                    SettingsPageContent(
                                        settings = state.settings,
                                        onAppLanguageChange = viewModel::updateAppLanguage,
                                        onDeleteFilesWhenNoSeedersChange = viewModel::updateDeleteFilesWhenNoSeeders,
                                        onDeleteFilesDefaultChange = viewModel::updateDeleteFilesDefault,
                                    )
                                }
                                if (state.serverProfiles.isEmpty()) {
                                    item {
                                        ConnectionCard(
                                            state = state,
                                            onBackendTypeChange = viewModel::updateServerBackendType,
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
                }

                val animatedPageTarget = remember(currentPage, serverDashboardSessionKey) {
                    PageAnimationState(
                        page = currentPage,
                        dashboardSessionKey = if (currentPage == AppPage.SERVER_DASHBOARD) {
                            serverDashboardSessionKey
                        } else {
                            ""
                        },
                    )
                }
                val animatedPageContent: @Composable () -> Unit = {
                    AnimatedContent(
                        targetState = animatedPageTarget,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 140))
                        },
                        label = "pageAnimation",
                    ) { pageState ->
                        key(pageState.page, pageState.dashboardSessionKey) {
                            contentList(pageState.page)
                        }
                    }
                }

                if (
                    (currentPage == AppPage.DASHBOARD || currentPage == AppPage.SERVER_DASHBOARD) &&
                    (state.connected || state.serverProfiles.isNotEmpty())
                ) {
                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = state.isManualRefreshing,
                        onRefresh = { viewModel.refresh(manual = true) },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState),
                    ) {
                        animatedPageContent()
                        PullRefreshIndicator(
                            refreshing = state.isManualRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    animatedPageContent()
                }
            }

            if (showServerProfileSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showServerProfileSheet = false
                        serverSheetEditingProfileId = ""
                    },
                    sheetState = serverProfileSheetState,
                    containerColor = qbGlassStrongContainerColor(),
                    shape = PanelShape,
                ) {
                    ServerProfileSheet(
                        profiles = state.serverProfiles,
                        activeProfileId = state.activeServerProfileId,
                        initialEditingProfileId = serverSheetEditingProfileId.ifBlank { null },
                        onSwitchProfile = { profileId ->
                            viewModel.switchServerProfile(profileId)
                            showServerProfileSheet = false
                            serverSheetEditingProfileId = ""
                            currentPage = AppPage.DASHBOARD
                        },
                        onAddProfile = { name, backendType, host, port, useHttps, username, password, refreshSeconds ->
                            viewModel.addServerProfile(
                                name = name,
                                backendType = backendType,
                                host = host,
                                port = port,
                                useHttps = useHttps,
                                username = username,
                                password = password,
                                refreshSeconds = refreshSeconds,
                            )
                            showServerProfileSheet = false
                            serverSheetEditingProfileId = ""
                            currentPage = AppPage.DASHBOARD
                        },
                        onUpdateProfile = { profileId, name, backendType, host, port, useHttps, username, password, refreshSeconds ->
                            viewModel.updateServerProfile(
                                profileId = profileId,
                                name = name,
                                backendType = backendType,
                                host = host,
                                port = port,
                                useHttps = useHttps,
                                username = username,
                                password = password,
                                refreshSeconds = refreshSeconds,
                            )
                            showServerProfileSheet = false
                            serverSheetEditingProfileId = ""
                        },
                        onRequestDeleteProfile = ::requestDeleteServerProfile,
                        onCancel = {
                            showServerProfileSheet = false
                            serverSheetEditingProfileId = ""
                        },
                    )
                }
            }

            if (showAddTorrentSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAddTorrentSheet = false },
                    sheetState = addTorrentSheetState,
                    containerColor = qbGlassStrongContainerColor(),
                    shape = PanelShape,
                ) {
                    AddTorrentSheet(
                        context = localContext,
                        capabilities = state.activeCapabilities,
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

            if (showDashboardCardManagerSheet && selectedServerProfile != null) {
                ModalBottomSheet(
                    onDismissRequest = { showDashboardCardManagerSheet = false },
                    sheetState = dashboardCardManagerSheetState,
                    containerColor = qbGlassStrongContainerColor(),
                    shape = PanelShape,
                ) {
                    ServerDashboardCardManagerSheet(
                        availableCards = availableDashboardCards,
                        preferences = displayDashboardPreferences,
                        onToggleCard = { card, visible ->
                            val currentProfileId = selectedServerProfile.id
                            val previousVisibleKeys = localVisibleDashboardCardKeys
                            localVisibleDashboardCardKeys = if (visible) {
                                previousVisibleKeys + card.storageKey
                            } else {
                                previousVisibleKeys - card.storageKey
                            }
                            viewModel.updateServerDashboardCardVisibility(
                                currentProfileId,
                                card,
                                visible,
                            ) { success ->
                                if (!success && currentProfileId == state.activeServerProfileId) {
                                    localVisibleDashboardCardKeys = previousVisibleKeys
                                }
                            }
                        },
                        onReset = {
                            val currentProfileId = selectedServerProfile.id
                            val previousOrder = localDashboardCardOrder
                            val previousVisibleKeys = localVisibleDashboardCardKeys
                            val defaults = defaultServerDashboardPreferences(selectedDashboardBackendType)
                            localDashboardCardOrder = parseDashboardCardOrder(
                                defaults.cardOrder,
                                availableDashboardCards,
                            )
                            localVisibleDashboardCardKeys = defaults.visibleCards.toSet()
                            viewModel.resetServerDashboardPreferences(currentProfileId) { success ->
                                if (!success && currentProfileId == state.activeServerProfileId) {
                                    localDashboardCardOrder = previousOrder
                                    localVisibleDashboardCardKeys = previousVisibleKeys
                                }
                            }
                        },
                        onDismiss = { showDashboardCardManagerSheet = false },
                    )
                }
            }

            if (pendingDeleteProfile != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteProfileId = "" },
                    title = { Text(stringResource(R.string.server_delete_title)) },
                    text = {
                        Text(
                            if (pendingDeleteProfile.id == state.activeServerProfileId) {
                                stringResource(
                                    R.string.server_delete_desc_active,
                                    pendingDeleteProfile.name,
                                )
                            } else {
                                stringResource(
                                    R.string.server_delete_desc,
                                    pendingDeleteProfile.name,
                                )
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (
                                    currentPage == AppPage.SERVER_DASHBOARD &&
                                    pendingDeleteProfile.id == state.activeServerProfileId
                                ) {
                                    currentPage = AppPage.DASHBOARD
                                }
                                viewModel.deleteServerProfile(pendingDeleteProfile.id)
                                pendingDeleteProfileId = ""
                                showServerProfileSheet = false
                                serverSheetEditingProfileId = ""
                            },
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteProfileId = "" }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            state.pendingBackendRepair?.let { pendingRepair ->
                val detectedBackendLabel = when (pendingRepair.detectedBackend) {
                    ServerBackendType.QBITTORRENT -> "qBittorrent"
                    ServerBackendType.TRANSMISSION -> "Transmission"
                }
                AlertDialog(
                    onDismissRequest = viewModel::dismissPendingBackendRepair,
                    title = { Text(stringResource(R.string.server_backend_repair_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.server_backend_repair_desc,
                                pendingRepair.profileName,
                                detectedBackendLabel,
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::confirmPendingBackendRepair) {
                            Text(stringResource(R.string.server_backend_repair_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissPendingBackendRepair) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
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
            containerColor = qbGlassChipColor(),
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
    val siteText = trackerSiteName(
        tracker = torrent.tracker,
        unknownLabel = stringResource(R.string.site_unknown),
    )
    val stateStyle = torrentStateStyle(effectiveState)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.3f)),
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
                    text = stringResource(R.string.added_fmt, formatAddedOn(torrent.addedOn)),
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
                    text = stringResource(R.string.last_activity_fmt, formatActiveAgo(torrent.lastActivity)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            TorrentInfoCell(
                text = stringResource(
                    R.string.torrent_speed_fmt,
                    formatSpeed(torrent.uploadSpeed),
                    formatSpeed(torrent.downloadSpeed),
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TorrentInfoCell(
                    text = stringResource(
                        R.string.torrent_uploaded_downloaded_fmt,
                        formatBytes(torrent.uploaded),
                        formatBytes(torrent.downloaded),
                    ),
                    modifier = Modifier.weight(1f),
                )
                TorrentInfoCell(
                    text = stringResource(R.string.torrent_size_fmt, formatBytes(torrent.size)),
                    modifier = Modifier.weight(1f),
                )
            }

            TorrentInfoCell(
                text = stringResource(R.string.torrent_category_fmt, categoryText),
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
                text = stringResource(R.string.torrent_save_path_fmt, savePathText),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServerOverviewCard(
    overviewTitle: String,
    backendType: ServerBackendType,
    serverProfiles: List<ServerProfile>,
    activeProfileId: String?,
    serverVersion: String,
    transferInfo: TransferInfo,
    torrents: List<TorrentInfo>,
    torrentCount: Int,
    showTotals: Boolean,
    showEntryHint: Boolean,
    onCardClick: (() -> Unit)?,
    onDismissEntryHint: () -> Unit,
    onOpenTorrentList: () -> Unit,
    onSwitchServerProfile: (String) -> Unit,
    onEditServerProfile: (String) -> Unit,
    onRequestDeleteServerProfile: (String) -> Unit,
    swipeActionsEnabled: Boolean,
    showSwipeHint: Boolean,
    onDismissSwipeHint: () -> Unit,
) {
    val stateSummary = remember(torrents) { buildDashboardStateSummary(torrents) }
    val activeProfile = remember(serverProfiles, activeProfileId) {
        serverProfiles.firstOrNull { it.id == activeProfileId }
    }
    val uploadLimitText = formatRateLimit(
        value = transferInfo.uploadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val downloadLimitText = formatRateLimit(
        value = transferInfo.downloadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val pausedTotal = stateSummary.pausedUploadCount + stateSummary.pausedDownloadCount
    val statusUploadingLabel = stringResource(R.string.status_uploading)
    val statusDownloadingLabel = stringResource(R.string.status_downloading)
    val statusPausedLabel = stringResource(R.string.status_paused)
    val statusErrorLabel = stringResource(R.string.status_error)
    val statusCheckingLabel = stringResource(R.string.status_checking)
    val statusWaitingLabel = stringResource(R.string.status_waiting)
    val statusTotalLabel = stringResource(R.string.status_total_torrents)
    val statusPills = remember(
        stateSummary,
        torrentCount,
        statusUploadingLabel,
        statusDownloadingLabel,
        statusPausedLabel,
        statusErrorLabel,
        statusCheckingLabel,
        statusWaitingLabel,
        statusTotalLabel,
    ) {
        listOf(
            DashboardStatusPillItem(
                label = statusUploadingLabel,
                count = stateSummary.uploadingCount,
                accentColor = Color(0xFF3BBA6F),
            ),
            DashboardStatusPillItem(
                label = statusDownloadingLabel,
                count = stateSummary.downloadingCount,
                accentColor = Color(0xFF3990FF),
            ),
            DashboardStatusPillItem(
                label = statusPausedLabel,
                count = pausedTotal,
                accentColor = Color(0xFF8D98A8),
            ),
            DashboardStatusPillItem(
                label = statusErrorLabel,
                count = stateSummary.errorCount,
                accentColor = Color(0xFFE1493D),
            ),
            DashboardStatusPillItem(
                label = statusCheckingLabel,
                count = stateSummary.checkingCount,
                accentColor = Color(0xFFE1A22B),
            ),
            DashboardStatusPillItem(
                label = statusWaitingLabel,
                count = stateSummary.waitingCount,
                accentColor = Color(0xFFA674E8),
            ),
            DashboardStatusPillItem(
                label = statusTotalLabel,
                count = torrentCount,
                accentColor = Color(0xFF11A9B5),
            ),
        )
    }
    val actionWidth = 104.dp
    val actionWidthPx = with(LocalDensity.current) { 104.dp.toPx() }
    var revealOffset by rememberSaveable(activeProfileId) { mutableFloatStateOf(0f) }
    val animatedRevealOffset by animateFloatAsState(
        targetValue = revealOffset,
        label = "serverOverviewRevealOffset",
    )
    LaunchedEffect(activeProfile?.id, swipeActionsEnabled) {
        revealOffset = 0f
    }
    val showActionRail = swipeActionsEnabled &&
        activeProfile != null &&
        animatedRevealOffset <= -(actionWidthPx * 0.42f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape),
    ) {
        if (showActionRail) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(actionWidth)
                    .clip(PanelShape)
                    .background(qbGlassStrongContainerColor()),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServerOverviewActionButton(
                        iconRes = R.drawable.ic_action_edit,
                        description = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            revealOffset = 0f
                            activeProfile?.id?.let(onEditServerProfile)
                        },
                    )
                    ServerOverviewActionButton(
                        iconRes = R.drawable.ic_action_delete,
                        description = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            revealOffset = 0f
                            activeProfile?.id?.let(onRequestDeleteServerProfile)
                        },
                    )
                }
            }
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedRevealOffset }
                .pointerInput(activeProfile?.id) {
                    if (activeProfile == null || !swipeActionsEnabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (showSwipeHint) {
                                onDismissSwipeHint()
                            }
                            revealOffset = (revealOffset + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = {
                            revealOffset = if (revealOffset <= -(actionWidthPx * 0.45f)) {
                                -actionWidthPx
                            } else {
                                0f
                            }
                        },
                    )
                }
                .then(
                    if (onCardClick != null) {
                        Modifier.clickable {
                            if (revealOffset < 0f) {
                                revealOffset = 0f
                            } else {
                                onCardClick()
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = PanelShape,
            border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.28f)),
            colors = qbGlassCardColors(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(id = backendOverviewIconRes(backendType)),
                        contentDescription = overviewTitle,
                        modifier = Modifier.size(34.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = overviewTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.server_version_fmt, serverVersion),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onOpenTorrentList) {
                        Text(
                            text = stringResource(R.string.dashboard_open_torrents),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (serverProfiles.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        items(serverProfiles, key = { it.id }) { profile ->
                            ServerProfileSummaryCard(
                                profile = profile,
                                active = profile.id == activeProfileId,
                                addressText = buildServerAddressText(
                                    ConnectionSettings(
                                        host = profile.host,
                                        port = profile.port,
                                        useHttps = profile.useHttps,
                                    ),
                                ),
                                summaryText = if (profile.id == activeProfileId) {
                                    stringResource(
                                        R.string.server_summary_speed_fmt,
                                        formatSpeed(transferInfo.uploadSpeed),
                                        formatSpeed(transferInfo.downloadSpeed),
                                    )
                                } else {
                                    stringResource(R.string.server_profile_saved)
                                },
                                onSwitch = {
                                    revealOffset = 0f
                                    onSwitchServerProfile(profile.id)
                                },
                                onEdit = {
                                    revealOffset = 0f
                                    onEditServerProfile(profile.id)
                                },
                                onDelete = {
                                    revealOffset = 0f
                                    onRequestDeleteServerProfile(profile.id)
                                },
                            )
                        }
                    }
                }

                if (showEntryHint) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DashboardEntryHintBubble(
                            text = stringResource(R.string.dashboard_open_torrents_hint),
                            dismissDescription = stringResource(R.string.dismiss_hint),
                            onDismiss = onDismissEntryHint,
                        )
                    }
                }
                if (showSwipeHint) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DashboardEntryHintBubble(
                            text = stringResource(R.string.dashboard_swipe_hint),
                            dismissDescription = stringResource(R.string.dismiss_hint),
                            onDismiss = onDismissSwipeHint,
                        )
                    }
                }

                DashboardSecondaryStatsBlock(
                    uploadSpeedText = formatSpeed(transferInfo.uploadSpeed),
                    downloadSpeedText = formatSpeed(transferInfo.downloadSpeed),
                    uploadLimitText = uploadLimitText,
                    downloadLimitText = downloadLimitText,
                    showTotals = showTotals,
                    totalDownloadedText = formatBytes(transferInfo.downloadedTotal),
                    totalUploadedText = formatBytes(transferInfo.uploadedTotal),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    items(statusPills, key = { it.label }) { pill ->
                        DashboardStatusPill(
                            label = pill.label,
                            count = pill.count,
                            accentColor = pill.accentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiServerDashboardSection(
    aggregate: DashboardAggregateState,
    snapshots: List<CachedDashboardServerSnapshot>,
    draggingProfileId: String?,
    draggingOffsetY: Float,
    draggingTargetIndex: Int,
    dragSession: VerticalReorderSession<String>?,
    showReorderHint: Boolean,
    onDismissReorderHint: () -> Unit,
    onStartDrag: (String) -> Unit,
    onDragDelta: (String, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenServerDashboard: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardAggregateOverviewCard(
            aggregate = aggregate,
        )
        WalletServerCardStack(
            snapshots = snapshots,
            draggingProfileId = draggingProfileId,
            draggingOffsetY = draggingOffsetY,
            draggingTargetIndex = draggingTargetIndex,
            dragSession = dragSession,
            showReorderHint = showReorderHint,
            onDismissReorderHint = onDismissReorderHint,
            onStartDrag = onStartDrag,
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            onOpenServerDashboard = onOpenServerDashboard,
        )
    }
}

@Composable
private fun DashboardAggregateOverviewCard(
    aggregate: DashboardAggregateState,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        border = BorderStroke(1.dp, qbGlassOutlineColor()),
        colors = qbGlassCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_server_speed_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.dashboard_server_speed_online_count,
                        aggregate.totalServerCount,
                        aggregate.totalServerCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AggregateSpeedTextLine(
                label = stringResource(R.string.sort_upload_speed),
                value = formatSpeed(aggregate.transferInfo.uploadSpeed),
                accent = Color(0xFF3BBA6F),
            )
            AggregateSpeedTextLine(
                label = stringResource(R.string.sort_download_speed),
                value = formatSpeed(aggregate.transferInfo.downloadSpeed),
                accent = Color(0xFF3990FF),
            )
            InlineRealtimeSpeedChart(
                aggregate = aggregate,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AggregateSpeedTextLine(
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, RoundedCornerShape(99.dp)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WalletServerCardStack(
    snapshots: List<CachedDashboardServerSnapshot>,
    draggingProfileId: String?,
    draggingOffsetY: Float,
    draggingTargetIndex: Int,
    dragSession: VerticalReorderSession<String>?,
    showReorderHint: Boolean,
    onDismissReorderHint: () -> Unit,
    onStartDrag: (String) -> Unit,
    onDragDelta: (String, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenServerDashboard: (String) -> Unit,
) {
    val orderedSnapshots = snapshots
    val paletteIndexByProfileId = remember(orderedSnapshots) {
        orderedSnapshots
            .map { it.profileId }
            .distinct()
            .sorted()
            .mapIndexed { index, profileId -> profileId to index }
            .toMap()
    }
    val stackHeight = if (orderedSnapshots.isEmpty()) {
        0.dp
    } else {
        HomeServerStackExpandedCardHeight + HomeServerStackExposedHeight * (orderedSnapshots.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.server_stack_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.server_stack_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showReorderHint) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DashboardEntryHintBubble(
                        text = stringResource(R.string.server_stack_reorder_hint),
                        dismissDescription = stringResource(R.string.dismiss_hint),
                        onDismiss = onDismissReorderHint,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight),
        ) {
            orderedSnapshots.withIndex().toList().asReversed().forEach { (index, snapshot) ->
                WalletServerStackCard(
                    snapshot = snapshot,
                    stackedIndex = index,
                    paletteIndex = paletteIndexByProfileId[snapshot.profileId] ?: index,
                    cardHeight = HomeServerStackExpandedCardHeight,
                    collapsedCardHeight = HomeServerStackCollapsedCardHeight,
                    exposedHeight = HomeServerStackExposedHeight,
                    selected = index == 0,
                    stackCount = orderedSnapshots.size,
                    isDragging = draggingProfileId == snapshot.profileId,
                    dragOffsetY = if (draggingProfileId == snapshot.profileId) draggingOffsetY else 0f,
                    siblingOffsetY = calculateServerStackSiblingOffset(
                        profileId = snapshot.profileId,
                        draggingProfileId = draggingProfileId,
                        draggingTargetIndex = draggingTargetIndex,
                        dragSession = dragSession,
                    ),
                    onDragStart = { onStartDrag(snapshot.profileId) },
                    onDragDelta = { deltaY -> onDragDelta(snapshot.profileId, deltaY) },
                    onDragEnd = onDragEnd,
                    onClick = { onOpenServerDashboard(snapshot.profileId) },
                )
            }
        }
    }
}

@Composable
private fun WalletServerStackCard(
    snapshot: CachedDashboardServerSnapshot,
    stackedIndex: Int,
    paletteIndex: Int,
    cardHeight: Dp,
    collapsedCardHeight: Dp,
    exposedHeight: Dp,
    selected: Boolean,
    stackCount: Int,
    isDragging: Boolean,
    dragOffsetY: Float,
    siblingOffsetY: Float,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val palette = remember(snapshot.profileId, paletteIndex) {
        walletCardPalette(paletteIndex)
    }
    val stateLabel = if (snapshot.isStale) {
        stringResource(R.string.server_snapshot_stale_state)
    } else {
        stringResource(R.string.server_snapshot_live_state)
    }
    val exposedStepPx = with(LocalDensity.current) { exposedHeight.toPx() }
    val cornerShape = RoundedCornerShape(24.dp)
    val collapsedShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    val cardShape = if (selected || isDragging) cornerShape else collapsedShape
    val uploadLimitText = formatRateLimit(
        value = snapshot.transferInfo.uploadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val downloadLimitText = formatRateLimit(
        value = snapshot.transferInfo.downloadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val serverNameTextStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 18.sp,
        lineHeight = 22.sp,
    )
    val clickSuppressionThresholdPx = with(LocalDensity.current) {
        ServerCardClickSuppressionDragThreshold.toPx()
    }
    val interactionSource = remember(snapshot.profileId) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var lastDragFinishedAt by remember(snapshot.profileId) { mutableLongStateOf(0L) }
    var dragDistanceSinceStart by remember(snapshot.profileId) { mutableFloatStateOf(0f) }
    val animatedSiblingOffset by animateFloatAsState(
        targetValue = siblingOffsetY,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = 520f,
        ),
        label = "walletServerSiblingOffset",
    )
    val baseTranslationY = if (selected) {
        (stackCount - 1) * exposedStepPx
    } else {
        (stackCount - 1 - stackedIndex) * exposedStepPx
    }
    val displayHeight = if (selected) cardHeight else collapsedCardHeight
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed && !isDragging) 0.985f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "walletServerPressedScale",
    )

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(displayHeight)
            .graphicsLayer {
                translationY = baseTranslationY + if (isDragging) dragOffsetY else animatedSiblingOffset
                scaleX = when {
                    isDragging -> ReorderDraggedScale
                    selected -> 1f
                    else -> 0.992f
                } * pressedScale
                scaleY = when {
                    isDragging -> ReorderDraggedScale
                    selected -> 1f
                    else -> 0.992f
                } * pressedScale
                shadowElevation = when {
                    isDragging -> ReorderDraggedShadow
                    selected -> ReorderSelectedShadow
                    else -> ReorderCollapsedShadow
                }
                shape = cardShape
                clip = true
            }
            .pointerInput(snapshot.profileId) {
                if (stackCount < 2) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragDistanceSinceStart = 0f
                        onDragStart()
                    },
                    onDragEnd = {
                        lastDragFinishedAt = if (dragDistanceSinceStart >= clickSuppressionThresholdPx) {
                            SystemClock.elapsedRealtime()
                        } else {
                            0L
                        }
                        onDragEnd()
                    },
                    onDragCancel = {
                        lastDragFinishedAt = if (dragDistanceSinceStart >= clickSuppressionThresholdPx) {
                            SystemClock.elapsedRealtime()
                        } else {
                            0L
                        }
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDistanceSinceStart += kotlin.math.abs(dragAmount.y)
                        onDragDelta(dragAmount.y)
                    },
                )
            }
            .zIndex(if (isDragging) (stackCount + 1).toFloat() else (stackCount - stackedIndex).toFloat())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                if (SystemClock.elapsedRealtime() - lastDragFinishedAt <= ServerCardClickSuppressionWindowMs) {
                    return@clickable
                }
                onClick()
            },
        shape = cardShape,
        border = BorderStroke(
            1.dp,
            if (selected) {
                Color.White.copy(alpha = 0.28f)
            } else {
                Color.White.copy(alpha = 0.14f)
            },
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = palette.background,
                    shape = cardShape,
                )
                .padding(
                    horizontal = if (selected) 16.dp else 15.dp,
                    vertical = if (selected) 14.dp else 10.dp,
                ),
        ) {
            if (selected) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = snapshot.profileName.ifBlank { backendLabel(snapshot.backendType) },
                                style = serverNameTextStyle,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = backendLabel(snapshot.backendType),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.82f),
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.92f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    R.string.server_summary_speed_fmt,
                                    formatSpeed(snapshot.transferInfo.uploadSpeed),
                                    formatSpeed(snapshot.transferInfo.downloadSpeed),
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    WalletCardMetricLine(
                        label = stringResource(R.string.sort_total_uploaded),
                        value = formatBytes(snapshot.transferInfo.uploadedTotal),
                    )
                    WalletCardMetricLine(
                        label = stringResource(R.string.sort_total_downloaded),
                        value = formatBytes(snapshot.transferInfo.downloadedTotal),
                    )
                    WalletCardMetricLine(
                        label = stringResource(R.string.upload_limit_kb_label),
                        value = uploadLimitText,
                    )
                    WalletCardMetricLine(
                        label = stringResource(R.string.download_limit_kb_label),
                        value = downloadLimitText,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = snapshot.profileName.ifBlank { backendLabel(snapshot.backendType) },
                        style = serverNameTextStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(
                                R.string.server_summary_speed_fmt,
                                formatSpeed(snapshot.transferInfo.uploadSpeed),
                                formatSpeed(snapshot.transferInfo.downloadSpeed),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.accent,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletCardMetricLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.76f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DashboardManagementEmptyCard(
    onOpenManager: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        border = BorderStroke(1.dp, qbGlassOutlineColor()),
        colors = qbGlassCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.dashboard_all_cards_hidden_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.dashboard_all_cards_hidden_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onOpenManager) {
                Text(stringResource(R.string.dashboard_manage_cards_action))
            }
        }
    }
}

@Composable
private fun PageRestorePlaceholder() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.28f)),
        colors = qbGlassCardColors(),
    ) {
        Text(
            text = stringResource(R.string.loading),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ServerDashboardSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ServerDashboardSkeletonCard(
            titleWidthFraction = 0.34f,
            bodyHeight = 132.dp,
        )
        ServerDashboardSkeletonCard(
            titleWidthFraction = 0.42f,
            bodyHeight = 182.dp,
        )
        ServerDashboardSkeletonCard(
            titleWidthFraction = 0.28f,
            bodyHeight = 168.dp,
        )
    }
}

@Composable
private fun ServerDashboardSkeletonCard(
    titleWidthFraction: Float,
    bodyHeight: Dp,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        border = BorderStroke(1.dp, qbGlassOutlineColor()),
        colors = qbGlassCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(titleWidthFraction)
                    .height(16.dp)
                    .background(
                        color = qbGlassSubtleContainerColor(),
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bodyHeight)
                    .background(
                        color = qbGlassStrongContainerColor(),
                        shape = RoundedCornerShape(18.dp),
                    ),
            )
        }
    }
}

@Composable
private fun ServerDashboardCardManagerSheet(
    availableCards: List<DashboardChartCard>,
    preferences: ServerDashboardPreferences,
    onToggleCard: (DashboardChartCard, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_manage_cards_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        availableCards.forEach { card ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dashboardChartCardLabel(card),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = preferences.visibleCards.contains(card.storageKey),
                    onCheckedChange = { checked -> onToggleCard(card, checked) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.dashboard_manage_cards_reset))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

private data class WalletCardPalette(
    val background: Brush,
    val accent: Color,
)

private fun walletCardPalette(
    stackedIndex: Int,
): WalletCardPalette {
    val palettes = listOf(
        WalletCardPalette(
            background = Brush.linearGradient(
                listOf(
                    Color(0xFF16213F),
                    Color(0xFF3458B8),
                    Color(0xFF5E89FF),
                ),
            ),
            accent = Color(0xFFEAF2FF),
        ),
        WalletCardPalette(
            background = Brush.linearGradient(
                listOf(
                    Color(0xFF3A1C0F),
                    Color(0xFF9B5223),
                    Color(0xFFE79A45),
                ),
            ),
            accent = Color(0xFFFFF3E7),
        ),
        WalletCardPalette(
            background = Brush.linearGradient(
                listOf(
                    Color(0xFF112C26),
                    Color(0xFF24755D),
                    Color(0xFF52C79B),
                ),
            ),
            accent = Color(0xFFE9FFF5),
        ),
        WalletCardPalette(
            background = Brush.linearGradient(
                listOf(
                    Color(0xFF2B183B),
                    Color(0xFF7A46B1),
                    Color(0xFFB97DFF),
                ),
            ),
            accent = Color(0xFFF6EEFF),
        ),
        WalletCardPalette(
            background = Brush.linearGradient(
                listOf(
                    Color(0xFF381520),
                    Color(0xFF9F4062),
                    Color(0xFFE785A7),
                ),
            ),
            accent = Color(0xFFFFEEF5),
        ),
        WalletCardPalette(
            background = Brush.linearGradient(
                listOf(
                    Color(0xFF13293F),
                    Color(0xFF2280A6),
                    Color(0xFF6FD2F7),
                ),
            ),
            accent = Color(0xFFEAFBFF),
        ),
    )
    return palettes[stackedIndex % palettes.size]
}

@Composable
private fun backendLabel(backendType: ServerBackendType): String {
    return when (backendType) {
        ServerBackendType.QBITTORRENT -> stringResource(R.string.backend_qbittorrent)
        ServerBackendType.TRANSMISSION -> stringResource(R.string.backend_transmission)
    }
}

private fun backendOverviewIconRes(backendType: ServerBackendType): Int {
    return when (backendType) {
        ServerBackendType.QBITTORRENT -> R.drawable.ic_backend_qbittorrent
        ServerBackendType.TRANSMISSION -> R.drawable.ic_backend_transmission
    }
}

@Composable
private fun dashboardChartCardLabel(card: DashboardChartCard): String {
    return when (card) {
        DashboardChartCard.COUNTRY_FLOW -> stringResource(R.string.dashboard_country_flow_title)
        DashboardChartCard.CATEGORY_SHARE -> stringResource(R.string.dashboard_category_share_title)
        DashboardChartCard.DAILY_UPLOAD -> stringResource(R.string.dashboard_upload_title)
        DashboardChartCard.TAG_UPLOAD -> stringResource(R.string.dashboard_tag_upload_share_title)
        DashboardChartCard.TORRENT_STATE -> stringResource(R.string.dashboard_torrent_state_share_title)
        DashboardChartCard.TRACKER_SITE -> stringResource(R.string.dashboard_tracker_site_share_title)
    }
}

private fun formatDashboardSnapshotTime(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return runCatching {
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }.getOrDefault("-")
}

@Composable
private fun ServerOverviewActionButton(
    iconRes: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .width(44.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.32f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = tint.copy(alpha = 0.10f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun defaultServerDashboardPreferences(
    backendType: ServerBackendType,
): ServerDashboardPreferences {
    val visibleCards = when (backendType) {
        ServerBackendType.QBITTORRENT -> dashboardCardsForBackend(backendType).map { it.storageKey }
        ServerBackendType.TRANSMISSION -> listOf(
            DashboardChartCard.CATEGORY_SHARE.storageKey,
            DashboardChartCard.TAG_UPLOAD.storageKey,
            DashboardChartCard.TORRENT_STATE.storageKey,
            DashboardChartCard.TRACKER_SITE.storageKey,
        )
    }
    return ServerDashboardPreferences(
        visibleCards = visibleCards,
        cardOrder = serializeDashboardCardOrder(
            dashboardCardsForBackend(backendType),
            dashboardCardsForBackend(backendType),
        ),
    )
}

private fun resolveServerDashboardPreferences(
    preferences: ServerDashboardPreferences?,
    backendType: ServerBackendType,
): ServerDashboardPreferences {
    val defaults = defaultServerDashboardPreferences(backendType)
    val availableCards = dashboardCardsForBackend(backendType)
    val availableStorageKeys = availableCards.map { it.storageKey }.toSet()
    val rawVisibleCards = preferences?.visibleCards
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
    val filteredVisibleCards = rawVisibleCards
        ?.filter { token -> token in availableStorageKeys }
        .orEmpty()
    val visibleCards = when {
        preferences == null -> defaults.visibleCards
        filteredVisibleCards.isNotEmpty() -> {
            if (
                backendType == ServerBackendType.TRANSMISSION &&
                DashboardChartCard.CATEGORY_SHARE.storageKey in availableStorageKeys &&
                DashboardChartCard.CATEGORY_SHARE.storageKey !in filteredVisibleCards
            ) {
                listOf(DashboardChartCard.CATEGORY_SHARE.storageKey) + filteredVisibleCards
            } else {
                filteredVisibleCards
            }
        }
        rawVisibleCards.isNullOrEmpty() -> emptyList()
        else -> defaults.visibleCards
    }
    val rawOrder = preferences?.cardOrder?.takeIf { it.isNotBlank() }
    val parsedOrder = rawOrder?.let { parseDashboardCardOrder(it, availableCards) }.orEmpty()
    val cardOrder = when {
        preferences == null -> defaults.cardOrder
        parsedOrder.isNotEmpty() -> serializeDashboardCardOrder(parsedOrder, availableCards)
        rawOrder == null -> defaults.cardOrder
        else -> defaults.cardOrder
    }
    return defaults.copy(
        visibleCards = visibleCards,
        cardOrder = cardOrder,
    )
}

@Composable
private fun ServerProfileSummaryCard(
    profile: ServerProfile,
    active: Boolean,
    addressText: String,
    summaryText: String,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.width(214.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            } else {
                qbGlassOutlineColor(defaultAlpha = 0.28f)
            },
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
            } else {
                qbGlassSubtleContainerColor()
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = addressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSwitch) {
                    Text(
                        text = if (active) {
                            stringResource(R.string.dashboard_open_torrents)
                        } else {
                            stringResource(R.string.connect)
                        },
                    )
                }
                Row {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
private fun DashboardEntryHintBubble(
    text: String,
    dismissDescription: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = qbGlassChipColor(),
                shape = RoundedCornerShape(14.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "×",
            modifier = Modifier
                .semantics { contentDescription = dismissDescription }
                .clickable(onClick = onDismiss)
                .padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DashboardSecondaryStatsBlock(
    uploadSpeedText: String,
    downloadSpeedText: String,
    uploadLimitText: String,
    downloadLimitText: String,
    showTotals: Boolean,
    totalDownloadedText: String,
    totalUploadedText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DashboardSpeedMetricPanel(
            title = stringResource(R.string.upload),
            directionGlyph = "↑",
            speedText = uploadSpeedText,
            limitText = uploadLimitText,
            totalText = totalUploadedText,
            showTotal = showTotals,
            accentColor = Color(0xFF2B73F5),
        )
        DashboardSpeedMetricPanel(
            title = stringResource(R.string.download),
            directionGlyph = "↓",
            speedText = downloadSpeedText,
            limitText = downloadLimitText,
            totalText = totalDownloadedText,
            showTotal = showTotals,
            accentColor = Color(0xFF08A3AE),
        )
    }
}

private data class SpeedDisplayParts(
    val value: String,
    val unit: String,
)

@Composable
private fun RowScope.DashboardSpeedMetricPanel(
    title: String,
    directionGlyph: String,
    speedText: String,
    limitText: String,
    totalText: String,
    showTotal: Boolean,
    accentColor: Color,
) {
    val speedParts = remember(speedText) { splitSpeedDisplayParts(speedText) }
    val speedDescription = if (directionGlyph == "↑") {
        stringResource(R.string.global_up_fmt, speedText)
    } else {
        stringResource(R.string.global_down_fmt, speedText)
    }
    val totalDescription = if (showTotal) {
        if (directionGlyph == "↑") {
            stringResource(R.string.global_total_up_fmt, totalText)
        } else {
            stringResource(R.string.global_total_down_fmt, totalText)
        }
    } else {
        ""
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .semantics {
                contentDescription = listOf(speedDescription, totalDescription)
                    .filter { it.isNotBlank() }
                    .joinToString("，")
            }
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = directionGlyph,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(2.dp)
                .background(accentColor, RoundedCornerShape(99.dp)),
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = speedParts.value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            if (speedParts.unit.isNotBlank()) {
                Text(
                    text = speedParts.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.limit_value_fmt, limitText),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showTotal) {
            Text(
                text = stringResource(R.string.total_value_fmt, totalText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            )
        }
    }
}

private fun splitSpeedDisplayParts(speedText: String): SpeedDisplayParts {
    val normalized = speedText.trim()
    val splitIndex = normalized.lastIndexOf(' ')
    return if (splitIndex in 1 until normalized.lastIndex) {
        SpeedDisplayParts(
            value = normalized.substring(0, splitIndex),
            unit = normalized.substring(splitIndex + 1),
        )
    } else {
        SpeedDisplayParts(value = normalized, unit = "")
    }
}

@Composable
private fun DashboardStatusPill(
    label: String,
    count: Int,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .background(
                color = qbGlassSubtleContainerColor(),
                shape = RoundedCornerShape(999.dp),
            )
            .border(
                width = 1.dp,
                color = qbGlassOutlineColor(defaultAlpha = 0.24f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accentColor, RoundedCornerShape(99.dp)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

private fun formatRateLimit(value: Long, unlimitedLabel: String): String {
    return if (value <= 0L) {
        unlimitedLabel
    } else {
        formatSpeed(value)
    }
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

private fun sortTorrentList(
    torrents: List<TorrentInfo>,
    sortOption: TorrentListSortOption,
    descending: Boolean,
    crossSeedCounts: Map<String, Int>,
): List<TorrentInfo> {
    val comparator = when (sortOption) {
        TorrentListSortOption.ADDED_TIME ->
            compareBy<TorrentInfo> { it.addedOn }
        TorrentListSortOption.UPLOAD_SPEED ->
            compareBy<TorrentInfo> { it.uploadSpeed }
                .thenBy { it.addedOn }
        TorrentListSortOption.DOWNLOAD_SPEED ->
            compareBy<TorrentInfo> { it.downloadSpeed }
                .thenBy { it.addedOn }
        TorrentListSortOption.SHARE_RATIO ->
            compareBy<TorrentInfo> { it.ratio }
                .thenBy { it.addedOn }
        TorrentListSortOption.TOTAL_UPLOADED ->
            compareBy<TorrentInfo> { it.uploaded }
                .thenBy { it.addedOn }
        TorrentListSortOption.TOTAL_DOWNLOADED ->
            compareBy<TorrentInfo> { it.downloaded }
                .thenBy { it.addedOn }
        TorrentListSortOption.TORRENT_SIZE ->
            compareBy<TorrentInfo> { it.size }
                .thenBy { it.addedOn }
        TorrentListSortOption.ACTIVITY_TIME ->
            compareBy<TorrentInfo> { it.lastActivity }
                .thenBy { it.addedOn }
        TorrentListSortOption.SEEDERS ->
            compareBy<TorrentInfo> { it.seeders }
                .thenBy { it.addedOn }
        TorrentListSortOption.LEECHERS ->
            compareBy<TorrentInfo> { it.leechers }
                .thenBy { it.addedOn }
        TorrentListSortOption.CROSS_SEED_COUNT ->
            compareBy<TorrentInfo> { crossSeedCounts[torrentIdentityKey(it)] ?: 0 }
                .thenBy { it.addedOn }
    }
    val finalComparator = if (descending) comparator.reversed() else comparator
    return torrents.sortedWith(finalComparator)
}

@Composable
private fun torrentListSortLabel(option: TorrentListSortOption): String {
    return when (option) {
        TorrentListSortOption.ADDED_TIME -> stringResource(R.string.sort_added_time)
        TorrentListSortOption.UPLOAD_SPEED -> stringResource(R.string.sort_upload_speed)
        TorrentListSortOption.DOWNLOAD_SPEED -> stringResource(R.string.sort_download_speed)
        TorrentListSortOption.SHARE_RATIO -> stringResource(R.string.sort_share_ratio)
        TorrentListSortOption.TOTAL_UPLOADED -> stringResource(R.string.sort_total_uploaded)
        TorrentListSortOption.TOTAL_DOWNLOADED -> stringResource(R.string.sort_total_downloaded)
        TorrentListSortOption.TORRENT_SIZE -> stringResource(R.string.sort_torrent_size)
        TorrentListSortOption.ACTIVITY_TIME -> stringResource(R.string.sort_activity_time)
        TorrentListSortOption.SEEDERS -> stringResource(R.string.sort_seeders)
        TorrentListSortOption.LEECHERS -> stringResource(R.string.sort_leechers)
        TorrentListSortOption.CROSS_SEED_COUNT -> stringResource(R.string.sort_cross_seed_count)
    }
}

private fun trackerSiteName(tracker: String, unknownLabel: String): String {
    return formatTrackerSiteName(tracker, unknownLabel)
}

private fun dashboardCardsForBackend(backendType: ServerBackendType): List<DashboardChartCard> {
    return when (backendType) {
        ServerBackendType.QBITTORRENT -> listOf(
            DashboardChartCard.COUNTRY_FLOW,
            DashboardChartCard.CATEGORY_SHARE,
            DashboardChartCard.DAILY_UPLOAD,
        )

        ServerBackendType.TRANSMISSION -> listOf(
            DashboardChartCard.CATEGORY_SHARE,
            DashboardChartCard.TAG_UPLOAD,
            DashboardChartCard.TORRENT_STATE,
            DashboardChartCard.TRACKER_SITE,
        )
    }
}

private fun parseDashboardCardOrder(
    raw: String,
    availableCards: List<DashboardChartCard>,
): List<DashboardChartCard> {
    val availableByKey = availableCards.associateBy { it.storageKey }
    val parsed = raw
        .split(',')
        .mapNotNull { token ->
            availableByKey[token.trim()]
        }
        .distinct()
        .toMutableList()
    availableCards.forEach { card ->
        if (!parsed.contains(card)) {
            parsed += card
        }
    }
    return parsed
}

private fun serializeDashboardCardOrder(
    order: List<DashboardChartCard>,
    availableCards: List<DashboardChartCard>,
): String {
    return parseDashboardCardOrder(
        order.joinToString(",") { it.storageKey },
        availableCards,
    )
        .joinToString(",") { it.storageKey }
}

private fun swapDashboardCardOrder(
    order: List<DashboardChartCard>,
    first: DashboardChartCard,
    second: DashboardChartCard,
): List<DashboardChartCard> {
    val mutable = order.toMutableList()
    val firstIndex = mutable.indexOf(first)
    val secondIndex = mutable.indexOf(second)
    if (firstIndex < 0 || secondIndex < 0) return order
    mutable[firstIndex] = second
    mutable[secondIndex] = first
    return mutable
}

private fun reorderDashboardCardOrder(
    order: List<DashboardChartCard>,
    visibleCards: List<DashboardChartCard>,
    card: DashboardChartCard,
    targetIndex: Int,
): List<DashboardChartCard> {
    val currentVisibleIndex = visibleCards.indexOf(card)
    if (currentVisibleIndex < 0 || targetIndex !in visibleCards.indices || currentVisibleIndex == targetIndex) {
        return order
    }

    val reorderedVisibleCards = visibleCards.toMutableList().apply {
        remove(card)
        add(targetIndex, card)
    }
    val visibleCardSet = visibleCards.toSet()
    var visibleCursor = 0

    return order.map { existingCard ->
        if (existingCard in visibleCardSet) {
            reorderedVisibleCards[visibleCursor++]
        } else {
            existingCard
        }
    }
}

private fun reorderServerProfileIds(
    current: List<String>,
    profileId: String,
    targetIndex: Int,
): List<String> {
    val currentIndex = current.indexOf(profileId)
    if (currentIndex < 0 || targetIndex !in current.indices || currentIndex == targetIndex) {
        return current
    }
    return current.toMutableList().apply {
        remove(profileId)
        add(targetIndex, profileId)
    }
}

private fun <T> buildVerticalReorderSession(
    items: List<T>,
    startIndex: Int,
    slotTops: List<Float>,
    slotHeights: List<Float>,
    edgeSlackPx: Float,
): VerticalReorderSession<T> {
    require(items.size == slotTops.size && items.size == slotHeights.size)
    val slotCenters = slotTops.indices.map { index ->
        slotTops[index] + (slotHeights[index] / 2f)
    }
    val startCenter = slotCenters[startIndex]
    val minOffset = (slotCenters.minOrNull() ?: startCenter) - startCenter - edgeSlackPx
    val maxOffset = (slotCenters.maxOrNull() ?: startCenter) - startCenter + edgeSlackPx
    return VerticalReorderSession(
        items = items,
        indexByItem = items.mapIndexed { index, item -> item to index }.toMap(),
        startIndex = startIndex,
        slotTops = slotTops,
        slotCenters = slotCenters,
        minOffset = minOffset,
        maxOffset = maxOffset,
    )
}

private fun <T> resolveVerticalReorderTargetIndex(
    session: VerticalReorderSession<T>,
    dragOffsetY: Float,
): Int {
    val currentCenter = session.slotCenters[session.startIndex] + dragOffsetY
    var bestIndex = session.startIndex
    var bestDistance = Float.MAX_VALUE
    session.slotCenters.forEachIndexed { index, center ->
        val distance = kotlin.math.abs(center - currentCenter)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

private fun <T> calculateVerticalReorderSiblingOffset(
    item: T,
    draggingItem: T?,
    draggingTargetIndex: Int,
    dragSession: VerticalReorderSession<T>?,
): Float {
    if (draggingItem == null || draggingItem == item || dragSession == null) return 0f
    if (draggingTargetIndex !in dragSession.items.indices) return 0f
    val originalIndex = dragSession.indexByItem[item] ?: return 0f
    if (dragSession.startIndex == draggingTargetIndex) return 0f
    val reorderedItems = moveItemInList(
        items = dragSession.items,
        fromIndex = dragSession.startIndex,
        toIndex = draggingTargetIndex,
    )
    val targetSlotIndex = reorderedItems.indexOf(item)
    if (targetSlotIndex < 0) return 0f
    return dragSession.slotTops[targetSlotIndex] - dragSession.slotTops[originalIndex]
}

private fun <T> moveItemInList(
    items: List<T>,
    fromIndex: Int,
    toIndex: Int,
): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
        return items
    }
    return items.toMutableList().apply {
        val item = removeAt(fromIndex)
        add(toIndex, item)
    }
}

private fun calculateServerStackSiblingOffset(
    profileId: String,
    draggingProfileId: String?,
    draggingTargetIndex: Int,
    dragSession: VerticalReorderSession<String>?,
): Float {
    return calculateVerticalReorderSiblingOffset(
        item = profileId,
        draggingItem = draggingProfileId,
        draggingTargetIndex = draggingTargetIndex,
        dragSession = dragSession,
    )
}

private fun calculateDashboardSiblingOffset(
    card: DashboardChartCard,
    draggingCard: DashboardChartCard?,
    draggingTargetIndex: Int,
    dragSession: VerticalReorderSession<DashboardChartCard>?,
): Float {
    return calculateVerticalReorderSiblingOffset(
        item = card,
        draggingItem = draggingCard,
        draggingTargetIndex = draggingTargetIndex,
        dragSession = dragSession,
    )
}







