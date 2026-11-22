package io.github.rhinolens.lens;

import java.util.LinkedHashMap;
import java.util.Map;

/** Which Java object an event is about. */
public final class TargetRef {

    private final String className;
    private final int identityHash;
    private final String entityId;
    private final String path;

    TargetRef(String className, int identityHash, String entityId, String path) {
        this.className = className;
        this.identityHash = identityHash;
        this.entityId = entityId;
        this.path = path;
    }

    /** Fully qualified class name. */
    public String className() {
        return className;
    }

    /** {@link System#identityHashCode(Object)} of the object: tells instances apart within a run. */
    public int identityHash() {
        return identityHash;
    }

    /** The object's id from the configured {@link IdResolver}, or {@code null}. */
    public String entityId() {
        return entityId;
    }

    /**
     * How the script reached the object, e.g. {@code order.getItems().get(0)}, when known: it starts
     * at a name given with {@link Lens#label(Object, String)}, or else at the class name.
     */
    public String path() {
        return path;
    }

    /** Short label: {@code Order(id=42)} or {@code Order@1b6d3586}. */
    public String label() {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        return entityId != null ? simple + "(id=" + entityId + ")"
                : simple + "@" + Integer.toHexString(identityHash);
    }

    /** True if this refers to {@code object}. */
    public boolean refersTo(Object object) {
        return object != null && System.identityHashCode(object) == identityHash
                && object.getClass().getName().equals(className);
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("className", className);
        m.put("identityHash", Integer.toHexString(identityHash));
        m.put("entityId", entityId);
        m.put("path", path);
        m.put("label", label());
        return m;
    }

    @Override
    public String toString() {
        return path != null ? path : label();
    }
}
