# Getting started

## 1. Add the dependency

batik-js-lens runs on Batik 1.7's Rhino, which your project provides. Lens declares Rhino as a `provided` dependency, so adding Lens never puts a second copy of `org.mozilla.javascript` on the classpath.

- **Your project already has Rhino 1.7** (`org.apache.xmlgraphics:batik-js:1.7`, for example through Batik): add only Lens.
- **It doesn't:** add Lens and `batik-js-lens-base`, a byte-identical rebuild of `batik-js:1.7`.

```xml
<dependency>
  <groupId>io.github.rhino-lens</groupId>
  <artifactId>batik-js-lens</artifactId>
  <version>0.1.0</version>
</dependency>
<!-- only if the project has no Rhino 1.7 yet: -->
<dependency>
  <groupId>io.github.rhino-lens</groupId>
  <artifactId>batik-js-lens-base</artifactId>
  <version>1.7</version>
</dependency>
```

Java 11 or newer is required.

## 2. Install at startup

<!-- snippet: install -->
```java
Lens.install();
```

That's the only required step. Every Context Rhino creates from now on is observed, including Contexts from plain `Context.enter()`, so existing rule-running code keeps working unchanged. Contexts that already existed before `install()` aren't observed; see [`Lens.instrument(Context)`](api/Lens.md).

`install()` never throws. If something goes wrong, it's flagged in [`Lens.diagnostics()`](api/LensDiagnostics.md).

## 3. See what scripts change

### As events, while they happen

<!-- snippet: listener -->
```java
List<String> lines = new ArrayList<>();
ChangeListener printer = new ChangeListener() {
    @Override
    public void onEvent(ChangeEvent event) {
        if (event instanceof PropertyChange) {
            PropertyChange c = (PropertyChange) event;
            lines.add(c.ruleName() + ":" + c.line() + " " + c.target().label() + "." + c.property()
                    + " " + c.oldValue().text() + " -> " + c.newValue().text());
        }
    }

    @Override
    public void onExecutionFinished(LensReport report) {
        lines.add(report.ruleName() + " took " + report.durationMillis() + " ms");
    }
};
Lens.addListener(printer);
```

Or log everything through `java.util.logging`:

<!-- snippet: logging -->
```java
ChangeListener logger = new JulChangeLogger(Level.INFO);   // logger io.github.rhinolens.lens.changes
Lens.addListener(logger);
```

### As a report per execution

Run the evaluation inside `Lens.observe(...)`. Here `evaluate(...)` stands for your existing code that enters a Context, builds the scope and calls `evaluateString`:

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

If your code already has a method that runs rules (enter a Context, build the scope, `evaluateString`), mark the start and end of the evaluation instead. The rest of the method stays as it is:

<!-- snippet: session-integration -->
```java
Lens.start(ruleName, attributes);
try {
    result = ctx.evaluateString(scope, script, ruleName, 1, null);
} finally {
    LensReport report = Lens.finish();   // never throws; null when Lens is off
    if (report != null) {
        reports.add(report);             // or save report.toJson()
    }
}
```

`start` and `finish` never throw, and do nothing when Lens is off (`finish()` then returns `null`). To mark a failed rule as `FAILED` with its error, call `Lens.finish(e)` in a `catch` block instead. If a `finish()` is ever skipped (say, an exception path without `finally`), the next `start()` closes that run as `STOPPED` and logs a warning naming the line that opened it, so runs never pile up on pooled threads.

Prefer an object you hold? `Lens.execution(ruleName, attributes)` returns a [`LensExecution`](api/LensExecution.md) to finish yourself (it is `AutoCloseable`).

### Inside the script

Put the script API in scope as `lens`:

<!-- snippet: script-api-scope -->
```java
scope.put("lens", scope, Lens.scriptApi());
```

Then a rule can read its own report so far, store it, and mark checkpoints:

<!-- snippet: script-api-js -->
```javascript
order.setStatus('PRICED');
var r = lens.report();                        // this execution so far
var summary = r.changeCount() + ' change(s) in ' + r.durationMillis() + ' ms';
auditService.record(r.toJson());             // one ServiceCall event, no loop
lens.mark('pricing done');                   // a checkpoint event
lens.quietly(function () {                   // not observed
    order.setAuditJson(r.toJson());
});
```

Reading the report never creates events, and writing it somewhere creates exactly one. See [loop safety](loop-safety.md).

## 4. Store reports

`LensReport.toJson()` needs no JSON library. `toMap()` gives plain maps and lists for your own serializer. The format is described in [report-format.md](report-format.md), with a [JSON Schema](report.schema.json).

## 5. Tune it

The defaults record everything. To change that, see [configuration.md](configuration.md):

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

## Next

- [How it works](how-it-works.md)
- [EclipseLink](integration/eclipselink.md): match rule changes with what EclipseLink detects at commit
- [Spring](integration/spring.md)
- [API reference](api/README.md)
