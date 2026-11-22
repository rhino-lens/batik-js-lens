package io.github.rhinolens.lens;

import java.util.ArrayDeque;

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
