package io.github.rhinolens;

/**
 * Receives what Lens observes, on the script's thread, as it happens.
 *
 * <p>Listeners run inside Lens's guard: anything a listener does (including running another
 * script) is not observed, so a listener can never cause new events. Each listener is called
 * separately; if one throws, the failure is flagged in {@link Lens#diagnostics()} and the other
 * listeners and the script carry on. Keep listeners fast: they run inline with the script.
 */
public interface ChangeListener {

    /** Called for every event, in order. */
    void onEvent(ChangeEvent event);

    /** Called once when an execution ends, with its final report. */
    default void onExecutionFinished(LensReport report) {
    }
}
