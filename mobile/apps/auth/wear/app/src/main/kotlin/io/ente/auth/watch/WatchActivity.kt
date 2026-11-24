package io.ente.auth.watch

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.ente.auth.watch.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class WatchActivity : ComponentActivity() {
    private lateinit var companion: WatchCompanion
    private var companionState by mutableStateOf(WatchCompanion.State())
    private var resumed by mutableStateOf(false)

    private val unlockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) companion.retry()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setBackgroundDrawableResource(android.R.color.black)
        companion = WatchCompanion(this)
        companion.onChange = { next -> companionState = next }
        setContent {
            AuthWatchTheme {
                AuthWatchRoot(
                    state = companionState,
                    resumed = resumed,
                    companion = companion,
                    onRequestUnlock = ::requestWatchUnlock,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        companion.setVisible(true)
    }

    override fun onPause() {
        resumed = false
        companion.setVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        companion.close()
        super.onDestroy()
    }

    private fun requestWatchUnlock() {
        @Suppress("DEPRECATION")
        val intent = getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent(
                getString(R.string.app_name),
                getString(R.string.unlock_watch),
            )
            ?: Intent(Settings.ACTION_SECURITY_SETTINGS)
        unlockLauncher.launch(intent)
    }
}

@Composable
private fun AuthWatchRoot(
    state: WatchCompanion.State,
    resumed: Boolean,
    companion: WatchCompanion,
    onRequestUnlock: () -> Unit,
) {
    val context = LocalContext.current
    var selectedEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }

    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        while (isActive) {
            if (keyguard.isDeviceLocked) {
                companion.setVisible(false)
                break
            }
            val nowMs = System.currentTimeMillis()
            seconds = (nowMs + 50) / 1000
            delay((1000L - nowMs % 1000L).coerceAtLeast(16L))
        }
    }

    val availableTags = remember(state.entries) {
        state.entries.flatMap { it.tags }.distinct().sorted()
    }
    LaunchedEffect(availableTags) {
        if (selectedTag != null && selectedTag !in availableTags) {
            selectedTag = null
        }
    }
    LaunchedEffect(state) {
        if (state.message != WatchCompanion.Message.hidden &&
            selectedEntryId != null &&
            state.entries.none { it.id == selectedEntryId }
        ) {
            selectedEntryId = null
        }
    }

    val isHidden = state.message == WatchCompanion.Message.hidden
    BackHandler(enabled = !isHidden && (showManage || selectedEntryId != null)) {
        if (showManage) showManage = false else selectedEntryId = null
    }

    AppScaffold {
        if (isHidden) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.codes_hidden),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val selectedEntry = selectedEntryId?.let { id -> state.entries.firstOrNull { it.id == id } }
            when {
                showManage -> ManageScreen(
                    state = state,
                    onRetry = companion::retry,
                    onErase = {
                        companion.erase()
                        showManage = false
                        selectedEntryId = null
                    },
                    onBack = { showManage = false },
                )
                selectedEntry != null -> CodeScreen(
                    entry = selectedEntry,
                    seconds = seconds,
                    onBack = { selectedEntryId = null },
                )
                else -> AccountsScreen(
                    state = state,
                    seconds = seconds,
                    availableTags = availableTags,
                    selectedTag = selectedTag,
                    onSelectTag = { selectedTag = it },
                    onSelectEntry = { selectedEntryId = it },
                    onEnroll = companion::enroll,
                    onUnlock = onRequestUnlock,
                    onOpenManage = { showManage = true },
                )
            }
        }
    }
}

