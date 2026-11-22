# ValueSnapshot

`public final class ValueSnapshot`: a value as it was at capture time. The text is frozen then, even if the object changes later.

| Method | Returns | Notes |
|---|---|---|
| `type()` | `String` or null | `String`, `Double`, `Order`, `ArrayList`, `String[]`, `js:Object`, `undefined`, `null`; null if not captured |
| `text()` | `String` | Strings are quoted (`"PRICED"`). Entities show as `Order(id=42)`, collections as `ArrayList(size=3)`, arrays as `String[2]`, others as `toString()` (cut at `maxValueLength`) |
| `isCaptured()` | `boolean` | False when the value wasn't read: by the [OldValues](OldValues.md) mode (text `<not captured>`), for a write-only property, or because reading failed (text `<unavailable: …>`) |
| `raw()` | `Object` or null | The object itself, only with `LensConfig.keepRawValues(true)` |

Rendering never calls script code. If an object's `toString()` throws, the text falls back to `Order@1b6d3586` and the failure is flagged.
