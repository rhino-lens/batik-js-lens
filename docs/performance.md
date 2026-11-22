# Performance

Lens only costs anything when a script touches a Java object, and the cost depends on what it records.

## Measured

`PerformanceTest` runs 20,000 operations per run in a loop: best of 5 runs after warm-up, scripts run like a rule engine, no listeners. Numbers from one run on an Intel (x86_64) Mac with macOS 12 and Java 21 (Corretto 21.0.11):

| Mode | Operation | Plain Rhino | Lens on | Lens on, inside an execution | Lens on, compiled-mode line numbers off |
|---|---|---|---|---|---|
| compiled (9) | setter call (recorded) | 0.77 µs | 15.28 µs | 12.54 µs | 1.72 µs |
| compiled (9) | getter call (nothing recorded) | 0.53 µs | 1.06 µs | 0.94 µs | 0.77 µs |
| interpreted (-1) | setter call (recorded) | 0.43 µs | 5.43 µs | 1.78 µs | 1.92 µs |
| interpreted (-1) | getter call (nothing recorded) | 3.25 µs | 4.43 µs | 2.13 µs | 0.95 µs |

Timings on this machine vary by a few microseconds between runs. Treat them as orders of magnitude, and run `mvn test -Dtest=PerformanceTest` on your own hardware (it writes `target/lens-performance.md`).

## What it means

- **Reads are nearly free.** Getters, `size()`, and any call that isn't a change or a service call add well under a microsecond: one wrapper and one classification.
- **A recorded change costs about 1–2 µs, plus finding the line in compiled mode.**
  - Each recorded change: reads the old and new value, renders them, finds the object's id, and builds an event.
  - Compiled mode also walks the stack to find the rule name and line. On this machine that walk alone is about 10 µs; it's the single largest cost.
- **If that matters,** turn the walk off with `lineNumbersInCompiledMode(false)`. Name executions with `Lens.execution(...)` so reports still say which rule ran. Interpreted mode finds the line cheaply either way.
- **For a typical rule** that changes 20 properties, recording costs about 0.25 ms in compiled mode with line numbers, or about 0.04 ms without.
- **Each report states its own cost:** `stats().overheadNanos()` is the time Lens spent in capture and dispatch for that execution.

## Keeping it low

| Lever | Effect |
|---|---|
| `lineNumbersInCompiledMode(false)` | Removes the stack walk in compiled mode |
| `OldValues.SIMPLE_TYPES` or `NONE` | Fewer getter calls; no lazy loads |
| `maxEventsPerExecution(n)` | Beyond the cap, changes skip capture entirely |
| `exclude("…")` | Objects of those classes get plain Rhino wrappers |
| `Lens.setEnabled(false)` | Plain Rhino, no cost; switchable at runtime |
| Few, fast listeners | Listeners run inline with the script |
