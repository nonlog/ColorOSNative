package dev.nonlog.colorosnative.xposed

import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

abstract class MethodHook {
    class Param(val thisObject: Any?, val args: Array<Any?>) {
        var result: Any? = null
            set(value) {
                field = value
                returnEarly = true
            }
        var throwable: Throwable? = null
        var returnEarly: Boolean = false
            internal set
    }

    open fun before(param: Param) {}
    open fun after(param: Param) {}
}

object ModuleRuntime {
    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var nativePrefs: SharedPreferences? = null

    @Volatile
    private var fontPrefs: SharedPreferences? = null

    private val installedKeys = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun attach(m: XposedModule) {
        module = m
        runCatching { nativePrefs = m.getRemotePreferences(PrefKeys.GROUP_NATIVE) }
            .onFailure { HookLog.w(HookLog.TAG, "Failed to attach native remote prefs", it) }
        runCatching { fontPrefs = m.getRemotePreferences(PrefKeys.GROUP_FONTS) }
            .onFailure { HookLog.w(HookLog.TAG, "Failed to attach font remote prefs", it) }
    }

    @JvmStatic
    fun requireModule(): XposedModule = requireNotNull(module) { "XposedModule not attached" }

    @JvmStatic
    fun nativePrefs(): SharedPreferences? = nativePrefs

    @JvmStatic
    fun fontPrefs(): SharedPreferences? = fontPrefs

    @JvmStatic
    fun openRemoteFile(name: String): ParcelFileDescriptor = requireModule().openRemoteFile(name)

    @JvmStatic
    fun moduleSourceDir(): String = requireModule().moduleApplicationInfo.sourceDir

    @JvmStatic
    fun getBool(key: String, default: Boolean = false): Boolean =
        runCatching { nativePrefs?.getBoolean(key, default) ?: default }.getOrDefault(default)

    @JvmStatic
    fun getInt(key: String, default: Int = 0): Int =
        runCatching { nativePrefs?.getInt(key, default) ?: default }.getOrDefault(default)

    @JvmStatic
    fun deoptimize(executable: Executable) {
        runCatching { requireModule().deoptimize(executable) }
    }

    @JvmStatic
    fun hookFast(key: String, executable: Executable, hooker: XposedInterface.Hooker): Boolean {
        if (!installedKeys.add(key)) return false
        when (executable) {
            is Method -> requireModule().hook(executable).intercept(hooker)
            is Constructor<*> -> requireModule().hook(executable).intercept(hooker)
            else -> return false
        }
        return true
    }

    @JvmStatic
    fun hookMethod(executable: Executable, callback: MethodHook) {
        val hooker = XposedInterface.Hooker { chain ->
            val args = chain.args.toTypedArray()
            val param = MethodHook.Param(chain.thisObject, args)
            callback.before(param)
            if (!param.returnEarly) {
                try {
                    param.result = chain.proceed(param.args)
                } catch (t: Throwable) {
                    param.throwable = t
                }
            }
            callback.after(param)
            param.throwable?.let { throw it }
            param.result
        }
        when (executable) {
            is Method -> requireModule().hook(executable).intercept(hooker)
            is Constructor<*> -> requireModule().hook(executable).intercept(hooker)
        }
    }

    @JvmStatic
    fun hookAllMethods(clazz: Class<*>, name: String, callback: MethodHook): Int {
        var count = 0
        clazz.declaredMethods.filter { it.name == name }.forEach { m ->
            hookMethod(m, callback)
            count++
        }
        return count
    }

    @JvmStatic
    fun hookAllConstructors(clazz: Class<*>, callback: MethodHook): Int {
        var count = 0
        clazz.declaredConstructors.forEach { c ->
            hookMethod(c, callback)
            count++
        }
        return count
    }
}
