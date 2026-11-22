package io.github.rhinolens.lens;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects events per thread until drained: for example, an EclipseLink listener can
 * {@link #drain()} at commit and attach "rule X, line N changed attribute Y" to the changes it
 * detected in the same transaction (same thread).
 *
 * <pre>{@code
 * InMemoryJournal journal = new InMemoryJournal();
 * Lens.addListener(journal);
 * // ... scripts run in the transaction ...
 * List<ChangeEvent> events = journal.drain();   // this thread's events, then cleared
 * }</pre>
 *
 * <p>Holds at most {@code maxPerThread} events per thread; older ones are dropped first.
 * Observation failures are not journaled (see {@link Lens#diagnostics()}).
 */
public final class InMemoryJournal implements ChangeListener {

    private final int maxPerThread;
    private final ThreadLocal<ArrayDeque<ChangeEvent>> events = ThreadLocal.withInitial(ArrayDeque::new);

    /** At most 100,000 events per thread. */
    public InMemoryJournal() {
        this(100_000);
    }

    public InMemoryJournal(int maxPerThread) {
        this.maxPerThread = Math.max(1, maxPerThread);
    }

    @Override
    public void onEvent(ChangeEvent event) {
        if (event instanceof ObservationFailure) {
            return;
        }
        ArrayDeque<ChangeEvent> queue = events.get();
        if (queue.size() >= maxPerThread) {
            queue.pollFirst(); // constant time, however full
        }
        queue.addLast(event);
    }

    /** This thread's events, oldest first, without clearing them. */
    public List<ChangeEvent> peek() {
        return Collections.unmodifiableList(new ArrayList<>(events.get()));
    }

    /** This thread's events, oldest first; the journal for this thread is then empty. */
    public List<ChangeEvent> drain() {
        List<ChangeEvent> out = Collections.unmodifiableList(new ArrayList<>(events.get()));
        events.remove();
        return out;
    }

    /** Discards this thread's events. */
    public void clear() {
        events.remove();
    }
}
