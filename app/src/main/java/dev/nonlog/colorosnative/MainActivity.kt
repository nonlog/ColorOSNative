package dev.nonlog.colorosnative

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt

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
                if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
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
    val connected by AppPrefStore.connected.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ColorOS Native", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (connected) "LSPosed 服务已连接" else "未连接 LSPosed（请在管理器中勾选作用域）",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重启作用域")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("重启系统界面 (SystemUI)") },
                            onClick = {
                                menuExpanded = false
                                runRootCommand(context, "killall com.android.systemui", "已重启 SystemUI")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("软重启 (重启 Zygote，应用字体)") },
                            onClick = {
                                menuExpanded = false
                                SystemFontStore.whenReady(
                                    context,
                                    action = { runRootCommand(context, "setprop ctl.restart zygote", "正在重启 Zygote") },
                                    onFailure = { err ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            Toast.makeText(context, "字体准备失败: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    label = { Text("原生界面") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.FontDownload, contentDescription = null) },
                    label = { Text("系统字体") }
                )
            }
        }
    ) { padding ->
        if (tab == 0) {
            NativeUiTab(padding)
        } else {
            SystemFontsTab(padding)
        }
    }
}

@Composable
private fun NativeUiTab(padding: PaddingValues) {
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionLabel("系统界面 · 原生")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    ToggleRow(
                        title = "原生通知图标",
                        summary = "启用原生图标样式，此功能并不能完全覆盖，需要 App 适配，需要重启系统",
                        checked = notifyIcon,
                        onCheckedChange = { notifyIcon = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_NOTIFY_ICON, it) }
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "原生通知气泡",
                        summary = "在框架层强制放行安卓对话气泡，需要重启系统",
                        checked = bubbles,
                        onCheckedChange = { bubbles = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_NOTIFICATION_BUBBLES, it) }
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "原生关机菜单",
                        summary = "使用原生样式的关机菜单 (GlobalActionsDialogLite)",
                        checked = powerMenu,
                        onCheckedChange = { powerMenu = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_POWER_MENU, it) }
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "原生音量面板",
                        summary = "使用原生样式的音量面板",
                        checked = volume,
                        onCheckedChange = { volume = it; AppPrefStore.setBool(context, PrefKeys.AOSP_VOLUME, it) }
                    )
                    if (volume) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("音量面板缩放: $volumeScale%", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = volumeScale.toFloat(),
                                onValueChange = { volumeScale = it.roundToInt() },
                                onValueChangeFinished = { AppPrefStore.setInt(context, PrefKeys.AOSP_VOLUME_SCALE, volumeScale) },
                                valueRange = 60f..100f,
                                steps = 7,
                            )
                        }
                        ToggleRow(
                            title = "控制中心内隐藏原生音量条",
                            summary = "展开快捷设置时自动隐藏悬浮音量条",
                            checked = volumeHideQs,
                            onCheckedChange = { volumeHideQs = it; AppPrefStore.setBool(context, PrefKeys.AOSP_VOLUME_HIDE_IN_QS, it) }
                        )
                    }
                    HorizontalDivider()
                    ToggleRow(
                        title = "原生实时通知胶囊",
                        summary = "使用原生实时通知胶囊。开启后流体云将不再显示，两者无法同时使用",
                        checked = liveUpdate,
                        onCheckedChange = { liveUpdate = it; AppPrefStore.setBool(context, PrefKeys.NATIVE_LIVE_UPDATE, it) }
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "原生双卡堆叠信号（ColorOS 17 修复版）",
                        summary = "强制启用堆叠模式并替换为原生 AOSP/C16 双排信号绘制，修复默认并排机型不生效问题",
                        checked = stackedSignal,
                        onCheckedChange = { stackedSignal = it; AppPrefStore.setBool(context, PrefKeys.C16_STACKED_SIGNAL, it) }
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "原生剪贴板",
                        summary = "强制启用左下角原生剪贴板浮窗",
                        checked = clipboard,
                        onCheckedChange = { clipboard = it; AppPrefStore.setBool(context, PrefKeys.FORCE_NATIVE_CLIPBOARD, it) }
                    )
                }
            }
        }
        item {
            SectionLabel("充电反馈")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    ToggleRow(
                        title = "原生充电特效",
                        summary = "使用原生水波纹充电特效 (WiredChargingRippleController)",
                        checked = chargingEffect,
                        onCheckedChange = { chargingEffect = it; AppPrefStore.setBool(context, PrefKeys.AOSP_CHARGING_EFFECT, it) }
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "原生充电提示音",
                        summary = "替换为原生 AOSP 充电提示音",
                        checked = chargingSound,
                        onCheckedChange = { chargingSound = it; AppPrefStore.setBool(context, PrefKeys.AOSP_CHARGING_SOUND, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemFontsTab(padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.settingsPrefs() }

    var disableReplacement by remember { mutableStateOf(prefs.getBoolean(SystemFontHooks.DISABLE_REPLACEMENT, false)) }
    var zhChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.ZH, 0)) }
    var latinChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.LATIN, 0)) }
    var monoChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.MONO, -1)) }
    var emojiChoice by remember { mutableIntStateOf(prefs.getInt(SystemFontHooks.EMOJI, SystemFontHooks.DEFAULT_EMOJI)) }

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
                        SystemFontStore.import(context, slot, uri) { p -> progress = p }
                    }.await()
                }
            }
            progress = null
            res.onSuccess {
                val customIdx = SystemFontStore.customIndex(slot)
                when (slot) {
                    SystemFontHooks.ZH -> zhChoice = customIdx
                    SystemFontHooks.LATIN -> latinChoice = customIdx
                    SystemFontHooks.MONO -> monoChoice = customIdx
                    SystemFontHooks.EMOJI -> emojiChoice = customIdx
                }
                Toast.makeText(context, "字体导入成功，请软重启 Zygote 生效", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun applySelection(slot: String, value: Int) {
        if (value == SystemFontStore.customIndex(slot)) {
            pendingImportSlot = slot
            picker.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
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
                Toast.makeText(context, "已更新字体设置，软重启 Zygote 后生效", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "设置失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val zhOptions = listOf(0 to "OPPO Sans (默认)", 1 to "系统思源黑体 (Noto CJK)", 2 to "自定义导入 (.ttf/.otf/.ttc)")
    val latinOptions = listOf(0 to "OPPO Sans (默认)", 1 to "Inter", 2 to "Manrope", 4 to "Figtree", 3 to "自定义导入 (.ttf/.otf/.ttc)")
    val monoOptions = listOf(-1 to "系统默认", 0 to "Maple Mono", 1 to "JetBrains Mono", 2 to "Fira Code", 3 to "Cascadia Code", 4 to "自定义导入 (.ttf/.otf/.ttc)")
    val emojiOptions = listOf(0 to "系统默认", 1 to "Apple Emoji", 2 to "Twemoji", 3 to "Noto Color Emoji", 4 to "Fluent Emoji 3D", SystemFontHooks.CUSTOM_EMOJI to "自定义导入 (.ttf)")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionLabel("ColorOS 字体拦截")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ToggleRow(
                    title = "停用 ColorOS OEM 字体替换",
                    summary = "阻止 ColorOS 强制接管系统字体与假粗体映射，保留原生 Android 字体回退链",
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
        item {
            SectionLabel("全局系统字体替换 (移植自 coloros-mod)")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    FontRow("中文字体 (CJK)", zhOptions.firstOrNull { it.first == zhChoice }?.second ?: "自定义") {
                        activeSlotDialog = SystemFontHooks.ZH
                    }
                    HorizontalDivider()
                    FontRow("英文字体 (Sans-Serif)", latinOptions.firstOrNull { it.first == latinChoice }?.second ?: "自定义") {
                        activeSlotDialog = SystemFontHooks.LATIN
                    }
                    HorizontalDivider()
                    FontRow("等宽字体 (Monospace)", monoOptions.firstOrNull { it.first == monoChoice }?.second ?: "自定义") {
                        activeSlotDialog = SystemFontHooks.MONO
                    }
                    HorizontalDivider()
                    FontRow("Emoji 字体", emojiOptions.firstOrNull { it.first == emojiChoice }?.second ?: "自定义") {
                        activeSlotDialog = SystemFontHooks.EMOJI
                    }
                }
            }
        }
    }

    activeSlotDialog?.let { slot ->
        val (title, options, current) = when (slot) {
            SystemFontHooks.ZH -> Triple("选择中文字体", zhOptions, zhChoice)
            SystemFontHooks.LATIN -> Triple("选择英文字体", latinOptions, latinChoice)
            SystemFontHooks.MONO -> Triple("选择等宽字体", monoOptions, monoChoice)
            else -> Triple("选择 Emoji 字体", emojiOptions, emojiChoice)
        }
        AlertDialog(
            onDismissRequest = { activeSlotDialog = null },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                activeSlotDialog = null
                                applySelection(slot, id)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = current == id, onClick = {
                                activeSlotDialog = null
                                applySelection(slot, id)
                            })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeSlotDialog = null }) { Text("取消") }
            }
        )
    }

    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(p.stage)) },
            text = {
                Column {
                    if (p.total > 0) {
                        LinearProgressIndicator(
                            progress = { (p.completed.toFloat() / p.total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    p.detail?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun ToggleRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FontRow(title: String, current: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun runRootCommand(context: android.content.Context, cmd: String, okMsg: String) {
    Thread {
        val ok = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor() == 0
        }.getOrDefault(false)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, if (ok) okMsg else "Root 权限执行失败", Toast.LENGTH_SHORT).show()
        }
    }.start()
}
