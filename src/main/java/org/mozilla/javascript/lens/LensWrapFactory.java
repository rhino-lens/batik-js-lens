package org.mozilla.javascript.lens;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;

/**
 * Rhino's WrapFactory, extended: Java objects and arrays get Lens wrappers. If creating one fails
 * (flagged), Rhino's plain wrapper is used, exactly as without Lens. Values that cannot change
 * (strings, numbers, booleans, characters), Lens's own classes and excluded classes always get
 * plain wrappers.
 */
final class LensWrapFactory extends WrapFactory {

    @Override
    public Object wrap(Context cx, Scriptable scope, Object obj, Class staticType) {
        if (obj != null && obj.getClass().isArray() && Lens.wrapping()) {
            Object wrapped = LensSafe.call("wrap-array", () -> {
                TestHooks.maybeFail(TestHooks.Point.WRAP_ARRAY);
                return new LensJavaArray(scope, obj);
            }, null);
            if (wrapped != null) {
                return wrapped;
            }
        }
        return super.wrap(cx, scope, obj, staticType);
    }

    @Override
    public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
        if (Lens.wrapping() && shouldWrap(javaObject)) {
            Scriptable wrapped = LensSafe.call("wrap-object", () -> {
                TestHooks.maybeFail(TestHooks.Point.WRAP_OBJECT);
                return new LensJavaObject(scope, javaObject, staticType);
            }, null);
            if (wrapped != null) {
                return wrapped;
            }
        }
        return super.wrapAsJavaObject(cx, scope, javaObject, staticType);
    }

    private static boolean shouldWrap(Object o) {
        return LensSafe.call("wrap-check", () -> {
            if (o == null || o instanceof CharSequence || o instanceof Number || o instanceof Boolean
                    || o instanceof Character) {
                return false;
            }
            Class<?> type = o.getClass();
            if (type.getName().startsWith("org.mozilla.javascript.lens.")) {
                return false; // Lens's own objects (reports, script API) are never observed
            }
            return !Lens.config().isExcluded(type);
        }, false);
    }
}
