package io.github.rhinolens.lens;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Decides which Java objects in a script's reach are services (repositories, CRUD services, …).
 * Every method a script calls on a service is recorded as a {@link ServiceCall}.
 * Objects registered with {@link Lens#registerService(Object, String)} are always services.
 */
@FunctionalInterface
public interface ServicePolicy {

    /** The name to show for {@code target} if it is a service, else {@code null}. */
    String serviceName(Object target);

    /** Combines two policies: this one first, then {@code other}. */
    default ServicePolicy or(ServicePolicy other) {
        return target -> {
            String name = serviceName(target);
            return name != null ? name : other.serviceName(target);
        };
    }

    /**
     * The default: classes (or their superclasses / interfaces) annotated with
     * {@code org.springframework.stereotype.Service} or {@code …Repository}, or whose simple
     * name ends with {@code Service}, {@code Repository} or {@code Dao}.
     */
    static ServicePolicy defaults() {
        return annotations("org.springframework.stereotype.Service", "org.springframework.stereotype.Repository")
                .or(classNameSuffixes("Service", "Repository", "Dao"));
    }

    /** No object is a service (only registered ones). */
    static ServicePolicy none() {
        return target -> null;
    }

    /** Classes annotated with any of these annotations (by fully qualified name; no dependency needed). */
    static ServicePolicy annotations(String... annotationClassNames) {
        List<String> names = Arrays.asList(annotationClassNames);
        ClassValue<Boolean> matches = new ClassValue<Boolean>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                    if (annotated(c, names)) {
                        return true;
                    }
                    for (Class<?> i : c.getInterfaces()) {
                        if (annotated(i, names)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
        return target -> matches.get(target.getClass()) ? ServicePolicies.defaultName(target) : null;
    }

    /** Classes whose simple name (proxy suffixes removed) ends with any of these. */
    static ServicePolicy classNameSuffixes(String... suffixes) {
        ClassValue<Boolean> matches = new ClassValue<Boolean>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                String simple = ServicePolicies.simpleName(type);
                for (String s : suffixes) {
                    if (simple.endsWith(s)) {
                        return true;
                    }
                }
                return false;
            }
        };
        return target -> matches.get(target.getClass()) ? ServicePolicies.defaultName(target) : null;
    }

    /** Instances of any of these types. */
    static ServicePolicy types(Class<?>... types) {
        return target -> {
            for (Class<?> t : types) {
                if (t.isInstance(target)) {
                    return ServicePolicies.defaultName(target);
                }
            }
            return null;
        };
    }

    private static boolean annotated(Class<?> c, List<String> names) {
        for (Annotation a : c.getDeclaredAnnotations()) {
            if (names.contains(a.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }
}

/** Helpers for {@link ServicePolicy}. */
final class ServicePolicies {

    private ServicePolicies() {
    }

    /** {@code OrderRepository} → {@code orderRepository}; Spring/CGLIB proxy suffixes removed. */
    static String defaultName(Object target) {
        String simple = simpleName(target.getClass());
        return simple.isEmpty() ? simple : Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    static String simpleName(Class<?> type) {
        Class<?> c = type;
        while (c.getSimpleName().isEmpty() && c.getSuperclass() != null) {
            c = c.getSuperclass();
        }
        String name = c.getSimpleName();
        int proxy = name.indexOf("$$");
        return proxy > 0 ? name.substring(0, proxy) : name;
    }
}
