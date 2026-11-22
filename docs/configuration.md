# Configuration

Pass a `LensConfig` to `Lens.install(...)`. Calling `install` again replaces the configuration; the Rhino hook is installed only once. `LensConfig` is immutable, and each method returns a changed copy.

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

| Option | Default | Effect |
|---|---|---|
| `oldValues(OldValues)` | `ALWAYS` | Whether property values are read before and after a change. See below. |
| `lineNumbersInCompiledMode(boolean)` | `true` | Rule name and line on events in compiled mode (optimization 0–9). Off removes the stack walk, the largest cost per change in compiled mode ([performance](performance.md)); compiled-mode events then have no rule name or line, so name executions with `Lens.execution(...)` instead. Interpreted mode always has them, cheaply. |
| `services(ServicePolicy)` | `ServicePolicy.defaults()` | Which objects are services; every method call on a service is a `ServiceCall`. See below. |
| `idResolver(IdResolver)` | `IdResolver.getIdMethod()` | How an object's id is found (shown as `Order(id=42)`, and in `TargetRef.entityId()`). Default: a public `getId()` / `getID()`. |
| `maxEventsPerExecution(int)` | `10_000` | Events kept per execution. Beyond it, events are dropped and counted (`stats().dropped()`), the report is `truncated`, and dropped changes cost almost nothing. |
| `maxValueLength(int)` | `500` | Longest rendered value; longer values end with `…`. |
| `keepRawValues(boolean)` | `false` | Keep references to the actual objects in `ValueSnapshot.raw()`. Off by default because it keeps those objects alive as long as the events. |
| `exclude(String... classNamePrefixes)` | none | Never observe objects of these classes. |

Turn observation off and on at runtime with `Lens.setEnabled(boolean)`. Off means plain Rhino wrappers, with no capture cost.

## Old values: `OldValues`

Reading the old and new value of a property means calling its getter. On JPA / EclipseLink entities, a getter on a **lazy** relationship can load it from the database.

| Mode | Reads | Records |
|---|---|---|
| `ALWAYS` (default) | Every property | Old and new value of everything; may trigger lazy loads |
| `SIMPLE_TYPES` | Only primitives and their wrappers, `String`, enums, `BigDecimal`, `BigInteger`, `Date`, `java.time` types, `UUID` | Other properties: old value "not captured", new value = the assigned value (an entity shows as `Customer(id=7)`). Their getters are never called. |
| `NONE` | Nothing | New value = the assigned value |

A value that wasn't read has `ValueSnapshot.isCaptured() == false`.

## Services: `ServicePolicy`

A `ServiceCall` is recorded for **every** method a script calls on a service, with arguments, result or exception, and duration. The default recognises classes, their superclasses or their interfaces that are:
- annotated `org.springframework.stereotype.Service` or `…Repository` (matched by name, so no Spring dependency is needed), or
- named ending in `Service`, `Repository` or `Dao`.

Spring/CGLIB proxy suffixes are ignored. Policies combine with `or(...)`:

| Factory | Matches |
|---|---|
| `ServicePolicy.defaults()` | As above |
| `ServicePolicy.annotations(String...)` | Annotated classes, by annotation class name |
| `ServicePolicy.classNameSuffixes(String...)` | Simple class name suffixes |
| `ServicePolicy.types(Class<?>...)` | Instances of these types |
| `ServicePolicy.none()` | Nothing (only registered services) |

Register any object as a service under a name of your choice. Registrations hold objects weakly:

<!-- snippet: register-service -->
```java
Lens.registerService(auditService, "auditService");   // always a service, whatever its class
```

The service name shown is the registered name, or else the class's simple name with a lowercase first letter (`OrderRepository` → `orderRepository`).

## Paths: `Lens.label`

Events show how the script reached an object, e.g. `order.getItems().get(0)`. A path starts at the class name unless the object has a label:

<!-- snippet: label -->
```java
Lens.label(order, "order");   // paths start at "order" instead of the class name
```

## Quiet blocks

Run something without observing it, e.g. maintenance scripts. Operations inside are counted as `suppressed`:

<!-- snippet: unobserved -->
```java
Lens.unobserved(() -> evaluate("maintenance.js", "order.setStatus('ARCHIVED');", Map.of("order", order)));
```

Scripts can do the same with `lens.quietly(function () { … })`.
