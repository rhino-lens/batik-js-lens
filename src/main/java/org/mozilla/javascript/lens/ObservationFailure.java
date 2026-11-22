package org.mozilla.javascript.lens;

import java.util.Map;

/**
 * Lens's own observation code failed at some step. The script was not affected and carried on;
 * the failure is also counted in {@link Lens#diagnostics()}.
 */
public final class ObservationFailure extends ChangeEvent {

    private final String step;
    private final String error;

    ObservationFailure(ScriptPosition position, String step, String error) {
        super(Kind.OBSERVATION_FAILURE, position, null, false);
        this.step = step;
        this.error = error;
    }

    /** The step that failed, e.g. {@code "assign-before"}. */
    public String step() {
        return step;
    }

    /** The exception's class and message. */
    public String error() {
        return error;
    }

    @Override
    void addDetails(Map<String, Object> m) {
        m.put("step", step);
        m.put("error", error);
    }

    @Override
    String describe() {
        return "OBSERVATION FAILURE in " + step + ": " + error;
    }
}
