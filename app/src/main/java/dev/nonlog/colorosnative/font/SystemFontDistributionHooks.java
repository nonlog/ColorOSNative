package dev.nonlog.colorosnative.font;

import android.graphics.Typeface;
import android.os.Parcel;
import dev.nonlog.colorosnative.xposed.MethodHook;
import java.util.LinkedHashMap;
import java.util.Map;

/** Suppresses OEM font replacement instructions in system_server. */
public final class SystemFontDistributionHooks {
    private static boolean installed;

    public static void hook(ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> extra = FontHookBridge.findClass("oplus.content.res.OplusExtraConfiguration", loader);
            java.lang.reflect.Constructor<?> constructor = extra.getDeclaredConstructor();
            constructor.setAccessible(true);
            FontHookBridge.hookChainMethod(extra.getDeclaredMethod("writeToParcel", Parcel.class, int.class), chain -> {
                if (!SystemFontHooks.fontReplacementDisabled()) return chain.proceed();
                Object copy = constructor.newInstance();
                FontHookBridge.callMethod(copy, "setTo", chain.getThisObject());
                int variation = FontHookBridge.getIntField(copy, "mFontVariationSettings");
                FontHookBridge.setIntField(copy, "mFontVariationSettings", disabledVariation(variation));
                FontHookBridge.setIntField(copy, "mFlipFont", 0);
                FontHookBridge.setIntField(copy, "mFontUserId", -1);
                return chain.proceedWith(copy);
            });
            FontHookBridge.hookAllMethods(Typeface.class, "serializeFontMap", new MethodHook() {
                @Override public void before(Param p) {
                    if (!SystemFontHooks.fontReplacementDisabled() || !(p.getArgs()[0] instanceof Map<?, ?> original)) return;
                    Map<Object, Object> copy = new LinkedHashMap<>(original);
                    copy.remove("individual-font");
                    p.getArgs()[0] = copy;
                }
            });
            installed = true;
            FontHookBridge.log("HOOK OK system font configuration distribution");
        } catch (Throwable error) { FontHookBridge.log("HOOK FAIL system font configuration distribution: " + error); }
    }

    static int disabledVariation(int original) {
        return original & ~0xf000;
    }

    private SystemFontDistributionHooks() {}
}
