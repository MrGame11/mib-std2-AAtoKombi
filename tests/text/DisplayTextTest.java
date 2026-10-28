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

        s.setText("ABCDE", 4, 0L, 1000);       // exactly one character would be clipped
        eq("one hidden character stays whole", s.getVisible(), "ABCDE");
        yes("one hidden character does not scroll", !s.advance(5000L, 100, 1000));

        s.setText("ABCDEF", 4, 0L, 1000);      // two hidden characters enable the marquee
        eq("two hidden characters use a window", s.getVisible(), "ABCD");
        yes("two hidden characters scroll", s.advance(1000L, 100, 1000));

        s.setText("AAAAAA", 4, 0L, 100);
        yes("identical windows need no display update", !s.advance(100L, 100, 100));
    }

    private static void unicodeScrolling() {
        TextScroller s = new TextScroller();
        // A non-BMP character occupies two UTF-16 code units, but is one scrolling unit.
        s.setText("A\uD83C\uDFB5BCDE", 2, 0L, 100);
        eq("surrogate pair stays whole at right edge", s.getVisible(), "A\uD83C\uDFB5");
        s.advance(100L, 100, 100);
        eq("surrogate pair stays whole at left edge", s.getVisible(), "\uD83C\uDFB5B");
        s.advance(200L, 100, 100);
        eq("surrogate pair moves in one step", s.getVisible(), "BC");

        s.setText("Ae\u0301BCDE", 2, 0L, 100);
        eq("decomposed accent stays with letter", s.getVisible(), "Ae\u0301");
        s.advance(100L, 100, 100);
        eq("accent is not a separate scroll step", s.getVisible(), "e\u0301B");
        s.advance(200L, 100, 100);
        eq("no detached accent on left edge", s.getVisible(), "BC");

        s.setText("ABe\u0301", 2, 0L, 100);
        eq("one hidden accented letter stays whole", s.getVisible(), "ABe\u0301");
        yes("accent does not trigger two-character threshold", !s.advance(100L, 100, 100));
    }

    private static void transportLimit() {
        TextScroller s = new TextScroller();
        // PQ media has 48 payload BYTES. Twenty three-byte characters exceed that, despite being
        // below the old character window. Every frame must fit and the final letters must appear.
        String text = "\u4e00\u4e01\u4e02\u4e03\u4e04\u4e05\u4e06\u4e07\u4e08\u4e09"
                + "\u4e0a\u4e0b\u4e0c\u4e0d\u4e0e\u4e0f\u4e10\u4e11\u4e12\u4e13";
        s.setText(text, 20, 48, 0L, 100);
        eq("PQ byte cap sets first window", s.getVisible(), text.substring(0, 16));
        for (int i = 1; i <= 4; i++) {
            yes("PQ byte-limited step " + i, s.advance(i * 100L, 100, 100));
            eq("PQ byte-limited window " + i, s.getVisible(), text.substring(i, i + 16));
        }
        yes("PQ end pause", !s.advance(499L, 100, 100));
        yes("PQ wrap", s.advance(500L, 100, 100));
        eq("PQ wrap preserves text", s.getVisible(), text.substring(0, 16));

        // A late layout change must reset the window even when the metadata text is unchanged.
        s.setText(text, 20, 72, 600L, 100);
        eq("updated payload cap restores full fitting text", s.getVisible(), text);
        yes("fitting text remains static", !s.advance(800L, 100, 100));
        s.clear();
        eq("disconnect clears output", s.getVisible(), "");
        yes("cleared scroller is inert", !s.advance(1000L, 100, 100));
        s.setText(text, 20, 48, 1100L, 100);
        eq("same title after reconnect starts at beginning", s.getVisible(), text.substring(0, 16));
    }

    public static void main(String[] args) {
        progress();
        scrolling();
        unicodeScrolling();
        transportLimit();
        if (failed != 0) {
            System.out.println(failed + " test(s) failed");
            System.exit(1);
        }
        System.out.println("DisplayTextTest: all checks passed");
    }
}
