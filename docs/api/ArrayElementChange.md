# ArrayElementChange

`public final class ArrayElementChange extends ChangeEvent`: a script set an element of a **Java** array: `tags[1] = "vip"`, where `tags` came from Java (e.g. `order.getTags()`).

| Method | Returns | Notes |
|---|---|---|
| `index()` | `int` | |
| `oldValue()` | `ValueSnapshot` | Element before |
| `newValue()` | `ValueSnapshot` | Element after |

Plus everything in [ChangeEvent](ChangeEvent.md). The `target()` is the array; its `path()` shows how the script got it (e.g. `order.getTags()`). An out-of-range index produces no event, and the script gets Rhino's usual error.
