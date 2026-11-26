package dev.nonlog.colorosnative.font;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import dev.nonlog.colorosnative.xposed.ModuleRuntime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NativeFontHooks {
    private static boolean active;
    private static native boolean configure(String[] paths, int[] descriptors);
    public static boolean isActive() { return active; }

    public static synchronized void hook() {
        if (active || android.os.Build.VERSION.SDK_INT < 29) return;
        List<ParcelFileDescriptor> descriptors = new ArrayList<>();
        try {
            SharedPreferences prefs = ModuleRuntime.fontPrefs();
            if (prefs == null) return;
            String manifest = prefs.getString("native_manifest", "");
            if (manifest.isEmpty()) return;
            List<String> paths = new ArrayList<>();
            List<String> names = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new ParcelFileDescriptor.AutoCloseInputStream(ModuleRuntime.openRemoteFile(manifest)), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\\t", -1);
                    if (fields.length != 2 || !fields[0].startsWith("/")
                            || !fields[1].matches("native(font|cfg)_[0-9a-f]{64}\\.(font|xml)"))
                        throw new IOException("Invalid native font snapshot");
                    paths.add(fields[0]); names.add(fields[1]);
                    if (paths.size() > 128) throw new IOException("Native font snapshot too large");
                }
            }
            int[] fds = new int[names.size()];
            for (int i = 0; i < names.size(); i++) {
                ParcelFileDescriptor fd = ModuleRuntime.openRemoteFile(names.get(i));
                descriptors.add(fd); fds[i] = fd.getFd();
            }
            System.loadLibrary("coloros_font_bridge");
            active = configure(paths.toArray(new String[0]), fds);
            if (!active) throw new IOException("Native font bridge init unavailable");
        } catch (Throwable error) {
            FontHookBridge.log("native font setup skipped: " + error);
        } finally {
            for (ParcelFileDescriptor fd : descriptors) try { fd.close(); } catch (IOException ignored) {}
        }
    }

    private NativeFontHooks() {}
}
