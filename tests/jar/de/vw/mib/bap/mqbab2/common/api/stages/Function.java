package de.vw.mib.bap.mqbab2.common.api.stages;

import de.vw.mib.bap.functions.BAPFunctionControllerDelegate;
import de.vw.mib.bap.mqbab2.common.api.navigation.NavigationService;
import de.vw.mib.bap.mqbab2.common.api.timer.TimerService;
import de.vw.mib.bap.mqbab2.common.api.addressbook.AddressbookService;

/** Host-only framework fixture for executing the real RGActDeact shadow with recording services.
 * Never compiled into the mod. These are service accessors, not an implementation of BAP or VW nav.
 */
public abstract class Function implements BAPStage {
    protected BAPStageContext context;
    public static NavigationService navigation;
    public static TimerService timers;
    public void setDelegate(BAPStageContext value) { context = value; }
    protected final BAPFunctionControllerDelegate getDelegate() { return context; }
    protected NavigationService getNavigationService() { return navigation; }
    protected TimerService getTimerService() { return timers; }
    protected AddressbookService getAddressbookService() { throw new UnsupportedOperationException(); }
}
