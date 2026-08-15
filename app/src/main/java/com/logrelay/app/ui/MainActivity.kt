package com.logrelay.app.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logrelay.app.data.Record
import com.logrelay.app.data.RecordRepository
import com.logrelay.app.ui.theme.CalendarIcon
import com.logrelay.app.ui.theme.CameraIcon
import com.logrelay.app.ui.theme.DotGridBackground
import com.logrelay.app.ui.theme.EdgeScrollbar
import com.logrelay.app.ui.theme.EdgeScrollbarGrid
import com.logrelay.app.ui.theme.PinIcon
import com.logrelay.app.ui.theme.LogRelayColors
import com.logrelay.app.ui.theme.LogRelayTheme
import com.logrelay.app.ui.theme.SavedCheckmark
import com.logrelay.app.ui.theme.StampTextStyle
import com.logrelay.app.ui.theme.GridViewIcon
import com.logrelay.app.ui.theme.ListViewIcon
import com.logrelay.app.util.DateUtils
import com.logrelay.app.util.GalleryStorage
import com.logrelay.app.util.AutoBackupScheduler
import com.logrelay.app.util.SettingsStore
import com.logrelay.app.util.ViewMode
import androidx.work.ExistingPeriodicWorkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** 日付フィルタの単位。DAY=特定の1日、MONTH=その月全体 */
private enum class DateFilterMode { DAY, MONTH }

