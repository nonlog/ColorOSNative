package dev.nonlog.colorosnative.nativeui

import android.app.Notification
import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal object NativeFrameworkHooker {
    private const val TAG = "ColorOSNative-Framework"
    private val helperClassNames = listOf(
        "com.android.server.notification.OplusNotificationFixHelper",
        "com.android.server.notification.OplusNotificationManagerExtImpl",
        "com.android.server.notification.NotificationManagerServiceExtImpl",
        "com.android.server.notification.OplusNotificationManagerServiceExtImpl",
    )

    @Volatile private var bubblesEnabledAtStart = false

    fun hook(loader: ClassLoader) {
        val nativeIcon = ModuleRuntime.getBool(PrefKeys.NATIVE_NOTIFY_ICON, false)
        val bubbles = ModuleRuntime.getBool(PrefKeys.NATIVE_NOTIFICATION_BUBBLES, false)
        bubblesEnabledAtStart = bubbles

        if (nativeIcon) {
            runCatching { hookNativeNotifyIcon(loader) }
                .onFailure { HookLog.w(TAG, "Native notify icon framework hook failed", it) }
        }
        if (bubbles) {
            runCatching { hookNativeNotificationBubbles(loader) }
                .onFailure { HookLog.w(TAG, "Native notification bubbles hook failed", it) }
        }
    }

    private fun hookNativeNotifyIcon(loader: ClassLoader) {
        var count = 0
        helperClassNames.forEach { className ->
            val clazz = Reflect.findClassIfExists(className, loader) ?: return@forEach
            clazz.declaredMethods.filter { m ->
                m.name.equals("fixSmallIcon", ignoreCase = true) &&
                    m.parameterCount == 4 &&
                    m.parameterTypes[0] == Notification::class.java
            }.forEach { method ->
                if (ModuleRuntime.hookFast("fw:fixSmallIcon:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                        if (!ModuleRuntime.getBool(PrefKeys.NATIVE_NOTIFY_ICON, false)) {
                            chain.proceed()
                        } else {
                            defaultResult(method.returnType)
                        }
                    })) {
                    count++
                }
            }
        }
        HookLog.i(TAG, "Framework native notify icon hooks installed: $count")
    }

    private fun hookNativeNotificationBubbles(loader: ClassLoader) {
        var count = 0
        fun hookExact(className: String, methodName: String, params: Array<Any>, intercept: (XposedInterface.Chain) -> Any?) {
            val clazz = Reflect.findClassIfExists(className, loader) ?: return
            val paramTypes = params.map { p ->
                when (p) {
                    is Class<*> -> p
                    is String -> Reflect.findClassIfExists(p, loader) ?: return
                    else -> return
                }
            }.toTypedArray()
            val method: Method = runCatching { Reflect.findMethodExact(clazz, methodName, *paramTypes) }.getOrNull() ?: return
            if (ModuleRuntime.hookFast("fw:bubble:$className#$methodName", method, XposedInterface.Hooker(intercept))) {
                count++
            }
        }

        val prefHelper = "com.android.server.notification.PreferencesHelper"
        val extractor = "com.android.server.notification.BubbleExtractor"
        val recordCls = "com.android.server.notification.NotificationRecord"
        val channelCls = "android.app.NotificationChannel"

        hookExact(prefHelper, "bubblesEnabled", arrayOf("android.os.UserHandle")) { true }
        hookExact(prefHelper, "getBubblePreference", arrayOf(String::class.java, Int::class.javaPrimitiveType!!)) { chain ->
            val orig = chain.proceed()
            if ((orig as? Int ?: 0) == 0) 1 else orig
        }
        hookExact(extractor, "canPresentAsBubble", arrayOf(recordCls)) { chain ->
            if (shouldForceBubbleRecord(chain.getArg(0))) true else chain.proceed()
        }
        hookExact(extractor, "process", arrayOf(recordCls)) { chain ->
            val rec = chain.getArg(0)
            if (shouldForceBubbleRecord(rec)) {
                forceBubbleRecord(rec)
                null
            } else {
                chain.proceed()
            }
        }
        hookExact(recordCls, "setAllowBubble", arrayOf(Boolean::class.javaPrimitiveType!!)) { chain ->
            if (shouldForceBubbleRecord(chain.thisObject)) chain.proceed(arrayOf(true)) else chain.proceed()
        }
        hookExact(recordCls, "canBubble", emptyArray()) { chain ->
            if (shouldForceBubbleRecord(chain.thisObject)) true else chain.proceed()
        }
        hookExact(recordCls, "isConversation", emptyArray()) { chain ->
            if (shouldForceBubbleRecord(chain.thisObject)) true else chain.proceed()
        }
        hookExact(channelCls, "canBubble", emptyArray()) { true }
        hookExact(channelCls, "getAllowBubbles", emptyArray()) { chain ->
            val orig = chain.proceed()
            if ((orig as? Int ?: 0) == 0) 1 else orig
        }
        HookLog.i(TAG, "Framework native notification bubbles hooks installed: $count")
    }

    private fun shouldForceBubbleRecord(record: Any?): Boolean {
        if (!bubblesEnabledAtStart || record == null) return false
        val notif = runCatching { Reflect.callMethod(record, "getNotification") }.getOrNull() ?: return false
        val isFgs = runCatching { Reflect.callMethod(notif, "isFgsOrUij") as? Boolean == true }.getOrDefault(false)
        if (isFgs) return false
        return runCatching { Reflect.callMethod(notif, "getBubbleMetadata") }.getOrNull() != null
    }

    private fun forceBubbleRecord(record: Any?) {
        if (record == null) return
        runCatching { Reflect.callMethod(record, "setAllowBubble", true) }
        val notif = runCatching { Reflect.callMethod(record, "getNotification") as? Notification }.getOrNull() ?: return
        notif.flags = notif.flags or Notification.FLAG_BUBBLE
    }

    private fun defaultResult(returnType: Class<*>): Any? = when (returnType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
