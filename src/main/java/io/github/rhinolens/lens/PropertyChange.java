package io.github.rhinolens.lens;

import java.util.Map;

/**
 * A script set a Java property: {@code obj.x = v} ({@link Via#ASSIGNMENT}, or {@link Via#FIELD} for
 * a public field) or {@code obj.setX(v)} ({@link Via#SETTER}).
 *
 * <p>The new value is read back after the change, so it shows what was actually stored (a setter
 * may normalize what it is given).
 */
public final class PropertyChange extends ChangeEvent {

    /** How the script changed the property. */
    public enum Via { SETTER, ASSIGNMENT, FIELD }

    private final String property;
    private final Via via;
    private final ValueSnapshot oldValue;
    private final ValueSnapshot newValue;

    PropertyChange(ScriptPosition position, TargetRef target, boolean incomplete, String property, Via via,
                   ValueSnapshot oldValue, ValueSnapshot newValue) {
        super(Kind.PROPERTY, position, target, incomplete);
        this.property = property;
        this.via = via;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /** Property name as a script would write it, e.g. {@code status} for {@code setStatus}. */
    public String property() {
        return property;
    }

    public Via via() {
        return via;
    }

    public ValueSnapshot oldValue() {
        return oldValue;
    }

    public ValueSnapshot newValue() {
        return newValue;
    }

    @Override
    void addDetails(Map<String, Object> m) {
        m.put("property", property);
        m.put("via", via.name());
        m.put("oldValue", oldValue.toMap());
        m.put("newValue", newValue.toMap());
    }

    @Override
    String describe() {
        return via + " " + where(target()) + "." + property + " " + oldValue + " -> " + newValue;
    }
}