/** ⋮メニューの階層。項目が増えたため2階層に分けている */
private enum class MenuLevel { ROOT, EXPORT, BACKUP }

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 拒否されても時刻のみで動作継続するため、結果分岐は不要 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        val repository = RecordRepository(applicationContext)

        setContent {
            LogRelayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RecordScreen(repository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(repository: RecordRepository) {
    val pagerState = rememberPagerState(pageCount = { 3 }) // 0:記録一覧 1:今日の振り返り 2:ゴミ箱
    var editingRecord by remember { mutableStateOf<Record?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var listSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    val listSelectionMode = listSelectedIds.isNotEmpty()
    var trashSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showPermanentDeleteConfirm by remember { mutableStateOf(false) }

    var dateFilter by remember { mutableStateOf<Long?>(null) }
    var dateFilterMode by remember { mutableStateOf(DateFilterMode.DAY) }
    var showDatePicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current
    var dayStartHour by remember { mutableStateOf(SettingsStore.getDayStartHour(context)) }
    var aiPromptTemplate by remember { mutableStateOf(SettingsStore.getAiPromptTemplate(context)) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var backupFolderUri by remember { mutableStateOf(SettingsStore.getBackupFolderUri(context)) }
    var backupIntervalHours by remember { mutableStateOf(SettingsStore.getBackupIntervalHours(context)) }

    // アプリ起動時、現在の設定に沿って自動バックアップの定期実行を確認する。
    // KEEPを使うことで、既に動いているタイマーを毎回リセットしない
    LaunchedEffect(Unit) {
        AutoBackupScheduler.schedule(context, backupIntervalHours, ExistingPeriodicWorkPolicy.KEEP)
    }

    val backupFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            backupFolderUri = uri.toString()
            SettingsStore.setBackupFolderUri(context, uri.toString())
        }
    }

    // タブごと(0:記録一覧 1:今日の振り返り 2:ゴミ箱)に独立した表示形式(リスト/カード)
    var viewModeTab0 by remember { mutableStateOf(SettingsStore.getViewMode(context, 0)) }
    var viewModeTab1 by remember { mutableStateOf(SettingsStore.getViewMode(context, 1)) }
    var viewModeTab2 by remember { mutableStateOf(SettingsStore.getViewMode(context, 2)) }

    var showSavedCheckmark by remember { mutableStateOf(false) }
    LaunchedEffect(showSavedCheckmark) {
        if (showSavedCheckmark) {
            kotlinx.coroutines.delay(700)
            showSavedCheckmark = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        listSelectedIds = emptySet()
        trashSelectedIds = emptySet()
    }

    LaunchedEffect(Unit) {
        repository.purgeExpiredTrash()
    }

    // 「1日の区切り時刻」の設定を反映した「今日」の範囲。深夜作業層向けに、
    // 例えば4時区切りにすると深夜2時の記録も「前日の振り返り」に含まれる
    val todayRecords by repository.observeForDay(
        DateUtils.startOfTodayLogical(dayStartHour),
        DateUtils.endOfTodayLogical(dayStartHour)
    ).collectAsState(initial = emptyList())
    val trashRecords by repository.observeTrash().collectAsState(initial = emptyList())

    val baseRecords by (
        dateFilter?.let { d ->
            val (start, end) = if (dateFilterMode == DateFilterMode.MONTH) {
                DateUtils.monthBoundsFromDatePickerUtcMillis(d)
            } else {
                DateUtils.dayBoundsFromDatePickerUtcMillis(d)
            }
            repository.observeForDay(start, end)
        } ?: repository.observeAll()
    ).collectAsState(initial = emptyList())

    val tab0Records = remember(baseRecords, searchQuery) {
        if (searchQuery.isBlank()) {
            baseRecords
        } else {
            baseRecords.filter {
                it.memo.contains(searchQuery, ignoreCase = true) ||
                    (it.placeName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    fun resolvePlaceNameFor(record: Record) {
        scope.launch { repository.resolvePlaceName(record) }
    }

    var showMenu by remember { mutableStateOf(false) }
    var menuLevel by remember { mutableStateOf(MenuLevel.ROOT) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    // 「今表示している範囲」＝共有・書き出しの対象。
    // 記録一覧はフィルタ・検索後の内容、今日の振り返りはその日の内容、ゴミ箱はゴミ箱の内容を反映する
    val currentDisplayedRecords = when (pagerState.currentPage) {
        0 -> tab0Records
        1 -> todayRecords
        else -> trashRecords
    }

    // ファイル名の重複回避・識別のため、書き出しのたびに秒単位のタイムスタンプを付与する
    fun timestampedFilename(prefix: String, extension: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
        return "${prefix}_${ts}.${extension}"
    }

    val exportMdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            val text = repository.exportMarkdown(currentDisplayedRecords)
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            scope.launch { snackbarHostState.showSnackbar("Markdownを書き出しました", duration = SnackbarDuration.Short) }
        }
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val text = repository.exportCsv(currentDisplayedRecords)
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            scope.launch { snackbarHostState.showSnackbar("CSVを書き出しました", duration = SnackbarDuration.Short) }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { out -> repository.exportBackupZip(out) }
                snackbarHostState.showSnackbar("バックアップを作成しました", duration = SnackbarDuration.Short)
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirm = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        containerColor = LogRelayColors.Ink,
                        contentColor = LogRelayColors.Paper,
                        actionColor = LogRelayColors.Paper,
                        snackbarData = data
                    )
                }
            },
            floatingActionButton = {
                // 「記録一覧」タブで、複数選択モード中でないときだけ表示。
                // ウィジェットを使わずアプリ内からも記録を作れるようにするための手動追加ボタン。
                if (pagerState.currentPage == 0 && !listSelectionMode) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                val record = repository.captureNow()
                                editingRecord = record
                            }
                        },
                        containerColor = LogRelayColors.Indigo,
                        contentColor = LogRelayColors.Paper
                    ) {
                        Text("＋", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Surface(shadowElevation = 2.dp, color = LogRelayColors.Paper) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LogRelay",
                                color = LogRelayColors.Indigo,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.6).sp
                                )
                            )
                            Box {
                                Text(
                                    text = "⋮",
                                    color = LogRelayColors.InkFaint,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.clickable { showMenu = true }.padding(4.dp)
                                )
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false; menuLevel = MenuLevel.ROOT }
                                ) {
                                    when (menuLevel) {
                                        MenuLevel.ROOT -> {
                                            DropdownMenuItem(
                                                text = { Text("エクスポート  ›") },
                                                onClick = { menuLevel = MenuLevel.EXPORT }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("バックアップ  ›") },
                                                onClick = { menuLevel = MenuLevel.BACKUP }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("設定") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    showSettingsDialog = true
                                                }
                                            )
                                        }
                                        MenuLevel.EXPORT -> {
                                            DropdownMenuItem(
                                                text = { Text("‹ 戻る", color = LogRelayColors.InkFaint) },
                                                onClick = { menuLevel = MenuLevel.ROOT }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Markdownで書き出す") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    exportMdLauncher.launch(timestampedFilename("logrelay_export", "md"))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("CSVで書き出す") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    exportCsvLauncher.launch(timestampedFilename("logrelay_export", "csv"))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Markdownを共有") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    val text = repository.exportMarkdown(currentDisplayedRecords)
                                                    com.logrelay.app.util.ShareHelper.shareTextFile(
                                                        context, timestampedFilename("logrelay_export", "md"), "text/markdown", text
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("CSVを共有") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    val text = repository.exportCsv(currentDisplayedRecords)
                                                    com.logrelay.app.util.ShareHelper.shareTextFile(
                                                        context, timestampedFilename("logrelay_export", "csv"), "text/csv", text
                                                    )
                                                }
                                            )
                                        }
                                        MenuLevel.BACKUP -> {
                                            DropdownMenuItem(
                                                text = { Text("‹ 戻る", color = LogRelayColors.InkFaint) },
                                                onClick = { menuLevel = MenuLevel.ROOT }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("バックアップを作成") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    backupLauncher.launch(timestampedFilename("logrelay_backup", "zip"))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("バックアップから復元") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("自動バックアップ設定") },
                                                onClick = {
                                                    showMenu = false
                                                    menuLevel = MenuLevel.ROOT
                                                    showSettingsDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        WeightedTabRow(
                            selectedIndex = pagerState.currentPage,
                            onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            tabs = listOf(
                                TabItem("記録一覧", 0.42f),
                                TabItem("今日の振り返り", 0.42f),
                                TabItem("ゴミ箱", 0.16f)
                            )
                        )

                        if (pagerState.currentPage in 0..1 && listSelectionMode) {
                            SelectionActionBar(
                                count = listSelectedIds.size,
                                onCancel = { listSelectedIds = emptySet() },
                                onDelete = {
                                    val idsToDelete = listSelectedIds.toList()
                                    listSelectedIds = emptySet()
                                    scope.launch {
                                        repository.softDeleteMany(idsToDelete)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "${idsToDelete.size}件削除しました",
                                            actionLabel = "元に戻す",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            repository.restoreMany(idsToDelete)
                                        }
                                    }
                                }
                            )
                        } else if (pagerState.currentPage == 0) {
                            SearchAndDateBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                dateFilter = dateFilter,
                                dateFilterMode = dateFilterMode,
                                onOpenPicker = { showDatePicker = true },
                                onClearDate = { dateFilter = null },
                                viewMode = viewModeTab0,
                                onViewModeChange = { mode ->
                                    viewModeTab0 = mode
                                    SettingsStore.setViewMode(context, 0, mode)
                                }
                            )
                        } else if (pagerState.currentPage == 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                ViewModeToggle(
                                    mode = viewModeTab1,
                                    onChange = { mode ->
                                        viewModeTab1 = mode
                                        SettingsStore.setViewMode(context, 1, mode)
                                    }
                                )
                            }
                        }

                        if (pagerState.currentPage == 2 && trashRecords.isNotEmpty()) {
                            TrashToolbar(
                                allSelected = trashSelectedIds.size == trashRecords.size,
                                selectedCount = trashSelectedIds.size,
                                onToggleSelectAll = {
                                    trashSelectedIds = if (trashSelectedIds.size == trashRecords.size) {
                                        emptySet()
                                    } else {
                                        trashRecords.map { it.id }.toSet()
                                    }
                                },
                                onPermanentDelete = { showPermanentDeleteConfirm = true },
                                viewMode = viewModeTab2,
                                onViewModeChange = { mode ->
                                    viewModeTab2 = mode
                                    SettingsStore.setViewMode(context, 2, mode)
                                }
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    DotGridBackground(modifier = Modifier.fillMaxSize()) {
                        when (page) {
                            0 -> RecordListPage(
                                records = tab0Records,
                                grouped = true,
                                dayStartHour = dayStartHour,
                                viewMode = viewModeTab0,
                                emptyText = if (dateFilter != null || searchQuery.isNotBlank()) "条件に合う記録がありません" else "まだ記録がありません",
                                listSelectedIds = listSelectedIds,
                                listSelectionMode = listSelectionMode,
                                onToggleSelect = { id ->
                                    listSelectedIds = if (listSelectedIds.contains(id)) listSelectedIds - id else listSelectedIds + id
                                },
                                onLongSelect = { id -> listSelectedIds = listSelectedIds + id },
                                onEdit = { editingRecord = it },
                                onNeedsGeocode = ::resolvePlaceNameFor,
                                onDelete = { record ->
                                    scope.launch {
                                        repository.softDelete(record.id)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "削除しました",
                                            actionLabel = "元に戻す",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) repository.restore(record.id)
                                    }
                                }
                            )
                            1 -> RecordListPage(
                                records = todayRecords,
                                grouped = false,
                                dayStartHour = dayStartHour,
                                viewMode = viewModeTab1,
                                emptyText = "今日はまだ何も記録がありません",
                                listSelectedIds = listSelectedIds,
                                listSelectionMode = listSelectionMode,
                                onToggleSelect = { id ->
                                    listSelectedIds = if (listSelectedIds.contains(id)) listSelectedIds - id else listSelectedIds + id
                                },
                                onLongSelect = { id -> listSelectedIds = listSelectedIds + id },
                                onEdit = { editingRecord = it },
                                onNeedsGeocode = ::resolvePlaceNameFor,
                                onDelete = { record ->
                                    scope.launch {
                                        repository.softDelete(record.id)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "削除しました",
                                            actionLabel = "元に戻す",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) repository.restore(record.id)
                                    }
                                }
                            )
                            2 -> TrashPage(
                                records = trashRecords,
                                viewMode = viewModeTab2,
                                selectedIds = trashSelectedIds,
                                onToggleSelect = { id ->
                                    trashSelectedIds = if (trashSelectedIds.contains(id)) trashSelectedIds - id else trashSelectedIds + id
                                },
                                onRestore = { id -> scope.launch { repository.restore(id) } }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SavedCheckmark(visible = showSavedCheckmark)
        }
    }

    editingRecord?.let { record ->
        MemoEditDialog(
            record = record,
            repository = repository,
            snackbarHostState = snackbarHostState,
            scope = scope,
            onDismiss = { editingRecord = null },
            onSave = { newMemo ->
                scope.launch {
                    repository.updateMemo(record.id, newMemo)
                    showSavedCheckmark = true
                }
                editingRecord = null
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateFilter)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            dateFilter = selected
                            dateFilterMode = DateFilterMode.MONTH
                            showDatePicker = false
                        }
                    }) { Text("この月で絞り込む", color = LogRelayColors.Indigo) }
                    TextButton(onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            dateFilter = selected
                            dateFilterMode = DateFilterMode.DAY
                            showDatePicker = false
                        }
                    }) { Text("この日で絞り込む", color = LogRelayColors.Indigo) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル", color = LogRelayColors.InkFaint) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            title = { Text("完全に削除しますか？") },
            text = { Text("${trashSelectedIds.size}件の記録を完全に削除します。この操作は取り消せません。") },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = trashSelectedIds.toList()
                        trashSelectedIds = emptySet()
                        showPermanentDeleteConfirm = false
                        scope.launch { repository.hardDeleteMany(ids) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LogRelayColors.Vermilion)
                ) { Text("完全に削除") }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("キャンセル", color = LogRelayColors.InkFaint)
                }
            }
        )
    }

    if (showRestoreConfirm && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false; pendingRestoreUri = null },
            title = { Text("バックアップから復元しますか？") },
            text = { Text("現在アプリ内にある記録はすべて上書きされ、選択したバックアップファイルの内容に置き換わります。この操作は取り消せません。") },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        showRestoreConfirm = false
                        pendingRestoreUri = null
                        if (uri != null) {
                            scope.launch {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    repository.restoreFromBackupZip(input)
                                }
                                snackbarHostState.showSnackbar("復元しました", duration = SnackbarDuration.Short)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LogRelayColors.Vermilion)
                ) { Text("復元する") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false; pendingRestoreUri = null }) {
                    Text("キャンセル", color = LogRelayColors.InkFaint)
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("設定") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "1日の区切り時刻",
                        color = LogRelayColors.Ink,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "深夜に振り返りをする場合、区切りを遅らせると「今日の振り返り」に反映されます",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogRelayColors.InkFaint,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            dayStartHour = (dayStartHour + 23) % 24
                            SettingsStore.setDayStartHour(context, dayStartHour)
                        }) { Text("－", color = LogRelayColors.Indigo, style = MaterialTheme.typography.titleLarge) }
                        Text(
                            text = "%02d:00".format(dayStartHour),
                            style = MaterialTheme.typography.titleLarge,
                            color = LogRelayColors.Indigo,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        TextButton(onClick = {
                            dayStartHour = (dayStartHour + 1) % 24
                            SettingsStore.setDayStartHour(context, dayStartHour)
                        }) { Text("＋", color = LogRelayColors.Indigo, style = MaterialTheme.typography.titleLarge) }
                    }

                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(1.dp).background(LogRelayColors.PaperDot))

                    Text(
                        "AI用にコピーのテンプレート",
                        color = LogRelayColors.Ink,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "{date} {location} {memo} の3つは、実際の値に自動で置き換わります",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogRelayColors.InkFaint,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = aiPromptTemplate,
                        onValueChange = {
                            aiPromptTemplate = it
                            SettingsStore.setAiPromptTemplate(context, it)
                        },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = {
                            aiPromptTemplate = SettingsStore.DEFAULT_AI_PROMPT_TEMPLATE
                            SettingsStore.setAiPromptTemplate(context, aiPromptTemplate)
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("デフォルトに戻す", color = LogRelayColors.InkFaint, style = MaterialTheme.typography.bodySmall)
                    }

                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(1.dp).background(LogRelayColors.PaperDot))

                    Text(
                        "ローカル自動バックアップ",
                        color = LogRelayColors.Ink,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "指定したフォルダに、設定した間隔で自動的にバックアップZIPを作成します",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogRelayColors.InkFaint,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = backupFolderUri?.let { "保存先：設定済み" } ?: "保存先：未設定",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (backupFolderUri != null) LogRelayColors.Indigo else LogRelayColors.InkFaint,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { backupFolderPickerLauncher.launch(null) }) {
                            Text("フォルダを選択", color = LogRelayColors.Indigo)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("自動保存の間隔", style = MaterialTheme.typography.bodySmall, color = LogRelayColors.InkFaint)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val intervalOptions = listOf(0L to "オフ", 24L to "毎日", 72L to "3日ごと", 168L to "毎週")
                        intervalOptions.forEach { (hours, label) ->
                            val selected = backupIntervalHours == hours
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) LogRelayColors.Paper else LogRelayColors.Indigo,
                                modifier = Modifier
                                    .clickable {
                                        backupIntervalHours = hours
                                        SettingsStore.setBackupIntervalHours(context, hours)
                                        AutoBackupScheduler.schedule(context, hours, ExistingPeriodicWorkPolicy.UPDATE)
                                    }
                                    .background(
                                        if (selected) LogRelayColors.Indigo else LogRelayColors.IndigoSoft,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (backupIntervalHours > 0 && backupFolderUri == null) {
                        Text(
                            "保存先フォルダが未設定のため、自動保存は行われません",
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.Vermilion,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = LogRelayColors.Indigo)) {
                    Text("閉じる")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordListPage(
    records: List<Record>,
    grouped: Boolean,
    dayStartHour: Int,
    viewMode: ViewMode,
    emptyText: String,
    listSelectedIds: Set<Long>,
    listSelectionMode: Boolean,
    onToggleSelect: (Long) -> Unit,
    onLongSelect: (Long) -> Unit,
    onEdit: (Record) -> Unit,
    onNeedsGeocode: (Record) -> Unit,
    onDelete: (Record) -> Unit
) {
    if (records.isEmpty()) {
        EmptyState(text = emptyText, hint = "ホーム画面のウィジェットをタップしてみてください")
        return
    }

    if (viewMode == ViewMode.CARD) {
        // カード(グリッド)表示：メモを主役にしたコンパクトな2列カード
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(records, key = { it.id }) { record ->
                    val isSelected = listSelectedIds.contains(record.id)
                    RecordGridCard(
                        record = record,
                        isSelected = isSelected,
                        selectionMode = listSelectionMode,
                        onClick = { if (listSelectionMode) onToggleSelect(record.id) else onEdit(record) },
                        onLongClick = { onLongSelect(record.id) },
                        onNeedsGeocode = onNeedsGeocode,
                        onDelete = { onDelete(record) }
                    )
                }
            }
            EdgeScrollbarGrid(gridState = gridState, totalItemCount = records.size)
        }
        return
    }

    val listState = rememberLazyListState()
    val dayHeaderFormatter = remember { SimpleDateFormat("M月d日(E)", Locale.JAPAN) }
    // 区切り時刻(dayStartHour)を考慮した論理的な1日でグループ化する。
    // 例えば区切りが4時なら、深夜2時の記録は前日のグループに含まれる
    val groupedMap: Map<String, List<Record>> = if (grouped) {
        records
            .groupBy { DateUtils.startOfLogicalDay(it.timestamp, dayStartHour) }
            .toSortedMap(compareByDescending { it })
            .mapKeys { (dayStart, _) -> dayHeaderFormatter.format(Date(dayStart)) }
    } else {
        mapOf("" to records)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groupedMap.forEach { (dayLabel, recordsForDay) ->
                if (grouped) {
                    stickyHeader(key = "header_$dayLabel") {
                        DateGroupHeader(dayLabel)
                    }
                }
                items(recordsForDay, key = { it.id }) { record ->
                    val isSelected = listSelectedIds.contains(record.id)
                    RecordCard(
                        record = record,
                        isSelected = isSelected,
                        selectionMode = listSelectionMode,
                        onClick = { if (listSelectionMode) onToggleSelect(record.id) else onEdit(record) },
                        onLongClick = { onLongSelect(record.id) },
                        onNeedsGeocode = onNeedsGeocode,
                        onDelete = { onDelete(record) }
                    )
                }
            }
        }
        EdgeScrollbar(listState = listState, totalItemCount = records.size + groupedMap.size)
    }
}

