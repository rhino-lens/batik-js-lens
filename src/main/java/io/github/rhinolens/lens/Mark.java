package io.github.rhinolens.lens;

import java.util.Map;

/** A checkpoint a script recorded with {@code lens.mark("label")}. */
public final class Mark extends ChangeEvent {

    private final String label;

    Mark(ScriptPosition position, String label) {
        super(Kind.MARK, position, null, false);
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    void addDetails(Map<String, Object> m) {
        m.put("label", label);
    }

    @Override
    String describe() {
        return "MARK " + label;
    }
}
