# Failure handling

**Rule: observing a script must never break it.** If anything inside Lens fails, the script still runs to completion with the same result, and the failure is reported gracefully.

## How it's enforced

Every piece of Lens's own logic is a small utility method run through `LensSafe`:

```java
LensSafe.run("step-name", () -> …);                // on failure: flag, carry on
LensSafe.call("step-name", () -> …, fallback);     // on failure: flag, return the fallback
```

Each hook has the same three-part shape:

```java
Capture.AssignBefore before = Capture.beforeAssignment(this, name);   // 1. observe (failure-safe)
super.put(name, start, value);                                         // 2. Rhino's own operation: unguarded, unchanged
Capture.afterAssignment(before, value);                                // 3. observe (failure-safe)
```

- **Step 2 always runs exactly once,** outside any try/catch of Lens's. If the operation itself throws (a setter rejecting a value, say), the script receives exactly that exception, as it would without Lens.
- **If step 1 fails, step 3 still records what it can,** and marks the event `incomplete`.
- **Where Lens would replace something, it falls back to plain Rhino.** If creating a Lens wrapper fails, Rhino's plain wrapper is used. If classifying a method call fails, the call goes straight to Rhino.
- **`OutOfMemoryError` is the only exception that passes through,** because nothing can recover from it by carrying on.

## What happens on each failure

| Failure | Result for the script | Result for observation |
|---|---|---|
| A getter throws while reading an old or new value | None | Value `<unavailable: getter failed>`; event `incomplete` |
| `toString()` throws while rendering a value | None | Value shown as `Order@1b6d3586` |
| The `IdResolver` throws | None | `entityId` null |
| The rule name / line lookup fails | None | `ruleName` null, `line` -1 |
| Creating a Lens wrapper fails | None | That object isn't observed (Rhino's plain wrapper) |
| A listener throws | None | The other listeners still get the event |
| Building or finishing a report fails | None | `LensExecution.finish()` returns an empty report |
| `Lens.install()` fails | None | Nothing is observed |

## How failures are reported

1. **Counted per step,** with the last 50 kept, in `Lens.diagnostics()` (example below).
2. **Logged** through `java.util.logging` (logger `org.mozilla.javascript.lens`, level WARNING) the first time per step, then at 10, 100, 1000, … occurrences, so logs aren't flooded.
3. **Added to the current execution's report** under `observationFailures`.
4. **Sent to listeners** as an `ObservationFailure` event (`step`, `error`, rule, line). If a listener fails while handling one, that failure is ignored rather than reported again, so reporting can't loop.

<!-- snippet: diagnostics -->
```java
Map<String, Long> failures = Lens.diagnostics().counts();   // step -> count; scripts were not affected
Lens.diagnostics().recent().forEach(f -> System.out.println(f));
```

## Tested

Each case above has a test that runs the same script with Lens off and on, and asserts the results are identical and the failure is flagged. The forced failures (position lookup, wrapper creation) use test-only hooks that are never armed outside tests. Every test runs in compiled and interpreted mode.
