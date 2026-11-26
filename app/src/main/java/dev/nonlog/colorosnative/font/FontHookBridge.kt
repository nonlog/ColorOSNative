package dev.nonlog.colorosnative.font

import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.MethodHook
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method

internal object FontHookBridge {
    @JvmStatic
    fun log(msg: String) = HookLog.i("ColorOSNative-Font", msg)

    @JvmStatic
    fun deoptimize(e: Executable) = ModuleRuntime.deoptimize(e)

    @JvmStatic
    fun findClass(name: String, loader: ClassLoader?): Class<*> = Reflect.findClass(name, loader)

    @JvmStatic
    fun findClassIfExists(name: String, loader: ClassLoader?): Class<*>? = Reflect.findClassIfExists(name, loader)

    @JvmStatic
    fun callMethod(obj: Any, name: String, vararg args: Any?): Any? = Reflect.callMethod(obj, name, *args)

    @JvmStatic
    fun getIntField(obj: Any, name: String): Int = Reflect.getIntField(obj, name)

    @JvmStatic
    fun setIntField(obj: Any, name: String, value: Int) = Reflect.setIntField(obj, name, value)

    @JvmStatic
    fun hookAllMethods(clazz: Class<*>, name: String, hook: MethodHook) {
        ModuleRuntime.hookAllMethods(clazz, name, hook)
    }

    @JvmStatic
    fun findAndHookMethod(clazz: Class<*>, name: String, hook: MethodHook) {
        ModuleRuntime.hookAllMethods(clazz, name, hook)
    }

    @JvmStatic
    fun hookChainMethod(method: Method, hooker: XposedInterface.Hooker) {
        ModuleRuntime.hookFast("font:${method.toGenericString()}", method, hooker)
    }
}
