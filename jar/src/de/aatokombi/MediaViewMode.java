package de.aatokombi;

/** Display choice only. No media command or source change is sent. */
public final class MediaViewMode {
    private static boolean active;
    private static boolean music;

    public static synchronized void guidance(boolean value) {
        if (!value || !active) music = false;
        active = value;
    }

    public static synchronized boolean showNavigation() { return !music; }

    /** A confirmed direct UP/DOWN event selects a view. BAP NEXT/PREVIOUS are not inputs here. */
    public static synchronized boolean selectUpDown(boolean down) {
        if (!active) return false;
        music = down;
        return true;
    }

    private MediaViewMode() {}
}
