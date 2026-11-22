package org.mozilla.javascript.lens;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaMethod;
import org.mozilla.javascript.Scriptable;

/**
 * Wraps (does not replace) Rhino's {@link NativeJavaMethod}: every call goes to the original
 * object, which does overload resolution, conversion and the Java call exactly as without Lens.
 * Around it, setter calls, collection changes and service calls are recorded.
 *
 * <p>One wrapper exists per Rhino method object, so {@code obj.f === obj.f} behaves as in plain
 * Rhino. The target object and its path are read from {@code thisObj} at call time.
 */
final class LensMethod extends BaseFunction {

    private static final long serialVersionUID = 1L;

    private static final Map<NativeJavaMethod, WeakReference<LensMethod>> SHARED = new WeakHashMap<>();

    private final NativeJavaMethod delegate;
    private final String name;

    private LensMethod(NativeJavaMethod delegate) {
        this.delegate = delegate;
        this.name = delegate.getFunctionName();
        setParentScope(delegate.getParentScope());
        setPrototype(delegate.getPrototype());
    }

    /** The shared wrapper for {@code method}. */
    static LensMethod of(NativeJavaMethod method) {
        synchronized (SHARED) {
            WeakReference<LensMethod> ref = SHARED.get(method);
            LensMethod wrapper = ref == null ? null : ref.get();
            if (wrapper == null) {
                wrapper = new LensMethod(method);
                SHARED.put(method, new WeakReference<>(wrapper));
            }
            return wrapper;
        }
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (!Lens.observing()) {
            ExecutionTracker.noteSuppressed();
            return delegate.call(cx, scope, thisObj, args);
        }
        Capture.CallBefore before = Capture.beforeCall(thisObj, name, args);
        Object result;
        try {
            result = delegate.call(cx, scope, thisObj, args);   // Rhino's own call, unguarded and unchanged
        } catch (RuntimeException | Error e) {
            if (before != null) {
                Capture.callFailed(before, e);
            }
            throw e;                                             // the script sees exactly this
        }
        if (before != null) {
            Capture.afterCall(before, result);
        }
        Capture.trackPath(thisObj, name, args, result);
        return result;
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        return delegate.construct(cx, scope, args);
    }

    @Override
    public String getFunctionName() {
        return name;
    }

    @Override
    public int getArity() {
        return delegate.getArity();
    }

    @Override
    public int getLength() {
        return delegate.getLength();
    }
}
