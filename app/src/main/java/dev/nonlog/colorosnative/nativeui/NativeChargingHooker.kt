package dev.nonlog.colorosnative.nativeui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.nonlog.colorosnative.R
import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.MethodHook
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object NativeChargingHooker {
    private const val TAG = "ColorOSNative-Charging"

    fun hook(loader: ClassLoader) {
        if (ModuleRuntime.getBool(PrefKeys.AOSP_CHARGING_EFFECT, false)) {
            runCatching { hookRipple(loader) }
                .onFailure { HookLog.w(TAG, "Native charging ripple unavailable", it) }
        }
        if (ModuleRuntime.getBool(PrefKeys.AOSP_CHARGING_SOUND, false)) {
            runCatching { hookSound(loader) }
                .onFailure { HookLog.w(TAG, "Native charging sound unavailable", it) }
        }
    }

    private fun hookRipple(loader: ClassLoader) {
        val type = Reflect.findClass("com.android.systemui.charging.WiredChargingRippleController", loader)
        val callbacks = type.getMethod("registerCallbacks")
        val originals = Collections.synchronizedMap(IdentityHashMap<Any, Boolean>())
        val registered = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()))
        val ready = AtomicBoolean(false)

        ModuleRuntime.hookAllMethods(type, "registerCallbacks", object : MethodHook() {
            override fun before(param: Param) {
                val owner = param.thisObject ?: return
                if (!registered.add(owner)) param.result = null
            }
        })
        ModuleRuntime.hookAllMethods(type, "startRipple", object : MethodHook() {
            override fun before(param: Param) {
                val owner = param.thisObject ?: return
                if (!ModuleRuntime.getBool(PrefKeys.AOSP_CHARGING_EFFECT, false) && originals[owner] != true) {
                    param.result = null
                }
            }
            override fun after(param: Param) {
                if (param.throwable != null && param.thisObject in originals) {
                    ready.set(false)
                    param.throwable = null
                    param.result = null
                }
            }
        })
        ModuleRuntime.hookAllConstructors(type, object : MethodHook() {
            override fun after(param: Param) {
                val owner = param.thisObject ?: return
                if (!ModuleRuntime.getBool(PrefKeys.AOSP_CHARGING_EFFECT, false)) return
                originals[owner] = Reflect.getObjectField(owner, "rippleEnabled") == true
                Reflect.setObjectField(owner, "rippleEnabled", true)
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        callbacks.invoke(owner)
                        ready.set(true)
                        HookLog.i(TAG, "Native charging ripple ready")
                    }.onFailure {
                        registered.remove(owner)
                        Reflect.setObjectField(owner, "rippleEnabled", originals.remove(owner) == true)
                    }
                }
            }
        })
        Reflect.findClassIfExists("com.oplus.systemui.keyguard.charginganim.ChargingAnimationImpl", loader)
            ?.declaredMethods?.filter { it.name.contains("createChargeAnim") }
            ?.forEach { method ->
                ModuleRuntime.hookMethod(method, object : MethodHook() {
                    override fun before(param: Param) {
                        if (ready.get() && ModuleRuntime.getBool(PrefKeys.AOSP_CHARGING_EFFECT, false)) {
                            param.result = null
                        }
                    }
                })
            }
    }

    private fun hookSound(loader: ClassLoader) {
        val type = Reflect.findClass("com.oplus.systemui.statusbar.notification.power.OplusPowerUISoundUtil", loader)
        ModuleRuntime.hookAllMethods(type, "playPowerUISound", object : MethodHook() {
            override fun before(param: Param) {
                if (!ModuleRuntime.getBool(PrefKeys.AOSP_CHARGING_SOUND, false)) return
                if ((param.args.getOrNull(0) as? Int) != 3) return
                val owner = param.thisObject ?: return
                val context = Reflect.getObjectField(owner, "context") as? Context ?: return
                val player = Reflect.getObjectField(owner, "notificationPlayer") ?: return
                val uri = Uri.parse("android.resource://${PrefKeys.MODULE_PACKAGE}/${R.raw.aosp_charging_started}")
                val attrs = AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_SYSTEM).build()
                Reflect.callMethod(player, "play", context, uri, false, attrs, 1f)
                param.result = null
            }
        })
        HookLog.i(TAG, "Native charging sound hook installed")
    }
}
