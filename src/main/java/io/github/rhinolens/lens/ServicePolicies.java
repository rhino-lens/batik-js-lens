package io.github.rhinolens.lens;

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
