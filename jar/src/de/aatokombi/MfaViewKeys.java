package de.aatokombi;

import de.adi961.miblogger.MIBLogger;
import de.vw.mib.asl.framework.api.dsiproxy.DSIProxy;
import de.vw.mib.asl.framework.api.dsiproxy.DSIProxyFactory;
import de.vw.mib.asl.framework.api.dsiproxy.DSIServiceStateListener;
import de.vw.mib.asl.framework.internal.framework.ServiceManager;
import de.vw.mib.asl.internal.androidauto.target.NavigationHandler;
import de.vw.mib.bap.mqbab2.audiosd.functions.CurrentStationInfo;
import de.vw.mib.bap.mqbab2.common.api.APIFactory;
import de.vw.mib.bap.mqbab2.common.api.APIFactoryInterface;
import de.vw.mib.bap.mqbab2.navsd.functions.ClusterCaps;
import de.vw.mib.genericevents.Target;
import org.dsi.ifc.keypanel.DSIKeyPanel;
import org.dsi.ifc.keypanel.DSIKeyPanelListener;

/** Observe raw wheel UP/DOWN. Never intercept, synthesize or forward a media/track command. */
public final class MfaViewKeys implements DSIKeyPanelListener, DSIServiceStateListener {
    interface Environment {
        MfaKeyCodes codes() throws Exception;
        int attribute() throws Exception;
        int instance();
        DSIProxy proxy();
        boolean canSelect();
    }

    private static final class RuntimeEnvironment implements Environment {
        public MfaKeyCodes codes() throws Exception { return MfaKeyCodes.fromFirmware(); }
        public int attribute() throws Exception { return MfaKeyCodes.value(DSIKeyPanel.class, "ATTR_KEY2"); }
        public int instance() { return ServiceManager.configManagerDiag.getSystemKeyPanelInstance(); }
        public DSIProxy proxy() { return DSIProxyFactory.getDSIProxyAPI().getDSIProxy(); }
        public boolean canSelect() {
            APIFactoryInterface api = APIFactory.getAPIFactory();
            return eligible(api.getSystemService().getSystemOnOffState(),
                    api.getSoundService().getCurrentAudioComponent(),
                    api.getSmartphoneIntegrationService().getSmarphoneIntegrationActiveConnectionType(),
                    NavigationHandler.aaRouteGuidanceActive, ClusterCaps.useNativeNavigation());
        }
    }

    private final Target target;
    private final Environment environment;
    private DSIProxy proxy;
    private DSIKeyPanel panel;
    private MfaKeyCodes codes;
    private int instance;
    private int attribute;
    private boolean started;
    private boolean responseAdded;

    public MfaViewKeys(Target target) { this(target, new RuntimeEnvironment()); }

    MfaViewKeys(Target target, Environment environment) {
        this.target = target;
        this.environment = environment;
    }

    public synchronized void start() {
        if (started || !Config.MFA_UP_DOWN_SWITCH || !Config.SHOW_NAV || !Config.SHOW_MEDIA) return;
        try {
            codes = environment.codes();
            if (!codes.valid) throw new IllegalStateException("ambiguous runtime key constants");
            attribute = environment.attribute();
            if (attribute <= 0) throw new IllegalStateException("invalid KEY2 notification id");
            instance = environment.instance();
            proxy = environment.proxy();
            started = true; // service registration may synchronously call registered().
            proxy.addServiceStateListener(DSIKeyPanel.class, instance, this);
            diagnostic("START instance=" + instance + " " + codes
                    + " forceMedia=" + Config.TEST_FORCE_MEDIA_NAVIGATION);
        } catch (Throwable failure) {
            stop();
            diagnostic("DISABLED " + failure.getClass().getName() + " codes=" + codes);
        }
    }

    public synchronized void stop() {
        boolean wasStarted = started;
        started = false;
        detach();
        if (wasStarted && proxy != null) {
            try { proxy.removeServiceStateListener(DSIKeyPanel.class, instance, this); }
            catch (Throwable ignored) {}
        }
    }

