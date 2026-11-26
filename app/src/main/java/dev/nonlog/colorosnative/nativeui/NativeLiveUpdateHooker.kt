package dev.nonlog.colorosnative.nativeui

import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import dev.nonlog.colorosnative.xposed.HookLog
import dev.nonlog.colorosnative.xposed.MethodHook
import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys
import dev.nonlog.colorosnative.xposed.Reflect
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object NativeLiveUpdateHooker {
    private const val TAG = "ColorOSNative-LiveUpdate"
    private const val BASE = "com.oplus.systemui.statusbar."

    fun hook(loader: ClassLoader) {
        runCatching { Session(loader).install() }
            .onFailure { HookLog.w(TAG, "Native Live Update routing unavailable; retaining stock", it) }
    }

    private class Session(private val loader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val repoType = Reflect.findClass(BASE + "notification.livealert.data.repository.OplusLiveAlertNotificationsRepository", loader)
        private val filtersType = Reflect.findClass(BASE + "notification.livealert.data.repository.OplusLiveAlertFilters", loader)
        private val controllerType = Reflect.findClass(BASE + "seeding.CapsulePluginContainerController", loader)
        private val capsuleType = Reflect.findClass(BASE + "seeding.CapsulePluginContainer", loader)
        private val entryType = Reflect.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry", loader)
        private val locationType = Reflect.findClass("com.android.systemui.statusbar.phone.StatusBarLocation", loader)
        private val visibilityType = Reflect.findClass("com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel", loader)
        private val binderType = Reflect.findClass("com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinderImpl", loader)
        private val boundsType = Reflect.findClass("com.android.systemui.statusbar.layout.ui.viewmodel.StatusBarBoundsViewModel", loader)
        private val chipVm = "com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel"
        private val chipFilterType = Reflect.findClass("$chipVm\$special\$\$inlined\$filterNot\$1\$2", loader)
        private val stateFlowType = Reflect.findClass("kotlinx.coroutines.flow.MutableStateFlow", loader)
        private val hostUnit = Reflect.findClass("kotlin.Unit", loader).getField("INSTANCE").get(null)
        private val chipModelType = Reflect.findClass("com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel", loader)
        private val emptyChipBundle = Reflect.findClass("$chipVm\$ChipBundle", loader)
            .getDeclaredConstructor(chipModelType, chipModelType, chipModelType, List::class.java, Int::class.javaPrimitiveType)
            .newInstance(null, null, null, null, 31)
        private val chipRelay = NativeLiveUpdateChipRelay(emptyChipBundle) { sink, value ->
            Reflect.callMethod(sink, "setValue", value)
        }
        private val viewBounds = Reflect.findClass("com.android.systemui.util.ConvenienceExtensionsKt", loader)
            .getDeclaredMethod("getBoundsOnScreen", View::class.java).apply { isAccessible = true }
        private val adjustVisibility = binderType.getDeclaredMethod("adjustVisibility", View::class.java, visibilityType)
            .apply { isAccessible = true }
        private val hidden = visibilityType.getConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .newInstance(View.GONE, false)

        private var repository = WeakReference<Any>(null)
        private val controllers = WeakHashMap<Any, Bundle?>()
        private val views = WeakHashMap<View, Int>()
        @Volatile private var ready = false
        private var replayingIcons = false
        private var applyingViews = false
        private var modeApplied = false

        private val refresh = Runnable {
            runCatching { bootstrap(); reconcile() }.onFailure {
                ready = false
                runCatching { reconcile(forceStock = true) }
                HookLog.w(TAG, "Live Update refresh failed; restored stock", it)
            }
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PrefKeys.NATIVE_LIVE_UPDATE) schedule()
        }

        private fun active() = ready && ModuleRuntime.getBool(PrefKeys.NATIVE_LIVE_UPDATE, false)
        private fun schedule() { main.removeCallbacks(refresh); main.post(refresh) }

        fun install() {
            val filter = filtersType.getDeclaredMethod("filterEntry", entryType)
            val live = repoType.getDeclaredMethod("isLiveAlert", entryType, Boolean::class.javaPrimitiveType)
            val has = repoType.getDeclaredMethod("hasStatusBarLiveAlert", entryType)
            val hun = repoType.getDeclaredMethod("interceptorHun", entryType)
            val areas = controllerType.getDeclaredMethod("setHiddenIconAreas\$1", locationType, List::class.java)
            val icons = controllerType.getDeclaredMethod("hiddenStatusBarIconView", Bundle::class.java)
            val fake = controllerType.getDeclaredMethod("setCapsuleFakeContainerVisibility", Boolean::class.javaPrimitiveType)
            val chipEmission = chipFilterType.getDeclaredMethod("emit", Any::class.java, Reflect.findClass("kotlin.coroutines.Continuation", loader))

            ModuleRuntime.hookMethod(chipEmission, object : MethodHook() {
                override fun before(param: Param) {
                    val owner = param.thisObject ?: return
                    val sink = Reflect.getObjectField(owner, "\$this_unsafeFlow") ?: return
                    if (!stateFlowType.isInstance(sink)) return
                    val bundle = param.args[0] ?: return
                    val on = active()
                    chipRelay.accept(sink, bundle, on)
                    if (on) param.result = hostUnit
                }
            })

            listOf("getStartSideContainerBounds" to "startSideContainerView", "getClockBounds" to "clockView")
                .forEach { (getter, field) ->
                    val m = boundsType.getDeclaredMethod(getter)
                    ModuleRuntime.hookMethod(m, object : MethodHook() {
                        override fun after(param: Param) {
                            if (!active() || param.throwable != null) return
                            val owner = param.thisObject ?: return
                            val view = Reflect.getObjectField(owner, field) as? View ?: return
                            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
                            val actual = viewBounds.invoke(null, view) as Rect
                            if (actual != param.result) param.result = actual
                        }
                    })
                }

            ModuleRuntime.hookMethod(filter, object : MethodHook() {
                override fun before(param: Param) { if (active()) param.result = 0 }
            })
            listOf(live, has, hun).forEach { m ->
                ModuleRuntime.hookMethod(m, object : MethodHook() {
                    override fun before(param: Param) {
                        param.thisObject?.let { repository = WeakReference(it) }
                        if (active()) param.result = false
                    }
                })
            }
            ModuleRuntime.hookAllConstructors(repoType, object : MethodHook() {
                override fun after(param: Param) {
                    if (param.throwable == null) param.thisObject?.let { repository = WeakReference(it); schedule() }
                }
            })
            ModuleRuntime.hookAllConstructors(controllerType, object : MethodHook() {
                override fun after(param: Param) {
                    if (param.throwable == null) param.thisObject?.let { rememberController(it); schedule() }
                }
            })
            controllerType.declaredMethods.filter {
                it.name in setOf("onViewAttached", "onPluginAdded", "createCapsuleFakeContainer")
            }.forEach { m ->
                ModuleRuntime.hookMethod(m, object : MethodHook() {
                    override fun after(param: Param) {
                        if (param.throwable == null) param.thisObject?.let {
                            rememberController(it)
                            if (active()) { modeApplied = true; applyViews(true) }
                        }
                    }
                })
            }
            ModuleRuntime.hookMethod(adjustVisibility, object : MethodHook() {
                override fun before(param: Param) {
                    val view = param.args[0] as? View ?: return
                    if (!capsuleType.isInstance(view) && !views.containsKey(view)) return
                    val orig = Reflect.callMethod(param.args[1]!!, "getVisibility") as Int
                    if (!applyingViews) views[view] = orig
                    if (active()) param.args[1] = hidden
                }
            })
            ModuleRuntime.hookMethod(areas, object : MethodHook() {
                override fun before(param: Param) {
                    if (active()) param.args[1] = java.util.Collections.emptyList<Any>()
                }
            })
            ModuleRuntime.hookMethod(icons, object : MethodHook() {
                override fun before(param: Param) {
                    val owner = param.thisObject ?: return
                    val orig = param.args[0] as? Bundle ?: return
                    if (!replayingIcons) controllers[owner] = Bundle(orig)
                    if (active()) param.args[0] = Bundle(orig).apply { putStringArrayList("hiddenIconList", arrayListOf()) }
                }
            })
            ModuleRuntime.hookMethod(fake, object : MethodHook() {
                override fun before(param: Param) {
                    val owner = param.thisObject ?: return
                    rememberController(owner)
                    (Reflect.getObjectField(owner, "capsuleFakeContainer") as? View)?.let {
                        views[it] = if (param.args[0] == true) View.VISIBLE else View.INVISIBLE
                    }
                    if (active()) param.args[0] = false
                }
            })
            ready = true
            ModuleRuntime.nativePrefs()?.registerOnSharedPreferenceChangeListener(listener)
            schedule()
            HookLog.i(TAG, "Native Live Update capsule hooks installed")
        }

        private fun rememberController(owner: Any) {
            if (!controllers.containsKey(owner)) controllers[owner] = runCatching {
                val dispatcher = Reflect.getObjectField(owner, "hiddenIconDispatcher") ?: return@runCatching null
                val list = Reflect.getObjectField(dispatcher, "hiddenIcons") as? List<*> ?: return@runCatching null
                Bundle().apply { putStringArrayList("hiddenIconList", ArrayList(list.filterIsInstance<String>())) }
            }.getOrNull()
            for (f in listOf("capsulePluginContainer", "capsuleFakeContainer")) {
                (Reflect.getObjectField(owner, f) as? View)?.let {
                    if (!views.containsKey(it) || (!modeApplied && !active())) views[it] = it.visibility
                }
            }
        }

        private fun bootstrap() {
            val app = Reflect.callStaticMethod(Reflect.findClass("android.app.ActivityThread", loader), "currentApplication") ?: return
            val comp = Reflect.getObjectField(app, "mSysUIComponent") ?: return
            val dump = Reflect.callMethod(comp, "createDumpManager") ?: return
            val entries = Reflect.callMethod(dump, "getDumpables") as? Collection<*> ?: return
            for (entry in entries.toList()) {
                val owner = entry?.let { Reflect.callMethod(it, "getDumpable") } ?: continue
                when {
                    repoType.isInstance(owner) -> repository = WeakReference(owner)
                    controllerType.isInstance(owner) -> rememberController(owner)
                }
            }
        }

        private fun applyViews(enabled: Boolean) {
            for ((view, original) in views.entries.toList()) {
                val vis = if (enabled) View.GONE else original
                val alpha = if (vis == View.VISIBLE) 1f else 0f
                if (view.visibility == vis && view.alpha == alpha) continue
                applyingViews = true
                try {
                    val model = if (enabled) hidden else Reflect.newInstance(visibilityType, vis, false)
                    adjustVisibility.invoke(null, view, model)
                } finally { applyingViews = false }
            }
        }

        private fun reconcile(forceStock: Boolean = false) {
            val on = !forceStock && active()
            if (!on && !modeApplied) return
            if (on) modeApplied = true
            chipRelay.reconcile(on)
            controllers.keys.toList().forEach(::rememberController)
            applyViews(on)
            for ((owner, saved) in controllers.entries.toList()) {
                replayingIcons = true
                try {
                    if (on || saved != null) {
                        Reflect.callMethod(
                            owner,
                            "hiddenStatusBarIconView",
                            if (on) Bundle().apply { putStringArrayList("hiddenIconList", arrayListOf()) } else Bundle(saved!!)
                        )
                    }
                } finally { replayingIcons = false }
                locationType.enumConstants.orEmpty().forEach { Reflect.callMethod(owner, "notifyRect", it) }
            }
            repository.get()?.let { repo ->
                Reflect.callMethod(repo, "reInitLiveAlert")
                val col = Reflect.getObjectField(repo, "notifCollection") ?: return@let
                Reflect.callMethod(col, "dispatchEventsAndRebuildList", "ColorOSNative Live Update", false)
            }
            modeApplied = on
        }
    }
}
