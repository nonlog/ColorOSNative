package dev.nonlog.colorosnative.nativeui

import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.MethodHook
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect

internal object NativeSettingsHooker {
    private const val TAG = "ColorOSNative-Settings"

    fun hook(loader: ClassLoader) {
        val clazz = Reflect.findClassIfExists(
            "com.oplus.settings.SettingsActivityPlugin\$ConfigureNotificationSettings",
            loader
        ) ?: return
        runCatching {
            ModuleRuntime.hookAllMethods(clazz, "onCreate", object : MethodHook() {
                override fun before(param: Param) {
                    if (ModuleRuntime.getBool(PrefKeys.NATIVE_NOTIFICATION_BUBBLES, false)) {
                        param.result = null
                    }
                }
            })
            HookLog.i(TAG, "Native notification bubbles settings hook installed")
        }.onFailure { HookLog.w(TAG, "Settings bubble redirect hook failed", it) }
    }
}
