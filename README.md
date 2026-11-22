# batik-js-lens

Opt-in observability for JavaScript run on **Batik 1.7's Rhino** ([`batik-js-lens-base`](https://github.com/rhino-lens/batik-js-lens-base), a byte-identical rebuild of `org.apache.xmlgraphics:batik-js:1.7`).

When a script changes a Java object, your application normally can't see it: a persistence layer may later notice that an entity is dirty, but not **which rule, which line, which property, old value → new value**, or which services the rule called. batik-js-lens records exactly that, with no change to Rhino and one line of startup code.

> Not affiliated with or endorsed by the Apache Software Foundation or Mozilla.

## What it records

| A script does | Lens records |
|---|---|
| `order.setStatus("PRICED")` | `PropertyChange` (via `SETTER`): rule, line, object, `status`, `"NEW"` → `"PRICED"` |
| `order.status = "PRICED"` | `PropertyChange` (via `ASSIGNMENT`), same details |
| `order.note = "rush"` (public field) | `PropertyChange` (via `FIELD`) |
| `tags[1] = "vip"` (Java array) | `ArrayElementChange`: index, old → new |
| `items.add(x)`, `map.put(k, v)`, … | `CollectionChange`: method, arguments, size before → after, returned value |
| `orderRepository.save(order)` | `ServiceCall`: service, method, arguments, result or exception, duration |
| `lens.mark("done")` | `Mark`: a checkpoint |

Each execution can also produce a `LensReport`: total time, outcome, every event in order, net change per property, service time, and Lens's own overhead. It's available in Java and to the script itself, and exports to JSON.

## Quick start

Install once at startup. Existing `Context.enter()` / `evaluateString(...)` code needs no change:

<!-- snippet: install -->
```java
Lens.install();
```

Name an evaluation to get its report. Here `evaluate(...)` stands for your existing code that enters a Context, builds the scope and calls `evaluateString`:

<!-- snippet: quick-start -->
```java
LensResult<Object> result = Lens.observe("pricing-rule", Map.of("recordId", 42),
        () -> evaluate("pricing-rule.js", script, Map.of("order", order, "orderRepository", orderRepository)));

LensReport report = result.report();
for (NetChange change : report.netChanges()) {
    System.out.println(change);                 // Order.status "NEW" -> "PRICED"
}
System.out.println(report.durationMillis() + " ms, " + report.serviceCalls().size() + " service call(s)");
String json = report.toJson();                  // store it anywhere
```

```
Order.status "NEW" -> "PRICED"
Order.finalTotal 0.0 -> 35.09
4.8 ms, 1 service call(s)
```

The full report as JSON is in [docs/examples/report-example.json](docs/examples/report-example.json).

## Guarantees

- **Scripts behave exactly as without Lens.** The real operation always runs unchanged, so scripts produce the same results and the same exceptions.
- **Observation never breaks a script.** All of Lens's own code is failure-safe. A failure is flagged in `Lens.diagnostics()` and the script carries on. See [failure handling](docs/failure-handling.md).
- **No Rhino class is changed.** Lens extends Rhino's own wrappers through its public extension points (`WrapFactory`, `ContextFactory.Listener`), so `batik-js-lens-base` stays byte-identical. See [how it works](docs/how-it-works.md).
- **No loops.** Reading reports, listeners, and writing reports into observed objects can't cascade. See [loop safety](docs/loop-safety.md).
- **Works in both modes:** compiled (optimization 0–9) and interpreted (-1). It's tested the way rule engines run scripts: `Context.enter()`, Contexts reused per thread, shared library scopes, Java objects and services put straight into the scope.

## Documentation

| Guide | |
|---|---|
| [Getting started](docs/getting-started.md) | Dependency, install, reports in Java and in scripts, logging, storing |
| [How it works](docs/how-it-works.md) | Where Lens hooks into Rhino, with diagrams |
| [Configuration](docs/configuration.md) | Every option and its default |
| [Report format](docs/report-format.md) | The JSON, field by field, with a [JSON Schema](docs/report.schema.json) |
| [Failure handling](docs/failure-handling.md) | The never-break-the-script rule |
| [Loop safety](docs/loop-safety.md) | Why observing never cascades |
| [Performance](docs/performance.md) | Measured cost per operation |
| [Limitations](docs/limitations.md) | What isn't observed |
| [EclipseLink](docs/integration/eclipselink.md) · [Spring](docs/integration/spring.md) | Integration guides |
| [API reference](docs/api/README.md) | Every public type |

Javadoc: **[rhino-lens.github.io/batik-js-lens](https://rhino-lens.github.io/batik-js-lens/)** (also built with `mvn javadoc:javadoc` and shipped in the `-javadoc.jar`).

## Compatibility

- **Java 11 or newer** (the library is compiled with `--release 11`).
- **Rhino:** Batik 1.7's Rhino, which your project provides: [`batik-js-lens-base:1.7`](https://github.com/rhino-lens/batik-js-lens-base) or Apache's identical `org.apache.xmlgraphics:batik-js:1.7`. Lens declares it as `provided`, so it never adds a second copy. It has no other dependencies.
- **Status:** version `0.1.0`, on [Maven Central](https://central.sonatype.com/artifact/io.github.rhino-lens/batik-js-lens).

```xml
<dependency>
  <groupId>io.github.rhino-lens</groupId>
  <artifactId>batik-js-lens</artifactId>
  <version>0.1.0</version>
</dependency>
<!-- plus your Rhino 1.7, if the project doesn't have it yet (either one, not both): -->
<dependency>
  <groupId>io.github.rhino-lens</groupId>
  <artifactId>batik-js-lens-base</artifactId>
  <version>1.7</version>
</dependency>
```

## Building

```sh
mvn verify          # tests (compiled and interpreted mode), docs checks, javadoc
mvn test -Dlens.updateDocs=true   # refresh doc samples from the compiled examples
```

## License

Mozilla Public License 2.0 (see [LICENSE](LICENSE)).
