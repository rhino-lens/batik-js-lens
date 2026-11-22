# LensConfig

`public final class LensConfig`: settings, passed to `Lens.install(...)`. Immutable: every setter-style method returns a changed copy. Details and trade-offs are in [configuration.md](../configuration.md).

<!-- snippet: configuration -->
```java
Lens.install(LensConfig.defaults()
        .oldValues(OldValues.SIMPLE_TYPES)          // don't call getters of entity/collection properties
        .services(ServicePolicy.defaults()
                .or(ServicePolicy.classNameSuffixes("Gateway")))
        .exclude("com.example.internal.")           // never observe these classes
        .maxEventsPerExecution(5_000)
        .lineNumbersInCompiledMode(true));
```

| Method | Default | Notes |
|---|---|---|
| `static defaults()` | | A configuration with all the defaults below |
| `oldValues(OldValues mode)` | `ALWAYS` | See [OldValues](OldValues.md) |
| `lineNumbersInCompiledMode(boolean on)` | `true` | Rule name and line on events in compiled mode; off removes the stack walk |
| `services(ServicePolicy policy)` | `ServicePolicy.defaults()` | See [ServicePolicy](ServicePolicy.md) |
| `idResolver(IdResolver resolver)` | `IdResolver.getIdMethod()` | See [IdResolver](IdResolver.md) |
| `maxEventsPerExecution(int events)` | `10_000` | Minimum 1. Beyond it events are dropped and counted |
| `maxValueLength(int chars)` | `500` | Minimum 16. Longer rendered values end with `…` |
| `keepRawValues(boolean keep)` | `false` | Keep object references in `ValueSnapshot.raw()` |
| `exclude(String... classNamePrefixes)` | none | Adds prefixes; objects of matching classes are never observed |

Each option also has a getter of the same name without arguments (`oldValues()`, `maxEventsPerExecution()`, …; `excludedPrefixes()` for the exclusions). Passing `null` to `oldValues`, `services` or `idResolver` throws `IllegalArgumentException`. That happens when you build the configuration, not inside a script.
