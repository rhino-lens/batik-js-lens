# ObservationFailure

`public final class ObservationFailure extends ChangeEvent`: Lens's own observation code failed at some step. **The script wasn't affected** and carried on. Listeners receive this event. Reports list failures under `observationFailures()` rather than as events, and they're counted in [`Lens.diagnostics()`](LensDiagnostics.md).

| Method | Returns | Notes |
|---|---|---|
| `step()` | `String` | The step that failed, e.g. `read-value`, `render-toString`, `listener com.example.MyListener` |
| `error()` | `String` | The exception's class and message |

Plus everything in [ChangeEvent](ChangeEvent.md). See [failure handling](../failure-handling.md).

If a listener throws while handling an `ObservationFailure`, that's ignored rather than reported again, so reporting can't loop.
