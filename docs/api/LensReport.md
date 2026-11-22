# LensReport

`public final class LensReport`: everything observed during one execution, frozen. It's immutable, so it's safe to keep, log or store. The JSON form is described in [report-format.md](../report-format.md).

Get one from `LensExecution.finish()` / `report()`, `Lens.observe(...)`, `Lens.lastReport()`, `ChangeListener.onExecutionFinished(...)`, or `lens.report()` in a script (a snapshot so far).

| Method | Returns | Notes |
|---|---|---|
| `static empty()` | `LensReport` | A report with nothing in it |
| `executionId()` | `String` | |
| `ruleName()` | `String` or null | The execution's name, else the script name of its first event |
| `attributes()` | `Map<String, Object>` | |
| `startedAt()` · `endedAt()` | `Instant` | `endedAt` is null for a `RUNNING` snapshot |
| `durationNanos()` · `durationMillis()` | `long` · `double` | Total time (or so far) |
| `outcome()` | `Outcome` | `RUNNING`, `SUCCESS`, `FAILED`, `STOPPED` |
| `error()` | `String` or null | The script's error when `FAILED` |
| `events()` | `List<ChangeEvent>` | Every event kept, in order |
| `eventsOf(Class<T> type)` | `List<T>` | Events of one type |
| `propertyChanges()` · `collectionChanges()` · `serviceCalls()` | lists | Shortcuts for `eventsOf` |
| `changesTo(Object object)` | `List<ChangeEvent>` | Events about this object (by identity) |
| `netChanges()` | `List<NetChange>` | Per object and property (or array element): first old value → last new value, count, lines |
| `changeCount()` | `int` | Property, array element and collection changes |
| `stats()` | `Stats` | See below |
| `observationFailures()` | `List<LensDiagnostics.Failure>` | Failures inside Lens during this execution (the script wasn't affected) |
| `isTruncated()` | `boolean` | True if events were dropped at the per-execution limit |
| `toMap()` | `Map<String, Object>` | Plain maps, lists, strings, numbers |
| `toJson()` | `String` | No dependencies |
| `toText()` | `String` | Multi-line summary for logs |

## `LensReport.Stats`

| Method | Returns | Notes |
|---|---|---|
| `counts()` · `count(Kind kind)` | | Events kept, per kind |
| `serviceCallNanos()` | `long` | Total time inside service calls |
| `overheadNanos()` | `long` | Time Lens spent capturing and dispatching: the cost of observing this execution |
| `suppressed()` | `long` | Operations not observed because Lens's guard was active (quiet blocks, listeners) |
| `dropped()` | `long` | Events dropped beyond `maxEventsPerExecution` |
