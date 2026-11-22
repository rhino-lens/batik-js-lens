package io.github.rhinolens;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached reflection: how to read a Java property the way Rhino resolves it. Rhino lets a public
 * field win over a bean property of the same name, so this does too.
 */
final class Beans {

    /** Reads one property of one class: a public field, or a getter ({@code getX} / {@code isX}). */
    static final class Accessor {
        private final Field field;
        private final Method getter;
        private final Class<?> writeOnlyType;

        private Accessor(Field field, Method getter, Class<?> writeOnlyType) {
            this.field = field;
            this.getter = getter;
            this.writeOnlyType = writeOnlyType;
        }

        boolean isField() {
            return field != null;
        }

        /** False for a write-only bean property (a setter with no getter). */
        boolean readable() {
            return field != null || getter != null;
        }

        Class<?> type() {
            if (field != null) {
                return field.getType();
            }
            return getter != null ? getter.getReturnType() : writeOnlyType;
        }

        /** Reads the value; exceptions from the getter are thrown (callers run this via LensSafe). */
        Object read(Object target) throws ReflectiveOperationException {
            if (field != null) {
                return field.get(target);
            }
            if (getter == null) {
                throw new UnsupportedOperationException("write-only property");
            }
            return invoke(getter, target);
        }
    }

    private static final ClassValue<Map<String, Optional<Accessor>>> PROPERTIES =
            new ClassValue<Map<String, Optional<Accessor>>>() {
                @Override
                protected Map<String, Optional<Accessor>> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };

    private static final ClassValue<Map<String, Optional<Method>>> NO_ARG_METHODS =
            new ClassValue<Map<String, Optional<Method>>>() {
                @Override
                protected Map<String, Optional<Method>> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };

    private Beans() {
    }

    /** The accessor for property {@code name} (as written in a script), or {@code null}. */
    static Accessor property(Class<?> type, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return PROPERTIES.get(type).computeIfAbsent(name, n -> Optional.ofNullable(find(type, n))).orElse(null);
    }

    /**
     * The accessor behind setter {@code setX}: Rhino's bean name rules, using the part after
     * "set" for the getter ({@code getX} / {@code isX}). {@code null} if there is no getter.
     */
    static Accessor forSetter(Class<?> type, String setterName) {
        if (setterName.length() <= 3 || !setterName.startsWith("set")) {
            return null;
        }
        String component = setterName.substring(3);
        Method getter = noArg(type, "get" + component);
        if (getter == null) {
            getter = noArg(type, "is" + component);
        }
        return getter == null ? null : new Accessor(null, getter, null);
    }

    /** A public no-argument method by name, or {@code null}. */
    static Method noArg(Class<?> type, String name) {
        return NO_ARG_METHODS.get(type).computeIfAbsent(name, n -> {
            try {
                Method m = type.getMethod(n);
                return m.getReturnType() == void.class || Modifier.isStatic(m.getModifiers())
                        ? Optional.<Method>empty() : Optional.of(m);
            } catch (NoSuchMethodException e) {
                return Optional.<Method>empty();
            }
        }).orElse(null);
    }

    /** Calls a public method, falling back to making it accessible (e.g. on non-public classes). */
    static Object invoke(Method m, Object target) throws ReflectiveOperationException {
        try {
            return m.invoke(target);
        } catch (IllegalAccessException e) {
            if (m.trySetAccessible()) {
                return m.invoke(target);
            }
            throw e;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private static Accessor find(Class<?> type, String name) {
        try {
            Field f = type.getField(name);
            if (!Modifier.isStatic(f.getModifiers())) {
                return new Accessor(f, null, null);
            }
        } catch (NoSuchFieldException ignored) {
            // not a public field; try a getter
        }
        String component = Character.isUpperCase(name.charAt(0))
                ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Method getter = noArg(type, "get" + component);
        if (getter == null) {
            getter = noArg(type, "is" + component);
        }
        if (getter != null) {
            return new Accessor(null, getter, null);
        }
        // a write-only bean property (setter without getter) is still a property
        for (Method m : type.getMethods()) {
            if (m.getName().equals("set" + component) && m.getParameterCount() == 1
                    && !Modifier.isStatic(m.getModifiers())) {
                return new Accessor(null, null, m.getParameterTypes()[0]);
            }
        }
        return null;
    }
}
