package de.aatokombi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.dsi.ifc.androidauto2.DSIAndroidAuto2;

/** Real focus command and cancellation lifecycle; no phone or DSI implementation is emulated. */
public final class NavCancellationTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static final class Sender implements NavCancellation.Transport, InvocationHandler {
        int calls;
        boolean available = true, fail;
        DSIAndroidAuto2 dsi = (DSIAndroidAuto2)Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{DSIAndroidAuto2.class}, this);
        public Object invoke(Object proxy, Method method, Object[] args) {
            check(method.getName().equals("navFocusNotification"), "only navigation focus is sent");
            check(((Integer)args[0]).intValue() == 1 && ((Boolean)args[1]).booleanValue(),
                    "stock native/unsolicited focus values");
            calls++;
            if (fail) throw new IllegalStateException();
            return null;
        }
        public boolean requestNativeNavigationFocus() {
            return NativeNavigationFocus.request(dsi, available, available);
        }
    }
    private static NavCancellation active(Sender sender) {
        NavCancellation cancel = new NavCancellation();
        cancel.attach(sender);
        cancel.observe(10, 1, 1, 42, 1000);
        return cancel;
    }
    public static void main(String[] args) {
        Sender sender = new Sender();
        check(!NativeNavigationFocus.request(null, true, true), "null DSI rejected");
        check(!NativeNavigationFocus.request(sender.dsi, false, true), "unregistered DSI rejected");
        check(!NativeNavigationFocus.request(sender.dsi, true, false), "disconnected phone rejected");
        check(sender.calls == 0, "unavailable DSI never receives a request");
        NavCancellation cancel = active(sender);
        check(cancel.request(1100) == NavCancellation.WAITING, "delivery is not stop confirmation");
        cancel.request(1150);
        check(sender.calls == 1, "pending request is not resent");
        cancel.observe(10, 2, 1, 42, 1200);
        check(cancel.state() == NavCancellation.WAITING, "unchanged sequence cannot confirm stop");
        cancel.observe(11, 3, 1, 42, 1300);
        check(cancel.state() == NavCancellation.WAITING, "rerouting is still active");
        cancel.observe(12, 1, 1, 42, 1400);
        check(cancel.state() == NavCancellation.WAITING, "next maneuver cannot confirm stop");
        cancel.observe(13, 2, 1, 42, 1500);
        check(cancel.state() == NavCancellation.STOPPED, "fresh same-session inactive confirms stop");
        cancel.tick(9000);
        check(cancel.state() == NavCancellation.STOPPED, "completion cannot time out later");

        cancel = active(sender); cancel.request(1100);
        cancel.observe(11, 0, 1, 42, 1200);
        check(cancel.state() == NavCancellation.STOPPED, "fresh unavailable also confirms no guidance");
        cancel = active(sender); cancel.request(1100);
        cancel.observe(10, 1, 0, 42, 1200);
        check(cancel.state() == NavCancellation.DISCONNECTED, "disconnect without seq change is not proof of phone stop");
        cancel = active(sender); cancel.request(1100);
        cancel.observe(1, 2, 1, 43, 1200);
        check(cancel.state() == NavCancellation.SESSION_CHANGED, "other session cannot confirm old request");
        cancel = active(sender); cancel.request(1100);
        cancel.observe(11, 9, 1, 42, 1200);
        check(cancel.state() == NavCancellation.WAITING, "unknown status is not a stop");
        cancel.tick(6099);
        check(cancel.state() == NavCancellation.WAITING, "five second grace");
        cancel.tick(6100);
        check(cancel.state() == NavCancellation.TIMED_OUT, "missing/frozen IPC times out");
        cancel.observe(12, 2, 1, 42, 6200);
        check(cancel.state() == NavCancellation.TIMED_OUT, "late frame cannot turn failure into success");
        cancel = active(sender); cancel.request(1100);
        cancel.observe(11, 2, 1, 42, 6100);
        check(cancel.state() == NavCancellation.TIMED_OUT, "deadline checked before accepting feedback");
        cancel = active(sender); cancel.request(1100); cancel.tick(1000);
        check(cancel.state() == NavCancellation.TIMED_OUT, "clock rollback cannot hang request");
        cancel = active(sender); cancel.request(1100); cancel.abandon();
        check(cancel.state() == NavCancellation.ABANDONED, "BAP reset abandons pending request");
        cancel = active(sender); cancel.request(1100); cancel.detach(new Sender());
        check(cancel.state() == NavCancellation.WAITING, "unrelated target cannot detach owner");
        cancel.detach(sender);
        check(cancel.state() == NavCancellation.UNAVAILABLE, "shutdown releases operation");
        cancel = active(sender); cancel.request(1100); cancel.attach(new Sender());
        check(cancel.state() == NavCancellation.UNAVAILABLE, "replacement target fails old operation");

        sender.available = false; cancel = active(sender);
        check(cancel.request(1100) == NavCancellation.SEND_FAILED, "unavailable transport fails immediately");
        sender.available = true; sender.fail = true; cancel = active(sender);
        check(cancel.request(1100) == NavCancellation.SEND_FAILED, "DSI exception contained");
        sender.fail = false; cancel = active(sender);
        int calls = sender.calls;
        check(cancel.request(2600) == NavCancellation.UNAVAILABLE, "stale reader does not control phone");
        check(sender.calls == calls, "stale observation sends nothing");
        cancel.observe(11, 3, 1, 42, 3000);
        check(cancel.request(3100) == NavCancellation.WAITING, "new request allowed during rerouting");
        cancel.observe(12, 2, 1, 42, 3200);
        check(cancel.request(3300) == NavCancellation.UNAVAILABLE, "inactive route is not stopped again");
        System.out.println("NavCancellationTest PASS");
    }
}
