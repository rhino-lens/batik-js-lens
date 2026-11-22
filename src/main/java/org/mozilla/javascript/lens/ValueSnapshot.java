package org.mozilla.javascript.lens;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A value as it was at capture time: its type and a rendered text, frozen then (the object may
 * change later). The raw reference is kept only when {@link LensConfig#keepRawValues(boolean)} is on.
 */
public final class ValueSnapshot {

    private static final ValueSnapshot NOT_CAPTURED = new ValueSnapshot(null, "<not captured>", false, null);

    private final String type;
    private final String text;
    private final boolean captured;
    private final Object raw;

    ValueSnapshot(String type, String text, boolean captured, Object raw) {
        this.type = type;
        this.text = text;
        this.captured = captured;
        this.raw = raw;
    }

    static ValueSnapshot notCaptured() {
        return NOT_CAPTURED;
    }

    static ValueSnapshot unavailable(String reason) {
        return new ValueSnapshot(null, "<unavailable: " + reason + ">", false, null);
    }

    /** True for a snapshot that failed (as opposed to one deliberately not captured). */
    boolean isUnavailable() {
        return !captured && text.startsWith("<unavailable");
    }

    /** Simple type name, e.g. {@code "String"}, {@code "Order"}, {@code "undefined"}; {@code null} if not captured. */
    public String type() {
        return type;
    }

    /** Rendered value, e.g. {@code "PRICED"} (quoted), {@code 35.09}, {@code Order(id=42)}. */
    public String text() {
        return text;
    }

    /** False when the value was not read (old-value mode, write-only property, or a failure). */
    public boolean isCaptured() {
        return captured;
    }

    /** The object itself, or {@code null} unless raw values are kept. */
    public Object raw() {
        return raw;
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("text", text);
        if (!captured) {
            m.put("captured", false);
        }
        return m;
    }

    @Override
    public String toString() {
        return text;
    }
}
