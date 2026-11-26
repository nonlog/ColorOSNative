package dev.nonlog.colorosnative.nativeui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import dalvik.system.InMemoryDexClassLoader
import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ColorOS 17 fixed AOSP/C16 dual-SIM stacked signal hooker.
 *
 * Root-cause fix over ONextBox:
 * On ColorOS 17 devices whose StatusBarFeatureOption.isSignalMobileIconDefaultParallel() is true
 * (e.g. OnePlus 12 CPH2573), OplusMobileIconsInteractorExImpl.isStackableMode() emits false when
 * Settings.System "signal_icon_display_mode" == 0, so SystemUI renders two side-by-side signal
 * icons and never enters OplusStackedMobileIconLayoutStrategy.SignalIcon-cf5BqRc.
 *
 * This hooker:
 * 1) Forces OplusMobileIconsInteractorExImpl.isStackableMode() to emit true (reactively via
 *    SystemUI's own MutableStateFlow) and StatusBarFeatureOption.isSignalMobileIconDefaultParallel()
 *    to return false whenever C16_STACKED_SIGNAL is enabled.
 * 2) Replaces OplusStackedMobileIconLayoutStrategy.SignalIcon-* drawing with the C16 AOSP
 *    stacked signal Canvas renderer (c16-stacked-signal.dex).
 */
internal object C16StackedSignalHooker {
    private const val TAG = "ColorOSNative-Signal"
    private const val PREFIX = "com.android.systemui.statusbar.pipeline.shared.ui.composable."

    private data class StackableOverride(
        val overrideFlow: Any,
        val originalFlow: Any?,
        val setValue: java.lang.reflect.Method,
        val getValue: java.lang.reflect.Method?,
    )

    private val stackableOverrides = CopyOnWriteArrayList<StackableOverride>()
    private var prefListenerRegistered = false

    private fun enabled(): Boolean = ModuleRuntime.getBool(PrefKeys.C16_STACKED_SIGNAL, false)

    fun hook(loader: ClassLoader) {
        hookStackableModeGate(loader)
        hookStackedSignalRenderer(loader)
    }

