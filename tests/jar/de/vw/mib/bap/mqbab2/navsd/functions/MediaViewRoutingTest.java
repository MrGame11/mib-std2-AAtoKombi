package de.vw.mib.bap.mqbab2.navsd.functions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.aatokombi.MediaViewMode;
import de.aatokombi.TextScroller;
import de.vw.mib.asl.internal.androidauto.target.NavigationHandler;
import de.vw.mib.asl.internal.androidauto.target.ShmemMediaReader;
import de.vw.mib.bap.mqbab2.audiosd.functions.CurrentStationInfo;

/** Exercise the real publisher/lifecycle methods without a running HMI or timers. */
public final class MediaViewRoutingTest {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static Method method(String name, Class[] args) throws Exception {
        Method method = AANavReader.class.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }
    private static Field field(Class type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    public static void main(String[] args) throws Exception {
        Constructor ctor = AANavReader.class.getDeclaredConstructor(new Class[]{NavigationHandler.class});
        ctor.setAccessible(true);
        Object reader = ctor.newInstance(new Object[]{null});
        Field caps = ClusterCaps.class.getDeclaredField("_state");
        caps.setAccessible(true);
        Method navCapable = method("navCapable", new Class[0]);
        Method media = method("publishMedia", new Class[]{Integer.TYPE, Integer.TYPE, Integer.TYPE,
                String.class, Integer.TYPE, String.class});
        Method nav = method("publishNavsd", new Class[]{Integer.TYPE, Integer.TYPE, Integer.TYPE,
                Integer.TYPE, String.class, Integer.TYPE, Integer.TYPE});
        Method clear = method("clearActive", new Class[0]);
        Field cleared = AANavReader.class.getDeclaredField("cleared");
        cleared.setAccessible(true);

        caps.setInt(null, 2); // NON_NAV
        check(!((Boolean)navCapable.invoke(reader, new Object[0])).booleanValue(), "non-nav routing");
        media.invoke(reader, new Object[]{2, 1, 0, "First Street", 100, "100 m"});
        check(MediaViewMode.showNavigation(), "new route defaults to navigation");
        check(MediaViewMode.select(1, 0, 1), "non-nav wheel selects music");
        media.invoke(reader, new Object[]{2, 1, 0, "Second Street", 80, "80 m"});
        check(!MediaViewMode.showNavigation(), "updates preserve selected music");
        check("Second Street".equals(CurrentStationInfo.navSecondary), "hidden navigation keeps updating");

        // Real scrolling callbacks must preserve the wheel selection, even without a new AA frame.
        TextScroller street = (TextScroller)field(AANavReader.class, "streetScroller").get(reader);
        String longStreet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        street.setText(longStreet, 28, 72, 0L, 0);
        field(AANavReader.class, "streetActive").setBoolean(reader, true);
        cleared.setBoolean(reader, false);
        method("scrollStreetOnce", new Class[0]).invoke(reader, new Object[0]);
        check(!MediaViewMode.showNavigation(), "hidden street scrolling preserves music selection");
        check(longStreet.substring(1, 29).equals(CurrentStationInfo.navSecondary), "hidden street scroll advances");

        ShmemMediaReader musicReader = new ShmemMediaReader(null);
        TextScroller title = (TextScroller)field(ShmemMediaReader.class, "titleScroller").get(musicReader);
        title.setText(longStreet, 18, 72, 0L, 0);
        CurrentStationInfo.mediaTitle = title.getVisible();
        Method scrollTitle = ShmemMediaReader.class.getDeclaredMethod("scrollTitleOnce", new Class[0]);
        scrollTitle.setAccessible(true);
        scrollTitle.invoke(musicReader, new Object[0]);
        check(longStreet.substring(1, 19).equals(CurrentStationInfo.mediaTitle), "music scroll remains active during route");
        check(!MediaViewMode.showNavigation(), "title scrolling preserves music selection");
        check(MediaViewMode.select(2, 0, 1), "return to current navigation after scrolling");

        cleared.setBoolean(reader, false);
        clear.invoke(reader, new Object[0]);
        check(CurrentStationInfo.navPrimary == null, "route end clears media navigation");
        check(!MediaViewMode.select(1, 0, 1), "route end disables interception");

        media.invoke(reader, new Object[]{2, 1, 0, "Third Street", 60, "60 m"});
        MediaViewMode.select(1, 0, 1);
        caps.setInt(null, 1); // NAV: retain the normal navigation menu.
        check(((Boolean)navCapable.invoke(reader, new Object[0])).booleanValue(), "nav-capable routing unchanged");
        nav.invoke(reader, new Object[]{2, 1, 0, 0, "Nav Street", 50, 10});
        check(NavState.ACTIVE && "Nav Street".equals(NavState.street), "native nav output remains active");
        check(!MediaViewMode.select(1, 0, 1), "navsd publisher disables wheel view selection");
        check(MediaViewMode.showNavigation(), "navsd resets stale view choice");

        // The older PQ stack uses its own initialised navigation function as the routing signal.
        Field pqAudio = field(de.vw.mib.bap.mqbpq.audiosd.functions.CurrentStationInfo.class, "INSTANCE");
        Field pqNav = field(de.vw.mib.bap.mqbpq.navsd.functions.ManeuverDescriptor.class, "INSTANCE");
        pqAudio.set(null, new de.vw.mib.bap.mqbpq.audiosd.functions.CurrentStationInfo());
        check(!((Boolean)navCapable.invoke(reader, new Object[0])).booleanValue(), "PQ non-nav ignores MQB flag");
        caps.setInt(null, 2);
        pqNav.set(null, new de.vw.mib.bap.mqbpq.navsd.functions.ManeuverDescriptor());
        check(((Boolean)navCapable.invoke(reader, new Object[0])).booleanValue(), "PQ nav ignores MQB flag");
        pqAudio.set(null, null);
        pqNav.set(null, null);
        System.out.println("MediaViewRoutingTest PASS");
    }
}
