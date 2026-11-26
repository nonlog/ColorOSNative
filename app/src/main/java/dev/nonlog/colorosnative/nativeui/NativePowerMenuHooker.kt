package dev.nonlog.colorosnative.nativeui

import android.content.Context
import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.MethodHook
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect

internal object NativePowerMenuHooker {
    private const val TAG = "ColorOSNative-PowerMenu"
    private const val IMPL = "com.android.systemui.globalactions.GlobalActionsImpl"
    private const val DIALOG_LITE = "com.android.systemui.globalactions.GlobalActionsDialogLite"
    private val PROVIDER_HOLDERS = arrayOf(
        "com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel\$Factory",
        "com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel",
    )

    private val lock = Any()
    @Volatile private var provider: Any? = null
    @Volatile private var cachedDialog: Any? = null
    @Volatile private var observedDialog: Any? = null

    fun hook(loader: ClassLoader) {
        if (!ModuleRuntime.getBool(PrefKeys.NATIVE_POWER_MENU, false)) return
        var hooks = 0
        Reflect.findClassIfExists(DIALOG_LITE, loader)?.let { cls ->
            hooks += ModuleRuntime.hookAllConstructors(cls, object : MethodHook() {
                override fun after(param: Param) { observedDialog = param.thisObject }
            })
        }
        PROVIDER_HOLDERS.forEach { name ->
            val cls = Reflect.findClassIfExists(name, loader) ?: return@forEach
            hooks += ModuleRuntime.hookAllConstructors(cls, object : MethodHook() {
                override fun after(param: Param) {
                    val holder = param.thisObject ?: return
                    runCatching { Reflect.getObjectField(holder, "globalActionsDialogLiteProvider") }
                        .getOrNull()?.let { provider = it }
                }
            })
        }
        val impl = Reflect.findClassIfExists(IMPL, loader) ?: return
        impl.declaredMethods.filter {
            it.name == "showGlobalActions" || it.name == "showOrHideGlobalActions"
        }.forEach { method ->
            ModuleRuntime.hookMethod(method, object : MethodHook() {
                override fun before(param: Param) {
                    if (!ModuleRuntime.getBool(PrefKeys.NATIVE_POWER_MENU, false)) return
                    val host = param.thisObject ?: return
                    if (showNative(host)) param.result = null
                }
            })
            hooks++
        }
        HookLog.i(TAG, "Native power menu hooks installed: $hooks")
    }

    private fun showNative(host: Any): Boolean {
        val ctx = runCatching { Reflect.getObjectField(host, "mContext") as? Context }.getOrNull() ?: return false
        val dialog = obtainDialog() ?: return false
        return runCatching {
            val displayId = (Reflect.callMethod(ctx, "getDisplayId") as? Int) ?: 0
            Reflect.callMethod(dialog, "showOrHideDialog", null, displayId)
            true
        }.getOrElse {
            synchronized(lock) {
                if (cachedDialog === dialog) cachedDialog = null
                if (observedDialog === dialog) observedDialog = null
            }
            false
        }
    }

    private fun obtainDialog(): Any? {
        cachedDialog?.let { return it }
        synchronized(lock) {
            cachedDialog?.let { return it }
            val created = provider?.let { p -> runCatching { Reflect.callMethod(p, "get") }.getOrNull() }
            if (created?.javaClass?.name == DIALOG_LITE) cachedDialog = created
            return cachedDialog ?: observedDialog
        }
    }
}
