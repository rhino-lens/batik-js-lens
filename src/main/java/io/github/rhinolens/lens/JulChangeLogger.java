package io.github.rhinolens.lens;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs every event (one line each) and every finished execution (a summary) through
 * {@code java.util.logging}, logger {@code io.github.rhinolens.lens.changes}. SLF4J / Log4j users
 * can route JUL there with their usual bridge.
 *
 * <pre>{@code
 * Lens.addListener(new JulChangeLogger(Level.INFO));
 * }</pre>
 */
public final class JulChangeLogger implements ChangeListener {

    private static final Logger LOG = Logger.getLogger("io.github.rhinolens.lens.changes");

    private final Level level;

    /** Logs at {@link Level#INFO}. */
    public JulChangeLogger() {
        this(Level.INFO);
    }

    public JulChangeLogger(Level level) {
        this.level = level;
    }

    @Override
    public void onEvent(ChangeEvent event) {
        if (LOG.isLoggable(level)) {
            LOG.log(level, event.toText());
        }
    }

    @Override
    public void onExecutionFinished(LensReport report) {
        if (LOG.isLoggable(level)) {
            LOG.log(level, report.toText());
        }
    }
}
