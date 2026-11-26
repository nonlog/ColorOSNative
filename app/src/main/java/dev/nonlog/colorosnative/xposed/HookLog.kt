package dev.nonlog.colorosnative.xposed

import android.util.Log

internal object HookLog {
    const val TAG = "ColorOSNative"

    @JvmStatic
    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
