package de.vw.mib.bap.mqbab2.navsd.functions;

import de.aatokombi.Config;
import de.aatokombi.NavCancellation;
import de.vw.mib.asl.internal.androidauto.target.NavigationHandler;
import de.vw.mib.bap.mqbab2.common.api.stages.Function;
import de.vw.mib.bap.mqbab2.common.api.stages.BAPStageContext;
import de.vw.mib.bap.mqbab2.common.api.navigation.NavigationService;
import de.vw.mib.bap.mqbab2.common.api.timer.TimerService;
import de.vw.mib.bap.mqbab2.generated.navsd.serializer.RG_ActDeact_Result;
import de.vw.mib.bap.mqbab2.generated.navsd.serializer.RG_ActDeact_StartResult;
import de.vw.mib.bap.functions.MethodListener;
import de.vw.mib.bap.array.timer.Timer;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

/** Execute the real BAP handler + real IPC reader, using recording framework service fixtures. */
public final class NavCancellationRoutingTest implements InvocationHandler, NavCancellation.Transport {
    int nativeState, focusCalls, nativeStops, nativeStarts, timerStarts, timerStops;
    boolean alternatives = true;
    ArrayList<Integer> results = new ArrayList<Integer>();
    MethodListener listener = (MethodListener)proxy(MethodListener.class);
    Timer timer = (Timer)proxy(Timer.class);
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private Object proxy(Class type) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{type}, this);
    }
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        String name = method.getName();
        if (name.equals("getRouteGuidanceState")) return nativeState;
        if (name.equals("getNavigationStatus")) return 0;
        if (name.equals("getSuggestAlternativeRouteCalculation")) return alternatives;
        if (name.equals("setAlternativeRouteCalcSetting")) { alternatives = (Boolean)args[0]; return null; }
        if (name.equals("stopRouteGuidance")) { nativeStops++; return null; }
        if (name.equals("startRouteGuidance")) { nativeStarts++; return null; }
        if (name.equals("createTimer")) { check(((Integer)args[1]) == 120000, "original native timer is 120 seconds"); return timer; }
        if (name.equals("retrigger")) { timerStarts++; return null; }
        if (name.equals("stop")) { timerStops++; return null; }
        if (name.equals("dequeueBAPEntity")) return ((Class)args[1]).newInstance();
        if (name.equals("getMethodListener")) return listener;
        if (name.equals("result")) { results.add(((RG_ActDeact_Result)args[0]).rg_ActDeact_Result); return null; }
        if (name.equals("requestError")) { results.add(-((Integer)args[0])); return null; }
        if (name.equals("updateStages") || name.startsWith("add") || name.startsWith("remove")) return null;
        throw new AssertionError("unexpected framework call " + name);
    }
    public boolean requestNativeNavigationFocus() { focusCalls++; return true; }
    private static void snapshot(String text) throws Exception {
        FileWriter writer = new FileWriter("/dev/shmem/aa_nav");
        try { writer.write(text); } finally { writer.close(); }
    }
    private static void frame(long seq, int status) throws Exception {
        snapshot(seq + " " + status + " 2 1 0 0 100 10 0 1 42 Test Road\n");
    }
    private void run() throws Exception {
        new File("/dev/shmem").mkdirs();
        Field caps = ClusterCaps.class.getDeclaredField("_state");
        caps.setAccessible(true); caps.setInt(null, 1);
        Function.navigation = (NavigationService)proxy(NavigationService.class);
        Function.timers = (TimerService)proxy(TimerService.class);
        RGActDeact bap = new RGActDeact();
        bap.setDelegate((BAPStageContext)proxy(BAPStageContext.class));
        bap.init(null);
        bap.initialize(false);
        check(bap.getFunctionId() == 34, "correct BAP method");
        RG_ActDeact_StartResult stop = new RG_ActDeact_StartResult(); stop.controlType = 1;
        RG_ActDeact_StartResult start = new RG_ActDeact_StartResult(); start.controlType = 0; start.ci_Type = 4;
        Constructor ctor = AANavReader.class.getDeclaredConstructor(new Class[]{NavigationHandler.class});
        ctor.setAccessible(true);
        AANavReader reader = (AANavReader)ctor.newInstance(new Object[]{null});
        NavCancellation.INSTANCE.attach(this);

        // No AA guidance: preserve the original native stop/start/error/state-machine path.
        NavState.ACTIVE = false;
        bap.startResult(stop, listener);
        check(results.remove(0) == 4 && focusCalls == 0, "stock inactive result");
        nativeState = 1;
        bap.startResult(stop, listener);
        check(nativeStops == 1 && timerStarts == 1 && results.isEmpty(), "stock asynchronous native stop");
        nativeState = 0; bap.process(-1);
        check(results.remove(0) == 0 && bap.currentActionState == 0, "stock native stop completion");
        bap.startResult(start, listener);
        check(nativeStarts == 1 && !alternatives, "stock native home route starts with alternatives disabled");
        nativeState = 1; bap.process(-1);
        check(results.remove(0) == 0 && alternatives && focusCalls == 0, "stock native start completion and setting restored");
        nativeState = 0;

        frame(1, 1); reader.run();
        if (Config.TEST_FORCE_MEDIA_NAVIGATION || !Config.TEST_AA_NAV_CANCEL) {
            bap.startResult(stop, listener);
            check(results.remove(0) == 4 && focusCalls == 0, "media output/disabled feature does not intercept native BAP stop");
        } else {
            check(NavState.ACTIVE, "real reader published AA guidance");
            bap.startResult(stop, listener);
            check(focusCalls == 1 && results.isEmpty() && NavState.ACTIVE, "confirmed BAP stop sends focus, leaves guidance visible pending feedback");
            bap.process(-1);
            bap.processHMIEvent(75);
            bap.timerFired(timer);
            check(results.isEmpty(), "idle native engine, native error and timer cannot acknowledge AA stop");
            bap.startResult(stop, listener);
            check(results.remove(0) == 1 && focusCalls == 1, "duplicate BAP request does not resend focus");
            frame(2, 3); reader.run();
            check(results.isEmpty() && NavState.ACTIVE, "rerouting keeps request pending");
            frame(3, 2); reader.run();
            check(results.remove(0) == 0 && !NavState.ACTIVE, "real IPC reader clears route before successful BAP result");
            reader.run(); check(results.isEmpty(), "frozen stopped frame does not reply twice");

            frame(4, 1); reader.run(); bap.startResult(stop, listener);
            check(focusCalls == 2, "new route can be stopped independently");
            NavCancellation.INSTANCE.tick(System.currentTimeMillis() + NavCancellation.TIMEOUT_MS);
            snapshot("incomplete write"); reader.run();
            check(results.remove(0) == 1 && NavState.ACTIVE, "malformed IPC still completes timeout without hiding live route");
            frame(5, 1); reader.run();
            check(NavState.ACTIVE && results.isEmpty(), "timeout leaves subsequent guidance operational");
            bap.startResult(stop, listener);
            bap.abortResult(stop, listener);
            check(results.remove(0) == 2, "BAP transaction abort handled separately from guidance stop");
            frame(6, 2); reader.run();
            check(results.isEmpty(), "abandoned transaction never emits a second result");

            frame(7, 1); reader.run(); bap.startResult(stop, listener);
            snapshot("7 1 2 1 0 0 100 10 0 0 42 Test Road\n"); reader.run();
            check(results.remove(0) == 1 && !NavState.ACTIVE, "same-sequence disconnect clears MFA without claiming Maps stopped");
        }
        check(nativeStops == 1 && nativeStarts == 1, "AA stop never starts or stops a fabricated VW route");
        bap.uninitialize();
        check(timerStops == 1, "native timer disposed");
        NavCancellation.INSTANCE.detach(this);
        new File("/dev/shmem/aa_nav").delete();
        System.out.println("NavCancellationRoutingTest PASS");
    }
    public static void main(String[] args) throws Exception { new NavCancellationRoutingTest().run(); }
}
