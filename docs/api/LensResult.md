# LensResult

`public final class LensResult<T>`: what [`Lens.observe(ruleName, attributes, work)`](Lens.md#executions-and-reports) returns.

| Method | Returns | Notes |
|---|---|---|
| `value()` | `T` | What `work` returned, e.g. the script's result |
| `report()` | `LensReport` | The finished execution's report |

If `work` throws, `observe` rethrows the exception unchanged, so there's no `LensResult`. The failed execution's report is then available from `Lens.lastReport()`.

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
