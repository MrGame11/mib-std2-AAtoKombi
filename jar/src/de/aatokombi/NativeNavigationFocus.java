package de.aatokombi;

import org.dsi.ifc.androidauto2.Constants;
import org.dsi.ifc.androidauto2.DSIAndroidAuto2;

/** Same DSI command as the stock NavigationListener when VW route guidance becomes active. */
public final class NativeNavigationFocus {
    public static boolean request(DSIAndroidAuto2 dsi, boolean registered, boolean connected) {
        if (dsi == null || !registered || !connected) return false;
        dsi.navFocusNotification(Constants.NAVFOCUS_NATIVE, true);
        return true;
    }
    private NativeNavigationFocus() {}
}
