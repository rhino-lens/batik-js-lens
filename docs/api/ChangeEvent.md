# ChangeEvent

`public abstract class ChangeEvent`: the base type of everything Lens records. It's immutable to applications. The subclasses are [PropertyChange](PropertyChange.md), [ArrayElementChange](ArrayElementChange.md), [CollectionChange](CollectionChange.md), [ServiceCall](ServiceCall.md), [Mark](Mark.md) and [ObservationFailure](ObservationFailure.md).

| Method | Returns | Notes |
|---|---|---|
| `kind()` | `Kind` | `PROPERTY`, `ARRAY_ELEMENT`, `COLLECTION`, `SERVICE_CALL`, `MARK`, `OBSERVATION_FAILURE` |
| `ruleName()` | `String` or null | The name passed to `evaluateString`; null if unknown (e.g. compiled mode with `lineNumbersInCompiledMode(false)`) |
| `line()` | `int` | Script line, or -1 |
| `thread()` | `String` | Thread name |
| `time()` | `Instant` | When it happened |
| `target()` | `TargetRef` or null | The Java object involved (null for `Mark` and `ObservationFailure`) |
| `isIncomplete()` | `boolean` | Part of the capture failed; what is present is still correct |
| `executionId()` | `String` or null | The execution it belongs to; null outside an execution |
| `attributes()` | `Map<String, Object>` | The execution's attributes; empty outside an execution |
| `sequence()` | `long` | 0, 1, 2, … within its execution; -1 outside |
| `offsetNanos()` | `long` | Time since its execution started; -1 outside |
| `toMap()` | `Map<String, Object>` | The JSON form |
| `toText()` | `String` | One line, e.g. `[rule.js:12] SETTER order.status "NEW" -> "PRICED"` |
