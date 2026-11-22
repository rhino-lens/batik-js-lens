package io.github.rhinolens.lens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A script called a changing method on a Java {@link java.util.Collection} or {@link java.util.Map}:
 * {@code add}, {@code addAll}, {@code remove}, {@code removeAll}, {@code retainAll}, {@code clear},
 * {@code set}, {@code put}, {@code putAll}.
 */
public final class CollectionChange extends ChangeEvent {

    private final String method;
    private final List<ValueSnapshot> args;
    private final int sizeBefore;
    private final int sizeAfter;
    private final ValueSnapshot returned;
    private final String error;

    CollectionChange(ScriptPosition position, TargetRef target, boolean incomplete, String method,
                     List<ValueSnapshot> args, int sizeBefore, int sizeAfter, ValueSnapshot returned, String error) {
        super(Kind.COLLECTION, position, target, incomplete);
        this.method = method;
        this.args = Collections.unmodifiableList(new ArrayList<>(args));
        this.sizeBefore = sizeBefore;
        this.sizeAfter = sizeAfter;
        this.returned = returned;
        this.error = error;
    }

    public String method() {
        return method;
    }

    public List<ValueSnapshot> args() {
        return args;
    }

    /** Size before the call, or {@code -1} if it could not be read. */
    public int sizeBefore() {
        return sizeBefore;
    }

    /** Size after the call, or {@code -1} if it could not be read. */
    public int sizeAfter() {
        return sizeAfter;
    }

    /** What the method returned, e.g. the previous value for {@code Map.put} / {@code List.set}. */
    public ValueSnapshot returned() {
        return returned;
    }

    /** The exception the call threw (it still reached the script), or {@code null}. */
    public String error() {
        return error;
    }

    @Override
    void addDetails(Map<String, Object> m) {
        m.put("method", method);
        List<Object> a = new ArrayList<>();
        for (ValueSnapshot v : args) {
            a.add(v.toMap());
        }
        m.put("args", a);
        m.put("sizeBefore", sizeBefore);
        m.put("sizeAfter", sizeAfter);
        m.put("returned", returned == null ? null : returned.toMap());
        m.put("error", error);
    }

    @Override
    String describe() {
        return "COLLECTION " + where(target()) + "." + method + args + " size " + sizeBefore + " -> " + sizeAfter
                + (error != null ? " threw " + error : "");
    }
}
