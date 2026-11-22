package io.github.rhinolens.lens;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Object → value by identity (not {@code equals}, which entities often override), holding keys
 * weakly so registering an object never keeps it alive. Thread-safe. Cleared keys are removed on
 * writes; lookups stay cheap.
 */
final class WeakIdentityMap<V> {

    private static final class Key extends WeakReference<Object> {
        private final int hash;

        Key(Object referent) {
            super(referent);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Object mine = get();
            return mine != null && mine == ((Key) other).get();
        }
    }

    private final Map<Key, V> map = new HashMap<>();

    synchronized void put(Object key, V value) {
        map.keySet().removeIf(k -> k.get() == null);
        map.put(new Key(key), value);
    }

    synchronized V get(Object key) {
        if (key == null || map.isEmpty()) {
            return null;
        }
        return map.get(new Key(key));
    }

    synchronized void remove(Object key) {
        map.remove(new Key(key));
    }

    synchronized void clear() {
        map.clear();
    }
}
