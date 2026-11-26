package dev.nonlog.colorosnative.font;

import android.graphics.Typeface;
import android.widget.TextView;
import dev.nonlog.colorosnative.xposed.MethodHook;
import java.util.Set;

/** Blocks ColorOS OEM font substitutions while retaining Android's requested family, style and fallback. */
public final class SystemFontReplacementHooks {
    private static boolean installed;

    public static void hook(ClassLoader loader) {
        if (installed) return;
        Class<?> utils = FontHookBridge.findClassIfExists("com.oplus.util.OplusFontUtils", loader);
        if (utils == null) return;
        try {
            Class<?> manager = FontHookBridge.findClassIfExists("com.oplus.font.OplusFontManager", loader);
            Class<?> paint = FontHookBridge.findClassIfExists("android.graphics.PaintExtImpl", loader);
            Class<?> text = FontHookBridge.findClassIfExists("android.widget.TextViewExtImpl", loader);
            Class<?> typeface = FontHookBridge.findClassIfExists("android.graphics.TypefaceExtImpl", loader);

            unchangedFont(utils, true, "flipTypeface");
            unchangedFont(manager, true, "flipTypeface");
            unchangedFont(paint, false, "flipTypeface");
            unchangedFont(text, false, "flipTypeface");
            unchangedFont(utils, false, "replaceTypefaceWithVariation", "createTypefaceWithVariation");
            fakeBold(utils, true, "replaceFakeBoldToColorMedium");
            fakeBold(manager, true, "replaceFakeBoldToMedium");
            fakeBold(text, false, "replaceFakeBoldToMedium");
            hook(utils, "shouldReplaceToOSans", new MethodHook() {
                @Override public void before(Param p) {
                    if (disabled()) p.setResult(false);
                }
            });

            MethodHook originalIndex = new MethodHook() {
                @Override public void before(Param p) {
                    if (disabled() && p.getArgs().length > 0) p.setResult(p.getArgs()[0]);
                }
            };
            hook(manager, "getTypefaceIndex", originalIndex);
            hook(text, "getTypefaceIndex", originalIndex);
            MethodHook falseWhenDisabled = new MethodHook() {
                @Override public void before(Param p) {
                    if (disabled()) p.setResult(false);
                }
            };
            for (Class<?> owner : new Class<?>[]{utils, manager}) {
                hook(owner, "isOplusSansEnabled", falseWhenDisabled);
                hook(owner, "isFlipFontUsed", falseWhenDisabled);
            }
            for (Class<?> owner : new Class<?>[]{utils, typeface}) {
                hook(owner, "createTypefaceForCustom", falseWhenDisabled);
                hook(owner, "replaceSysSans", new MethodHook() {
                    @Override public void before(Param p) {
                        if (disabled()) p.setResult(p.getArgs()[0]);
                    }
                });
            }
            block(utils, "setFlipFont", "setFlipFontWhenUserChange", "updateTypefaceInCurrProcess",
                    "doUpdateTypeface", "createIndividualTypefae",
                    "apendIndividualFontFamily", "initVariationFontVariable", "initFontUtil",
                    "updateConfigurationInUIMode", "updateFontVariationRes", "checkAndCorrectFlipFontLink");
            hook(utils, "updateThemeStoreFontConfig", falseWhenDisabled);
            hook(utils, "updateOSansConfig", falseWhenDisabled);
            hook(utils, "getNeedReplaceAllTypefaceApp", falseWhenDisabled);
            block(manager, "setFlipFont", "setFlipFontWhenUserChange", "updateTypefaceInCurrProcess",
                    "initVariationFontVariable", "initFontsForserializeFontMap", "updateConfigurationInUIMode",
                    "createFontLink", "deleteFontLink");
            block(typeface, "createIndividualTypefae", "initFontsForserializeFontMap");
            block(paint, "replaceTypeface", "hookOnSetTextSize");
            hook(paint, "hookOnFontVariationSettingsChanged", new MethodHook() {
                @Override public void before(Param p) {
                    if (disabled()) p.setResult(p.getArgs()[0]);
                }
            });

            Set<String> callers = Set.of("flipTypeface", "replaceTypeface", "replaceFakeBoldToColorMedium",
                    "replaceFakeBoldToMedium", "injectedByOemOS", "setTypeface", "setTypefaceWithoutWarning",
                    "getTypefaceIndex", "setTypefaceFromAttrs", "resolveStyleAndSetTypeface", "setInputType",
                    "setSystemFontMap", "getSystemDefaultTypeface", "initSystemDefaultTypefaces",
                    "hookOnSetTextSize", "hookOnFontVariationSettingsChanged", "createFromTypefaceWithVariation");
            for (String name : new String[]{"android.graphics.PaintExtImpl", "com.oplus.font.OplusFontManager",
                    "android.graphics.Paint", "android.widget.TextViewExtImpl", "android.widget.TextView",
                    "android.graphics.TypefaceExtImpl", "android.graphics.Typeface"}) {
                Class<?> owner = FontHookBridge.findClassIfExists(name, loader);
                if (owner == null) continue;
                for (java.lang.reflect.Method method : owner.getDeclaredMethods()) {
                    if (callers.contains(method.getName())) FontHookBridge.deoptimize(method);
                }
                if (name.equals("android.widget.TextView")) {
                    for (java.lang.reflect.Constructor<?> constructor : owner.getDeclaredConstructors()) FontHookBridge.deoptimize(constructor);
                }
            }
            installed = true;
            FontHookBridge.log("HOOK OK ColorOS font substitution controls");
        } catch (Throwable error) { FontHookBridge.log("HOOK FAIL ColorOS font substitution controls: " + error); }
    }

    private static boolean disabled() { return SystemFontHooks.fontReplacementDisabled(); }
    private static Typeface fromExtension(Object extension) {
        return extension == null ? null : (Typeface) FontHookBridge.callMethod(extension, "getTypeface");
    }
    private static void unchangedFont(Class<?> owner, boolean extension, String... names) {
        for (String name : names) hook(owner, name, new MethodHook() {
            @Override public void before(Param p) {
                if (!disabled()) return;
                Typeface font = extension ? fromExtension(p.getArgs()[0]) : (Typeface) p.getArgs()[0];
                p.setResult(font);
            }
        });
    }
    private static void fakeBold(Class<?> owner, boolean extension, String name) {
        hook(owner, name, new MethodHook() {
            @Override public void before(Param p) {
                if (!disabled()) return;
                if (p.getArgs()[0] == null) { p.setResult(null); return; }
                Typeface font = extension ? fromExtension(p.getArgs()[1]) : (Typeface) p.getArgs()[1];
                ((TextView) p.getArgs()[0]).setTypeface(font, (Integer) p.getArgs()[2]);
                p.setResult(null);
            }
        });
    }
    private static void block(Class<?> owner, String... names) {
        for (String name : names) hook(owner, name, new MethodHook() {
            @Override public void before(Param p) {
                if (disabled()) p.setResult(null);
            }
        });
    }
    private static void hook(Class<?> owner, String name, MethodHook callback) {
        if (owner != null) FontHookBridge.hookAllMethods(owner, name, callback);
    }
    private SystemFontReplacementHooks() {}
}
