package dev.nonlog.colorosnative.xposed

import dev.nonlog.colorosnative.font.NativeFontHooks
import dev.nonlog.colorosnative.font.SystemFontDistributionHooks
import dev.nonlog.colorosnative.font.SystemFontHooks
import dev.nonlog.colorosnative.font.SystemFontReplacementHooks
import dev.nonlog.colorosnative.nativeui.AospVolumeHooker
import dev.nonlog.colorosnative.nativeui.C16StackedSignalHooker
import dev.nonlog.colorosnative.nativeui.NativeChargingHooker
import dev.nonlog.colorosnative.nativeui.NativeClipboardHooker
import dev.nonlog.colorosnative.nativeui.NativeFrameworkHooker
import dev.nonlog.colorosnative.nativeui.NativeLiveUpdateHooker
import dev.nonlog.colorosnative.nativeui.NativeNotifyIconHooker
import dev.nonlog.colorosnative.nativeui.NativePowerMenuHooker
import dev.nonlog.colorosnative.nativeui.NativeSettingsHooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.ConcurrentHashMap

class ModuleEntry : XposedModule() {
    private val dispatched = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var systemServerHooked = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        ModuleRuntime.attach(this)
        val sysLoader = ClassLoader.getSystemClassLoader()
        if (param.isSystemServer) {
            SystemFontDistributionHooks.hook(sysLoader)
        }
        SystemFontReplacementHooks.hook(sysLoader)
        if (!param.isSystemServer) {
            NativeFontHooks.hook()
        }
        HookLog.i(HookLog.TAG, "Loaded in ${param.processName} (api=$apiVersion, framework=$frameworkName $frameworkVersion)")
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        if (systemServerHooked) return
        systemServerHooked = true
        val loader = param.classLoader
        SystemFontHooks.hook(loader)
        SystemFontReplacementHooks.hook(loader)
        SystemFontDistributionHooks.hook(loader)
        NativeFrameworkHooker.hook(loader)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val pkg = param.packageName
        if (pkg == "android" || pkg == PrefKeys.MODULE_PACKAGE) return
        val key = "$pkg@${System.identityHashCode(param.classLoader)}"
        if (!dispatched.add(key)) return

        val loader = param.classLoader
        SystemFontReplacementHooks.hook(loader)

        when (pkg) {
            "com.android.systemui" -> {
                C16StackedSignalHooker.hook(loader)
                NativeNotifyIconHooker.hook(loader)
                NativePowerMenuHooker.hook(loader)
                AospVolumeHooker.hook(loader)
                NativeLiveUpdateHooker.hook(loader)
                NativeClipboardHooker.hook(loader)
                NativeChargingHooker.hook(loader)
            }
            "com.android.settings" -> {
                NativeSettingsHooker.hook(loader)
            }
        }
    }
}
