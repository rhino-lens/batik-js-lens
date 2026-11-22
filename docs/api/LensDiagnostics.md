# LensDiagnostics

`public final class LensDiagnostics`: failures inside Lens's own observation code. **Scripts are never affected by them.** Get the shared instance with `Lens.diagnostics()`. See [failure handling](../failure-handling.md).

<!-- snippet: diagnostics -->
```java
Map<String, Long> failures = Lens.diagnostics().counts();   // step -> count; scripts were not affected
Lens.diagnostics().recent().forEach(f -> System.out.println(f));
```

| Method | Returns | Notes |
|---|---|---|
| `counts()` | `Map<String, Long>` | Failures per step since start (or the last reset) |
| `total()` | `long` | All failures |
| `recent()` | `List<Failure>` | The last 50, oldest first |
| `reset()` | | Clear counts and history |

## `LensDiagnostics.Failure`

| Method | Returns |
|---|---|
| `step()` | `String`: e.g. `read-value`, `render-toString`, `id-resolver`, `position`, `wrap-object`, `listener <class>` |
| `ruleName()` · `line()` | Where the script was, if known |
| `error()` | `String`: the exception's class and message |
| `time()` | `Instant` |

Failures are also logged (logger `io.github.rhinolens`, WARNING, rate-limited), added to the current report's `observationFailures()`, and sent to listeners as [ObservationFailure](ObservationFailure.md) events.
