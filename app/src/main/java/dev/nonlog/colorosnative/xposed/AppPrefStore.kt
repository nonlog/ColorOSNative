package dev.nonlog.colorosnative.xposed

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppPrefStore {
    private val services = CopyOnWriteArrayList<XposedService>()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var registered = false

    fun register(context: Context, onConnected: (XposedService) -> Unit = {}) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                if (!services.contains(service)) services.add(service)
                _connected.value = true
                syncNativePrefs(app, service)
                onConnected(service)
            }

            override fun onServiceDied(service: XposedService) {
                services.remove(service)
                _connected.value = services.isNotEmpty()
            }
        })
    }

    fun firstService(): XposedService? = services.firstOrNull()

    fun allServices(): List<XposedService> = services.toList()

    fun localPrefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PrefKeys.GROUP_NATIVE, Context.MODE_PRIVATE)

    fun getBool(context: Context, key: String, default: Boolean = false): Boolean =
        localPrefs(context).getBoolean(key, default)

    fun setBool(context: Context, key: String, value: Boolean) {
        localPrefs(context).edit().putBoolean(key, value).apply()
        services.forEach { svc ->
            runCatching {
                svc.getRemotePreferences(PrefKeys.GROUP_NATIVE).edit().putBoolean(key, value).apply()
            }
        }
    }

    fun getInt(context: Context, key: String, default: Int = 0): Int =
        localPrefs(context).getInt(key, default)

    fun setInt(context: Context, key: String, value: Int) {
        localPrefs(context).edit().putInt(key, value).apply()
        services.forEach { svc ->
            runCatching {
                svc.getRemotePreferences(PrefKeys.GROUP_NATIVE).edit().putInt(key, value).apply()
            }
        }
    }

    fun syncNativePrefs(context: Context, service: XposedService) {
        val local = localPrefs(context).all
        if (local.isEmpty()) return
        runCatching {
            val editor = service.getRemotePreferences(PrefKeys.GROUP_NATIVE).edit()
            local.forEach { (k, v) ->
                when (v) {
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is String -> editor.putString(k, v)
                }
            }
            editor.apply()
        }
    }
}
