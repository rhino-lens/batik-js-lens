package io.github.rhinolens;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Per-thread stack of open executions, and the last finished report. All methods are failure-safe. */
final class ExecutionTracker {

    private static final ThreadLocal<ArrayDeque<LensExecution>> OPEN = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<LensReport> LAST = new ThreadLocal<>();

    private ExecutionTracker() {
    }

    static LensExecution current() {
        try {
            return OPEN.get().peekLast();
        } catch (Throwable t) {
            return null;
        }
    }

    static void push(LensExecution x) {
        LensSafe.run("execution-start", () -> OPEN.get().addLast(x));
    }

    /**
     * Before opening an execution at {@code now}: closes, as {@link LensReport.Outcome#STOPPED},
     * every open execution whose opening method has already returned (its {@code finish()} was
     * never called), and flags each one so the missing {@code finish()} can be fixed.
     */
    static void closeForgotten(CallSite now) {
        if (now == null) {
            return;
        }
        LensSafe.run("close-forgotten", () -> {
            List<LensExecution> open = new ArrayList<>(OPEN.get());
            if (open.isEmpty()) {
                return; // the usual case: nothing to check, no stack walk
            }
            Set<String> callers = CallSite.callersOfCaller();
            Collections.reverse(open); // newest first, as the stack would have unwound
            for (LensExecution x : open) {
                CallSite site = x.site();
                if (site != null && !site.isStillRunning(now, callers)) {
                    x.stopped();
                    x.finish();
                    LensDiagnostics.flag("execution-not-finished", new IllegalStateException(
                            "execution '" + x.name() + "' opened at " + site
                                    + " was never finished; closed as STOPPED (call finish() in a finally block)"));
                }
            }
        });
    }

    /** The newest open execution opened by {@link Lens#start}, or {@code null}. */
    static LensExecution lastStarted() {
        try {
            Iterator<LensExecution> it = OPEN.get().descendingIterator();
            while (it.hasNext()) {
                LensExecution x = it.next();
                if (x.viaStart()) {
                    return x;
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    static void remove(LensExecution x) {
        LensSafe.run("execution-end", () -> {
            ArrayDeque<LensExecution> open = OPEN.get();
            open.remove(x);
            if (open.isEmpty()) {
                OPEN.remove();
            }
        });
    }

    static LensReport last() {
        try {
            return LAST.get();
        } catch (Throwable t) {
            return null;
        }
    }

    static void setLast(LensReport report) {
        LensSafe.run("last-report", () -> LAST.set(report));
    }

    static void addOverhead(long nanos) {
        try {
            LensExecution x = current();
            if (x != null) {
                x.addOverhead(nanos);
            }
        } catch (Throwable ignored) {
            // bookkeeping only
        }
    }

    /** Counts an operation that was not observed because Lens's guard was active. */
    static void noteSuppressed() {
        try {
            if (Guard.active()) {
                LensExecution x = current();
                if (x != null) {
                    x.addSuppressed();
                }
            }
        } catch (Throwable ignored) {
            // bookkeeping only
        }
    }

    /** For {@link LensDiagnostics}: must not flag again on failure. */
    static void recordFailureNoFlag(LensDiagnostics.Failure f) {
        try {
            LensExecution x = current();
            if (x != null) {
                x.addFailure(f);
            }
        } catch (Throwable ignored) {
            // diagnostics already counted it
        }
    }
}
