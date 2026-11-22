package io.github.rhinolens.lens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A script called a method on a service object (a repository, CRUD service, …), as decided by the
 * configured {@link ServicePolicy} or {@link Lens#registerService(Object, String)}. What the service
 * does inside is Java code and is not observed; the call itself, its arguments, result or
 * exception, and duration are.
 */
public final class ServiceCall extends ChangeEvent {

    private final String service;
    private final String method;
    private final List<ValueSnapshot> args;
    private final ValueSnapshot result;
    private final String error;
    private final long durationNanos;

    ServiceCall(ScriptPosition position, TargetRef target, boolean incomplete, String service, String method,
                List<ValueSnapshot> args, ValueSnapshot result, String error, long durationNanos) {
        super(Kind.SERVICE_CALL, position, target, incomplete);
        this.service = service;
        this.method = method;
        this.args = Collections.unmodifiableList(new ArrayList<>(args));
        this.result = result;
        this.error = error;
        this.durationNanos = durationNanos;
    }

    /** The service's name, e.g. {@code orderRepository}. */
    public String service() {
        return service;
    }

    public String method() {
        return method;
    }

    public List<ValueSnapshot> args() {
        return args;
    }

    /** The return value, or {@code null} if the call threw. */
    public ValueSnapshot result() {
        return result;
    }

    /** The exception the call threw (it still reached the script), or {@code null}. */
    public String error() {
        return error;
    }

    public long durationNanos() {
        return durationNanos;
    }

    @Override
    void addDetails(Map<String, Object> m) {
        m.put("service", service);
        m.put("method", method);
        List<Object> a = new ArrayList<>();
        for (ValueSnapshot v : args) {
            a.add(v.toMap());
        }
        m.put("args", a);
        m.put("result", result == null ? null : result.toMap());
        m.put("error", error);
        m.put("durationNanos", durationNanos);
    }

    @Override
    String describe() {
        return "SERVICE " + service + "." + method + args
                + (error != null ? " threw " + error : " -> " + result)
                + " in " + String.format(Locale.ROOT, "%.3f ms", durationNanos / 1e6);
    }
}
