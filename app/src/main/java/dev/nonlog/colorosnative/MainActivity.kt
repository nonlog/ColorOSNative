package dev.nonlog.colorosnative

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nonlog.colorosnative.font.SystemFontHooks
import dev.nonlog.colorosnative.font.SystemFontStore
import dev.nonlog.colorosnative.font.setBool
import dev.nonlog.colorosnative.font.settingsPrefs
import dev.nonlog.colorosnative.xposed.AppPrefStore
import dev.nonlog.colorosnative.xposed.PrefKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val SLOT_ZH_LATIN = "zh_latin_combined"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppPrefStore.register(this) { service ->
            SystemFontStore.enqueueSync { SystemFontStore.sync(applicationContext, service) }
        }
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                ColorOSNativeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorOSNativeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val connected by AppPrefStore.connected.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun triggerSoftReboot() {
        SystemFontStore.whenReady(
            context,
            action = { runRootCommand(context, "setprop ctl.restart zygote", "正在软重启 Zygote…") },
            onFailure = { err ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "字体准备失败: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    fun showRebootHint(message: String, restartZygote: Boolean) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val res = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (restartZygote) "软重启" else "重启界面",
                duration = SnackbarDuration.Long,
            )
            if (res == SnackbarResult.ActionPerformed) {
                if (restartZygote) triggerSoftReboot()
                else runRootCommand(context, "killall com.android.systemui", "已重启 SystemUI")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_full),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ColorOS Native",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "ColorOS 17 · v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "重启作用域")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            text = { Text("重启系统界面 (SystemUI)") },
                            onClick = {
                                menuExpanded = false
                                runRootCommand(context, "killall com.android.systemui", "已重启 SystemUI")
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                            text = { Text("软重启 Zygote (应用字体)") },
                            onClick = {
                                menuExpanded = false
                                triggerSoftReboot()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(if (tab == 0) Icons.Rounded.Tune else Icons.Outlined.Tune, contentDescription = null) },
                    label = { Text("原生界面") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(if (tab == 1) Icons.Rounded.FontDownload else Icons.Outlined.FontDownload, contentDescription = null) },
                    label = { Text("系统字体") }
                )
            }
        }
    ) { padding ->
        if (tab == 0) {
            NativeUiTab(
                padding = padding,
                connected = connected,
                onRestartSystemUi = { runRootCommand(context, "killall com.android.systemui", "已重启 SystemUI") },
                onSoftReboot = ::triggerSoftReboot,
            )
        } else {
            SystemFontsTab(
                padding = padding,
                connected = connected,
                onRestartSystemUi = { runRootCommand(context, "killall com.android.systemui", "已重启 SystemUI") },
                onSoftReboot = ::triggerSoftReboot,
                onFontApplied = { msg -> showRebootHint(msg, restartZygote = true) },
            )
        }
    }
}

