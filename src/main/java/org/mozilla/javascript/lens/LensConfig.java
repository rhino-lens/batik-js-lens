package org.mozilla.javascript.lens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lens settings. Immutable: each {@code withX}-style method returns a changed copy.
 *
 * <pre>{@code
 * Lens.install(LensConfig.defaults()
 *         .oldValues(OldValues.SIMPLE_TYPES)
 *         .services(ServicePolicy.defaults())
 *         .exclude("com.example.internal."));
 * }</pre>
 */
public final class LensConfig {

    private final OldValues oldValues;
    private final int maxValueLength;
    private final int maxEventsPerExecution;
    private final boolean keepRawValues;
    private final ServicePolicy services;
    private final IdResolver idResolver;
    private final List<String> excludedPrefixes;
    private final boolean lineNumbersInCompiledMode;

    private LensConfig(OldValues oldValues, int maxValueLength, int maxEventsPerExecution, boolean keepRawValues,
                       ServicePolicy services, IdResolver idResolver, List<String> excludedPrefixes,
                       boolean lineNumbersInCompiledMode) {
        this.oldValues = oldValues;
        this.maxValueLength = maxValueLength;
        this.maxEventsPerExecution = maxEventsPerExecution;
        this.keepRawValues = keepRawValues;
        this.services = services;
        this.idResolver = idResolver;
        this.excludedPrefixes = Collections.unmodifiableList(new ArrayList<>(excludedPrefixes));
        this.lineNumbersInCompiledMode = lineNumbersInCompiledMode;
    }

    /**
     * Old values {@link OldValues#ALWAYS}; values cut at 500 characters; 10,000 events per
     * execution; raw values not kept; {@link ServicePolicy#defaults()}; {@link IdResolver#getIdMethod()};
     * nothing excluded; line numbers in compiled mode on.
     */
    public static LensConfig defaults() {
        return new LensConfig(OldValues.ALWAYS, 500, 10_000, false, ServicePolicy.defaults(),
                IdResolver.getIdMethod(), Collections.emptyList(), true);
    }

    /** Whether to read property values before and after a change. */
    public LensConfig oldValues(OldValues mode) {
        return new LensConfig(require(mode), maxValueLength, maxEventsPerExecution, keepRawValues, services,
                idResolver, excludedPrefixes, lineNumbersInCompiledMode);
    }

    /** Longest rendered value text; longer values end with "…". */
    public LensConfig maxValueLength(int chars) {
        return new LensConfig(oldValues, Math.max(16, chars), maxEventsPerExecution, keepRawValues, services,
                idResolver, excludedPrefixes, lineNumbersInCompiledMode);
    }

    /**
     * Most events kept per execution. Beyond it events are dropped and counted, the report is
     * marked truncated, and the script carries on.
     */
    public LensConfig maxEventsPerExecution(int events) {
        return new LensConfig(oldValues, maxValueLength, Math.max(1, events), keepRawValues, services,
                idResolver, excludedPrefixes, lineNumbersInCompiledMode);
    }

    /**
     * Keep references to the actual objects in {@link ValueSnapshot#raw()}. Off by default: it
     * keeps those objects alive as long as the events are.
     */
    public LensConfig keepRawValues(boolean keep) {
        return new LensConfig(oldValues, maxValueLength, maxEventsPerExecution, keep, services, idResolver,
                excludedPrefixes, lineNumbersInCompiledMode);
    }

    /** Which objects are services. */
    public LensConfig services(ServicePolicy policy) {
        return new LensConfig(oldValues, maxValueLength, maxEventsPerExecution, keepRawValues, require(policy),
                idResolver, excludedPrefixes, lineNumbersInCompiledMode);
    }

    /** How to find an object's id. */
    public LensConfig idResolver(IdResolver resolver) {
        return new LensConfig(oldValues, maxValueLength, maxEventsPerExecution, keepRawValues, services,
                require(resolver), excludedPrefixes, lineNumbersInCompiledMode);
    }

    /** Never observe objects whose class name starts with any of these prefixes. */
    public LensConfig exclude(String... classNamePrefixes) {
        List<String> next = new ArrayList<>(excludedPrefixes);
        next.addAll(Arrays.asList(classNamePrefixes));
        return new LensConfig(oldValues, maxValueLength, maxEventsPerExecution, keepRawValues, services,
                idResolver, next, lineNumbersInCompiledMode);
    }

    /**
     * Whether events in compiled mode (optimization level 0–9) get the script name and line.
     * Finding them walks the stack, the largest part of Lens's cost per change in compiled mode
     * (see docs/performance.md). Off: compiled-mode events have no rule name or line (name the
     * execution with {@link Lens#execution} instead). Interpreted mode always has them, cheaply.
     */
    public LensConfig lineNumbersInCompiledMode(boolean on) {
        return new LensConfig(oldValues, maxValueLength, maxEventsPerExecution, keepRawValues, services, idResolver,
                excludedPrefixes, on);
    }

    public boolean lineNumbersInCompiledMode() {
        return lineNumbersInCompiledMode;
    }

    public OldValues oldValues() {
        return oldValues;
    }

    public int maxValueLength() {
        return maxValueLength;
    }

    public int maxEventsPerExecution() {
        return maxEventsPerExecution;
    }

    public boolean keepRawValues() {
        return keepRawValues;
    }

    public ServicePolicy services() {
        return services;
    }

    public IdResolver idResolver() {
        return idResolver;
    }

    public List<String> excludedPrefixes() {
        return excludedPrefixes;
    }

    boolean isExcluded(Class<?> type) {
        String name = type.getName();
        for (String prefix : excludedPrefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new IllegalArgumentException("null");
        }
        return value;
    }
}
