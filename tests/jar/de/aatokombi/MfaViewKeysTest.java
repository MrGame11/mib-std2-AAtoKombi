package de.aatokombi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import de.vw.mib.asl.framework.api.dsiproxy.DSIProxy;
import de.vw.mib.asl.framework.api.dsiproxy.DSIServiceStateListener;
import org.dsi.ifc.keypanel.DSIKeyPanel;

/** Test actual callbacks and lifecycle with simulated DSI services and synthetic key IDs. */
public final class MfaViewKeysTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static final class Services implements InvocationHandler, MfaViewKeys.Environment {
        MfaKeyCodes keys = new MfaKeyCodes(4, 9, 100, 101, 1, new int[]{10, 14, 46, 47, 48, 49});
        boolean allow = true;
        boolean failNotification;
        boolean failResponse;
        int stateAdds, stateRemoves, responseAdds, responseRemoves, notifications, clears;
        final DSIKeyPanel panel = (DSIKeyPanel)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DSIKeyPanel.class}, this);
        final DSIProxy proxy = (DSIProxy)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DSIProxy.class}, this);
        public MfaKeyCodes codes() { return keys; }
        public int attribute() { return 25; }
        public int instance() { return 2; }
        public DSIProxy proxy() { return proxy; }
        public boolean canSelect() { return allow; }
        public Object invoke(Object object, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("addServiceStateListener")) {
                stateAdds++;
                check(((Integer)args[1]).intValue() == 2, "configured service instance");
                ((DSIServiceStateListener)args[2]).registered(DSIKeyPanel.class.getName(), 2);
            } else if (name.equals("removeServiceStateListener")) stateRemoves++;
            else if (name.equals("getService")) return panel;
            else if (name.equals("addResponseListener")) {
                responseAdds++;
                if (failResponse) throw new IllegalStateException("response listener failure");
            }
            else if (name.equals("removeResponseListener")) responseRemoves++;
            else if (name.equals("setNotification")) {
                notifications++;
                check(((Integer)args[0]).intValue() == 25, "only KEY2 subscription");
                if (failNotification) throw new IllegalStateException("notification failure");
            } else if (name.equals("clearNotification")) clears++;
            else throw new AssertionError("Unexpected DSI operation: " + name);
            return null;
        }
    }

    private static void key(MfaViewKeys listener, int keyboard, int key, int state, int validity) {
        listener.updateKey2(keyboard, key, state, 0, validity);
    }

    public static void main(String[] args) {
        check(MfaViewKeys.eligible(2, 6, 3, true, false), "AA media guidance eligible");
        check(!MfaViewKeys.eligible(1, 6, 3, true, false), "system off");
        check(!MfaViewKeys.eligible(2, 1, 3, true, false), "radio source");
        check(!MfaViewKeys.eligible(2, 6, 2, true, false), "other phone integration");
        check(!MfaViewKeys.eligible(2, 6, 3, false, false), "no AA guidance");
        check(!MfaViewKeys.eligible(2, 6, 3, true, true), "native output");

        Services services = new Services();
        MfaViewKeys listener = new MfaViewKeys(null, services);
        listener.start();
        listener.start();
        check(services.stateAdds == 1 && services.responseAdds == 1 && services.notifications == 1,
                "start is idempotent with synchronous service registration");
        listener.registered(DSIKeyPanel.class.getName(), 2);
        listener.registered(DSIKeyPanel.class.getName(), 3);
        listener.registered("another.Service", 2);
        check(services.responseAdds == 1, "duplicate and unrelated registrations ignored");

        MediaViewMode.guidance(false);
        key(listener, 4, 101, 1, 1);
        check(MediaViewMode.showNavigation(), "inactive route cannot select music");
        MediaViewMode.guidance(true);
        key(listener, 4, 101, 1, 1);
        check(!MediaViewMode.showNavigation(), "DOWN selects music");
        key(listener, 4, 101, 1, 1);
        check(!MediaViewMode.showNavigation(), "repeated DOWN keeps music");
        key(listener, 9, 100, 1, 1);
        check(MediaViewMode.showNavigation(), "UP selects navigation on second wheel keyboard");

        int[] excluded = new int[]{0, 1, 2, 10, 14, 46, 47, 48, 49, 42, 43, 50};
        for (int i = 0; i < excluded.length; i++) key(listener, 4, excluded[i], 1, 1);
        key(listener, 1, 101, 1, 1);
        key(listener, 4, 101, 1, 0);
        key(listener, 4, 101, 0, 1);
        key(listener, 4, 101, 2, 1);
        check(MediaViewMode.showNavigation(), "track/A/B/other keys, release, hold, invalid data ignored");
        services.allow = false;
        key(listener, 4, 101, 1, 1);
        check(MediaViewMode.showNavigation(), "source/context gate");
        services.allow = true;

        listener.unregistered(DSIKeyPanel.class.getName(), 2);
        key(listener, 4, 101, 1, 1);
        check(MediaViewMode.showNavigation(), "late callback after service loss ignored");
        listener.registered(DSIKeyPanel.class.getName(), 2);
        key(listener, 4, 101, 1, 1);
        check(!MediaViewMode.showNavigation(), "service reconnect reattaches");
        listener.stop();
        listener.stop();
        key(listener, 4, 100, 1, 1);
        listener.registered(DSIKeyPanel.class.getName(), 2);
        check(!MediaViewMode.showNavigation(), "late callback/registration after shutdown ignored");
        check(services.stateRemoves == 1 && services.responseRemoves == 2 && services.clears == 2,
                "each response listener and notification removed once");

        services.failNotification = true;
        listener.start();
        check(services.responseAdds == services.responseRemoves, "partial attach rolled back");
        services.failNotification = false;
        listener.registered(DSIKeyPanel.class.getName(), 2);
        key(listener, 4, 100, 1, 1);
        check(MediaViewMode.showNavigation(), "retry after notification failure");
        listener.stop();

        services.failResponse = true;
        listener.start();
        check(services.responseAdds == services.responseRemoves, "partially registered response rolled back");
        listener.stop();

        Services ambiguous = new Services();
        ambiguous.keys = new MfaKeyCodes(4, 9, 14, 100, 1, new int[]{14});
        new MfaViewKeys(null, ambiguous).start();
        check(ambiguous.stateAdds == 0, "UP aliasing a track key disables registration");
        check(!new MfaKeyCodes(4, 9, 100, 100, 1, new int[0]).valid, "identical UP/DOWN rejected");
        check(!new MfaKeyCodes(0, 0, 0, 0, 0, new int[0]).valid, "missing runtime constants rejected");
        System.out.println("MfaViewKeysTest PASS");
    }
}
