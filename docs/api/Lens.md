# Lens

`public final class Lens`: the entry point. Every method is static. Install once at startup; existing rule-running code needs no change.

<!-- snippet: install -->
```java
Lens.install();
```

## Installing

| Method | Notes |
|---|---|
| `install()` | Same as `install(LensConfig.defaults())`. |
| `install(LensConfig config)` | Adds a listener to Rhino's global `ContextFactory`, once per JVM; later calls only replace the configuration. Every Context created afterwards (including through plain `Context.enter()`) is observed. **Never throws.** |
| `installAsGlobalFactory(LensConfig config)` | Also makes Lens Rhino's global `ContextFactory`, so each top-level evaluation becomes an execution automatically and `lastReport()` works with no calling-code change. Rhino allows one global factory per JVM; if another is set, falls back to `install` and flags it. Call before any script runs. **Never throws.** |
| `instrument(Context cx)` | Instruments an existing Context (e.g. created before install and reused from a thread-local). Keeps its optimization level. **Never throws.** |
| `isInstalled()` · `isGlobalFactory()` | State. |
| `setEnabled(boolean on)` · `isEnabled()` | Turn observation off and on at runtime (on by default). Off means plain Rhino wrappers, at no cost. Takes effect for the next operations on all threads. |
| `config()` | The current `LensConfig`. |

## Listeners

| Method | Notes |
|---|---|
| `addListener(ChangeListener l)` | Receives every event and every finished execution, on the script's thread. The same listener is added once. |
| `removeListener(ChangeListener l)` | |

## Executions and reports

| Method | Returns | Notes |
|---|---|---|
| `start(String ruleName)` / `start(String ruleName, Map<String, ?> attributes)` | — | Marks the start of a run on this thread; nothing to hold. Does nothing when Lens is not installed or is disabled. Runs nest: a `start` deeper inside another run's work opens an inner run. If an earlier run's `finish()` was skipped (the method that started it has returned), this closes it as `STOPPED` and flags `execution-not-finished` in `diagnostics()`. Costs about 9 µs with `finish()`. |
| `finish()` | `LensReport` or null | Ends the newest run opened by `start` on this thread and returns its report (`SUCCESS`); `null` if there is none, for example because Lens is off. |
| `finish(Throwable error)` | `LensReport` or null | Same, marking the run `FAILED` with `error` (when non-null). |
| `execution(String ruleName, Map<String, ?> attributes)` | `LensExecution` | Opens an execution on this thread and returns it to finish yourself. Everything observed on the thread until `finish()` joins it. `ruleName` may be null (the script name of the first event is used); `attributes` may be null. Never throws (if opening fails, the failure is flagged and an untracked execution is returned). Forgotten ones are closed like `start`'s. |
| `observe(String ruleName, Map<String, ?> attributes, Supplier<T> work)` | `LensResult<T>` | Runs `work` as one execution. If `work` throws, the execution is marked failed, its report becomes `lastReport()`, and the exception is rethrown unchanged. |
| `currentExecution()` | `LensExecution` or null | The innermost open execution on this thread. |
| `lastReport()` | `LensReport` or null | The last execution finished on this thread. |

<!-- snippet: quick-start -->
```java
LensResult<Object> result = Lens.observe("pricing-rule", Map.of("recordId", 42),
        () -> evaluate("pricing-rule.js", script, Map.of("order", order, "orderRepository", orderRepository)));

LensReport report = result.report();
for (NetChange change : report.netChanges()) {
    System.out.println(change);                 // Order.status "NEW" -> "PRICED"
}
System.out.println(report.durationMillis() + " ms, " + report.serviceCalls().size() + " service call(s)");
String json = report.toJson();                  // store it anywhere
```

## Quiet blocks

| Method | Notes |
|---|---|
| `unobserved(Runnable work)` · `unobserved(Supplier<T> work)` | Runs `work` with observation off on this thread. Operations are counted as `suppressed`. Exceptions from `work` pass through. |

<!-- snippet: unobserved -->
```java
Lens.unobserved(() -> evaluate("maintenance.js", "order.setStatus('ARCHIVED');", Map.of("order", order)));
```

## Services, labels, scripts, diagnostics

| Method | Returns | Notes |
|---|---|---|
| `registerService(Object service, String name)` | | Always treat this object as a service with this name. Held weakly. |
| `label(Object object, String name)` | | Event paths start at `name` for this object (e.g. `order.getItems().get(0)`). Held weakly. |
| `scriptApi()` | `LensScriptApi` | Put it in scope as `lens`: `scope.put("lens", scope, Lens.scriptApi())`. |
| `diagnostics()` | `LensDiagnostics` | Failures inside Lens. |
