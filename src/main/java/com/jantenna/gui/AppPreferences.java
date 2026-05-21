package com.jantenna.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public final class AppPreferences {

    private static final Preferences PREFS = Preferences.userNodeForPackage(AppPreferences.class);

    private static final String KEY_LAST_INPUT_DIR  = "lastInputDir";
    private static final String KEY_LAST_OUTPUT_DIR = "lastOutputDir";
    private static final String KEY_RECENT_PREFIX   = "recent_";
    private static final int    MAX_RECENT           = 5;

    private AppPreferences() { }

    public static String getLastInputDir() {
        return PREFS.get(KEY_LAST_INPUT_DIR, null);
    }

    public static void setLastInputDir(String path) {
        PREFS.put(KEY_LAST_INPUT_DIR, path);
    }

    public static String getLastOutputDir() {
        return PREFS.get(KEY_LAST_OUTPUT_DIR, null);
    }

    public static void setLastOutputDir(String path) {
        PREFS.put(KEY_LAST_OUTPUT_DIR, path);
    }

    public static List<String> getRecentFiles() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String val = PREFS.get(KEY_RECENT_PREFIX + i, null);
            if (val != null) result.add(val);
        }
        return result;
    }

    public static void addRecentFile(String path) {
        List<String> recent = new ArrayList<>(getRecentFiles());
        recent.remove(path);
        recent.add(0, path);
        if (recent.size() > MAX_RECENT) {
            recent = recent.subList(0, MAX_RECENT);
        }
        for (int i = 0; i < recent.size(); i++) {
            PREFS.put(KEY_RECENT_PREFIX + i, recent.get(i));
        }
        for (int i = recent.size(); i < MAX_RECENT; i++) {
            PREFS.remove(KEY_RECENT_PREFIX + i);
        }
    }
}
