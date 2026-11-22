package org.mozilla.javascript.lens;

import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

/**
 * Rhino's Java-object wrapper, extended (not replaced). It is still a {@link NativeJavaObject}:
 * member lookup, overloads, conversions and {@code unwrap()} are all Rhino's own. Only two methods
 * are overridden, and both call {@code super} for the real work:
 * <ul>
 * <li>{@link #put}: the {@code =} operator; observed before and after.</li>
 * <li>{@link #get}: hands out {@link LensMethod} for Java methods, and names paths to child objects.</li>
 * </ul>
 */
final class LensJavaObject extends NativeJavaObject {

    private static final long serialVersionUID = 1L;

    private volatile String path;

    LensJavaObject(Scriptable scope, Object javaObject, Class<?> staticType) {
        super(scope, javaObject, staticType);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (!Lens.observing()) {
            ExecutionTracker.noteSuppressed();
            super.put(name, start, value);
            return;
        }
        Capture.AssignBefore before = Capture.beforeAssignment(this, name);
        super.put(name, start, value);                 // Rhino's own assignment, unguarded and unchanged
        Capture.afterAssignment(before, value);
    }

    @Override
    public Object get(String name, Scriptable start) {
        Object member = super.get(name, start);        // Rhino's own lookup
        if (!Lens.wrapping()) {
            return member;
        }
        // Even inside a quiet block the method wrapper is handed out: its call() then runs the
        // plain Rhino call and counts it as suppressed.
        return Capture.onGet(this, name, member);
    }

    String path() {
        return path;
    }

    void setPathIfAbsent(String p) {
        if (path == null) {
            path = p;
        }
    }
}
