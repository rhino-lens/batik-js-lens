# Mark

`public final class Mark extends ChangeEvent`: a checkpoint a script recorded with `lens.mark("label")`. Useful for timing steps inside a rule, since each event has `offsetNanos` from the start of its execution.

| Method | Returns |
|---|---|
| `label()` | `String` |

Plus everything in [ChangeEvent](ChangeEvent.md). `target()` is null.
