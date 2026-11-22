package io.github.rhinolens;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

/** Which Java object an event is about. */
public final class TargetRef {

    private final String className;
    private final int identityHash;
    private final String entityId;
    private final String path;
    /** The object itself, held weakly (never kept alive by an event): exact identity while it lives. */
    private final WeakReference<Object> object;

    TargetRef(String className, int identityHash, String entityId, String path) {
        this(className, identityHash, entityId, path, null);
    }

    TargetRef(String className, int identityHash, String entityId, String path, Object object) {
        this.className = className;
        this.identityHash = identityHash;
        this.entityId = entityId;
        this.path = path;
        this.object = object == null ? null : new WeakReference<>(object);
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

    /**
     * True if this refers to {@code candidate}: by identity while the object is still in memory;
     * after it has been garbage-collected, by class name and identity hash.
     */
    public boolean refersTo(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Object live = live();
        if (live != null) {
            return live == candidate;
        }
        return System.identityHashCode(candidate) == identityHash && candidate.getClass().getName().equals(className);
    }

    /** True if both refer to the same object (identity while it lives; class and hash after). */
    boolean sameObjectAs(TargetRef other) {
        Object mine = live();
        Object theirs = other.live();
        if (mine != null && theirs != null) {
            return mine == theirs;
        }
        return identityHash == other.identityHash && className.equals(other.className);
    }

    private Object live() {
        return object == null ? null : object.get();
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
