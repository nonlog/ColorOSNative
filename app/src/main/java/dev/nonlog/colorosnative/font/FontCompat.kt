package dev.nonlog.colorosnative.font

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.nonlog.colorosnative.xposed.AppPrefStore
import io.github.libxposed.service.XposedService

internal object FontCompat {
    fun xposedServices(): List<XposedService> = AppPrefStore.allServices()
    fun log(msg: String) { Log.i("ColorOSNative-Font", msg) }
}

internal fun Context.settingsPrefs(): SharedPreferences =
    createDeviceProtectedStorageContext().getSharedPreferences("system_fonts_local", Context.MODE_PRIVATE)

internal fun setInt(context: Context, key: String, value: Int) {
    context.settingsPrefs().edit().putInt(key, value).apply()
}

internal fun setBool(context: Context, key: String, value: Boolean) {
    context.settingsPrefs().edit().putBoolean(key, value).apply()
}
