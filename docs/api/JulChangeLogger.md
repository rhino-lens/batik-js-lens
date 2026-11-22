# JulChangeLogger

`public final class JulChangeLogger implements ChangeListener`: logs every event as one line, and every finished execution as a summary (`LensReport.toText()`), through `java.util.logging` with the logger `io.github.rhinolens.changes`.

<!-- snippet: logging -->
```java
ChangeListener logger = new JulChangeLogger(Level.INFO);   // logger io.github.rhinolens.changes
Lens.addListener(logger);
```

| Constructor | Notes |
|---|---|
| `JulChangeLogger()` | Logs at `Level.INFO` |
| `JulChangeLogger(Level level)` | Logs at this level |

SLF4J, Log4j and Spring Boot users can route `java.util.logging` to their logging system with the usual bridge. Spring Boot does this by default.