@Composable
private fun AccountsScreen(
    state: WatchCompanion.State,
    seconds: Long,
    availableTags: List<String>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    onSelectEntry: (String) -> Unit,
    onEnroll: () -> Unit,
    onUnlock: () -> Unit,
    onOpenManage: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val statusText = remember(state.message, state.entries) {
        if (state.message == WatchCompanion.Message.offline && state.entries.isNotEmpty()) null
        else statusStringRes(state.message)
    }?.let { stringResource(it) }.orEmpty()
    val filteredEntries = remember(state.entries, selectedTag) {
        if (selectedTag == null) state.entries
        else state.entries.filter { selectedTag in it.tags }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            if (statusText.isNotEmpty()) {
                item(key = "status") {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = WatchStyle.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (state.message == WatchCompanion.Message.unlockWatch) {
                item(key = "unlock") {
                    Button(
                        onClick = onUnlock,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    ) {
                        Text(stringResource(R.string.unlock))
                    }
                }
            }
            if (availableTags.size >= 2) {
                item(key = "tag-filter") {
                    TagFilterBar(
                        tags = availableTags,
                        selectedTag = selectedTag,
                        onSelectTag = onSelectTag,
                    )
                }
            }
            items(filteredEntries, key = { it.id }) { entry ->
                AccountCard(
                    entry = entry,
                    seconds = seconds,
                    hideCodes = state.hideCodes,
                    onClick = { onSelectEntry(entry.id) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                )
            }
            state.updatedAt?.let { updatedAt ->
                item(key = "updated") {
                    Text(
                        text = updatedLabel(updatedAt, (seconds / 60) * 60_000L),
                        style = MaterialTheme.typography.labelSmall,
                        color = WatchStyle.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            if (state.canEnroll) {
                item(key = "enroll") {
                    Button(
                        onClick = onEnroll,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    ) {
                        Text(stringResource(R.string.setup))
                    }
                }
            }
            if (state.canErase) {
                item(key = "manage") {
                    Button(
                        onClick = onOpenManage,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    ) {
                        Text(stringResource(R.string.manage))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagFilterBar(
    tags: List<String>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        TagChipButton(
            label = stringResource(R.string.all_tags),
            selected = selectedTag == null,
            onClick = { onSelectTag(null) },
        )
        for (tag in tags) {
            TagChipButton(
                label = tag,
                selected = selectedTag == tag,
                onClick = { onSelectTag(if (selectedTag == tag) null else tag) },
            )
        }
    }
}

@Composable
private fun TagChipButton(label: String, selected: Boolean, onClick: () -> Unit) {
    CompactButton(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) WatchStyle.accent else WatchStyle.surface,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountCard(
    entry: Entry,
    seconds: Long,
    hideCodes: Boolean,
    onClick: () -> Unit,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
) {
    val label = listOf(entry.issuer, entry.account).filter { it.isNotEmpty() }.joinToString(", ")
    val codeValue = if (hideCodes) null else entry.code(seconds)
    val remaining = if (hideCodes) null else (entry.period - seconds % entry.period).toInt()
    val spokenDesc = codeValue?.let {
        "$label, ${stringResource(R.string.code_spoken, it.toCharArray().joinToString(" "))}"
    } ?: label

    Card(
        onClick = onClick,
        transformation = transformation,
        colors = CardDefaults.cardColors(
            containerColor = WatchStyle.surface,
            contentColor = Color.White,
        ),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = spokenDesc
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = entry.issuer.ifEmpty { entry.account },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (entry.pinned) {
                val pinnedDesc = stringResource(R.string.pinned)
                Canvas(
                    modifier = Modifier.size(6.dp).semantics { contentDescription = pinnedDesc },
                ) {
                    drawCircle(color = WatchStyle.accent)
                }
            }
        }
        if (entry.issuer.isNotEmpty() && entry.account.isNotEmpty()) {
            Text(
                text = entry.account,
                style = MaterialTheme.typography.bodySmall,
                color = WatchStyle.secondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        if (codeValue != null && remaining != null) {
            val isWarning = remaining <= 5
            Spacer(modifier = Modifier.height(4.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = formatCode(codeValue),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (isWarning) WatchStyle.warning else Color.White,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { remaining.toFloat() / entry.period.toFloat() },
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = if (isWarning) WatchStyle.warning else WatchStyle.accent,
                    trackColor = WatchStyle.track,
                ),
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun CodeScreen(entry: Entry, seconds: Long, onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val codeValue = entry.code(seconds)
    val nextCodeValue = entry.code(seconds + entry.period)
    val formatted = formatCode(codeValue)
    val formattedNext = formatCode(nextCodeValue)
    val spoken = stringResource(R.string.code_spoken, codeValue.toCharArray().joinToString(" "))
    val spokenNext = stringResource(R.string.next_code_spoken, nextCodeValue.toCharArray().joinToString(" "))
    val remaining = (entry.period - seconds % entry.period).toInt()
    val isWarning = remaining <= 5
    val codeFontSize = when {
        formatted.length >= 10 -> 24.sp
        formatted.length >= 8 -> 28.sp
        else -> 34.sp
    }

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = entry.issuer.ifEmpty { stringResource(R.string.code) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (entry.account.isNotEmpty()) {
                Text(
                    text = entry.account,
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchStyle.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    textAlign = TextAlign.Center,
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = formatted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = codeFontSize,
                    color = if (isWarning) WatchStyle.warning else Color.White,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
            LinearProgressIndicator(
                progress = { remaining.toFloat() / entry.period.toFloat() },
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = if (isWarning) WatchStyle.warning else WatchStyle.accent,
                    trackColor = WatchStyle.track,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clearAndSetSemantics {},
            )
            Text(
                text = pluralStringResource(R.plurals.seconds_remaining, remaining, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = WatchStyle.secondary,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = stringResource(R.string.next_code, formattedNext),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = WatchStyle.secondary,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = spokenNext },
                )
            }
            val fullIdentity = listOf(entry.issuer, entry.account).filter { it.isNotEmpty() }.joinToString("\n")
            if (fullIdentity.isNotEmpty()) {
                Text(
                    text = fullIdentity,
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchStyle.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics {},
                )
            }
            Button(
                onClick = onBack,
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
private fun ManageScreen(
    state: WatchCompanion.State,
    onRetry: () -> Unit,
    onErase: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    var confirmErase by remember { mutableStateOf(false) }
    val helpText = statusStringRes(state.message)?.let { stringResource(it) }
        ?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.sync_help)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "manage-title") {
                Text(
                    text = stringResource(R.string.manage),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "manage-help") {
                Text(
                    text = helpText,
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchStyle.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item(key = "manage-sync") {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.sync))
                }
            }
            if (state.canErase) {
                item(key = "manage-erase") {
                    Button(
                        onClick = { confirmErase = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = WatchStyle.destructive,
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    ) {
                        Text(stringResource(R.string.erase))
                    }
                }
            }
            item(key = "manage-back") {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.back))
                }
            }
        }
    }

    val eraseActionLabel = stringResource(R.string.erase_action)
    val cancelLabel = stringResource(R.string.cancel)
    AlertDialog(
        visible = confirmErase,
        onDismissRequest = { confirmErase = false },
        title = { Text(stringResource(R.string.erase_title)) },
        text = { Text(stringResource(R.string.erase_description)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    confirmErase = false
                    onErase()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = WatchStyle.destructive,
                    contentColor = Color.White,
                ),
                modifier = Modifier.semantics { contentDescription = eraseActionLabel },
            )
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(
                onClick = { confirmErase = false },
                modifier = Modifier.semantics { contentDescription = cancelLabel },
            )
        },
    )
}

@Composable
private fun updatedLabel(updatedAt: Long, nowMs: Long): String {
    val now = maxOf(nowMs, System.currentTimeMillis())
    return if (now - updatedAt < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.updated_just_now)
    } else {
        stringResource(
            R.string.updated,
            DateUtils.getRelativeTimeSpanString(updatedAt, now, DateUtils.MINUTE_IN_MILLIS),
        )
    }
}

private fun statusStringRes(message: WatchCompanion.Message): Int? = when (message) {
    WatchCompanion.Message.setup -> R.string.setup_instructions
    WatchCompanion.Message.approvePhone -> R.string.approve_phone
    WatchCompanion.Message.selectAccounts -> R.string.select_accounts
    WatchCompanion.Message.offline -> R.string.offline
    WatchCompanion.Message.unlockWatch -> R.string.unlock_watch
    WatchCompanion.Message.storageFailed -> R.string.storage_failed
    WatchCompanion.Message.secureStorageUnavailable -> R.string.secure_storage_unavailable
    WatchCompanion.Message.setupFailed -> R.string.setup_failed
    WatchCompanion.Message.syncFailed -> R.string.sync_failed
    WatchCompanion.Message.connectPhone -> R.string.connect_phone
    WatchCompanion.Message.ready, WatchCompanion.Message.hidden -> null
}
