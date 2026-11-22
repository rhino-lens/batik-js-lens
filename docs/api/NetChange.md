# NetChange

`public final class NetChange`: the overall change to one property (or array element) of one object during an execution: the value before the first change and after the last, however many times the script touched it. From `LensReport.netChanges()`, in order of first change.

| Method | Returns | Notes |
|---|---|---|
| `target()` | `TargetRef` | The object |
| `property()` | `String` | Property name, or `[i]` for an array element |
| `oldValue()` | `ValueSnapshot` | Before the first change |
| `newValue()` | `ValueSnapshot` | After the last change |
| `changes()` | `int` | How many times it was changed |
| `lines()` | `List<Integer>` | Script lines of the changes, in order |
| `isEffective()` | `boolean` | False if the final value equals the original one (as rendered), e.g. set and then set back |

`toString()` gives `Order.status "NEW" -> "C" (3 changes)`.
