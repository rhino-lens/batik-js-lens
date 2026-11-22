package io.github.rhinolens.lens;

/** What {@link Lens#observe} returns: the work's value and the execution's report. */
public final class LensResult<T> {

    private final T value;
    private final LensReport report;

    LensResult(T value, LensReport report) {
        this.value = value;
        this.report = report;
    }

    /** What the observed work returned (e.g. the script's result). */
    public T value() {
        return value;
    }

    public LensReport report() {
        return report;
    }
}
