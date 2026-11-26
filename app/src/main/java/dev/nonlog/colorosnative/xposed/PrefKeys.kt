package dev.nonlog.colorosnative.xposed

object PrefKeys {
    const val GROUP_NATIVE = "coloros_native"
    const val GROUP_FONTS = "system_fonts"
    const val MODULE_PACKAGE = "dev.nonlog.colorosnative"

    // ONextBox SystemUI -> Native (9 features + volume options)
    const val NATIVE_NOTIFY_ICON = "native_notify_icon_enabled"
    const val NATIVE_NOTIFICATION_BUBBLES = "native_notification_bubbles"
    const val NATIVE_POWER_MENU = "systemui_native_power_menu"
    const val AOSP_VOLUME = "systemui_aosp_volume"
    const val AOSP_VOLUME_SCALE = "systemui_aosp_volume_scale"
    const val AOSP_VOLUME_HIDE_IN_QS = "systemui_aosp_volume_hide_in_qs"
    const val NATIVE_LIVE_UPDATE = "systemui_native_live_update"
    const val C16_STACKED_SIGNAL = "systemui_c16_stacked_signal"
    const val FORCE_NATIVE_CLIPBOARD = "systemui_force_native_clipboard_overlay"
    const val AOSP_CHARGING_EFFECT = "systemui_aosp_charging_effect"
    const val AOSP_CHARGING_SOUND = "systemui_aosp_charging_sound"

    // coloros-mod System Font Replacement
    const val SYSTEM_FONT_ZH = "system_font_zh"
    const val SYSTEM_FONT_LATIN = "system_font_latin"
    const val SYSTEM_FONT_MONO = "system_font_mono"
    const val SYSTEM_FONT_EMOJI = "system_font_emoji"
    const val SYSTEM_FONT_DISABLE_REPLACEMENT = "system_font_disable_replacement"
}
