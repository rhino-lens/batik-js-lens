# LensExecution

`public final class LensExecution implements AutoCloseable`: one named script execution on the current thread. Everything Lens observes on this thread while it's open joins it. Create it with `Lens.execution(ruleName, attributes)` when you want an object to hold; [`Lens.start` / `Lens.finish`](Lens.md) do the same with nothing to hold.

<!-- snippet: execution-object -->
```java
LensExecution execution = Lens.execution(ruleName, attributes);
try {
    result = ctx.evaluateString(scope, script, ruleName, 1, null);
} catch (RuntimeException e) {
    execution.failed(e);                  // outcome FAILED, with the error
    throw e;
} finally {
    LensReport report = execution.finish();   // never throws
    reports.add(report);                      // or save report.toJson()
}
```

| Method | Returns | Notes |
|---|---|---|
| `id()` | `String` | Unique id, also on each of its events |
| `name()` | `String` or null | The name given at creation |
| `attributes()` | `Map<String, Object>` | Unmodifiable copy of the attributes given |
| `succeeded()` | | Outcome `SUCCESS` (also the default if nothing is marked) |
| `failed(Throwable scriptError)` | | Outcome `FAILED`, with the error's class and message |
| `stopped()` | | Outcome `STOPPED` (e.g. stopped by a debugger or a timeout) |
| `report()` | `LensReport` | The report so far (outcome `RUNNING`), or the final one once finished |
| `finish()` | `LensReport` | Ends the execution, once; later calls return the same report. Makes it `Lens.lastReport()` and calls listeners' `onExecutionFinished` |
| `close()` | | Same as `finish()`, for try-with-resources |

**Never throws:** every method is failure-safe. If finishing fails, `finish()` returns an empty report and the failure is flagged.

**Threads:** use an execution on the thread that opened it. Executions nest, and events join the innermost open one.
