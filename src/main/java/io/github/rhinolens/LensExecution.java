package io.github.rhinolens;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One named script execution on the current thread. Everything Lens observes on this thread
 * while it is open joins it, and {@link #finish()} freezes it into a {@link LensReport}.
 *
 * <pre>{@code
 * LensExecution x = Lens.execution("pricing-rule", Map.of("recordId", 42));
 * try {
 *     result = ctx.evaluateString(scope, script, "pricing-rule", 1, null);
 *     x.succeeded();
 * } catch (RuntimeException e) {
 *     x.failed(e);
 *     throw e;
 * } finally {
 *     LensReport report = x.finish();
 * }
 * }</pre>
 *
 * <p>None of these methods throw; failures inside them are flagged in {@link Lens#diagnostics()}.
 * Executions nest: events join the innermost open one.
 */
public final class LensExecution implements AutoCloseable {

    private final String id = UUID.randomUUID().toString();
    private final String name;
    private final Map<String, Object> attributes;
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();

    private final List<ChangeEvent> events = new ArrayList<>();
    private final Map<ChangeEvent.Kind, Integer> counts = new EnumMap<>(ChangeEvent.Kind.class);
    private final List<LensDiagnostics.Failure> failures = new ArrayList<>();
    private String ruleNameFromEvents;
    private long serviceNanos;
    private long overheadNanos;
    private long suppressed;
    private long dropped;
    private LensReport.Outcome outcome;
    private String error;
    private LensReport finalReport;

    /** Opened by {@link Lens#start}, so {@link Lens#finish()} may close it. */
    private final boolean viaStart;
    /** Where it was opened (null for automatic executions); used to close forgotten ones. */
    private final CallSite site;

    LensExecution(String name, Map<String, ?> attributes) {
        this(name, attributes, false, null);
    }

    LensExecution(String name, Map<String, ?> attributes, boolean viaStart, CallSite site) {
        this.name = name;
        Map<String, Object> copy = new LinkedHashMap<>();
        if (attributes != null) {
            copy.putAll(attributes);
        }
        this.attributes = Collections.unmodifiableMap(copy);
        this.viaStart = viaStart;
        this.site = site;
    }

    boolean viaStart() {
        return viaStart;
    }

    CallSite site() {
        return site;
    }

    /** Time since it started, without building a report. */
    long elapsedNanos() {
        return System.nanoTime() - startNanos;
    }

    /** Unique id of this execution (also on each of its events). */
    public String id() {
        return id;
    }

    /** The name given when it was started, or {@code null}. */
    public String name() {
        return name;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    /** Marks the outcome as {@link LensReport.Outcome#SUCCESS} (the default if nothing else is marked). */
    public void succeeded() {
        LensSafe.run("execution-outcome", () -> {
            synchronized (this) {
                if (finalReport == null) {
                    outcome = LensReport.Outcome.SUCCESS;
                }
            }
        });
    }

    /** Marks the outcome as {@link LensReport.Outcome#FAILED} with the script's error. */
    public void failed(Throwable scriptError) {
        LensSafe.run("execution-outcome", () -> {
            synchronized (this) {
                if (finalReport == null) {
                    outcome = LensReport.Outcome.FAILED;
                    error = scriptError == null ? null : LensDiagnostics.summary(scriptError);
                }
            }
        });
    }

    /** Marks the outcome as {@link LensReport.Outcome#STOPPED} (e.g. stopped by a debugger or a timeout). */
    public void stopped() {
        LensSafe.run("execution-outcome", () -> {
            synchronized (this) {
                if (finalReport == null) {
                    outcome = LensReport.Outcome.STOPPED;
                }
            }
        });
    }

    /** The report so far ({@link LensReport.Outcome#RUNNING}), or the final one once finished. */
    public LensReport report() {
        return LensSafe.call("execution-report", () -> {
            synchronized (this) {
                return finalReport != null ? finalReport : build(LensReport.Outcome.RUNNING, null);
            }
        }, LensReport.empty());
    }

    /**
     * Ends the execution (once; later calls return the same report): freezes the report, makes it
     * {@link Lens#lastReport()}, and hands it to listeners' {@code onExecutionFinished}.
     */
    public LensReport finish() {
        LensReport report = LensSafe.call("execution-finish", this::freeze, null);
        if (report == null) {
            return LensReport.empty();
        }
        return report;
    }

    /** Same as {@link #finish()}, for try-with-resources. */
    @Override
    public void close() {
        finish();
    }

    private LensReport freeze() {
        boolean first;
        LensReport report;
        synchronized (this) {
            first = finalReport == null;
            if (first) {
                finalReport = build(outcome == null ? LensReport.Outcome.SUCCESS : outcome, Instant.now());
            }
            report = finalReport;
        }
        if (first) {
            ExecutionTracker.remove(this);
            ExecutionTracker.setLast(report);
            Dispatcher.executionFinished(report);
        }
        return report;
    }

    private LensReport build(LensReport.Outcome o, Instant endedAt) {
        LensReport.Stats stats = new LensReport.Stats(counts, serviceNanos, overheadNanos, suppressed, dropped);
        return new LensReport(id, name != null ? name : ruleNameFromEvents, attributes, startedAt, endedAt,
                System.nanoTime() - startNanos, o, o == LensReport.Outcome.FAILED ? error : null, events, stats,
                failures);
    }

    // ---- called on the owning thread by Lens internals (each caller is failure-safe) ----

    /** Adds an event; false if the per-execution limit is reached (the event is dropped). */
    synchronized boolean accept(ChangeEvent e) {
        if (finalReport != null) {
            return true;
        }
        if (events.size() >= Lens.config().maxEventsPerExecution()) {
            dropped++;
            return false;
        }
        e.joinExecution(id, attributes, events.size(), System.nanoTime() - startNanos);
        if (ruleNameFromEvents == null && e.ruleName() != null) {
            ruleNameFromEvents = e.ruleName();
        }
        events.add(e);
        counts.merge(e.kind(), 1, Integer::sum);
        if (e instanceof ServiceCall) {
            serviceNanos += ((ServiceCall) e).durationNanos();
        }
        return true;
    }

    /** True once the per-execution event limit is reached. */
    synchronized boolean isFull() {
        return finalReport == null && events.size() >= Lens.config().maxEventsPerExecution();
    }

    synchronized void addDropped() {
        dropped++;
    }

    synchronized void addOverhead(long nanos) {
        overheadNanos += nanos;
    }

    synchronized void addSuppressed() {
        suppressed++;
    }

    synchronized void addFailure(LensDiagnostics.Failure f) {
        if (failures.size() < 100) {
            failures.add(f);
        }
    }
}
