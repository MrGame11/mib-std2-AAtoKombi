package de.aatokombi;
public final class MediaViewModeTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        MediaViewMode.guidance(false);
        check(!MediaViewMode.selectUpDown(true));
        MediaViewMode.guidance(true);
        check(MediaViewMode.showNavigation());
        check(MediaViewMode.selectUpDown(true));
        check(!MediaViewMode.showNavigation());
        check(MediaViewMode.selectUpDown(true));
        MediaViewMode.guidance(true); // new guidance snapshot / rerouting preserves the view
        check(!MediaViewMode.showNavigation());
        check(MediaViewMode.selectUpDown(false));
        check(MediaViewMode.showNavigation());
        MediaViewMode.selectUpDown(true);
        MediaViewMode.guidance(false); // end, disconnect, source/session change
        check(MediaViewMode.showNavigation());
        check(!MediaViewMode.selectUpDown(false));
        MediaViewMode.guidance(true);
        check(MediaViewMode.showNavigation());
        System.out.println("MediaViewModeTest PASS");
    }
}
