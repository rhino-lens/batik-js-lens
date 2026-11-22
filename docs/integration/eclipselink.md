# EclipseLink: which rule changed what

EclipseLink detects at commit **that** an entity attribute changed. Lens records **which rule, which line, old value → new value**, and which services the rule called. To combine the two, collect Lens events on the transaction's thread and read them at commit.

## Collect events per thread

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

`InMemoryJournal` keeps events per thread (up to 100,000; oldest dropped first). `drain()` returns and clears this thread's events.

## Attach them at commit

EclipseLink calls session event listeners on the committing thread. In `preCommitUnitOfWork` (or `postCalculateUnitOfWorkChangeSet`), drain the journal and match events to the change set:
- `PropertyChange.target().className()` and `target().entityId()` identify the entity. The default `IdResolver` uses `getId()`, so supply your own if your entities name their key differently.
- `PropertyChange.property()` is the attribute, as scripts see it (`status` for `getStatus` / `setStatus`).
- `ruleName()`, `line()`, `oldValue()`, `newValue()` and `executionId()` / `attributes()` say who changed it and how.

```text
EclipseLink change set:  Order(id=42).status  NEW -> PRICED
Lens events:             [pricing-rule.js:12] SETTER order.status "NEW" -> "PRICED"   (execution 3f2b…, recordId=42)
```

What you do with the match is up to your application: write an audit row, attach it to your own change-history table, or log it.

## Things to know

- **Same thread.** The journal is per thread, so drain it on the thread that ran the rules, typically the request or transaction thread. If rules run on worker threads, drain there, or use a listener that writes events to a store keyed by your transaction id. (You can put the transaction id in the execution attributes: `Lens.execution(rule, Map.of("txId", id))`.)
- **Clear on rollback.** Call `journal.clear()` when a transaction rolls back, so events don't carry over to the next one on that thread.
- **Lazy relationships.** Capturing old values calls getters, and on lazy relationships that can trigger database loads inside the transaction. Use `OldValues.SIMPLE_TYPES` if that matters ([configuration](../configuration.md#old-values-oldvalues)).
- **Changes made in Java.** A service the rule calls may change entities in Java; Lens records the `ServiceCall`, not those inner changes. EclipseLink still sees them, and the `ServiceCall` tells you which rule triggered them.
