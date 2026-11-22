# LensScriptApi

`public final class LensScriptApi`: what scripts see as `lens`, once the application puts it in scope:

<!-- snippet: script-api-scope -->
```java
scope.put("lens", scope, Lens.scriptApi());
```

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

| Method (in scripts) | Returns | Notes |
|---|---|---|
| `lens.report()` | `LensReport` | The open execution so far (outcome `RUNNING`), else the last report on this thread, else empty. Use any [LensReport](LensReport.md) method on it |
| `lens.elapsedMillis()` | number | Milliseconds since the open execution started; 0 if none |
| `lens.mark(label)` | | Records a [Mark](Mark.md) event at the current line |
| `lens.quietly(fn)` | `fn`'s result | Runs the function with observation off (counted as `suppressed`). Errors it throws reach the script as usual |

Lens's own objects are never observed, so using `lens` and reports creates no events. Writing a report into an observed object creates exactly one. See [loop safety](../loop-safety.md).
