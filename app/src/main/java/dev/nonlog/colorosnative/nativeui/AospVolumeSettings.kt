package dev.nonlog.colorosnative.nativeui

import dev.nonlog.colorosnative.xposed.ModuleRuntime
import dev.nonlog.colorosnative.xposed.PrefKeys

internal object AospVolumeSettings {
    fun enabledXposed(): Boolean = ModuleRuntime.getBool(PrefKeys.AOSP_VOLUME, false)
    fun scaleXposed(): Int = if (enabledXposed()) AospVolumeSize.percent(ModuleRuntime.getInt(PrefKeys.AOSP_VOLUME_SCALE, 100)) else 100
    fun hideInQsXposed(): Boolean = enabledXposed() && ModuleRuntime.getBool(PrefKeys.AOSP_VOLUME_HIDE_IN_QS, false)
}

internal object HostClassLookup {
    fun load(name: String, initialize: Boolean = false, loader: ClassLoader? = null): Class<*> =
        Class.forName(name, initialize, loader ?: ClassLoader.getSystemClassLoader())
}

internal object ModernHookRuntime {
    fun requireModule() = ModuleRuntime.requireModule()
}

internal object ModernHotReloadSession {
    class Session(val previousTargets: List<Any> = emptyList())
    val current: Session? = null
}

internal object HotReloadLifecycle {
    fun register(
        key: String,
        capture: (() -> Any?)? = null,
        detach: (() -> Unit)? = null,
        restore: ((Any?) -> Unit)? = null,
        canPrepare: (() -> Boolean)? = null,
        canReload: (() -> Boolean)? = null,
    ) {}

    fun registerReplay(
        key: String,
        capture: (() -> Any?)? = null,
        detach: (() -> Unit)? = null,
        restore: ((Any?) -> Unit)? = null,
        replay: ((Any?) -> Unit)? = null,
        canPrepare: (() -> Boolean)? = null,
        canReload: (() -> Boolean)? = null,
    ) {}

    fun withInfrastructureHook(fallback: () -> Unit, action: () -> Unit) {
        action()
    }

    inline fun <T> withHook(fallback: () -> T, action: () -> T): T = action()
}
