package org.mozilla.javascript.lens;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Failures inside Lens's own observation code.
 *
 * <p>Lens never lets such a failure reach a script. Instead it is counted per step, kept in a
 * short history, logged through {@code java.util.logging} (the first time per step, then at
 * 10, 100, 1000, … occurrences), added to the current execution's report, and sent to listeners
 * as an {@link ObservationFailure} event. Get the shared instance with {@link Lens#diagnostics()}.
 */
public final class LensDiagnostics {

    /** One flagged failure. */
    public static final class Failure {
        private final String step;
        private final String ruleName;
        private final int line;
        private final String error;
        private final Instant time;

        Failure(String step, String ruleName, int line, String error, Instant time) {
            this.step = step;
            this.ruleName = ruleName;
            this.line = line;
            this.error = error;
            this.time = time;
        }

        /** The observation step that failed, e.g. {@code "assign-before"} or {@code "listener …"}. */
        public String step() {
            return step;
        }

        /** The script running at the time, or {@code null} if unknown. */
        public String ruleName() {
            return ruleName;
        }

        /** The script line at the time, or {@code -1} if unknown. */
        public int line() {
            return line;
        }

        /** The exception's class and message. */
        public String error() {
            return error;
        }

        public Instant time() {
            return time;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("step", step);
            m.put("ruleName", ruleName);
            m.put("line", line);
            m.put("error", error);
            m.put("time", String.valueOf(time));
            return m;
        }

        @Override
        public String toString() {
            return step + " failed at " + (ruleName == null ? "?" : ruleName) + ":" + line + ": " + error;
        }
    }

    private static final int MAX_RECENT = 50;
    private static final Logger LOG = Logger.getLogger("org.mozilla.javascript.lens");
    private static final LensDiagnostics INSTANCE = new LensDiagnostics();

    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final ArrayDeque<Failure> recent = new ArrayDeque<>();

    private LensDiagnostics() {
    }

    static LensDiagnostics instance() {
        return INSTANCE;
    }

    /** Failure counts per step since start (or the last {@link #reset()}). */
    public Map<String, Long> counts() {
        Map<String, Long> out = new LinkedHashMap<>();
        counts.forEach((step, n) -> out.put(step, n.sum()));
        return Collections.unmodifiableMap(out);
    }

    /** Total failures since start (or the last {@link #reset()}). */
    public long total() {
        long sum = 0;
        for (LongAdder n : counts.values()) {
            sum += n.sum();
        }
        return sum;
    }

    /** The most recent failures, oldest first (at most 50). */
    public List<Failure> recent() {
        synchronized (recent) {
            return Collections.unmodifiableList(new ArrayList<>(recent));
        }
    }

    /** Clears counts and history. */
    public void reset() {
        counts.clear();
        synchronized (recent) {
            recent.clear();
        }
    }

    /** Called by {@link LensSafe}. Never throws and never recurses into itself. */
    static void flag(String step, Throwable t) {
        try {
            INSTANCE.record(step, t);
        } catch (Throwable ignored) {
            // the reporter itself must never throw
        }
    }

    private void record(String step, Throwable t) {
        ScriptPosition pos = ScriptPosition.currentOrUnknownNoFlag();
        Failure f = new Failure(step, pos.ruleName(), pos.line(), summary(t), Instant.now());
        LongAdder n = counts.computeIfAbsent(step, k -> new LongAdder());
        n.increment();
        synchronized (recent) {
            if (recent.size() == MAX_RECENT) {
                recent.removeFirst();
            }
            recent.addLast(f);
        }
        log(step, t, n.sum());
        ExecutionTracker.recordFailureNoFlag(f);
        Dispatcher.dispatchFailureNoFlag(f);
    }

    private static void log(String step, Throwable t, long count) {
        try {
            if (count == 1 || isPowerOfTen(count)) {
                LOG.log(Level.WARNING, "batik-js-lens: observation step '" + step + "' failed (" + count
                        + " time" + (count == 1 ? "" : "s") + "); the script continued", t);
            }
        } catch (Throwable ignored) {
            // logging problems must not matter
        }
    }

    private static boolean isPowerOfTen(long n) {
        while (n >= 10 && n % 10 == 0) {
            n /= 10;
        }
        return n == 1;
    }

    static String summary(Throwable t) {
        try {
            String message = t.getMessage();
            return t.getClass().getName() + (message == null ? "" : ": " + message);
        } catch (Throwable ignored) {
            return "unknown error";
        }
    }
}
