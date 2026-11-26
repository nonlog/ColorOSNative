# ColorOS Native

轻量级 ColorOS 17 LSPosed (libxposed API 102) 模块，整合以下两大核心能力：

1. **系统界面 · 原生**（移植自 [ONextBox](https://github.com/MiToverG422/ONextBox)）：
   - 原生通知图标
   - 原生通知气泡
   - 原生关机菜单 (`GlobalActionsDialogLite`)
   - 原生音量面板（含缩放比例调节与控制中心隐藏）
   - 原生实时通知胶囊
   - **原生双卡堆叠信号（ColorOS 17 修复版）**
   - 原生剪贴板浮窗
   - 原生充电特效 & 原生充电提示音
2. **系统字体替换**（移植自 [coloros-mod](https://github.com/rikumi/coloros-mod)）：
   - 停用 ColorOS OEM 字体替换与假粗体映射
   - 全局中文 / 英文 / 等宽 / Emoji 字体替换（内置预设 + 自定义 `.ttf/.otf/.ttc` 导入）
   - 底层挂载脚本 + LSPosed Native Hook (`libcoloros_font_bridge.so`)

## 双卡堆叠信号修复说明

在 ColorOS 17 的部分默认双卡并排机型（如 OnePlus 12 `CPH2573`）上，`SystemUI` 的 `MobileIconsViewModel.isStackable` 依赖 `OplusMobileIconsInteractorExImpl.isStackableMode()`，其底层读取 `Settings.System` 中的 `signal_icon_display_mode`（默认由 `StatusBarFeatureOption.isSignalMobileIconDefaultParallel()` 决定为 `0` 并排模式）。

原版 `ONextBox` 仅 Hook 了 `OplusStackedMobileIconLayoutStrategy.SignalIcon-*` 的绘制分支，未接管 `isStackableMode()` 开关，导致并排模式下永远不会进入堆叠信号渲染路径。

本模块在 `C16StackedSignalHooker` 中补充了对 `OplusMobileIconsInteractorExImpl.isStackableMode()`（返回响应式 `MutableStateFlow<Boolean>`）以及 `StatusBarFeatureOption.isSignalMobileIconDefaultParallel()` 的 Hook，开启开关后即可直接切换为原生 AOSP/C16 双排堆叠信号样式。

## 推荐作用域

- `system`（系统框架）
- `com.android.systemui`（系统界面）
- `com.android.settings`（设置）