@Composable
private fun TrashPage(
    records: List<Record>,
    viewMode: ViewMode,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onRestore: (Long) -> Unit
) {
    if (records.isEmpty()) {
        EmptyState(text = "ゴミ箱は空です", hint = "削除した記録は${RecordRepository.TRASH_RETENTION_DAYS_CONST}日間ここに残ります")
        return
    }

    if (viewMode == ViewMode.CARD) {
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(records, key = { it.id }) { record ->
                    TrashGridCard(
                        record = record,
                        isSelected = selectedIds.contains(record.id),
                        onToggleSelect = { onToggleSelect(record.id) },
                        onRestore = { onRestore(record.id) }
                    )
                }
            }
            EdgeScrollbarGrid(gridState = gridState, totalItemCount = records.size)
        }
        return
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(records, key = { it.id }) { record ->
                TrashCard(
                    record = record,
                    isSelected = selectedIds.contains(record.id),
                    onToggleSelect = { onToggleSelect(record.id) },
                    onRestore = { onRestore(record.id) }
                )
            }
        }
        EdgeScrollbar(listState = listState, totalItemCount = records.size)
    }
}

@Composable
private fun ViewModeToggle(mode: ViewMode, onChange: (ViewMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.clickable { onChange(ViewMode.LIST) }.padding(6.dp)) {
            ListViewIcon(tint = if (mode == ViewMode.LIST) LogRelayColors.Indigo else LogRelayColors.InkFaint.copy(alpha = 0.4f))
        }
        Box(modifier = Modifier.clickable { onChange(ViewMode.CARD) }.padding(6.dp)) {
            GridViewIcon(tint = if (mode == ViewMode.CARD) LogRelayColors.Indigo else LogRelayColors.InkFaint.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun SearchAndDateBar(
    query: String,
    onQueryChange: (String) -> Unit,
    dateFilter: Long?,
    dateFilterMode: DateFilterMode,
    onOpenPicker: () -> Unit,
    onClearDate: () -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("メモや地名で検索", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Text(
                            text = "✕",
                            color = LogRelayColors.InkFaint,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { onQueryChange("") }
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(6.dp))
            ViewModeToggle(mode = viewMode, onChange = onViewModeChange)
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenPicker)
            ) {
                CalendarIcon(tint = if (dateFilter != null) LogRelayColors.Indigo else LogRelayColors.InkFaint)
            }
        }
        if (dateFilter != null) {
            val dayFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).apply { timeZone = TimeZone.getTimeZone("UTC") } }
            val monthFormatter = remember { SimpleDateFormat("yyyy年M月", Locale.JAPAN).apply { timeZone = TimeZone.getTimeZone("UTC") } }
            val label = if (dateFilterMode == DateFilterMode.MONTH) {
                monthFormatter.format(Date(dateFilter))
            } else {
                dayFormatter.format(Date(dateFilter))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = LogRelayColors.Indigo, fontWeight = FontWeight.Medium)
                TextButton(onClick = onClearDate) { Text("絞り込み解除", color = LogRelayColors.InkFaint) }
            }
        }
    }
}

