# CollectionChange

`public final class CollectionChange extends ChangeEvent`: a script called a changing method on a Java `Collection` or `Map`. The methods covered are `add`, `addAll`, `remove`, `removeAll`, `retainAll`, `clear`, `set`, `put`, `putAll`, `putIfAbsent`, `replace`, `addFirst`, `addLast`, `removeFirst`, `removeLast`, `push`, `pop`, `offer` and `poll`.

| Method | Returns | Notes |
|---|---|---|
| `method()` | `String` | e.g. `put` |
| `args()` | `List<ValueSnapshot>` | Arguments as passed |
| `sizeBefore()` · `sizeAfter()` | `int` | -1 if the size couldn't be read |
| `returned()` | `ValueSnapshot` or null | What the method returned, e.g. the previous value for `Map.put` / `List.set` |
| `error()` | `String` or null | The exception the call threw (it still reached the script) |

Plus everything in [ChangeEvent](ChangeEvent.md). The `target()` is the collection, and its `path()` shows how the script got it (e.g. `order.getItems()`).

Collections that are services (per [ServicePolicy](ServicePolicy.md)) are recorded as [ServiceCall](ServiceCall.md) instead.
