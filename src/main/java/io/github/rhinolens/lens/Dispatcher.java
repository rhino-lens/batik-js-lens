package io.github.rhinolens.lens;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hands events to the current execution and to listeners. Listeners run inside {@link Guard}, so
 * nothing they do is observed; each one runs through {@link LensSafe} on its own.
 */
final class Dispatcher {

    /** A listener and its step name for failure reports (built once, not per event). */
    private static final class Entry {
        final ChangeListener listener;
        final String step;

        Entry(ChangeListener listener) {
            this.listener = listener;
            this.step = "listener " + listener.getClass().getName();
        }
    }

    private static final List<Entry> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<boolean[]> IN_FAILURE = ThreadLocal.withInitial(() -> new boolean[1]);

    private Dispatcher() {
    }

    static synchronized void add(ChangeListener listener) {
        if (listener != null && LISTENERS.stream().noneMatch(e -> e.listener == listener)) {
            LISTENERS.add(new Entry(listener));
        }
    }

    static synchronized void remove(ChangeListener listener) {
        LISTENERS.removeIf(e -> e.listener == listener);
    }

    static void clear() {
        LISTENERS.clear();
    }

    /** Records {@code e} in the current execution (if any) and passes it to every listener. */
    static void emit(ChangeEvent e) {
        LensSafe.run("dispatch", () -> {
            LensExecution x = ExecutionTracker.current();
            if (x != null && !x.accept(e)) {
                return; // over the per-execution limit: dropped and counted
            }
            if (LISTENERS.isEmpty()) {
                return;
            }
            Guard.enter();
            try {
                for (Entry l : LISTENERS) {
                    LensSafe.run(l.step, () -> l.listener.onEvent(e));
                }
            } finally {
                Guard.exit();
            }
        });
    }

    static void executionFinished(LensReport report) {
        LensSafe.run("dispatch-report", () -> {
            if (LISTENERS.isEmpty()) {
                return;
            }
            Guard.enter();
            try {
                for (Entry l : LISTENERS) {
                    LensSafe.run(l.step, () -> l.listener.onExecutionFinished(report));
                }
            } finally {
                Guard.exit();
            }
        });
    }

    /**
     * For {@link LensDiagnostics}: tells listeners about an observation failure. Never flags again
     * and never recurses (a listener failing on this event is ignored).
     */
    static void dispatchFailureNoFlag(LensDiagnostics.Failure f) {
        try {
            boolean[] inFailure = IN_FAILURE.get();
            if (inFailure[0] || LISTENERS.isEmpty()) {
                return;
            }
            inFailure[0] = true;
            Guard.enter();
            try {
                ObservationFailure event = new ObservationFailure(
                        new ScriptPosition(f.ruleName(), f.line()), f.step(), f.error());
                for (Entry l : LISTENERS) {
                    try {
                        l.listener.onEvent(event);
                    } catch (Throwable ignored) {
                        // a listener failing on a failure report is not reported again
                    }
                }
            } finally {
                Guard.exit();
                inFailure[0] = false;
            }
        } catch (Throwable ignored) {
            // diagnostics already counted the failure
        }
    }
}
