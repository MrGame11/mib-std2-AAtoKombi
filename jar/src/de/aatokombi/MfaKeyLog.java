package de.aatokombi;

import java.io.FileWriter;

/** Test diagnostics only: at most 128 lines, 240 message characters plus timestamp each, in RAM. */
final class MfaKeyLog {
    private static int lines;
    static synchronized void write(String text) {
        if (!Config.TEST_LOG_MFA_KEYS) return;
        FileWriter writer = null;
        try {
            writer = new FileWriter("/dev/shmem/aatokombi_keys.log", lines > 0 && lines < 128);
            if (lines >= 128) lines = 0;
            if (text.length() > 240) text = text.substring(0, 240);
            writer.write(System.currentTimeMillis() + " " + text + "\n");
            lines++;
        } catch (Throwable ignored) {
            // Logging must never affect key handling or HMI startup.
        } finally {
            if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
        }
    }
    private MfaKeyLog() {}
}
