package io.github.rhinolens.lens;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.UUID;

/**
 * Whether Lens reads a property's value before (and after) a change. Reading means calling the
 * getter, which on JPA / EclipseLink entities can load a lazy relationship from the database.
 */
public enum OldValues {

    /** Always read old and new values (the default). Most complete; may trigger lazy loads. */
    ALWAYS,

    /**
     * Read only properties of simple types: primitives and their wrappers, {@code String},
     * enums, {@code BigDecimal}, {@code BigInteger}, {@code Date}, {@code java.time} types,
     * {@code UUID}. Other properties record "not captured" for the old value and the assigned
     * value as the new one, without calling their getter.
     */
    SIMPLE_TYPES,

    /** Never call getters; the new value is the assigned value. */
    NONE;

    boolean shouldRead(Class<?> propertyType) {
        switch (this) {
            case ALWAYS:
                return true;
            case SIMPLE_TYPES:
                return isSimple(propertyType);
            default:
                return false;
        }
    }

    static boolean isSimple(Class<?> t) {
        if (t == null) {
            return false;
        }
        return t.isPrimitive() || Number.class.isAssignableFrom(t) || t == Boolean.class
                || t == Character.class || CharSequence.class.isAssignableFrom(t) || t.isEnum()
                || t == BigDecimal.class || t == BigInteger.class || Date.class.isAssignableFrom(t)
                || Temporal.class.isAssignableFrom(t) || t == UUID.class;
    }
}
