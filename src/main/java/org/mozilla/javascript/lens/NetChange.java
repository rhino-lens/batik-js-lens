package org.mozilla.javascript.lens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The overall change to one property (or array element) of one object during an execution:
 * the value before the first change and after the last, however many times the script touched it.
 */
public final class NetChange {

    private final TargetRef target;
    private final String property;
    private final ValueSnapshot oldValue;
    private final ValueSnapshot newValue;
    private final int changes;
    private final List<Integer> lines;

    NetChange(TargetRef target, String property, ValueSnapshot oldValue, ValueSnapshot newValue, int changes,
              List<Integer> lines) {
        this.target = target;
        this.property = property;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changes = changes;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public TargetRef target() {
        return target;
    }

    /** Property name, or {@code [i]} for an array element. */
    public String property() {
        return property;
    }

    /** Value before the first change. */
    public ValueSnapshot oldValue() {
        return oldValue;
    }

    /** Value after the last change. */
    public ValueSnapshot newValue() {
        return newValue;
    }

    /** How many times it was changed. */
    public int changes() {
        return changes;
    }

    /** Script lines where it was changed, in order. */
    public List<Integer> lines() {
        return lines;
    }

    /** True if the final value differs from the original one (as rendered). */
    public boolean isEffective() {
        return !oldValue.isCaptured() || !newValue.isCaptured() || !oldValue.text().equals(newValue.text());
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", target.toMap());
        m.put("property", property);
        m.put("oldValue", oldValue.toMap());
        m.put("newValue", newValue.toMap());
        m.put("changes", changes);
        m.put("lines", lines);
        return m;
    }

    @Override
    public String toString() {
        return target + "." + property + " " + oldValue + " -> " + newValue
                + (changes > 1 ? " (" + changes + " changes)" : "");
    }
}
