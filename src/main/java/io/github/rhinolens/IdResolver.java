package io.github.rhinolens;

import java.lang.reflect.Method;

/**
 * Finds an object's identity for events and reports, e.g. a JPA entity's primary key, so that
 * changes can be matched with what a persistence layer (such as EclipseLink) saw.
 * Failures are flagged and treated as "no id".
 */
@FunctionalInterface
public interface IdResolver {

    /** The id of {@code object}, or {@code null} if it has none. */
    Object idOf(Object object);

    /** Calls a public no-argument {@code getId()} (or {@code getID()}) if the class has one. The default. */
    static IdResolver getIdMethod() {
        return object -> {
            Method m = Beans.noArg(object.getClass(), "getId");
            if (m == null) {
                m = Beans.noArg(object.getClass(), "getID");
            }
            if (m == null) {
                return null;
            }
            try {
                return Beans.invoke(m, object);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    /** Never reports an id. */
    static IdResolver none() {
        return object -> null;
    }
}
