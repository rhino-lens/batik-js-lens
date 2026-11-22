# ChangeListener

`public interface ChangeListener`: receives what Lens observes, on the script's thread, as it happens. Add one with `Lens.addListener(...)`.

| Method | Notes |
|---|---|
| `void onEvent(ChangeEvent event)` | Every event, in order. This includes [ObservationFailure](ObservationFailure.md) events |
| `default void onExecutionFinished(LensReport report)` | Once per finished execution |

<!-- snippet: listener -->
```java
List<String> lines = new ArrayList<>();
ChangeListener printer = new ChangeListener() {
    @Override
    public void onEvent(ChangeEvent event) {
        if (event instanceof PropertyChange) {
            PropertyChange c = (PropertyChange) event;
            lines.add(c.ruleName() + ":" + c.line() + " " + c.target().label() + "." + c.property()
                    + " " + c.oldValue().text() + " -> " + c.newValue().text());
        }
    }

    @Override
    public void onExecutionFinished(LensReport report) {
        lines.add(report.ruleName() + " took " + report.durationMillis() + " ms");
    }
};
Lens.addListener(printer);
```

- **Inline:** listeners run on the script's thread, inside the script's time, so keep them fast. Hand slow work (I/O, remote calls) to another thread.
- **Not observed:** anything a listener does (including running another script) isn't observed, so a listener can never cause new events. See [loop safety](../loop-safety.md).
- **Isolated:** each listener is called separately. If one throws, the failure is flagged and the other listeners and the script carry on.
- **Built-ins:** [InMemoryJournal](InMemoryJournal.md) and [JulChangeLogger](JulChangeLogger.md).
