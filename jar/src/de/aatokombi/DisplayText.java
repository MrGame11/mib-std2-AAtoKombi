package de.aatokombi;

/** Text formatting shared by the MQB and PQ media views. */
public final class DisplayText {
    private static final int DEFAULT_BAR_WIDTH = 12;

    private DisplayText() {}

    /**
     * Remove exact placeholder values emitted by some streaming apps. Real punctuation inside a
     * title is preserved; only a field containing nothing but the placeholder is treated as empty.
     */
    public static String cleanMetadata(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.equals(":")) return "";
        if (trimmed.equals("-")) return "";
        return value;
    }

    /**
     * A fixed-width-friendly playback line. Unknown duration means a live stream. For long-form
     * audio the bar shrinks before either time is sacrificed; if both times leave no useful bar,
     * elapsed/total is used instead.
     */
    public static String progressLine(int posSec, int durSec, int maxChars) {
        if (durSec <= 0) return "LIVE";
        if (posSec < 0) posSec = 0;
        if (posSec > durSec) posSec = durSec;
        if (maxChars < 1) maxChars = 1;

        String elapsed = formatDuration(posSec);
        String total = formatDuration(durSec);
        int barWidth = maxChars - elapsed.length() - total.length() - 2;
        if (barWidth > DEFAULT_BAR_WIDTH) barWidth = DEFAULT_BAR_WIDTH;
        if (barWidth >= 3) {
            int marker = (int)((long)posSec * (barWidth - 1) / durSec);
            StringBuffer out = new StringBuffer(maxChars);
            out.append(elapsed).append(' ');
            for (int i = 0; i < barWidth; i++) out.append(i == marker ? '|' : '-');
            out.append(' ').append(total);
            return out.toString();
        }

        String times = elapsed + "/" + total;
        if (times.length() <= maxChars) return times;
        return total.length() <= maxChars ? total : total.substring(0, maxChars);
    }

    /** m:ss below one hour, h:mm:ss from one hour up. */
    public static String formatDuration(int sec) {
        if (sec < 0) sec = 0;
        int hours = sec / 3600;
        int minutes = (sec % 3600) / 60;
        int seconds = sec % 60;
        if (hours == 0) return minutes + ":" + two(seconds);
        return hours + ":" + two(minutes) + ":" + two(seconds);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
