# PropertyChange

`public final class PropertyChange extends ChangeEvent`: a script set a Java property.

| Script | `via()` |
|---|---|
| `order.setStatus("X")` | `SETTER` |
| `order.status = "X"`, `order["status"] = "X"`, `with (order) { status = "X" }` | `ASSIGNMENT` (a bean property) |
| `order.note = "X"` where `note` is a public field | `FIELD` |

| Method | Returns | Notes |
|---|---|---|
| `property()` | `String` | Property name as a script writes it (`status` for `setStatus`; `URL` for `setURL`) |
| `via()` | `Via` | `SETTER`, `ASSIGNMENT`, `FIELD` |
| `oldValue()` | `ValueSnapshot` | Read before the change (see [OldValues](OldValues.md)) |
| `newValue()` | `ValueSnapshot` | Read back after the change, so it shows what was stored; the assigned value when the property isn't read |

Plus everything in [ChangeEvent](ChangeEvent.md).

A setter that **throws** produces no `PropertyChange`. The exception reaches the script unchanged, and nothing can be said to have changed.
