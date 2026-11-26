package dev.nonlog.colorosnative.nativeui

import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.MethodHook
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect

internal object NativeClipboardHooker {
    private const val TAG = "ColorOSNative-Clipboard"

    fun hook(loader: ClassLoader) {
        if (!ModuleRuntime.getBool(PrefKeys.FORCE_NATIVE_CLIPBOARD, false)) return
        val cls = Reflect.findClassIfExists("com.oplusos.systemui.common.feature.FeatureOption", loader) ?: return
        var count = 0
        cls.declaredMethods.filter {
            it.name == "isExpRegion" && it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType
        }.forEach { method ->
            ModuleRuntime.hookMethod(method, object : MethodHook() {
                override fun after(param: Param) {
                    if (ModuleRuntime.getBool(PrefKeys.FORCE_NATIVE_CLIPBOARD, false) && isClipboardCall()) {
                        param.result = true
                    }
                }
            })
            count++
        }
        HookLog.i(TAG, "Native clipboard overlay hooks installed: $count")
    }

    private fun isClipboardCall(): Boolean = Thread.currentThread().stackTrace.any {
        it.className == "com.android.systemui.clipboardoverlay.ClipboardListener" &&
            it.methodName == "onPrimaryClipChanged"
    }
}