    private fun hookStackableModeGate(loader: ClassLoader) {
        runCatching {
            val featureOption = Reflect.findClassIfExists(
                "com.oplusos.systemui.common.feature.StatusBarFeatureOption",
                loader
            )
            featureOption?.declaredMethods?.filter {
                it.name == "isSignalMobileIconDefaultParallel" && it.parameterCount == 0
            }?.forEach { method ->
                ModuleRuntime.hookFast("c16-signal:default-parallel", method, XposedInterface.Hooker { chain ->
                    if (enabled()) false else chain.proceed()
                })
                ModuleRuntime.deoptimize(method)
                HookLog.i(TAG, "Hooked StatusBarFeatureOption.isSignalMobileIconDefaultParallel")
            }
        }.onFailure {
            HookLog.w(TAG, "StatusBarFeatureOption hook skipped", it)
        }

        runCatching {
            val interactorExImpl = Reflect.findClassIfExists(
                "com.oplus.systemui.statusbar.pipeline.mobile.domain.interactor.OplusMobileIconsInteractorExImpl",
                loader
            ) ?: return
            val method = interactorExImpl.declaredMethods.firstOrNull {
                it.name == "isStackableMode" && it.parameterCount == 0
            } ?: return

            val stateFlowKt = Reflect.findClass("kotlinx.coroutines.flow.StateFlowKt", loader)
            val mutableFactory = stateFlowKt.getMethod("MutableStateFlow", Any::class.java)
            val mutableType = Reflect.findClass("kotlinx.coroutines.flow.MutableStateFlow", loader)
            val stateType = Reflect.findClass("kotlinx.coroutines.flow.StateFlow", loader)
            val setValue = mutableType.getMethod("setValue", Any::class.java)
            val getValue = stateType.getMethod("getValue")

            ModuleRuntime.hookFast("c16-signal:stackable-mode", method, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val initial = if (enabled()) true else (runCatching {
                    original?.let { getValue.invoke(it) as? Boolean }
                }.getOrNull() ?: false)
                val overrideFlow = mutableFactory.invoke(null, initial)!!
                stackableOverrides.add(StackableOverride(overrideFlow, original, setValue, getValue))
                ensureStackablePrefListener()
                HookLog.i(TAG, "Injected reactive isStackableMode override (initial=$initial)")
                overrideFlow
            })
            ModuleRuntime.deoptimize(method)
        }.onFailure {
            HookLog.w(TAG, "OplusMobileIconsInteractorExImpl.isStackableMode hook unavailable", it)
        }
    }

    private fun ensureStackablePrefListener() {
        if (prefListenerRegistered) return
        val prefs = ModuleRuntime.nativePrefs() ?: return
        prefListenerRegistered = true
        val main = Handler(Looper.getMainLooper())
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PrefKeys.C16_STACKED_SIGNAL) {
                main.post {
                    val on = enabled()
                    stackableOverrides.forEach { entry ->
                        runCatching {
                            val target = if (on) true else (entry.originalFlow?.let {
                                entry.getValue?.invoke(it) as? Boolean
                            } ?: false)
                            entry.setValue.invoke(entry.overrideFlow, target)
                        }
                    }
                }
            }
        }
    }

    private fun hookStackedSignalRenderer(loader: ClassLoader) {
        runCatching {
            val strategyClass = Reflect.findClass(PREFIX + "OplusStackedMobileIconLayoutStrategy", loader)
            val method = strategyClass.declaredMethods.single {
                it.name.startsWith("SignalIcon-") && it.parameterCount == 7
            }
            var adapter: Adapter? = null
            var unavailable = false
            ModuleRuntime.hookFast("c16-stacked-signal:render", method, XposedInterface.Hooker { chain ->
                if (unavailable) return@Hooker chain.proceed()
                val renderer = adapter ?: runCatching { Adapter(loader) }.getOrElse {
                    unavailable = true
                    HookLog.w(TAG, "Host Compose ABI unavailable; retaining stock stacked signal", it)
                    return@Hooker chain.proceed()
                }.also { adapter = it }

                val drawingReady = renderer.readState.invoke(renderer.state) == true && renderer.prepare()
                val args = chain.args.toTypedArray()
                try {
                    val composer = requireNotNull(renderer.start.invoke(args[5], 0x4f4e1637))
                    val on = renderer.readState.invoke(renderer.state) == true
                    if (on && drawingReady) renderer.render(args, composer)
                    else chain.proceed()
                    val scope = renderer.end.invoke(composer)
                    if (scope != null) {
                        val target = chain.thisObject
                        val restart = Proxy.newProxyInstance(loader, arrayOf(renderer.function2)) { proxy, called, next ->
                            when (called.name) {
                                "invoke" -> {
                                    val replay = args.copyOf()
                                    replay[5] = next!![0]
                                    replay[6] = (replay[6] as Int) or 1
                                    try { method.invoke(target, *replay) }
                                    catch (error: InvocationTargetException) { throw error.targetException }
                                    renderer.unit
                                }
                                "equals" -> proxy === next?.getOrNull(0)
                                "hashCode" -> System.identityHashCode(proxy)
                                "toString" -> "C16StackedSignalRestart"
                                else -> null
                            }
                        }
                        renderer.updateScope.invoke(scope, restart)
                    }
                    null
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            })

            listOf(
                "DefaultStackedMobileIconLayoutStrategy",
                "BigTypeStackedMobileIconLayoutStrategy",
                "TigoFiveGStackedMobileIconLayoutStrategy",
                "OplusStackedMobileIconLayoutStrategy\$ComposeDefaultImpls",
            ).forEach { name ->
                Reflect.findClassIfExists(PREFIX + name, loader)?.declaredMethods
                    ?.filter { it.name.contains("SignalIcon") }
                    ?.forEach { ModuleRuntime.deoptimize(it) }
            }
            HookLog.i(TAG, "C16 AOSP stacked signal renderer installed")
        }.onFailure {
            HookLog.w(TAG, "Stacked signal renderer entry unavailable", it)
        }
    }

    private class Adapter(private val loader: ClassLoader) {
        private fun type(name: String) = Reflect.findClass(name, loader)
        private val composerType = type("androidx.compose.runtime.Composer")
        val start = composerType.getMethod("startRestartGroup", Int::class.javaPrimitiveType)
        val end = composerType.getMethod("endRestartGroup")
        val function2 = type("kotlin.jvm.functions.Function2")
        val unit = type("kotlin.Unit").getField("INSTANCE").get(null)
        val updateScope = type("androidx.compose.runtime.ScopeUpdateScope").getMethod("updateScope", function2)
        private val snapshots = type("androidx.compose.runtime.SnapshotStateKt")
        val state = snapshots.getMethod(
            "mutableStateOf",
            Any::class.java,
            type("androidx.compose.runtime.SnapshotMutationPolicy")
        ).invoke(null, enabled(), snapshots.getMethod("structuralEqualityPolicy").invoke(null))
        val readState = type("androidx.compose.runtime.State").getMethod("getValue")
        private val writeState = type("androidx.compose.runtime.MutableState").getMethod("setValue", Any::class.java)
        private val main = Handler(Looper.getMainLooper())
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == PrefKeys.C16_STACKED_SIGNAL) {
                main.post { writeState.invoke(state, prefs.getBoolean(PrefKeys.C16_STACKED_SIGNAL, false)) }
            }
        }

        @Volatile private var drawing: Drawing? = null
        @Volatile private var failed = false

        init {
            ModuleRuntime.nativePrefs()?.let { prefs ->
                prefs.registerOnSharedPreferenceChangeListener(listener)
                writeState.invoke(state, prefs.getBoolean(PrefKeys.C16_STACKED_SIGNAL, false))
            }
        }

        fun prepare(): Boolean {
            if (drawing != null) return true
            if (failed) return false
            return synchronized(this) {
                if (drawing != null) return@synchronized true
                if (failed) return@synchronized false
                runCatching { Drawing(loader).also { drawing = it } }.onFailure {
                    failed = true
                    HookLog.w(TAG, "C16 donor dex unavailable; retaining stock drawing", it)
                }.isSuccess
            }
        }

        fun render(args: Array<Any?>, composer: Any) = requireNotNull(drawing).render(args, composer)
    }

    private class Drawing(private val loader: ClassLoader) {
        private fun type(name: String) = Reflect.findClass(name, loader)
        private val context = Reflect.findClass("android.app.ActivityThread", loader)
            .getMethod("currentApplication").invoke(null) as Context
        private val donor = InMemoryDexClassLoader(
            ByteBuffer.wrap(
                context.createPackageContext(PrefKeys.MODULE_PACKAGE, 0)
                    .assets.open("c16-stacked-signal.dex").use { it.readBytes() }
            ),
            loader
        )
        private fun port(name: String) = Class.forName("com.mi.onextbox.port.c16.$name", true, donor)
        private val four = port("FourBarsDimensions").getField("INSTANCE").get(null)
        private val five = port("FiveBarsDimensions").getField("INSTANCE").get(null)
        private val dimensions = port("StackedMobileIconDimensions")
        private val width = port("BarsDependentDimensions").getMethod("getTotalWidth-XSAIIZE")
        private val height = dimensions.getField("IconHeightSp").getLong(null)
        private val iconSpacing = dimensions.getField("IconSpacingSp").getLong(null)
        private val callback = port("StackedMobileIconKt\$StackedMobileIcon\$3\$1")
            .declaredConstructors.single().apply { isAccessible = true }
        private val dualType = type("com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSim")
        private val primary = dualType.getMethod("getPrimary")
        private val secondary = dualType.getMethod("getSecondary")
        private val cellType = primary.returnType
        private val levels = cellType.getMethod("getNumberOfLevels")
        private val composerType = type("androidx.compose.runtime.Composer")
        private val consume = composerType.getMethod("consume", type("androidx.compose.runtime.CompositionLocal"))
        private val densityLocal = type("androidx.compose.ui.platform.CompositionLocalsKt")
            .getMethod("getLocalDensity").invoke(null)
        private val toDp = type("androidx.compose.ui.unit.FontScaling")
            .getMethod("toDp-GaN1DYA", Long::class.javaPrimitiveType)
        private val modifierType = type("androidx.compose.ui.Modifier")
        private val rowScope = type("androidx.compose.foundation.layout.RowScopeInstance")
            .getField("INSTANCE").get(null)
        private val alignInRow = type("androidx.compose.foundation.layout.RowScope")
            .getMethod("align", modifierType, type("androidx.compose.ui.Alignment\$Vertical"))
        private val centerVertically = type("androidx.compose.ui.Alignment\$Companion")
            .getMethod("getCenterVertically")
            .invoke(type("androidx.compose.ui.Alignment").getField("Companion").get(null))
        private val size = type("androidx.compose.foundation.layout.SizeKt")
            .getMethod("size-VpY3zN4", modifierType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        private val horizontalPadding = type("androidx.compose.foundation.layout.PaddingKt")
            .getMethod("padding-VpY3zN4", modifierType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        private val canvas = type("androidx.compose.foundation.CanvasKt").getMethod(
            "Canvas",
            modifierType,
            type("kotlin.jvm.functions.Function1"),
            composerType,
            Int::class.javaPrimitiveType
        )
        private var reported = false

        init { preflight() }

        fun render(args: Array<Any?>, composer: Any) {
            val dual = args[0]!!
            val bars = maxOf(
                levels.invoke(primary.invoke(dual)) as Int,
                levels.invoke(secondary.invoke(dual)) as Int
            ) - 1
            val dims = if (bars == 5) five else four
            val density = consume.invoke(composer, densityLocal)
            val aligned = alignInRow.invoke(rowScope, args[4], centerVertically)
            val spaced = horizontalPadding.invoke(null, aligned, toDp.invoke(density, iconSpacing), 0f)
            val modifier = size.invoke(null, spaced, toDp.invoke(density, width.invoke(dims)), toDp.invoke(density, height))
            canvas.invoke(null, modifier, callback.newInstance(dims, bars, dual, args[2]), composer, 0)
            if (!reported) {
                reported = true
                HookLog.i(TAG, "C16 AOSP stacked signal active ($bars bars)")
            }
        }

        private fun preflight() {
            val scopeType = type("androidx.compose.ui.graphics.drawscope.CanvasDrawScope")
            val scope = scopeType.getConstructor().newInstance()
            val draw = scopeType.declaredMethods.single { it.name == "draw-yzxVdVo" }
            val density = type("androidx.compose.ui.unit.DensityImpl")
                .getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .newInstance(3f, 1f)
            val direction = type("androidx.compose.ui.unit.LayoutDirection").getField("Ltr").get(null)
            val hostCanvasType = type("androidx.compose.ui.graphics.AndroidCanvas")
            val hostCanvas = hostCanvasType.getConstructor().newInstance()
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            try {
                hostCanvasType.getDeclaredField("internalCanvas").apply { isAccessible = true }
                    .set(hostCanvas, Canvas(bitmap))
                val packedSize = (96f.toRawBits().toLong() shl 32) or (96f.toRawBits().toLong() and 0xffffffffL)
                val cell = cellType.getConstructor(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                val dual = dualType.getConstructor(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    cellType,
                    cellType
                )
                for (count in 4..5) for (level in 0..count) for (warning in listOf(false, true)) {
                    val model = dual.newInstance(
                        1,
                        2,
                        cell.newInstance(level, count + 1, warning, false),
                        cell.newInstance(count - level, count + 1, warning, false)
                    )
                    draw.invoke(
                        scope,
                        density,
                        direction,
                        hostCanvas,
                        packedSize,
                        callback.newInstance(if (count == 5) five else four, count, model, -0x100000000L)
                    )
                }
                HookLog.i(TAG, "C16 donor dex ABI preflight passed")
            } finally {
                bitmap.recycle()
            }
        }
    }
}
