package dev.nonlog.colorosnative.nativeui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.ImageView
import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.LinkedHashMap

internal object NativeNotifyIconHooker {
    private const val TAG = "ColorOSNative-NotifyIcon"
    private const val SMALL_ICON_MAX_DP = 64
    private const val GRAYSCALE_PROBE_MAX_PX = 96
    private const val GRAYSCALE_CACHE_MAX_ENTRIES = 128

    private data class CacheKey(val id: Int, val cls: String, val w: Int, val h: Int)
    private data class Resolver(val method: Method, val target: Any?)

    private val cache = Collections.synchronizedMap(object : LinkedHashMap<CacheKey, Boolean>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Boolean>?) = size > GRAYSCALE_CACHE_MAX_ENTRIES
    })
    @Volatile private var systemResolvers: List<Resolver>? = null

    fun hook(loader: ClassLoader) {
        if (!ModuleRuntime.getBool(PrefKeys.NATIVE_NOTIFY_ICON, false)) return
        var count = 0
        Reflect.findClassIfExists(
            "com.oplus.systemui.statusbar.notification.util.OplusNotificationSmallIconUtil",
            loader
        )?.declaredMethods?.filter {
            it.returnType == Boolean::class.javaPrimitiveType &&
                it.name.equals("useAppIconForSmallIcon", ignoreCase = true)
        }?.forEach { method ->
            if (ModuleRuntime.hookFast("sysui:notify-icon:false:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                    if (ModuleRuntime.getBool(PrefKeys.NATIVE_NOTIFY_ICON, false)) false else chain.proceed()
                })) count++
        }

        listOf(
            "com.android.systemui.statusbar.notification.NotificationUtils",
            "com.oplus.systemui.statusbar.notification.NotificationUtils",
            "com.oplusos.systemui.statusbar.notification.NotificationUtils",
        ).forEach { name ->
            Reflect.findClassIfExists(name, loader)?.declaredMethods?.filter {
                it.returnType == Boolean::class.javaPrimitiveType &&
                    it.parameterTypes.firstOrNull()?.let(ImageView::class.java::isAssignableFrom) == true &&
                    (it.name.equals("isGrayscale", true) || it.name.equals("isGrayscaleOplus", true))
            }?.forEach { method ->
                if (ModuleRuntime.hookFast("sysui:notify-icon:gray:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                        if (!ModuleRuntime.getBool(PrefKeys.NATIVE_NOTIFY_ICON, false)) return@Hooker chain.proceed()
                        val iv = chain.args.firstOrNull() as? ImageView ?: return@Hooker chain.proceed()
                        val d = iv.drawable ?: return@Hooker chain.proceed()
                        if (isLarge(iv, d)) false
                        else resolveSystemGrayscale(loader, iv.context, d) ?: isGrayscaleDrawable(d, iv.context)
                    })) count++
            }
        }
        HookLog.i(TAG, "SystemUI native notify icon hooks installed: $count")
    }

    private fun resolveSystemGrayscale(loader: ClassLoader, context: Context, d: Drawable): Boolean? {
        val list = systemResolvers ?: synchronized(this) {
            systemResolvers ?: listOf(
                "com.android.internal.util.ContrastColorUtil",
                "com.android.internal.util.NotificationColorUtil",
                "com.oplusos.util.OplusContrastColorUtil",
                "com.oplus.util.OplusContrastColorUtil",
            ).mapNotNull { name ->
                val cls = Reflect.findClassIfExists(name, loader) ?: return@mapNotNull null
                val m = cls.declaredMethods.firstOrNull {
                    it.returnType == Boolean::class.javaPrimitiveType &&
                        (it.name.equals("isGrayscaleIcon", true) || it.name.equals("isGrayscale", true)) &&
                        it.parameterCount == 1 && Drawable::class.java.isAssignableFrom(it.parameterTypes[0])
                }?.apply { isAccessible = true } ?: return@mapNotNull null
                val target = if (Modifier.isStatic(m.modifiers)) null else {
                    val factory = cls.declaredMethods.firstOrNull {
                        it.name == "getInstance" && it.parameterCount == 1 &&
                            Context::class.java.isAssignableFrom(it.parameterTypes[0]) && Modifier.isStatic(it.modifiers)
                    }?.apply { isAccessible = true } ?: return@mapNotNull null
                    runCatching { factory.invoke(null, context) }.getOrNull() ?: return@mapNotNull null
                }
                Resolver(m, target)
            }.also { systemResolvers = it }
        }
        list.forEach { r ->
            runCatching { r.method.invoke(r.target, d) as? Boolean }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun isGrayscaleDrawable(d: Drawable, context: Context): Boolean {
        val maxPx = (SMALL_ICON_MAX_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        if (d.intrinsicWidth > maxPx || d.intrinsicHeight > maxPx) return false
        val key = d.constantState?.let { CacheKey(System.identityHashCode(it), d.javaClass.name, d.intrinsicWidth, d.intrinsicHeight) }
        key?.let { cache[it] }?.let { return it }
        val probe = d.constantState?.newDrawable()?.mutate() ?: d.mutate()
        val w = (probe.intrinsicWidth.takeIf { it > 0 } ?: 64).coerceIn(16, GRAYSCALE_PROBE_MAX_PX)
        val h = (probe.intrinsicHeight.takeIf { it > 0 } ?: 64).coerceIn(16, GRAYSCALE_PROBE_MAX_PX)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val res = try {
            probe.setBounds(0, 0, w, h)
            probe.draw(Canvas(bmp))
            isGrayscaleBitmap(bmp)
        } finally { bmp.recycle() }
        key?.let { cache[it] = res }
        return res
    }

    private fun isGrayscaleBitmap(bmp: Bitmap): Boolean {
        val xStep = (bmp.width / 32).coerceAtLeast(1)
        val yStep = (bmp.height / 32).coerceAtLeast(1)
        for (y in 0 until bmp.height step yStep) {
            for (x in 0 until bmp.width step xStep) {
                val c = bmp.getPixel(x, y)
                if (Color.alpha(c) == 0) continue
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                if (kotlin.math.abs(r - g) > 10 || kotlin.math.abs(r - b) > 10 || kotlin.math.abs(g - b) > 10) return false
            }
        }
        return true
    }

    private fun isLarge(iv: ImageView, d: Drawable): Boolean {
        val maxPx = (SMALL_ICON_MAX_DP * iv.context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val w = iv.width.takeIf { it > 0 } ?: iv.measuredWidth.takeIf { it > 0 } ?: 0
        val h = iv.height.takeIf { it > 0 } ?: iv.measuredHeight.takeIf { it > 0 } ?: 0
        return w > maxPx || h > maxPx || d.intrinsicWidth > maxPx || d.intrinsicHeight > maxPx
    }
}
