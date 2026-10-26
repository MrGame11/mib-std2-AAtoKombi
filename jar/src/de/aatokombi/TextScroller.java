package de.aatokombi;

/**
 * Small stateful character-window marquee for the text-only BAP displays. It deliberately moves by
 * Java characters instead of bytes, so accented names are not split in the middle. The target VM is
 * Java 1.3; keep this class free of newer language/library features.
 */
public final class TextScroller {
    private String text = "";
    private String visible = "";
    private int width = 1;
    private int offset = 0;
    private long nextMoveMs = 0L;

    /** Set a new source string. Repeated calls with the same text and width preserve the position. */
    public synchronized void setText(String value, int windowWidth, long nowMs, int edgePauseMs) {
        if (value == null) value = "";
        if (windowWidth < 1) windowWidth = 1;
        if (value.equals(this.text) && windowWidth == this.width) return;
        this.text = value;
        this.width = windowWidth;
        this.offset = 0;
        this.visible = windowAt(0);
        this.nextMoveMs = nowMs + positive(edgePauseMs, 1);
    }

    /** Advance at most one character. Returns true only when the displayed window changed. */
    public synchronized boolean advance(long nowMs, int stepMs, int edgePauseMs) {
        int max = this.text.length() - this.width;
        if (max <= 0 || nowMs < this.nextMoveMs) return false;

        if (this.offset < max) {
            this.offset++;
            this.visible = windowAt(this.offset);
            this.nextMoveMs = nowMs + positive(this.offset == max ? edgePauseMs : stepMs, 1);
        } else {
            this.offset = 0;
            this.visible = windowAt(0);
            this.nextMoveMs = nowMs + positive(edgePauseMs, 1);
        }
        return true;
    }

    public synchronized String getVisible() {
        return this.visible;
    }

    public synchronized void clear() {
        this.text = "";
        this.visible = "";
        this.offset = 0;
        this.nextMoveMs = 0L;
    }

    private String windowAt(int start) {
        if (this.text.length() <= this.width) return this.text;
        int end = start + this.width;
        if (end > this.text.length()) end = this.text.length();
        return this.text.substring(start, end);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
