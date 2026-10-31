package de.aatokombi;

import java.io.FileWriter;

/** RAM-only test diagnostics: fixed messages, no route, phone, song or personal data. */
final class NavCancelLog {
    private static int lines;
    static synchronized void write(String text) {
        if (!Config.TEST_LOG_NAV_CANCEL) return;
        FileWriter writer = null;
        try {
            writer = new FileWriter("/dev/shmem/aatokombi_nav_cancel.log", lines > 0 && lines < 128);
            if (lines >= 128) lines = 0;
            if (text.length() > 160) text = text.substring(0, 160);
            writer.write(System.currentTimeMillis() + " " + text + "\n");
            lines++;
        } catch (Throwable ignored) {
        } finally {
            if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
        }
    }
    private NavCancelLog() {}
}