@Composable
private fun StatusHeroCard(
    connected: Boolean,
    onRestartSystemUi: () -> Unit,
    onSoftReboot: () -> Unit,
) {
    val container = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (connected) "LSPosed 模块已激活" else "尚未连接 LSPosed 服务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (connected) "修改配置后可点击下方按钮快速重启生效"
                        else "请在 LSPosed 管理器中启用模块并勾选系统框架、系统界面与设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.82f),
                    )
                }
            }
            if (connected) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onRestartSystemUi,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = content.copy(alpha = 0.14f),
                            contentColor = content,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重启系统界面", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = onSoftReboot,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = content.copy(alpha = 0.14f),
                            contentColor = content,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("软重启 Zygote", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeUiTab(
    padding: PaddingValues,
    connected: Boolean,
    onRestartSystemUi: () -> Unit,
    onSoftReboot: () -> Unit,
) {
    val context = LocalContext.current
    var notifyIcon by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.NATIVE_NOTIFY_ICON)) }
    var bubbles by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.NATIVE_NOTIFICATION_BUBBLES)) }
    var powerMenu by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.NATIVE_POWER_MENU)) }
    var volume by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.AOSP_VOLUME)) }
    var volumeScale by remember { mutableIntStateOf(AppPrefStore.getInt(context, PrefKeys.AOSP_VOLUME_SCALE, 100).coerceIn(60, 100)) }
    var volumeHideQs by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.AOSP_VOLUME_HIDE_IN_QS)) }
    var liveUpdate by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.NATIVE_LIVE_UPDATE)) }
    var stackedSignal by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.C16_STACKED_SIGNAL)) }
    var clipboard by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.FORCE_NATIVE_CLIPBOARD)) }
    var chargingEffect by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.AOSP_CHARGING_EFFECT)) }
    var chargingSound by remember { mutableStateOf(AppPrefStore.getBool(context, PrefKeys.AOSP_CHARGING_SOUND)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatusHeroCard(connected = connected, onRestartSystemUi = onRestartSystemUi, onSoftReboot = onSoftReboot)
        }
        item {
            SectionLabel("状态栏与通知")
            GroupedCard {
                ToggleRow(
                    icon = Icons.Outlined.SignalCellularAlt,
                    title = "原生双卡堆叠信号",
                    badge = "C17 已修复",
                    summary = "强制启用堆叠模式并替换为原生 AOSP/C16 双排信号绘制",
                    checked = stackedSignal,
                    onCheckedChange = { stackedSignal = it; AppPrefStore.setBool(context, PrefKeys.C16_STACKED_SIGNAL, it) }
                )
                InsetDivider()
                ToggleRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "原生实时通知胶囊",
                    summary = "启用原生实时通知胶囊（与流体云互斥）",
                    checked = liveUpdate,
                    onCheckedChange = { liveUpdate = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_LIVE_UPDATE, it) }
                )
                InsetDivider()
                ToggleRow(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "原生通知图标",
                    summary = "使用原生通知图标样式，部分 App 需适配并重启系统",
                    checked = notifyIcon,
                    onCheckedChange = { notifyIcon = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_NOTIFY_ICON, it) }
                )
            }
        }
        item {
            SectionLabel("系统交互面板")
            GroupedCard {
                ToggleRow(
                    icon = Icons.Outlined.VolumeUp,
                    title = "原生音量面板",
                    summary = "替换为原生 Android 样式音量调节面板",
                    checked = volume,
                    onCheckedChange = { volume = it; AppPrefStore.setBool(context, PrefKeys.AOSP_VOLUME, it) }
                )
                AnimatedVisibility(visible = volume) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("音量面板缩放", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = "$volumeScale%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Slider(
                            value = volumeScale.toFloat(),
                            onValueChange = { volumeScale = it.roundToInt() },
                            onValueChangeFinished = { AppPrefStore.setInt(context, PrefKeys.AOSP_VOLUME_SCALE, volumeScale) },
                            valueRange = 60f..100f,
                            steps = 7,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    volumeHideQs = !volumeHideQs
                                    AppPrefStore.setBool(context, PrefKeys.AOSP_VOLUME_HIDE_IN_QS, volumeHideQs)
                                }
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("控制中心内隐藏原生音量条", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "展开快捷设置时自动隐藏悬浮音量条",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = volumeHideQs,
                                onCheckedChange = { volumeHideQs = it; AppPrefStore.setBool(context, PrefKeys.AOSP_VOLUME_HIDE_IN_QS, it) }
                            )
                        }
                    }
                }
                InsetDivider()
                ToggleRow(
                    icon = Icons.Outlined.PowerSettingsNew,
                    title = "原生关机菜单",
                    summary = "使用原生样式电源菜单 (GlobalActionsDialogLite)",
                    checked = powerMenu,
                    onCheckedChange = { powerMenu = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_POWER_MENU, it) }
                )
                InsetDivider()
                ToggleRow(
                    icon = Icons.Outlined.ContentPaste,
                    title = "原生剪贴板",
                    summary = "强制启用左下角原生 Android 剪贴板预览浮窗",
                    checked = clipboard,
                    onCheckedChange = { clipboard = it; AppPrefStore.setBool(context, PrefKeys.FORCE_NATIVE_CLIPBOARD, it) }
                )
                InsetDivider()
                ToggleRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "原生通知气泡",
                    summary = "在框架层强制放行安卓对话气泡（需重启系统）",
                    checked = bubbles,
                    onCheckedChange = { bubbles = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_NOTIFICATION_BUBBLES, it) }
                )
            }
        }
        item {
            SectionLabel("充电反馈")
            GroupedCard {
                ToggleRow(
                    icon = Icons.Outlined.BatteryChargingFull,
                    title = "原生充电特效",
                    summary = "使用原生水波纹充电特效 (WiredChargingRippleController)",
                    checked = chargingEffect,
                    onCheckedChange = { chargingEffect = it; AppPrefStore.setBool(context, PrefKeys.AOSP_CHARGING_EFFECT, it) }
                )
                InsetDivider()
                ToggleRow(
                    icon = Icons.Outlined.GraphicEq,
                    title = "原生充电提示音",
                    summary = "替换为原生 AOSP 充电提示音效",
                    checked = chargingSound,
                    onCheckedChange = { chargingSound = it; AppPrefStore.setBool(context, PrefKeys.AOSP_CHARGING_SOUND, it) }
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemFontsTab(
    padding: PaddingValues,
    connected: Boolean,
    onRestartSystemUi: () -> Unit,
    onSoftReboot: () -> Unit,
    onFontApplied: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.settingsPrefs() }

    var disableReplacement by remember { mutableStateOf(prefs.getBoolean(SystemFontHooks.DISABLE_REPLACEMENT, false)) }
    var zhChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.ZH, 0)) }
    var latinChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.LATIN, 0)) }
    var monoChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.MONO, -1)) }
    var emojiChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.EMOJI, SystemFontHooks.DEFAULT_EMOJI)) }

    var zhCustomLabel by remember { mutableStateOf(SystemFontStore.customLabel(context, SystemFontHooks.ZH)) }
    var latinCustomLabel by remember { mutableStateOf(SystemFontStore.customLabel(context, SystemFontHooks.LATIN)) }
    var monoCustomLabel by remember { mutableStateOf(SystemFontStore.customLabel(context, SystemFontHooks.MONO)) }
    var emojiCustomLabel by remember { mutableStateOf(SystemFontStore.customLabel(context, SystemFontHooks.EMOJI)) }

    var activeSlotDialog by remember { mutableStateOf<String?>(null) }
    var pendingImportSlot by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<SystemFontStore.Progress?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val slot = pendingImportSlot ?: return@rememberLauncherForActivityResult
        pendingImportSlot = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = runCatching {
                withContext(Dispatchers.IO) {
                    SystemFontStore.enqueue {
                        if (slot == SLOT_ZH_LATIN) {
                            SystemFontStore.importZhLatin(context, uri) { p -> progress = p }
                        } else {
                            SystemFontStore.import(context, slot, uri) { p -> progress = p }
                        }
                    }.await()
                }
            }
            progress = null
            res.onSuccess {
                if (slot == SLOT_ZH_LATIN) {
                    zhChoice = SystemFontStore.customIndex(SystemFontHooks.ZH)
                    latinChoice = SystemFontStore.customIndex(SystemFontHooks.LATIN)
                    zhCustomLabel = SystemFontStore.customLabel(context, SystemFontHooks.ZH)
                    latinCustomLabel = SystemFontStore.customLabel(context, SystemFontHooks.LATIN)
                    onFontApplied("中英文字体已同时替换，软重启 Zygote 后生效")
                } else {
                    val customIdx = SystemFontStore.customIndex(slot)
                    when (slot) {
                        SystemFontHooks.ZH -> {
                            zhChoice = customIdx
                            zhCustomLabel = SystemFontStore.customLabel(context, slot)
                        }
                        SystemFontHooks.LATIN -> {
                            latinChoice = customIdx
                            latinCustomLabel = SystemFontStore.customLabel(context, slot)
                        }
                        SystemFontHooks.MONO -> {
                            monoChoice = customIdx
                            monoCustomLabel = SystemFontStore.customLabel(context, slot)
                        }
                        SystemFontHooks.EMOJI -> {
                            emojiChoice = customIdx
                            emojiCustomLabel = SystemFontStore.customLabel(context, slot)
                        }
                    }
                    onFontApplied("字体导入成功，软重启 Zygote 后生效")
                }
            }.onFailure {
                Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchPicker(slot: String) {
        pendingImportSlot = slot
        picker.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
    }

    fun applySelection(slot: String, value: Int) {
        if (value == SystemFontStore.customIndex(slot)) {
            launchPicker(slot)
            return
        }
        scope.launch {
            val res = runCatching {
                withContext(Dispatchers.IO) {
                    SystemFontStore.enqueue {
                        SystemFontStore.select(context, slot, value) { p -> progress = p }
                    }.await()
                }
            }
            progress = null
            res.onSuccess {
                when (slot) {
                    SystemFontHooks.ZH -> zhChoice = value
                    SystemFontHooks.LATIN -> latinChoice = value
                    SystemFontHooks.MONO -> monoChoice = value
                    SystemFontHooks.EMOJI -> emojiChoice = value
                }
                onFontApplied("已更新字体设置，软重启 Zygote 后生效")
            }.onFailure {
                Toast.makeText(context, "设置失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val zhOptions = listOf(0 to "OPPO Sans (默认)", 1 to "系统思源黑体 (Noto CJK)", 2 to "自定义导入 (.ttf/.otf/.ttc)")
    val latinOptions = listOf(0 to "OPPO Sans (默认)", 1 to "Inter", 2 to "Manrope", 4 to "Figtree", 3 to "自定义导入 (.ttf/.otf/.ttc)")
    val monoOptions = listOf(-1 to "系统默认", 0 to "Maple Mono", 1 to "JetBrains Mono", 2 to "Fira Code", 3 to "Cascadia Code", 4 to "自定义导入 (.ttf/.otf/.ttc)")
    val emojiOptions = listOf(0 to "系统默认", 1 to "Apple Emoji", 2 to "Twemoji", 3 to "Noto Color Emoji", 4 to "Fluent Emoji 3D", SystemFontHooks.CUSTOM_EMOJI to "自定义导入 (.ttf)")

    fun formatCurrent(slot: String, choice: Int, options: List<Pair<Int, String>>, customLabel: String?): String {
        if (choice == SystemFontStore.customIndex(slot)) {
            return if (!customLabel.isNullOrBlank()) "自定义 · $customLabel" else "自定义字体"
        }
        return options.firstOrNull { it.first == choice }?.second ?: "自定义字体"
    }

    val isUnifiedCustomActive =
        zhChoice == SystemFontStore.customIndex(SystemFontHooks.ZH) &&
            latinChoice == SystemFontStore.customIndex(SystemFontHooks.LATIN) &&
            !zhCustomLabel.isNullOrBlank() &&
            zhCustomLabel == latinCustomLabel

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatusHeroCard(connected = connected, onRestartSystemUi = onRestartSystemUi, onSoftReboot = onSoftReboot)
        }
        item {
            SectionLabel("中英文统一替换")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "中英文同时替换（单文件导入）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "适用于 PingFangOne VF 等全字库字体，一次导入同时接管中文与英文（最大 256 MB）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                    if (isUnifiedCustomActive) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.10f),
                        ) {
                            Text(
                                text = "当前统一字体：$zhCustomLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { launchPicker(SLOT_ZH_LATIN) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("选择字体文件，中英文同时生效")
                    }
                }
            }
        }
        item {
            SectionLabel("分项字体配置")
            GroupedCard {
                FontRow(
                    icon = Icons.Outlined.Translate,
                    title = "中文字体 (CJK)",
                    current = formatCurrent(SystemFontHooks.ZH, zhChoice, zhOptions, zhCustomLabel),
                    onClick = { activeSlotDialog = SystemFontHooks.ZH },
                )
                InsetDivider()
                FontRow(
                    icon = Icons.Outlined.TextFields,
                    title = "英文字体 (Sans-Serif)",
                    current = formatCurrent(SystemFontHooks.LATIN, latinChoice, latinOptions, latinCustomLabel),
                    onClick = { activeSlotDialog = SystemFontHooks.LATIN },
                )
                InsetDivider()
                FontRow(
                    icon = Icons.Outlined.Code,
                    title = "等宽字体 (Monospace)",
                    current = formatCurrent(SystemFontHooks.MONO, monoChoice, monoOptions, monoCustomLabel),
                    onClick = { activeSlotDialog = SystemFontHooks.MONO },
                )
                InsetDivider()
                FontRow(
                    icon = Icons.Outlined.EmojiEmotions,
                    title = "Emoji 字体",
                    current = formatCurrent(SystemFontHooks.EMOJI, emojiChoice, emojiOptions, emojiCustomLabel),
                    onClick = { activeSlotDialog = SystemFontHooks.EMOJI },
                )
            }
        }
        item {
            SectionLabel("ColorOS 字体拦截")
            GroupedCard {
                ToggleRow(
                    icon = Icons.Outlined.Block,
                    title = "停用 ColorOS OEM 字体替换",
                    summary = "阻止 ColorOS 强制接管系统字体与假粗体映射，保留原生字体回退链",
                    checked = disableReplacement,
                    onCheckedChange = {
                        disableReplacement = it
                        setBool(context, SystemFontHooks.DISABLE_REPLACEMENT, it)
                        scope.launch(Dispatchers.IO) {
                            runCatching { SystemFontStore.syncReplacementSettings(context) }
                        }
                    }
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    activeSlotDialog?.let { slot ->
        val (title, options, current) = when (slot) {
            SystemFontHooks.ZH -> Triple("选择中文字体", zhOptions, zhChoice)
            SystemFontHooks.LATIN -> Triple("选择英文字体", latinOptions, latinChoice)
            SystemFontHooks.MONO -> Triple("选择等宽字体", monoOptions, monoChoice)
            else -> Triple("选择 Emoji 字体", emojiOptions, emojiChoice)
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activeSlotDialog = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                options.forEach { (id, label) ->
                    val selected = current == id
                    val rowBg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                    val rowFg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(rowBg)
                            .clickable {
                                activeSlotDialog = null
                                applySelection(slot, id)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = rowFg,
                        )
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = rowFg, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(p.stage), fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    if (p.total > 0) {
                        val fraction = (p.completed.toFloat() / p.total.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.1f MB / %.1f MB (%.0f%%)",
                                p.completed / 1048576.0,
                                p.total / 1048576.0,
                                fraction * 100f,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                    }
                    p.detail?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun LeadingBadge(icon: ImageVector, highlighted: Boolean = false) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    badge: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingBadge(icon = icon, highlighted = checked)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FontRow(
    icon: ImageVector,
    title: String,
    current: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingBadge(icon = icon, highlighted = true)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = current,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun runRootCommand(context: Context, cmd: String, okMsg: String) {
    Thread {
        val ok = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor() == 0
        }.getOrDefault(false)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, if (ok) okMsg else "Root 权限执行失败", Toast.LENGTH_SHORT).show()
        }
    }.start()
}
