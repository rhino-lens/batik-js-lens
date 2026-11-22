package io.github.rhinolens.lens;

import org.mozilla.javascript.NativeJavaArray;
import org.mozilla.javascript.Scriptable;

/** Rhino's Java-array wrapper, extended: only {@code arr[i] = v} is observed; Rhino does the work. */
final class LensJavaArray extends NativeJavaArray {

    private static final long serialVersionUID = 1L;

    private volatile String path;

    LensJavaArray(Scriptable scope, Object array) {
        super(scope, array);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        if (!Lens.observing()) {
            ExecutionTracker.noteSuppressed();
            super.put(index, start, value);
            return;
        }
        Capture.ElementBefore before = Capture.beforeElement(this, index);
        super.put(index, start, value);                // Rhino's own element assignment
        Capture.afterElement(before);
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
