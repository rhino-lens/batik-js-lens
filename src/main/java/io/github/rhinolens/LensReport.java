package io.github.rhinolens;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything Lens observed during one script execution, frozen: timing, outcome, every event in
 * order, summary statistics, and any observation failures. Get one from
 * {@link LensExecution#finish()}, {@link Lens#observe}, {@link Lens#lastReport()},
 * {@link ChangeListener#onExecutionFinished(LensReport)}, or {@code lens.report()} in a script
 * (a snapshot of the execution so far). Reports are immutable and safe to keep, log or store.
 */
public final class LensReport {

    /** How the execution ended. {@code RUNNING} for a snapshot taken while it runs. */
    public enum Outcome { RUNNING, SUCCESS, FAILED, STOPPED }

    /** Counts and timings. */
    public static final class Stats {
        private final Map<ChangeEvent.Kind, Integer> counts;
        private final long serviceCallNanos;
        private final long overheadNanos;
        private final long suppressed;
        private final long dropped;

        Stats(Map<ChangeEvent.Kind, Integer> counts, long serviceCallNanos, long overheadNanos, long suppressed,
              long dropped) {
            this.counts = Collections.unmodifiableMap(new EnumMap<>(counts));
            this.serviceCallNanos = serviceCallNanos;
            this.overheadNanos = overheadNanos;
            this.suppressed = suppressed;
            this.dropped = dropped;
        }

        /** Events kept, per kind (kinds with none are absent). */
        public Map<ChangeEvent.Kind, Integer> counts() {
            return counts;
        }

        public int count(ChangeEvent.Kind kind) {
            Integer n = counts.get(kind);
            return n == null ? 0 : n;
        }

        /** Total time spent inside service calls. */
        public long serviceCallNanos() {
            return serviceCallNanos;
        }

        /** Time Lens itself spent capturing and dispatching: the cost of observation for this run. */
        public long overheadNanos() {
            return overheadNanos;
        }

        /** Operations not observed because Lens's guard was active (quiet blocks, listeners). */
        public long suppressed() {
            return suppressed;
        }

        /** Events dropped after {@link LensConfig#maxEventsPerExecution(int)} was reached. */
        public long dropped() {
            return dropped;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            Map<String, Object> c = new LinkedHashMap<>();
            counts.forEach((k, v) -> c.put(k.name(), v));
            m.put("counts", c);
            m.put("serviceCallNanos", serviceCallNanos);
            m.put("overheadNanos", overheadNanos);
            m.put("suppressed", suppressed);
            m.put("dropped", dropped);
            return m;
        }
    }

    private final String executionId;
    private final String ruleName;
    private final Map<String, Object> attributes;
    private final Instant startedAt;
    private final Instant endedAt;
    private final long durationNanos;
    private final Outcome outcome;
    private final String error;
    private final List<ChangeEvent> events;
    private final Stats stats;
    private final List<LensDiagnostics.Failure> failures;

    LensReport(String executionId, String ruleName, Map<String, Object> attributes, Instant startedAt,
               Instant endedAt, long durationNanos, Outcome outcome, String error, List<ChangeEvent> events,
               Stats stats, List<LensDiagnostics.Failure> failures) {
        this.executionId = executionId;
        this.ruleName = ruleName;
        this.attributes = attributes;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationNanos = durationNanos;
        this.outcome = outcome;
        this.error = error;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        this.stats = stats;
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
    }

    /** A report with nothing in it (no execution was running). */
    public static LensReport empty() {
        Instant now = Instant.now();
        return new LensReport(null, null, Collections.emptyMap(), now, now, 0, Outcome.SUCCESS, null,
                Collections.emptyList(), new Stats(new EnumMap<>(ChangeEvent.Kind.class), 0, 0, 0, 0),
                Collections.emptyList());
    }

    public String executionId() {
        return executionId;
    }

    /** The name given to the execution, else the script name from its first event, else {@code null}. */
    public String ruleName() {
        return ruleName;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** End time, or {@code null} for a {@link Outcome#RUNNING} snapshot. */
    public Instant endedAt() {
        return endedAt;
    }

    /** Total time of the execution (or so far, for a snapshot). */
    public long durationNanos() {
        return durationNanos;
    }

    public double durationMillis() {
        return durationNanos / 1e6;
    }

    public Outcome outcome() {
        return outcome;
    }

    /** The script's error for {@link Outcome#FAILED}, else {@code null}. */
    public String error() {
        return error;
    }

    /** Every event kept, in order. */
    public List<ChangeEvent> events() {
        return events;
    }

    public Stats stats() {
        return stats;
    }

    /** Observation failures flagged during this execution (the script was not affected by them). */
    public List<LensDiagnostics.Failure> observationFailures() {
        return failures;
    }

    /** True if events were dropped at the per-execution limit. */
    public boolean isTruncated() {
        return stats.dropped() > 0;
    }

    /** Number of property, array element and collection changes. */
    public int changeCount() {
        return stats.count(ChangeEvent.Kind.PROPERTY) + stats.count(ChangeEvent.Kind.ARRAY_ELEMENT)
                + stats.count(ChangeEvent.Kind.COLLECTION);
    }

    /** Events of one type, in order. */
    public <T extends ChangeEvent> List<T> eventsOf(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (ChangeEvent e : events) {
            if (type.isInstance(e)) {
                out.add(type.cast(e));
            }
        }
        return Collections.unmodifiableList(out);
    }

    public List<PropertyChange> propertyChanges() {
        return eventsOf(PropertyChange.class);
    }

    public List<ServiceCall> serviceCalls() {
        return eventsOf(ServiceCall.class);
    }

    public List<CollectionChange> collectionChanges() {
        return eventsOf(CollectionChange.class);
    }

    /** Events about {@code object} (by identity), in order. */
    public List<ChangeEvent> changesTo(Object object) {
        List<ChangeEvent> out = new ArrayList<>();
        for (ChangeEvent e : events) {
            if (e.target() != null && e.target().refersTo(object)) {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * One entry per changed property (or array element) per object: first old value, last new
     * value, how many changes, which lines. In order of first change.
     */
    public List<NetChange> netChanges() {
        Map<MemberKey, List<ChangeEvent>> groups = new LinkedHashMap<>();
        for (ChangeEvent e : events) {
            String member = null;
            if (e instanceof PropertyChange) {
                member = "." + ((PropertyChange) e).property();
            } else if (e instanceof ArrayElementChange) {
                member = "[" + ((ArrayElementChange) e).index() + "]";
            }
            if (member != null && e.target() != null) {
                groups.computeIfAbsent(new MemberKey(e.target(), member), k -> new ArrayList<>()).add(e);
            }
        }
        List<NetChange> out = new ArrayList<>();
        for (List<ChangeEvent> group : groups.values()) {
            ChangeEvent first = group.get(0);
            ChangeEvent last = group.get(group.size() - 1);
            List<Integer> lines = new ArrayList<>();
            for (ChangeEvent e : group) {
                lines.add(e.line());
            }
            if (first instanceof PropertyChange) {
                out.add(new NetChange(last.target(), ((PropertyChange) first).property(),
                        ((PropertyChange) first).oldValue(), ((PropertyChange) last).newValue(), group.size(), lines));
            } else {
                out.add(new NetChange(last.target(), "[" + ((ArrayElementChange) first).index() + "]",
                        ((ArrayElementChange) first).oldValue(), ((ArrayElementChange) last).newValue(),
                        group.size(), lines));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The whole report as plain maps, lists, strings and numbers (store it with any JSON library). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("executionId", executionId);
        m.put("ruleName", ruleName);
        m.put("attributes", stringKeys(attributes));
        m.put("startedAt", String.valueOf(startedAt));
        m.put("endedAt", endedAt == null ? null : endedAt.toString());
        m.put("durationNanos", durationNanos);
        m.put("outcome", outcome.name());
        m.put("error", error);
        m.put("truncated", isTruncated());
        m.put("stats", stats.toMap());
        List<Object> ev = new ArrayList<>();
        for (ChangeEvent e : events) {
            ev.add(e.toMap());
        }
        m.put("events", ev);
        List<Object> net = new ArrayList<>();
        for (NetChange n : netChanges()) {
            net.add(n.toMap());
        }
        m.put("netChanges", net);
        List<Object> f = new ArrayList<>();
        for (LensDiagnostics.Failure failure : failures) {
            f.add(failure.toMap());
        }
        m.put("observationFailures", f);
        return m;
    }

    /** The report as JSON (format: docs/report-format.md). */
    public String toJson() {
        return Json.write(toMap());
    }

    /** Human-readable, multi-line summary for logs. */
    public String toText() {
        StringBuilder out = new StringBuilder();
        out.append("Lens report: ").append(ruleName == null ? "(unnamed)" : ruleName).append(' ')
                .append(outcome).append(" in ").append(String.format(Locale.ROOT, "%.3f ms", durationMillis()))
                .append(", ").append(changeCount()).append(" changes, ")
                .append(stats.count(ChangeEvent.Kind.SERVICE_CALL)).append(" service calls");
        if (error != null) {
            out.append(", error: ").append(error);
        }
        if (isTruncated()) {
            out.append(", truncated (").append(stats.dropped()).append(" events dropped)");
        }
        out.append('\n');
        for (ChangeEvent e : events) {
            out.append("  ").append(e.toText()).append('\n');
        }
        List<NetChange> net = netChanges();
        if (!net.isEmpty()) {
            out.append("  net changes:\n");
            for (NetChange n : net) {
                out.append("    ").append(n).append('\n');
            }
        }
        for (LensDiagnostics.Failure f : failures) {
            out.append("  observation failure: ").append(f).append('\n');
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return toText();
    }

    /** One property (or array element) of one object; objects compared by identity (see {@link TargetRef}). */
    private static final class MemberKey {
        private final TargetRef target;
        private final String member;

        MemberKey(TargetRef target, String member) {
            this.target = target;
            this.member = member;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MemberKey)) {
                return false;
            }
            MemberKey other = (MemberKey) o;
            return member.equals(other.member) && target.sameObjectAs(other.target);
        }

        @Override
        public int hashCode() {
            return (target.className().hashCode() * 31 + target.identityHash()) * 31 + member.hashCode();
        }
    }

    private static Map<String, Object> stringKeys(Map<String, Object> attrs) {
        Map<String, Object> m = new LinkedHashMap<>();
        attrs.forEach((k, v) -> m.put(k, v == null || v instanceof Number || v instanceof Boolean ? v : String.valueOf(v)));
        return m;
    }
}
