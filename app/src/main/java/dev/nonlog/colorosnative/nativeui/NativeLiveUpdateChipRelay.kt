package dev.nonlog.colorosnative.nativeui

import java.util.WeakHashMap

internal class NativeLiveUpdateChipRelay(
    private val empty: Any,
    private val write: (Any, Any) -> Unit
) {
    private val latest = WeakHashMap<Any, Any>()

    @Synchronized fun accept(sink: Any, bundle: Any, enabled: Boolean) {
        latest[sink] = bundle
        if (enabled) write(sink, bundle)
    }

    @Synchronized fun reconcile(enabled: Boolean) {
        latest.entries.toList().forEach { (sink, bundle) ->
            write(sink, if (enabled) bundle else empty)
        }
    }
}
