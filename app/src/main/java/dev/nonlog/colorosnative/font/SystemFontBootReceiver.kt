package dev.nonlog.colorosnative.font

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SystemFontBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED || action == Intent.ACTION_BOOT_COMPLETED) {
            val result = goAsync()
            Thread {
                try {
                    runCatching { SystemFontMounts.resume(context.applicationContext) }
                } finally {
                    result.finish()
                }
            }.start()
        }
    }
}
