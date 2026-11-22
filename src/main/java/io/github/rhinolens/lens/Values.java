package io.github.rhinolens.lens;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

/** Renders values and targets for events. Every entry point is failure-safe. */
final class Values {

    private Values() {
    }

    /** Snapshot of {@code value}; on failure an "unavailable" snapshot, and the failure is flagged. */
    static ValueSnapshot of(Object value) {
        return LensSafe.call("render-value", () -> render(value), ValueSnapshot.unavailable("could not render"));
    }

    /** Reference to {@code javaObject} reached by {@code path} (may be null). */
    static TargetRef target(Object javaObject, String path) {
        return LensSafe.call("target", () -> new TargetRef(javaObject.getClass().getName(),
                System.identityHashCode(javaObject), idOf(javaObject), path, javaObject),
                new TargetRef(String.valueOf(javaObject == null ? null : javaObject.getClass().getName()), 0, null, path));
    }

    /** The configured id of {@code o} as text, or null. */
    static String idOf(Object o) {
        return LensSafe.call("id-resolver", () -> {
            Object id = Lens.config().idResolver().idOf(o);
            return id == null ? null : String.valueOf(id);
        }, null);
    }

    private static ValueSnapshot render(Object v) {
        LensConfig config = Lens.config();
        int max = config.maxValueLength();
        if (v instanceof Wrapper) {
            v = ((Wrapper) v).unwrap();
        }
        Object raw = config.keepRawValues() ? v : null;
        if (v == null) {
            return new ValueSnapshot("null", "null", true, null);
        }
        if (v == Undefined.instance) {
            return new ValueSnapshot("undefined", "undefined", true, null);
        }
        if (v instanceof CharSequence) {
            return new ValueSnapshot("String", quote(v.toString(), max), true, raw);
        }
        if (v instanceof Number || v instanceof Boolean || v instanceof Character) {
            return new ValueSnapshot(v.getClass().getSimpleName(), v.toString(), true, raw);
        }
        if (v instanceof Enum) {
            return new ValueSnapshot(((Enum<?>) v).getDeclaringClass().getSimpleName(), ((Enum<?>) v).name(), true, raw);
        }
        if (v instanceof Scriptable) { // a JavaScript value
            return new ValueSnapshot("js:" + ((Scriptable) v).getClassName(), renderJs((Scriptable) v), true, null);
        }
        String type = ServicePolicies.simpleName(v.getClass());
        if (v instanceof Collection) {
            return new ValueSnapshot(type, type + "(size=" + ((Collection<?>) v).size() + ")", true, raw);
        }
        if (v instanceof Map) {
            return new ValueSnapshot(type, type + "(size=" + ((Map<?, ?>) v).size() + ")", true, raw);
        }
        if (v.getClass().isArray()) {
            String component = v.getClass().getComponentType().getSimpleName();
            return new ValueSnapshot(component + "[]", component + "[" + Array.getLength(v) + "]", true, raw);
        }
        if (v instanceof Date || v instanceof Temporal || v instanceof BigDecimal || v instanceof BigInteger
                || v instanceof UUID) {
            return new ValueSnapshot(type, cut(v.toString(), max), true, raw);
        }
        String id = idOf(v);
        if (id != null) {
            return new ValueSnapshot(type, type + "(id=" + id + ")", true, raw);
        }
        Object fallback = type + "@" + Integer.toHexString(System.identityHashCode(v));
        final Object value = v;
        String text = LensSafe.call("render-toString", () -> String.valueOf(value), (String) fallback);
        return new ValueSnapshot(type, cut(text, max), true, raw);
    }

    private static String renderJs(Scriptable s) {
        if (s instanceof Function) {
            String name = s instanceof BaseFunction ? ((BaseFunction) s).getFunctionName() : "";
            return "function " + (name == null ? "" : name) + "()";
        }
        if (s instanceof NativeArray) {
            return "[array length=" + ((NativeArray) s).getLength() + "]";
        }
        return "[object " + s.getClassName() + "]";
    }

    private static String quote(String s, int max) {
        return '"' + cut(s, max).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
