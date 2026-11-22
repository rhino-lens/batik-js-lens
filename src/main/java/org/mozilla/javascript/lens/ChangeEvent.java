package org.mozilla.javascript.lens;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Something a script did that Lens recorded. Every event says where it happened (rule name and
 * line), on which thread and when, and which execution it belongs to (if one is running).
 * Events are immutable to applications.
 *
 * @see PropertyChange
 * @see ArrayElementChange
 * @see CollectionChange
 * @see ServiceCall
 * @see Mark
 * @see ObservationFailure
 */
public abstract class ChangeEvent {

    /** The concrete event type. */
    public enum Kind { PROPERTY, ARRAY_ELEMENT, COLLECTION, SERVICE_CALL, MARK, OBSERVATION_FAILURE }

    private final Kind kind;
    private final String ruleName;
    private final int line;
    private final String thread;
    private final Instant time;
    private final TargetRef target;
    private final boolean incomplete;

    // set once, when the event joins an execution
    private long sequence = -1;
    private String executionId;
    private Map<String, Object> attributes = Collections.emptyMap();
    private long offsetNanos = -1;

    ChangeEvent(Kind kind, ScriptPosition position, TargetRef target, boolean incomplete) {
        this.kind = kind;
        this.ruleName = position.ruleName();
        this.line = position.line();
        this.thread = Thread.currentThread().getName();
        this.time = Instant.now();
        this.target = target;
        this.incomplete = incomplete;
    }

    void joinExecution(String executionId, Map<String, Object> attributes, long sequence, long offsetNanos) {
        this.executionId = executionId;
        this.attributes = attributes;
        this.sequence = sequence;
        this.offsetNanos = offsetNanos;
    }

    public Kind kind() {
        return kind;
    }

    /** The script name passed to {@code evaluateString}, or {@code null} if it could not be found. */
    public String ruleName() {
        return ruleName;
    }

    /** The script line, or {@code -1} if unknown. */
    public int line() {
        return line;
    }

    public String thread() {
        return thread;
    }

    public Instant time() {
        return time;
    }

    /** The Java object involved, or {@code null} for {@link Mark} and {@link ObservationFailure}. */
    public TargetRef target() {
        return target;
    }

    /** True if part of the capture failed (see {@link Lens#diagnostics()}); what is present is still correct. */
    public boolean isIncomplete() {
        return incomplete;
    }

    /** The execution this event belongs to, or {@code null} if none was running. */
    public String executionId() {
        return executionId;
    }

    /** The execution's attributes (e.g. a record id), empty if none. */
    public Map<String, Object> attributes() {
        return attributes;
    }

    /** Position within its execution (0, 1, 2, …), or {@code -1} outside an execution. */
    public long sequence() {
        return sequence;
    }

    /** Nanoseconds from the start of its execution, or {@code -1} outside an execution. */
    public long offsetNanos() {
        return offsetNanos;
    }

    /** All fields as plain maps, lists, strings and numbers (the JSON form). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind.name());
        m.put("sequence", sequence);
        m.put("ruleName", ruleName);
        m.put("line", line);
        m.put("offsetNanos", offsetNanos);
        m.put("thread", thread);
        m.put("time", time.toString());
        if (target != null) {
            m.put("target", target.toMap());
        }
        if (incomplete) {
            m.put("incomplete", true);
        }
        addDetails(m);
        return m;
    }

    /** One line for logs, e.g. {@code [rule.js:12] SETTER order.status "NEW" -> "PRICED"}. */
    public String toText() {
        return "[" + (ruleName == null ? "?" : ruleName) + ":" + line + "] " + describe()
                + (incomplete ? " (incomplete)" : "");
    }

    @Override
    public String toString() {
        return toText();
    }

    abstract void addDetails(Map<String, Object> m);

    abstract String describe();

    static String where(TargetRef target) {
        return target == null ? "?" : target.toString();
    }
}