/** タブごとの幅の比率を指定するためのデータ */
private data class TabItem(val label: String, val weight: Float)

/**
 * 標準のTabRowは各タブが均等幅になり、あまり見ない「ゴミ箱」タブまで
 * 「記録一覧」と同じ幅を取ってしまう。タブごとに幅の比率を指定できる
 * 軽量な代替実装。
 */
@Composable
private fun WeightedTabRow(selectedIndex: Int, onTabSelected: (Int) -> Unit, tabs: List<TabItem>) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(tab.weight)
                    .fillMaxHeight()
                    .clickable { onTabSelected(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) LogRelayColors.Indigo else LogRelayColors.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(2.dp)
                        .background(if (selected) LogRelayColors.Indigo else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(LogRelayColors.IndigoSoft).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${count}件選択中", color = LogRelayColors.Indigo, fontWeight = FontWeight.Medium)
        Row {
            TextButton(onClick = onCancel) { Text("キャンセル", color = LogRelayColors.InkFaint) }
            TextButton(onClick = onDelete) { Text("削除", color = LogRelayColors.Vermilion) }
        }
    }
}

@Composable
private fun TrashToolbar(
    allSelected: Boolean,
    selectedCount: Int,
    onToggleSelectAll: () -> Unit,
    onPermanentDelete: () -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onToggleSelectAll) {
            Text(if (allSelected) "選択解除" else "すべて選択", color = LogRelayColors.Indigo)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedCount > 0) {
                TextButton(onClick = onPermanentDelete) { Text("完全に削除(${selectedCount})", color = LogRelayColors.Vermilion) }
            }
            ViewModeToggle(mode = viewMode, onChange = onViewModeChange)
        }
    }
}

