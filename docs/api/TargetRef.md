# TargetRef

`public final class TargetRef`: which Java object an event is about.

| Method | Returns | Notes |
|---|---|---|
| `className()` | `String` | Fully qualified |
| `identityHash()` | `int` | `System.identityHashCode`: tells instances apart within a run |
| `entityId()` | `String` or null | From the [IdResolver](IdResolver.md) |
| `path()` | `String` or null | How the script reached it: `order.getItems().get(0)`. It starts at a [label](Lens.md#services-labels-scripts-diagnostics) if one is set, else at the class name |
| `label()` | `String` | `Order(id=42)`, or `Order@1b6d3586` without an id |
| `refersTo(Object object)` | `boolean` | Same object (by identity) |

`toString()` gives the path if known, else the label.