    public synchronized void registered(String name, int deviceInstance) {
        if (!started || deviceInstance != instance || !DSIKeyPanel.class.getName().equals(name)
                || panel != null) return;
        try {
            panel = (DSIKeyPanel)proxy.getService(target, DSIKeyPanel.class, instance);
            // Roll back even if the proxy registers the listener and then reports an error.
            responseAdded = true;
            proxy.addResponseListener(target, DSIKeyPanelListener.class, instance, this);
            panel.setNotification(attribute, this);
            diagnostic("ATTACHED instance=" + instance);
        } catch (Throwable failure) {
            detach();
            diagnostic("ATTACH FAILED " + failure.getClass().getName());
        }
    }

    public synchronized void unregistered(String name, int deviceInstance) {
        if (deviceInstance == instance && DSIKeyPanel.class.getName().equals(name)) {
            detach();
            diagnostic("DETACHED instance=" + instance);
        }
    }

    private void detach() {
        if (panel != null) {
            try { panel.clearNotification(attribute, this); } catch (Throwable ignored) {}
            panel = null;
        }
        if (responseAdded && proxy != null) {
            try { proxy.removeResponseListener(target, DSIKeyPanelListener.class, instance, this); }
            catch (Throwable ignored) {}
        }
        responseAdded = false;
    }

    static boolean eligible(int systemState, int audioComponent, int connectionType,
                            boolean guiding, boolean nativeOutput) {
        return Config.MFA_UP_DOWN_SWITCH && Config.SHOW_NAV && Config.SHOW_MEDIA
                && systemState > 1 && audioComponent == 6 && connectionType == 3
                && guiding && !nativeOutput;
    }

    public synchronized void updateKey2(int keyboard, int key, int state, int character, int validity) {
        if (!started || panel == null || codes == null || !codes.isWheel(keyboard)) return;
        int direction = codes.direction(keyboard, key, state, validity);
        boolean selected = false;
        try {
            if (direction != 0 && environment.canSelect()) {
                selected = MediaViewMode.selectUpDown(direction > 0);
                if (selected) CurrentStationInfo.pokeNav();
            }
        } catch (Throwable ignored) {
            // An unavailable service leaves the stock key path and the current view untouched.
        }
        // Numeric data only. No raw character, track, device name or personal data is logged.
        MfaKeyLog.write("KEY " + codes + " eventKbd=" + keyboard + " key=" + key + " state="
                + state + " validity=" + validity + " selected=" + selected
                + " view=" + (MediaViewMode.showNavigation() ? "nav" : "media"));
    }

    private static void diagnostic(String text) {
        MfaKeyLog.write(text);
        try { MIBLogger.getInstance().info("MFA keys: " + text); } catch (Throwable ignored) {}
    }

    public void asyncException(int error, String message, int request) {
        diagnostic("DSI error=" + error + " request=" + request);
    }
    public void updateEncoder2(int a, int b, int c, int d, int e) {}
    public void updateDisplayTurnMechStatus(int a, int b) {}
    public void updateRecognizerLanguage2(int a, String b, int c, int d) {}
    public void updateRecognizerMode(int a, int b, int c) {}
    public void updateCharacterEvent2(int a, String[] b, int[] c, int d) {}
    public void updateGesture2(int a, int b, int c, boolean d, int e, int f, int g, int h, int i, int j) {}
    public void genericSettingResponse(int a, int b, int c) {}
    public void updateProximity(int a, int b, int c) {}
    public void updateAdvancedProximity(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {}
    public void lastKey(int a, int b, int c) {}
    public void updateKeyboardType(int a, int b) {}
    public void updateTouchSensitiveArea(int a, int b, int c, int d, int e, int f) {}
    public void getVersionInfo(int a, int b, String c) {}
    public void updateInputPanelReady(int a, int b, int c) {}
    public void getProperty(int a, int b, int c, int d, byte[] e) {}
}