@Composable
private fun EmptyState(text: String, hint: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = text, color = LogRelayColors.InkFaint, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = hint, color = LogRelayColors.InkFaint, style = MaterialTheme.typography.bodySmall)
    }
}

/** 位置情報の表示テキスト：地名キャッシュがあればそれを、なければ緯度経度を表示 */
private fun locationDisplayText(record: Record): String = when {
    record.placeName != null -> record.placeName
    record.latitude != null && record.longitude != null -> "%.4f, %.4f".format(record.latitude, record.longitude)
    else -> "位置情報なし"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordCard(
    record: Record,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onNeedsGeocode: (Record) -> Unit,
    onDelete: () -> Unit
) {
    LaunchedEffect(record.id, record.placeName) {
        if (record.placeName == null && record.latitude != null && record.longitude != null) {
            onNeedsGeocode(record)
        }
    }

    var thumbnail by remember(record.photoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(record.photoPath) {
        val path = record.photoPath
        thumbnail = if (path != null) {
            withContext(Dispatchers.IO) { com.logrelay.app.util.PhotoStorage.decodeSquareThumbnail(path, 160) }
        } else null
    }

    val formatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }
    val timeText = formatter.format(Date(record.timestamp))
    val hasLocation = record.latitude != null && record.longitude != null

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) LogRelayColors.IndigoSoft else LogRelayColors.CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(if (record.memo.isNotBlank()) 76.dp else 56.dp)
                    .background(LogRelayColors.Indigo)
            )
            Column(
                modifier = Modifier.weight(1f).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(12.dp)
            ) {
                Text(text = timeText, style = StampTextStyle)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasLocation) {
                        PinIcon(tint = LogRelayColors.InkFaint)
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(text = locationDisplayText(record), style = MaterialTheme.typography.bodySmall, color = LogRelayColors.InkFaint)
                    if (record.photoPath != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CameraIcon(tint = LogRelayColors.InkFaint)
                    }
                }
                if (record.memo.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = record.memo, style = MaterialTheme.typography.bodyMedium, color = LogRelayColors.Ink)
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "タップしてメモを追加",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogRelayColors.Indigo.copy(alpha = 0.55f)
                    )
                }
            }
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = LogRelayColors.Indigo))
            } else {
                Text(
                    text = "✕",
                    color = LogRelayColors.InkFaint,
                    modifier = Modifier.padding(10.dp).clickable(onClick = onDelete)
                )
            }
        }
    }
}

