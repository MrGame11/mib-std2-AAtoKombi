package de.aatokombi;

/**
 * Small stateful character-window marquee for text-only BAP displays. Surrogate pairs and following
 * combining marks stay together. Width is an estimate in character units, not measured pixels.
 * The target VM is Java 1.3; keep this class free of newer language/library features.
 */
public final class TextScroller {
    // One clipped character is not worth animating. Keep sending the complete static string and let
    // the cluster clip it naturally; start the marquee only when at least two characters are hidden.
    private static final int MIN_HIDDEN_CHARS = 2;

    private String text = "";
    private String visible = "";
    private int width = 1;
    private int byteLimit = Integer.MAX_VALUE;
    private int[] boundaries = new int[]{ 0 };
    private int[] byteOffsets = new int[]{ 0 };
    private int count = 0;
    private int maxOffset = 0;
    private boolean scrolling = false;
    private int offset = 0;
    private long nextMoveMs = 0L;

    /** Set a new source string. Repeated calls with the same text and width preserve the position. */
    public synchronized void setText(String value, int windowWidth, long nowMs, int edgePauseMs) {
        setText(value, windowWidth, Integer.MAX_VALUE, nowMs, edgePauseMs);
    }

    /** Also respect the active BAP field's UTF-8 payload capacity (which is not its pixel width). */
    public synchronized void setText(String value, int windowWidth, int maxBytes,
            long nowMs, int edgePauseMs) {
        if (value == null) value = "";
        if (windowWidth < 1) windowWidth = 1;
        if (maxBytes < 1) maxBytes = 1;
        if (value.equals(this.text) && windowWidth == this.width && maxBytes == this.byteLimit) return;
        this.text = value;
        this.width = windowWidth;
        this.byteLimit = maxBytes;
        this.boundaries = new int[value.length() + 1];
        this.byteOffsets = new int[value.length() + 1];
        this.count = 0;
        boolean unitsFit = true;
        for (int start = 0; start < value.length();) {
            int end = unitEnd(value, start);
            int bytes = utf8Length(value.substring(start, end));
            if (bytes > maxBytes) unitsFit = false;
            this.count++;
            this.boundaries[this.count] = end;
            this.byteOffsets[this.count] = this.byteOffsets[this.count - 1] + bytes;
            start = end;
        }
        this.maxOffset = 0;
        while (this.maxOffset < this.count &&
                (this.count - this.maxOffset > this.width ||
                 this.byteOffsets[this.count] - this.byteOffsets[this.maxOffset] > this.byteLimit)) {
            this.maxOffset++;
        }
        // A pathological combining sequence may not fit even alone. Leave it to the stock static
        // formatter instead of tearing it apart or producing empty scrolling frames.
        this.scrolling = unitsFit && this.count - windowEnd(0) >= MIN_HIDDEN_CHARS;
        this.offset = 0;
        this.visible = windowAt(0);
        this.nextMoveMs = nowMs + positive(edgePauseMs, 1);
    }

    /** Advance at most one character. Returns true only when the displayed window changed. */
    public synchronized boolean advance(long nowMs, int stepMs, int edgePauseMs) {
        if (!this.scrolling || nowMs < this.nextMoveMs) return false;
        String previous = this.visible;

        if (this.offset < this.maxOffset) {
            this.offset++;
            this.visible = windowAt(this.offset);
            this.nextMoveMs = nowMs + positive(this.offset == this.maxOffset ? edgePauseMs : stepMs, 1);
        } else {
            this.offset = 0;
            this.visible = windowAt(0);
            this.nextMoveMs = nowMs + positive(edgePauseMs, 1);
        }
        return !previous.equals(this.visible);
    }

    public synchronized String getVisible() {
        return this.visible;
    }

    public synchronized void clear() {
        this.text = "";
        this.visible = "";
        this.offset = 0;
        this.count = 0;
        this.maxOffset = 0;
        this.scrolling = false;
        this.boundaries = new int[]{ 0 };
        this.byteOffsets = new int[]{ 0 };
        this.nextMoveMs = 0L;
    }

    private String windowAt(int start) {
        if (!this.scrolling) return this.text;
        return this.text.substring(this.boundaries[start], this.boundaries[windowEnd(start)]);
    }

    private int windowEnd(int start) {
        int end = start;
        while (end < this.count && end - start < this.width &&
                this.byteOffsets[end + 1] - this.byteOffsets[start] <= this.byteLimit) end++;
        return end;
    }

    private static int unitEnd(String value, int start) {
        int end = start + 1;
        char first = value.charAt(start);
        if (first >= '\uD800' && first <= '\uDBFF' && end < value.length()) {
            char next = value.charAt(end);
            if (next >= '\uDC00' && next <= '\uDFFF') end++;
        }
        while (end < value.length()) {
            int type = Character.getType(value.charAt(end));
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) break;
            end++;
        }
        return end;
    }

    private static int utf8Length(String value) {
        try {
            // Use the same encoding as BAPString on the target VM.
            return value.getBytes("UTF-8").length;
        } catch (java.io.UnsupportedEncodingException e) {
            return value.length() * 3;
        }
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
