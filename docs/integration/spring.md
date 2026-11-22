# Spring

rhino-lens has no Spring dependency. In a Spring (Boot) application, install it once at startup, before the first script runs, for example from an `ApplicationRunner` bean, a `@PostConstruct` method on a configuration class, or a `SmartInitializingSingleton`:

<!-- snippet: install -->
```java
Lens.install();
```

With a configuration, for example to treat a gateway bean as a service:

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

## Services

Beans annotated `@Service` or `@Repository` (also through CGLIB proxies), or whose class name ends in `Service`, `Repository` or `Dao`, are recognised as services automatically. Every method a script calls on them is recorded as a `ServiceCall`. For other beans you put into script scopes, register them under the name scripts use:

<!-- snippet: register-service -->
```java
Lens.registerService(auditService, "auditService");   // always a service, whatever its class
```

## Exposing reports

- **Per request:** wrap rule evaluation in `Lens.observe(...)` or an execution ([getting started](../getting-started.md#as-a-report-per-execution)), and return or store `report.toJson()`.
- **All executions:** add a `ChangeListener` whose `onExecutionFinished(LensReport)` publishes reports, e.g. to a Spring `ApplicationEventPublisher`, a metrics registry, or a log. Keep it fast: it runs on the script's thread.
- **Logging:** `JulChangeLogger` logs through `java.util.logging`. Spring Boot routes JUL to its logging system, so set the level of `io.github.rhinolens.changes` in `application.properties`:

  ```properties
  logging.level.io.github.rhinolens.changes=INFO
  ```

## Threads

Lens keeps executions and the journal per thread. With virtual threads or async request handling, open and finish an execution on the thread that actually evaluates the script.
