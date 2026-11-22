package io.github.rhinolens.lens;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.NativeJavaMethod;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrappedException;

/**
 * Builds events around the real operations done by {@link LensJavaObject}, {@link LensMethod} and
 * {@link LensJavaArray}. Every entry point here is failure-safe: it runs inside {@link Guard}
 * (so nothing it touches is observed), through {@link LensSafe}, and records its own time as
 * Lens overhead. The real operation always runs outside, unguarded and unchanged.
 */
final class Capture {

    /** Changing methods of {@code java.util.Collection} / {@code java.util.Map}. */
    static final Set<String> COLLECTION_MUTATORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "add", "addAll", "remove", "removeAll", "retainAll", "clear", "set", "put", "putAll",
            "putIfAbsent", "replace", "addFirst", "addLast", "removeFirst", "removeLast", "push", "pop",
            "offer", "poll")));

    private Capture() {
    }

    // ------------------------------------------------------------------ obj.x = v

    static final class AssignBefore {
        final LensJavaObject wrapper;
        final String property;
        final Beans.Accessor accessor;
        final ValueSnapshot oldValue;
        final ScriptPosition position;
        final TargetRef target;
        final boolean failed;

        AssignBefore(LensJavaObject wrapper, String property, Beans.Accessor accessor, ValueSnapshot oldValue,
                     ScriptPosition position, TargetRef target, boolean failed) {
            this.wrapper = wrapper;
            this.property = property;
            this.accessor = accessor;
            this.oldValue = oldValue;
            this.position = position;
            this.target = target;
            this.failed = failed;
        }
    }

    /** Before {@code obj.name = value}; null if {@code name} is not a Java property. */
    static AssignBefore beforeAssignment(LensJavaObject w, String name) {
        long t0 = System.nanoTime();
        Guard.enter();
        try {
            return LensSafe.call("assign-before", () -> {
                Object target = w.unwrap();
                Beans.Accessor a = Beans.property(target.getClass(), name);
                if (a == null) {
                    return null; // not a Java property: Rhino handles it (error or prototype)
                }
                if (dropIfFull()) {
                    return null;
                }
                return new AssignBefore(w, name, a, readValue(a, target), ScriptPosition.current(),
                        Values.target(target, pathOf(w)), false);
            }, new AssignBefore(w, name, null, ValueSnapshot.unavailable("capture failed"), null, null, true));
        } finally {
            Guard.exit();
            ExecutionTracker.addOverhead(System.nanoTime() - t0);
        }
    }

    /** After {@code obj.name = value} completed. */
    static void afterAssignment(AssignBefore b, Object assigned) {
        if (b == null) {
            return;
        }
        long t0 = System.nanoTime();
        Guard.enter();
        try {
            LensSafe.run("assign-after", () -> {
                Object target = b.wrapper.unwrap();
                Beans.Accessor a = b.accessor != null ? b.accessor : Beans.property(target.getClass(), b.property);
                if (a == null) {
                    return;
                }
                ValueSnapshot newValue = canRead(a) ? readValue(a, target) : Values.of(assigned);
                ScriptPosition pos = b.position != null ? b.position : ScriptPosition.current();
                TargetRef ref = b.target != null ? b.target : Values.target(target, pathOf(b.wrapper));
                boolean incomplete = b.failed || b.oldValue.isUnavailable() || newValue.isUnavailable();
                Dispatcher.emit(new PropertyChange(pos, ref, incomplete, b.property,
                        a.isField() ? PropertyChange.Via.FIELD : PropertyChange.Via.ASSIGNMENT, b.oldValue, newValue));
            });
        } finally {
            Guard.exit();
            ExecutionTracker.addOverhead(System.nanoTime() - t0);
        }
    }

    // ------------------------------------------------------------------ method calls

    enum CallKind { SETTER, COLLECTION, SERVICE }

    static final class CallBefore {
        final CallKind kind;
        final LensJavaObject owner;
        final Object target;
        final String method;
        final Object[] args;
        final List<ValueSnapshot> argSnapshots;
        final Beans.Accessor accessor;
        final String property;
        final ValueSnapshot oldValue;
        final int sizeBefore;
        final String service;
        final ScriptPosition position;
        final TargetRef targetRef;
        long startNanos;

        CallBefore(CallKind kind, LensJavaObject owner, Object target, String method, Object[] args,
                   List<ValueSnapshot> argSnapshots, Beans.Accessor accessor, String property, ValueSnapshot oldValue,
                   int sizeBefore, String service, ScriptPosition position, TargetRef targetRef) {
            this.kind = kind;
            this.owner = owner;
            this.target = target;
            this.method = method;
            this.args = args;
            this.argSnapshots = argSnapshots;
            this.accessor = accessor;
            this.property = property;
            this.oldValue = oldValue;
            this.sizeBefore = sizeBefore;
            this.service = service;
            this.position = position;
            this.targetRef = targetRef;
        }
    }

    /** Before a Java method call from a script; null if the call is not one Lens records. */
    static CallBefore beforeCall(Scriptable thisObj, String method, Object[] args) {
        long t0 = System.nanoTime();
        Guard.enter();
        CallBefore b = null;
        try {
            b = LensSafe.call("call-before", () -> classify(thisObj, method, args), null);
            return b;
        } finally {
            Guard.exit();
            ExecutionTracker.addOverhead(System.nanoTime() - t0);
            if (b != null) {
                b.startNanos = System.nanoTime(); // service durations exclude Lens's own time
            }
        }
    }

    private static CallBefore classify(Scriptable thisObj, String method, Object[] args) {
        LensJavaObject owner = ownerOf(thisObj);
        if (owner == null) {
            return null;
        }
        Object target = owner.unwrap();
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        String service = serviceName(target);
        boolean collection = service == null && (target instanceof Collection || target instanceof Map)
                && COLLECTION_MUTATORS.contains(method);
        boolean setter = service == null && !collection && safeArgs.length == 1 && method.length() > 3
                && method.startsWith("set");
        if ((service != null || collection || setter) && dropIfFull()) {
            return null;
        }
        if (service != null) {
            return new CallBefore(CallKind.SERVICE, owner, target, method, safeArgs, snapshots(safeArgs), null, null,
                    null, -1, service, ScriptPosition.current(), Values.target(target, pathOf(owner)));
        }
        if (collection) {
            return new CallBefore(CallKind.COLLECTION, owner, target, method, safeArgs, snapshots(safeArgs), null,
                    null, null, size(target), null, ScriptPosition.current(), Values.target(target, pathOf(owner)));
        }
        if (setter) {
            Beans.Accessor a = Beans.forSetter(target.getClass(), method);
            ValueSnapshot old = a != null ? readValue(a, target) : ValueSnapshot.notCaptured();
            return new CallBefore(CallKind.SETTER, owner, target, method, safeArgs, null, a, propertyName(method),
                    old, -1, null, ScriptPosition.current(), Values.target(target, pathOf(owner)));
        }
        return null;
    }

    /** After the call returned normally. */
    static void afterCall(CallBefore b, Object result) {
        long end = System.nanoTime();
        long t0 = end;
        Guard.enter();
        try {
            LensSafe.run("call-after", () -> {
                switch (b.kind) {
                    case SETTER: {
                        ValueSnapshot newValue = b.accessor != null && canRead(b.accessor)
                                ? readValue(b.accessor, b.target) : Values.of(b.args[0]);
                        boolean incomplete = b.oldValue.isUnavailable() || newValue.isUnavailable();
                        Dispatcher.emit(new PropertyChange(b.position, b.targetRef, incomplete, b.property,
                                PropertyChange.Via.SETTER, b.oldValue, newValue));
                        break;
                    }
                    case COLLECTION:
                        Dispatcher.emit(new CollectionChange(b.position, b.targetRef, false, b.method, b.argSnapshots,
                                b.sizeBefore, size(b.target), Values.of(result), null));
                        break;
                    case SERVICE:
                        Dispatcher.emit(new ServiceCall(b.position, b.targetRef, false, b.service, b.method,
                                b.argSnapshots, Values.of(result), null, end - b.startNanos));
                        break;
                    default:
                        break;
                }
            });
        } finally {
            Guard.exit();
            ExecutionTracker.addOverhead(System.nanoTime() - t0);
        }
    }

    /** After the call threw (the exception still goes to the script, unchanged). */
    static void callFailed(CallBefore b, Throwable error) {
        long end = System.nanoTime();
        long t0 = end;
        Guard.enter();
        try {
            LensSafe.run("call-failed", () -> {
                String summary = scriptErrorSummary(error);
                if (b.kind == CallKind.COLLECTION) {
                    Dispatcher.emit(new CollectionChange(b.position, b.targetRef, false, b.method, b.argSnapshots,
                            b.sizeBefore, size(b.target), null, summary));
                } else if (b.kind == CallKind.SERVICE) {
                    Dispatcher.emit(new ServiceCall(b.position, b.targetRef, false, b.service, b.method,
                            b.argSnapshots, null, summary, end - b.startNanos));
                }
                // a setter that threw changed nothing we can vouch for; the script sees the error
            });
        } finally {
            Guard.exit();
            ExecutionTracker.addOverhead(System.nanoTime() - t0);
        }
    }

    // ------------------------------------------------------------------ arr[i] = v

    static final class ElementBefore {
        final LensJavaArray wrapper;
        final int index;
        final ValueSnapshot oldValue;
        final ScriptPosition position;
        final TargetRef target;

        ElementBefore(LensJavaArray wrapper, int index, ValueSnapshot oldValue, ScriptPosition position, TargetRef target) {
            this.wrapper = wrapper;
            this.index = index;
            this.oldValue = oldValue;
            this.position = position;
            this.target = target;
        }
    }

    static ElementBefore beforeElement(LensJavaArray w, int index) {
        long t0 = System.nanoTime();
        Guard.enter();
        try {
            return LensSafe.call("array-before", () -> {
                Object array = w.unwrap();
                if (index < 0 || index >= Array.getLength(array)) {
                    return null; // Rhino reports the bad index to the script
                }
                if (dropIfFull()) {
                    return null;
                }
                return new ElementBefore(w, index, Values.of(Array.get(array, index)), ScriptPosition.current(),
                        Values.target(array, w.path()));
            }, null);
        } finally {
            Guard.exit();
            ExecutionTracker.addOverhead(System.nanoTime() - t0);
        }
    }

    static void afterElement(ElementBefore b) {
        if (b == null) {
            return;
        }
        long t0 = System.nanoTime();
        Guard.enter();
        try {
            LensSafe.run("array-after", () -> {
                ValueSnapshot newValue = Values.of(Array.get(b.wrapper.unwrap(), b.index));
                Dispatcher.emit(new ArrayElementChange(b.position, b.target,
                        b.oldValue.isUnavailable() || newValue.isUnavailable(), b.index, b.oldValue, newValue));
            });
        } finally {
            Guard.exit();
            ExecutionTracker.addOverhead(System.nanoTime() - t0);
        }
    }

    // ------------------------------------------------------------------ member access and paths

    /** {@code obj.name} returned {@code member}: wrap methods, and name the path to child objects. */
    static Object onGet(LensJavaObject w, String name, Object member) {
        if (member instanceof NativeJavaMethod) {
            return LensSafe.call("wrap-method", () -> LensMethod.of((NativeJavaMethod) member), member);
        }
        if (member instanceof LensJavaObject || member instanceof LensJavaArray) {
            LensSafe.run("path", () -> setChildPath(member, pathOf(w) + "." + name));
        }
        return member;
    }

    /** After {@code owner.method(args)} returned {@code result}: name the path to a returned object. */
    static void trackPath(Scriptable thisObj, String method, Object[] args, Object result) {
        if (!(result instanceof LensJavaObject) && !(result instanceof LensJavaArray)) {
            return;
        }
        LensSafe.run("path", () -> {
            LensJavaObject owner = ownerOf(thisObj);
            if (owner != null) {
                setChildPath(result, pathOf(owner) + "." + method + "(" + shortArgs(args) + ")");
            }
        });
    }

    private static void setChildPath(Object child, String path) {
        if (child instanceof LensJavaObject) {
            ((LensJavaObject) child).setPathIfAbsent(path);
        } else if (child instanceof LensJavaArray) {
            ((LensJavaArray) child).setPathIfAbsent(path);
        }
    }

    /** The wrapper's path, else its label ({@link Lens#label}), else the simple class name. */
    static String pathOf(LensJavaObject w) {
        String path = w.path();
        if (path != null) {
            return path;
        }
        Object target = w.unwrap();
        String label = Lens.labelOf(target);
        return label != null ? label : ServicePolicies.simpleName(target.getClass());
    }

    // ------------------------------------------------------------------ helpers

    /** If the open execution is at its event limit, counts one dropped event and returns true. */
    private static boolean dropIfFull() {
        LensExecution x = ExecutionTracker.current();
        if (x != null && x.isFull()) {
            x.addDropped();
            return true;
        }
        return false;
    }

    static LensJavaObject ownerOf(Scriptable thisObj) {
        for (Scriptable s = thisObj; s != null; s = s.getPrototype()) {
            if (s instanceof LensJavaObject) {
                return (LensJavaObject) s;
            }
        }
        return null;
    }

    private static String serviceName(Object target) {
        String registered = Lens.registeredServiceName(target);
        if (registered != null) {
            return registered;
        }
        return LensSafe.call("service-policy", () -> Lens.config().services().serviceName(target), null);
    }

    private static boolean canRead(Beans.Accessor a) {
        return a.readable() && Lens.config().oldValues().shouldRead(a.type());
    }

    private static ValueSnapshot readValue(Beans.Accessor a, Object target) {
        if (!canRead(a)) {
            return ValueSnapshot.notCaptured();
        }
        return LensSafe.call("read-value", () -> {
            try {
                return Values.of(a.read(target));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }, ValueSnapshot.unavailable("getter failed"));
    }

    private static List<ValueSnapshot> snapshots(Object[] args) {
        List<ValueSnapshot> out = new ArrayList<>(args.length);
        for (Object a : args) {
            out.add(Values.of(a));
        }
        return out;
    }

    private static int size(Object target) {
        return LensSafe.call("collection-size", () -> target instanceof Collection
                ? ((Collection<?>) target).size() : ((Map<?, ?>) target).size(), -1);
    }

    /** {@code setFinalTotal} → {@code finalTotal}, {@code setURL} → {@code URL} (Rhino's bean naming). */
    static String propertyName(String setter) {
        String component = setter.substring(3);
        if (component.length() > 1 && Character.isUpperCase(component.charAt(1))) {
            return component;
        }
        return Character.toLowerCase(component.charAt(0)) + component.substring(1);
    }

    private static String shortArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            Object a = args[i];
            if (a instanceof Number || a instanceof Boolean) {
                Object n = a instanceof Double && ((Double) a) == Math.rint((Double) a) && !((Double) a).isInfinite()
                        ? (Object) ((Double) a).longValue() : a;
                out.append(n);
            } else if (a instanceof CharSequence) {
                out.append('"').append(Values.cut(a.toString(), 20)).append('"');
            } else {
                out.append('…');
            }
        }
        return out.toString();
    }

    private static String scriptErrorSummary(Throwable error) {
        Throwable t = error;
        if (t instanceof WrappedException) {
            t = ((WrappedException) t).getWrappedException();
        }
        if (t instanceof RhinoException) {
            return t.getClass().getSimpleName() + ": " + ((RhinoException) t).details();
        }
        return LensDiagnostics.summary(t);
    }
}
