package com.example.cycletracker.ui

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cycletracker.domain.CycleCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class PendingReminderToggle {
    NONE,
    PERIOD,
    FERTILE
}

/**
 * SAF document-creation contract that explicitly includes [Intent.CATEGORY_OPENABLE]
 * (required by many OEM DocumentsUI implementations) and supports fallback MIME types.
 */
private class CreateBackupDocumentContract : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        val (fileName, mimeType) = input
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleApp(
    viewModel: CycleViewModel = viewModel(),
    onAppLockSettingChanged: (Boolean) -> Unit = {},
    onLaunchExternalPicker: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showHistoryPage by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var pendingJumpDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingReminderToggle by remember { mutableStateOf(PendingReminderToggle.NONE) }

    val homeSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(msg: String) {
        coroutineScope.launch {
            homeSnackbarHostState.currentSnackbarData?.dismiss()
            homeSnackbarHostState.showSnackbar(msg)
        }
    }

    // Hoisted & remembered contracts so DisposableEffect never unregisters launchers on
    // recomposition or when SettingsSheet is dismissed while a system picker is open.
    val permissionContract = remember { ActivityResultContracts.RequestPermission() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        val pending = pendingReminderToggle
        pendingReminderToggle = PendingReminderToggle.NONE
        if (granted) {
            when (pending) {
                PendingReminderToggle.PERIOD -> viewModel.setPeriodReminder(true)
                PendingReminderToggle.FERTILE -> viewModel.setFertileReminder(true)
                PendingReminderToggle.NONE -> Unit
            }
        } else {
            showSettingsSheet = false
            showMessage("Notification permission is required for reminders.")
        }
    }

    val createDocContract = remember { CreateBackupDocumentContract() }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = createDocContract
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val appContext = context.applicationContext
        val payload = viewModel.exportBackupJson()
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = runCatching {
                        appContext.contentResolver.openOutputStream(uri, "wt")
                    }.getOrNull() ?: appContext.contentResolver.openOutputStream(uri)
                        ?: error("Unable to open destination file.")
                    stream.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }
            showSettingsSheet = false
            result.onSuccess {
                showMessage("Backup exported successfully.")
            }.onFailure { err ->
                showMessage("Export failed: ${err.message ?: "unknown error"}")
            }
        }
    }

    fun importFromUri(uri: Uri) {
        val appContext = context.applicationContext
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = appContext.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("Unable to read selected file.")
                    viewModel.importBackupJson(text).getOrThrow()
                }
            }
            showSettingsSheet = false
            result.onSuccess { count ->
                val unit = if (count == 1) "entry" else "entries"
                showMessage("Imported $count period $unit.")
            }.onFailure { err ->
                showMessage("Import failed: ${err.message ?: "invalid backup file"}")
            }
        }
    }

    val openDocContract = remember { ActivityResultContracts.OpenDocument() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = openDocContract
    ) { uri ->
        if (uri != null) importFromUri(uri)
    }

    val getContentContract = remember { ActivityResultContracts.GetContent() }
    val fallbackImportLauncher = rememberLauncherForActivityResult(
        contract = getContentContract
    ) { uri ->
        if (uri != null) importFromUri(uri)
    }

    fun exportDirectlyToDownloads(fileName: String) {
        val appContext = context.applicationContext
        val payload = viewModel.exportBackupJson()
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val resolver = appContext.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: error("Unable to create file in Downloads.")
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(payload.toByteArray(Charsets.UTF_8))
                            out.flush()
                        } ?: error("Unable to write to Downloads.")
                    } else {
                        error("No file picker available on this device.")
                    }
                }
            }
            showSettingsSheet = false
            result.onSuccess {
                showMessage("Saved to Downloads/$fileName")
            }.onFailure { err ->
                showMessage("Export failed: ${err.message ?: "unknown error"}")
            }
        }
    }

    fun launchExport() {
        val fileName = "maya-cycle-backup-${LocalDate.now()}.json"
        onLaunchExternalPicker()
        val launchedJson = runCatching {
            exportLauncher.launch(fileName to "application/json")
        }
        if (launchedJson.isFailure) {
            val launchedAny = runCatching {
                exportLauncher.launch(fileName to "*/*")
            }
            if (launchedAny.isFailure) {
                // Device has no SAF document creator (e.g., disabled DocumentsUI): save straight to Downloads/
                exportDirectlyToDownloads(fileName)
            }
        }
    }

    fun launchImport() {
        onLaunchExternalPicker()
        val launched = runCatching {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        if (launched.isFailure) {
            val fallbackLaunched = runCatching {
                fallbackImportLauncher.launch("*/*")
            }
            if (fallbackLaunched.isFailure) {
                showSettingsSheet = false
                showMessage("Unable to open file picker on this device.")
            }
        }
    }

    fun requestNotificationThen(target: PendingReminderToggle, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            pendingReminderToggle = target
            onLaunchExternalPicker()
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }.onFailure {
                pendingReminderToggle = PendingReminderToggle.NONE
                showSettingsSheet = false
                showMessage("Unable to request notification permission.")
            }
        }
    }

    // Live midnight rollover refresh whenever the user resumes Maya on a new calendar day
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTodayIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showHistoryPage) {
        BackHandler { showHistoryPage = false }
    }

    AnimatedContent(
        targetState = showHistoryPage,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(animationSpec = tween(260)) { it / 6 } +
                    fadeIn(animationSpec = tween(220))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(220)) { -it / 10 } +
                        fadeOut(animationSpec = tween(180)))
            } else {
                (slideInHorizontally(animationSpec = tween(260)) { -it / 6 } +
                    fadeIn(animationSpec = tween(220))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(220)) { it / 10 } +
                        fadeOut(animationSpec = tween(180)))
            }
        },
        label = "homeHistoryTransition"
    ) { inHistory ->
        if (inHistory) {
            HistoryScreen(
                periodStarts = state.periodStarts,
                model = state.model,
                onRemove = viewModel::removePeriodStart,
                onUndoRemove = viewModel::addPeriodStart,
                onJumpToDate = { date ->
                    viewModel.selectDate(date)
                    pendingJumpDate = date
                    showHistoryPage = false
                },
                onLogFirstPeriod = {
                    viewModel.goToToday()
                    viewModel.togglePeriodStart(state.today)
                    showHistoryPage = false
                },
                onBack = { showHistoryPage = false }
            )
        } else {
            HomeScreen(
                state = state,
                snackbarHostState = homeSnackbarHostState,
                scrollToDateRequest = pendingJumpDate,
                onScrollRequestConsumed = { pendingJumpDate = null },
                onLoadSample = viewModel::loadSampleHistory,
                onOpenHistory = { showHistoryPage = true },
                onOpenSettings = { showSettingsSheet = true },
                onToday = viewModel::goToToday,
                onSelectDate = viewModel::selectDate,
                onTogglePeriodStart = viewModel::togglePeriodStart
            )
        }
    }

    if (showSettingsSheet) {
        SettingsSheet(
            settings = state.settings,
            onUpdatePeriodLength = viewModel::updatePeriodLength,
            onTogglePeriodReminder = { enabled, daysBefore ->
                if (enabled) {
                    requestNotificationThen(PendingReminderToggle.PERIOD) {
                        viewModel.setPeriodReminder(true, daysBefore)
                    }
                } else {
                    viewModel.setPeriodReminder(false, daysBefore)
                }
            },
            onToggleFertileReminder = { enabled ->
                if (enabled) {
                    requestNotificationThen(PendingReminderToggle.FERTILE) {
                        viewModel.setFertileReminder(true)
                    }
                } else {
                    viewModel.setFertileReminder(false)
                }
            },
            onSetAppLockEnabled = { enabled ->
                viewModel.setAppLockEnabled(enabled)
                onAppLockSettingChanged(enabled)
            },
            onExportClick = ::launchExport,
            onImportClick = ::launchImport,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: CycleUiState,
    snackbarHostState: SnackbarHostState,
    scrollToDateRequest: LocalDate?,
    onScrollRequestConsumed: () -> Unit,
    onLoadSample: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onTogglePeriodStart: (LocalDate) -> Unit
) {
    val isSelectedLogged = remember(state.periodStarts, state.selectedDate) {
        state.periodStarts.binarySearch(state.selectedDate) >= 0
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            if (!isLandscape) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MayaLogo(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Maya",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        HomeHeaderActions(
                            showSample = state.periodStarts.isEmpty(),
                            compact = false,
                            onLoadSample = onLoadSample,
                            onOpenHistory = onOpenHistory,
                            onOpenSettings = onOpenSettings
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onTogglePeriodStart(state.selectedDate) },
                modifier = Modifier.navigationBarsPadding(),
                containerColor = if (isSelectedLogged) CyclePalette.GreenFertile else CyclePalette.RedPeriod,
                contentColor = Color.White,
                icon = {
                    Icon(
                        imageVector = if (isSelectedLogged) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = if (isSelectedLogged) "Remove Period" else "Log Period",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        if (isLandscape) {
            val selectedInfo = remember(
                state.selectedDate,
                state.periodStarts,
                state.model,
                state.settings.periodLength
            ) {
                CycleCalculator.classifyDate(
                    date = state.selectedDate,
                    periodStarts = state.periodStarts,
                    model = state.model,
                    periodLength = state.settings.periodLength
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MayaLogo(modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Maya",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        HomeHeaderActions(
                            showSample = state.periodStarts.isEmpty(),
                            compact = true,
                            onLoadSample = onLoadSample,
                            onOpenHistory = onOpenHistory,
                            onOpenSettings = onOpenSettings
                        )
                    }

                    HomeWidgetPreviewCard(
                        todayInfo = state.todayClassification,
                        hasData = state.model.hasData,
                        periodStarts = state.periodStarts,
                        model = state.model,
                        periodLength = state.settings.periodLength,
                        modifier = Modifier.zIndex(1f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectedDayStatusBlock(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            selectedInfo = selectedInfo,
                            model = state.model,
                            centered = false,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LegendItem(CyclePalette.GreenFertile, "Fertile")
                            LegendItem(CyclePalette.YellowCouldBe, "Could be")
                            LegendItem(CyclePalette.BlueNotFertile, "Not fertile")
                            LegendItem(CyclePalette.RedPeriod, "Period")
                        }
                    }
                }

                CalendarCard(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
                    today = state.today,
                    selectedDate = state.selectedDate,
                    periodStarts = state.periodStarts,
                    model = state.model,
                    periodLength = state.settings.periodLength,
                    scrollToDateRequest = scrollToDateRequest,
                    onScrollRequestConsumed = onScrollRequestConsumed,
                    onToday = onToday,
                    onSelectDate = onSelectDate,
                    isCompactLandscape = true
                )
            }
        } else {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp + navBarBottom),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeWidgetPreviewCard(
                        todayInfo = state.todayClassification,
                        hasData = state.model.hasData,
                        periodStarts = state.periodStarts,
                        model = state.model,
                        periodLength = state.settings.periodLength,
                        modifier = Modifier.zIndex(1f)
                    )

                    CalendarCard(
                        modifier = Modifier.weight(1f),
                        today = state.today,
                        selectedDate = state.selectedDate,
                        periodStarts = state.periodStarts,
                        model = state.model,
                        periodLength = state.settings.periodLength,
                        scrollToDateRequest = scrollToDateRequest,
                        onScrollRequestConsumed = onScrollRequestConsumed,
                        onToday = onToday,
                        onSelectDate = onSelectDate
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 28.dp, end = 168.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LegendItem(CyclePalette.GreenFertile, "Fertile", Modifier.weight(1f))
                        LegendItem(CyclePalette.YellowCouldBe, "Could be", Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LegendItem(CyclePalette.BlueNotFertile, "Not fertile", Modifier.weight(1f))
                        LegendItem(CyclePalette.RedPeriod, "Period", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderActions(
    showSample: Boolean,
    compact: Boolean,
    onLoadSample: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showSample) {
            TextButton(onClick = onLoadSample) {
                Text("Sample", fontSize = if (compact) 12.sp else 14.sp)
            }
        }
        IconButton(
            onClick = onOpenHistory,
            modifier = if (compact) Modifier.size(36.dp) else Modifier
        ) {
            Icon(
                imageVector = MayaIcons.History,
                contentDescription = "Cycle Insights & History",
                modifier = if (compact) Modifier.size(21.dp) else Modifier
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = if (compact) Modifier.size(36.dp) else Modifier
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = if (compact) Modifier.size(20.dp) else Modifier
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
