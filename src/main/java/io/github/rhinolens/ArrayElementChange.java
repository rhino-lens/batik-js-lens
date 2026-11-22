package io.github.rhinolens;

import java.util.Map;

/** A script set an element of a Java array: {@code arr[i] = v}. */
public final class ArrayElementChange extends ChangeEvent {

    private final int index;
    private final ValueSnapshot oldValue;
    private final ValueSnapshot newValue;

    ArrayElementChange(ScriptPosition position, TargetRef target, boolean incomplete, int index,
                       ValueSnapshot oldValue, ValueSnapshot newValue) {
        super(Kind.ARRAY_ELEMENT, position, target, incomplete);
        this.index = index;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public int index() {
        return index;
    }

    public ValueSnapshot oldValue() {
        return oldValue;
    }

    public ValueSnapshot newValue() {
        return newValue;
    }

    @Override
    void addDetails(Map<String, Object> m) {
        m.put("index", index);
        m.put("oldValue", oldValue.toMap());
        m.put("newValue", newValue.toMap());
    }

    @Override
    String describe() {
        return "ARRAY " + where(target()) + "[" + index + "] " + oldValue + " -> " + newValue;
    }
}