/**
 * カード(グリッド)表示用。メモを主役にした、リストカードを2列用に
 * コンパクト化したレイアウト。写真は小さめのサムネイルとして時刻の隣に添える。
 * 個別の✕削除・長押し選択の両方に対応する。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordGridCard(
    record: Record,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onNeedsGeocode: (Record) -> Unit,
    onDelete: () -> Unit
) {
    LaunchedEffect(record.id, record.placeName) {
        if (record.placeName == null && record.latitude != null && record.longitude != null) {
            onNeedsGeocode(record)
        }
    }

    var thumbnail by remember(record.photoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(record.photoPath) {
        val path = record.photoPath
        thumbnail = if (path != null) {
            withContext(Dispatchers.IO) { com.logrelay.app.util.PhotoStorage.decodeSquareThumbnail(path, 120) }
        } else null
    }

    val formatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }
    val hasLocation = record.latitude != null && record.longitude != null

    Box {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSelected) LogRelayColors.IndigoSoft else LogRelayColors.CardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(LogRelayColors.Indigo)
                )
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bitmap = thumbnail
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(5.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (hasLocation) {
                            PinIcon(tint = LogRelayColors.InkFaint, iconSize = 12.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(text = formatter.format(Date(record.timestamp)), style = StampTextStyle.copy(fontSize = 11.sp))
                    }
                    if (hasLocation) {
                        Text(
                            text = locationDisplayText(record),
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.InkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (record.memo.isNotBlank()) {
                        Text(
                            text = record.memo,
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.Ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "タップしてメモを追加",
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.Indigo.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = LogRelayColors.Indigo),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        } else {
            Text(
                text = "✕",
                color = LogRelayColors.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}

@Composable
private fun TrashCard(record: Record, isSelected: Boolean, onToggleSelect: () -> Unit, onRestore: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }
    val timeText = formatter.format(Date(record.timestamp))
    val remainingDays = record.deletedAt?.let { deletedAt ->
        val elapsedMs = System.currentTimeMillis() - deletedAt
        val retentionMs = TimeUnit.DAYS.toMillis(RecordRepository.TRASH_RETENTION_DAYS_CONST)
        ((retentionMs - elapsedMs) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)
    } ?: 0

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) LogRelayColors.IndigoSoft else LogRelayColors.CardSurface.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() }, colors = CheckboxDefaults.colors(checkedColor = LogRelayColors.Indigo))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = timeText, style = StampTextStyle.copy(color = LogRelayColors.InkFaint))
                if (record.memo.isNotBlank()) {
                    Text(text = record.memo, style = MaterialTheme.typography.bodySmall, color = LogRelayColors.InkFaint)
                }
                Text(text = "あと${remainingDays}日で完全に削除されます", style = MaterialTheme.typography.bodySmall, color = LogRelayColors.Vermilion)
            }
            TextButton(onClick = onRestore) { Text("元に戻す", color = LogRelayColors.Indigo) }
        }
    }
}

/** カード(グリッド)表示用のゴミ箱カード。RecordGridCardと見た目のトーンを揃えたコンパクトなレイアウト */
@Composable
private fun TrashGridCard(record: Record, isSelected: Boolean, onToggleSelect: () -> Unit, onRestore: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }
    val remainingDays = record.deletedAt?.let { deletedAt ->
        val elapsedMs = System.currentTimeMillis() - deletedAt
        val retentionMs = TimeUnit.DAYS.toMillis(RecordRepository.TRASH_RETENTION_DAYS_CONST)
        ((retentionMs - elapsedMs) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)
    } ?: 0
    val hasLocation = record.latitude != null && record.longitude != null

    var thumbnail by remember(record.photoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(record.photoPath) {
        val path = record.photoPath
        thumbnail = if (path != null) {
            withContext(Dispatchers.IO) { com.logrelay.app.util.PhotoStorage.decodeSquareThumbnail(path, 120) }
        } else null
    }

    Box {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSelected) LogRelayColors.IndigoSoft else LogRelayColors.CardSurface.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.clickable { onToggleSelect() }
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(LogRelayColors.Vermilion.copy(alpha = 0.6f))
                )
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bitmap = thumbnail
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(5.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (hasLocation) {
                            PinIcon(tint = LogRelayColors.InkFaint, iconSize = 12.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(text = formatter.format(Date(record.timestamp)), style = StampTextStyle.copy(fontSize = 11.sp, color = LogRelayColors.InkFaint))
                    }
                    if (hasLocation) {
                        Text(
                            text = locationDisplayText(record),
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.InkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (record.memo.isNotBlank()) {
                        Text(
                            text = record.memo,
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.InkFaint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = "あと${remainingDays}日",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogRelayColors.Vermilion
                    )
                    TextButton(onClick = onRestore, contentPadding = PaddingValues(0.dp)) {
                        Text("元に戻す", style = MaterialTheme.typography.bodySmall, color = LogRelayColors.Indigo)
                    }
                }
            }
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            colors = CheckboxDefaults.colors(checkedColor = LogRelayColors.Indigo),
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun DateGroupHeader(dayLabel: String) {
    Column(modifier = Modifier.fillMaxWidth().background(LogRelayColors.Paper).padding(top = 6.dp, bottom = 4.dp)) {
        Text(text = dayLabel, style = MaterialTheme.typography.titleSmall, color = LogRelayColors.Indigo, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LogRelayColors.PaperDot))
    }
}

/**
 * Claudeにそのまま貼れる形式のプロンプト付きテキストを組み立てる。
 * YAML風のメタデータ + 分析を促す一言、という最小限の構成。
 */
/**
 * 「AI用にコピー」のテンプレートに、実際の記録内容を埋め込む。
 * テンプレート自体は設定画面でカスタマイズ可能({date} {location} {memo} を差し替える)。
 */
private fun buildClaudePrompt(record: Record, template: String): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN)
    val location = locationDisplayText(record)
    val memo = record.memo.ifBlank { "(メモなし)" }
    return template
        .replace("{date}", formatter.format(Date(record.timestamp)))
        .replace("{location}", location)
        .replace("{memo}", memo)
}

