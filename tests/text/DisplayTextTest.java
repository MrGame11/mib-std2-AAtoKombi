import de.aatokombi.DisplayText;
import de.aatokombi.TextScroller;

public final class DisplayTextTest {
    private static int failed = 0;

    private static void eq(String what, String got, String want) {
        if (!want.equals(got)) {
            failed++;
            System.out.println("FAIL " + what + ": got '" + got + "', want '" + want + "'");
        }
    }

    private static void yes(String what, boolean value) {
        if (!value) {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    private static void progress() {
        eq("unknown duration is live", DisplayText.progressLine(0, 0, 24), "LIVE");
        eq("negative duration is live", DisplayText.progressLine(5, -1, 24), "LIVE");
        eq("under an hour", DisplayText.formatDuration(83), "1:23");
        eq("one hour", DisplayText.formatDuration(3600), "1:00:00");
        eq("ten hours", DisplayText.formatDuration(36000), "10:00:00");

        String regular = DisplayText.progressLine(83, 225, 24);
        yes("regular progress fits 24", regular.length() <= 24);
        yes("regular progress keeps both times", regular.startsWith("1:23 ") && regular.endsWith(" 3:45"));

        String tenHours = DisplayText.progressLine(32400, 36000, 24);
        yes("ten-hour progress fits 24", tenHours.length() <= 24);
        yes("ten-hour progress keeps both times",
                tenHours.startsWith("9:00:00 ") && tenHours.endsWith(" 10:00:00"));

        String extreme = DisplayText.progressLine(Integer.MAX_VALUE, Integer.MAX_VALUE, 24);
        yes("extreme duration still fits", extreme.length() <= 24);
        eq("colon placeholder", DisplayText.cleanMetadata(":"), "");
        eq("real punctuation stays", DisplayText.cleanMetadata("Artist: Title"), "Artist: Title");
    }

    private static void scrolling() {
        TextScroller s = new TextScroller();
        s.setText("ABCDEFG", 4, 0L, 1000);
        eq("initial window", s.getVisible(), "ABCD");
        yes("initial pause", !s.advance(999L, 100, 1000));
        yes("first step", s.advance(1000L, 100, 1000));
        eq("first window", s.getVisible(), "BCDE");
        yes("second step", s.advance(1100L, 100, 1000));
        eq("second window", s.getVisible(), "CDEF");
        yes("last step", s.advance(1200L, 100, 1000));
        eq("last window", s.getVisible(), "DEFG");
        yes("end pause", !s.advance(2199L, 100, 1000));
        yes("wrap", s.advance(2200L, 100, 1000));
        eq("wrapped window", s.getVisible(), "ABCD");

        // Re-publishing the same phone metadata must not restart the marquee on every ~1 s status.
        s.setText("ABCDEFG", 4, 2250L, 1000);
        eq("same text preserves position", s.getVisible(), "ABCD");
        yes("start pause after wrap is preserved", !s.advance(3000L, 100, 1000));

        s.setText("short", 20, 0L, 1000);
        eq("short text is unchanged", s.getVisible(), "short");
        yes("short text never advances", !s.advance(5000L, 100, 1000));
    }

    public static void main(String[] args) {
        progress();
        scrolling();
        if (failed != 0) {
            System.out.println(failed + " test(s) failed");
            System.exit(1);
        }
        System.out.println("DisplayTextTest: all checks passed");
    }
}
