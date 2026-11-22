# ServiceCall

`public final class ServiceCall extends ChangeEvent`: a script called a method on a service (a repository, CRUD service, …), as decided by the [ServicePolicy](ServicePolicy.md) or `Lens.registerService`. **Every** method called on a service is recorded, reads included.

| Method | Returns | Notes |
|---|---|---|
| `service()` | `String` | e.g. `orderRepository` |
| `method()` | `String` | e.g. `save` |
| `args()` | `List<ValueSnapshot>` | Arguments, e.g. `Order(id=42)` |
| `result()` | `ValueSnapshot` or null | Return value; null if the call threw |
| `error()` | `String` or null | The exception the call threw. It still reached the script exactly as without Lens |
| `durationNanos()` | `long` | Time inside the call. Lens's own capture time isn't included |

Plus everything in [ChangeEvent](ChangeEvent.md).

What the service does inside is Java code and isn't observed. The call itself tells you which rule and line triggered it.