@Composable
private fun MemoEditDialog(
    record: Record,
    repository: RecordRepository,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var memoText by remember { mutableStateOf(record.memo) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hasLocation = record.latitude != null && record.longitude != null

    // 写真パスをダイアログ内のローカル状態として持つ。
    // DBの更新(Flow経由)を待たず、添付・削除操作の直後にここを更新することで
    // 「撮影して戻ってきたのに反映されない」という体感のズレを防ぐ。
    var localPhotoPath by remember(record.id) { mutableStateOf(record.photoPath) }

    // 写真のプレビュー用ビットマップ。localPhotoPathが変わるたびに読み直す
    var photoBitmap by remember(localPhotoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(localPhotoPath) {
        val path = localPhotoPath
        photoBitmap = if (path != null && File(path).exists()) {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
        } else null
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val newPath = repository.attachPhoto(record.id, uri)
                if (newPath != null) localPhotoPath = newPath
                snackbarHostState.showSnackbar("写真を追加しました", duration = SnackbarDuration.Short)
            }
        }
    }

    // カメラ撮影：撮影先の一時Uriを先に発行しておき、成功したら通常の添付フローに合流させる
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val newPath = repository.attachPhoto(record.id, uri)
                if (newPath != null) {
                    // 撮影直後にダイアログへ即座に反映
                    localPhotoPath = newPath
                    // 端末のギャラリーにもコピーしておく(アプリ内保存とは別枠)
                    withContext(Dispatchers.IO) {
                        GalleryStorage.saveToGallery(context, File(newPath))
                    }
                }
                snackbarHostState.showSnackbar("写真を追加しました", duration = SnackbarDuration.Short)
            }
        }
    }
    fun launchCamera() {
        val uri = com.logrelay.app.util.PhotoStorage.createCaptureUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("振り返りメモ") },
        text = {
            Column(modifier = Modifier.imePadding()) {
                val formatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }
                Text(text = formatter.format(Date(record.timestamp)), style = StampTextStyle)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (hasLocation) {
                        PinIcon(tint = LogRelayColors.InkFaint)
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(text = locationDisplayText(record), style = MaterialTheme.typography.bodySmall, color = LogRelayColors.InkFaint)
                }
                OutlinedTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    placeholder = { Text("何があった？") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                // 写真：あればプレビュー+削除、なければ追加ボタン
                val bitmap = photoBitmap
                if (bitmap != null) {
                    Box(modifier = Modifier.padding(top = 10.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(120.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "✕",
                            color = LogRelayColors.Paper,
                            modifier = Modifier
                                .padding(6.dp)
                                .background(LogRelayColors.Ink.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .clickable {
                                    scope.launch {
                                        repository.removePhoto(record.id)
                                        localPhotoPath = null
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Row(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = "ギャラリーから",
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.Indigo,
                            modifier = Modifier.clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "カメラで撮影",
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.Indigo,
                            modifier = Modifier.clickable {
                                val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasCameraPermission) {
                                    launchCamera()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    }
                }

                Row(modifier = Modifier.padding(top = 10.dp)) {
                    if (hasLocation) {
                        Text(
                            text = "地図で見る",
                            style = MaterialTheme.typography.bodySmall,
                            color = LogRelayColors.Indigo,
                            modifier = Modifier.clickable {
                                val uri = Uri.parse("geo:${record.latitude},${record.longitude}?q=${record.latitude},${record.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Text(
                        text = "AI用にコピー",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogRelayColors.Indigo,
                        modifier = Modifier.clickable {
                            val template = SettingsStore.getAiPromptTemplate(context)
                            clipboardManager.setText(AnnotatedString(buildClaudePrompt(record.copy(memo = memoText), template)))
                            scope.launch {
                                snackbarHostState.showSnackbar("コピーしました", duration = SnackbarDuration.Short)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(memoText) }, colors = ButtonDefaults.buttonColors(containerColor = LogRelayColors.Indigo)) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル", color = LogRelayColors.InkFaint) }
        }
    )
}
