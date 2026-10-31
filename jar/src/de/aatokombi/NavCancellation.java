package de.aatokombi;

/**
 * One bounded, acknowledged AA navigation-focus request. No fabricated native route and no
 * local-only suppression of AA guidance. Only a NEW inactive/unavailable frame in the same
 * phone session confirms completion; focus delivery alone is not a route-stop acknowledgement.
 */
public final class NavCancellation {
    public interface Transport {
        boolean requestNativeNavigationFocus();
    }

    public static final int IDLE = 0, WAITING = 1, STOPPED = 2, TIMED_OUT = 3,
            DISCONNECTED = 4, SESSION_CHANGED = 5, SEND_FAILED = 6, UNAVAILABLE = 7,
            ABANDONED = 8;
    public static final long TIMEOUT_MS = 5000L;
    public static final NavCancellation INSTANCE = new NavCancellation();

    private Transport transport;
    private int state = IDLE;
    private long sequence = -1L, session = -1L, observedAt;
    private int status = -1, connected;
    private boolean observed;
    private long requestedAt, requestedSequence, requestedSession;

    public synchronized void attach(Transport value) {
        if (transport != value && state == WAITING) finish(UNAVAILABLE, "TRANSPORT_CHANGED");
        transport = value;
    }

    public synchronized void detach(Transport value) {
        if (transport != value) return;
        transport = null;
        observed = false;
        if (state == WAITING) finish(UNAVAILABLE, "TRANSPORT_REMOVED");
    }

    public synchronized int request(long now) {
        if (state == WAITING) return state; // never resend while an operation is outstanding
        if (transport == null || !observed || connected != 1 || (status != 1 && status != 3)
                || now < observedAt || now - observedAt > 1500L) {
            finish(UNAVAILABLE, "NO_ACTIVE_AA_SESSION");
            return state;
        }
        requestedAt = now;
        requestedSequence = sequence;
        requestedSession = session;
        state = WAITING; // set before dispatch, including a possible synchronous callback
        try {
            if (!transport.requestNativeNavigationFocus()) {
                if (state == WAITING) finish(SEND_FAILED, "SEND_UNAVAILABLE");
            } else {
                NavCancelLog.write("FOCUS_REQUEST_SENT native=1 unsolicited=true");
            }
        } catch (Throwable failure) {
            if (state == WAITING) finish(SEND_FAILED, "SEND_FAILED");
        }
        return state;
    }

    /** Called for every parsed IPC snapshot, before the reader's unchanged-sequence shortcut. */
    public synchronized void observe(long seq, int navStatus, int connection, long epoch, long now) {
        tick(now);
        sequence = seq;
        session = epoch;
        status = navStatus;
        connected = connection;
        observedAt = now;
        observed = true;
        if (state != WAITING) return;
        if (epoch != requestedSession) finish(SESSION_CHANGED, "SESSION_CHANGED_NO_CONFIRMATION");
        else if (connection == 0) finish(DISCONNECTED, "DISCONNECTED_NO_CONFIRMATION");
        else if (connection == 1 && seq != requestedSequence && (navStatus == 0 || navStatus == 2))
            finish(STOPPED, "STOP_CONFIRMED");
    }

    /** Also called with missing/malformed/frozen IPC, so loss of input cannot hang BAP forever. */
    public synchronized void tick(long now) {
        if (state == WAITING && (now < requestedAt || now - requestedAt >= TIMEOUT_MS))
            finish(TIMED_OUT, "TIMEOUT_NO_STOP_CONFIRMATION");
    }

    public synchronized int state() { return state; }

    /** A BAP transaction can be abandoned; an already transmitted focus request cannot be undone. */
    public synchronized void abandon() {
        if (state == WAITING) finish(ABANDONED, "TRANSACTION_ABANDONED");
    }

    private void finish(int result, String diagnostic) {
        state = result;
        NavCancelLog.write(diagnostic);
    }
}
