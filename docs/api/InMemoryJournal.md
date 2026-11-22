# InMemoryJournal

`public final class InMemoryJournal implements ChangeListener`: collects events per thread until drained. Use it to attach "rule X, line N changed attribute Y" to what a persistence layer detects at commit ([EclipseLink guide](../integration/eclipselink.md)).

<!-- snippet: journal -->
```java
InMemoryJournal journal = new InMemoryJournal();
Lens.addListener(journal);

// ... in a transaction, rules run on this thread ...
evaluate("status-rule.js", "order.setStatus('SHIPPED');", Map.of("order", new Order(42)));

// at commit (same thread): what did the rules change?
for (ChangeEvent event : journal.drain()) {
    System.out.println(event.toText());   // [status-rule.js:1] SETTER Order.status "NEW" -> "SHIPPED"
}
```

| Method | Returns | Notes |
|---|---|---|
| `InMemoryJournal()` | | At most 100,000 events per thread |
| `InMemoryJournal(int maxPerThread)` | | Oldest events are dropped first beyond the limit |
| `peek()` | `List<ChangeEvent>` | This thread's events, oldest first, without clearing them |
| `drain()` | `List<ChangeEvent>` | This thread's events, oldest first; the journal for this thread is then empty |
| `clear()` | | Discard this thread's events (e.g. on rollback) |

`ObservationFailure` events aren't journaled.
