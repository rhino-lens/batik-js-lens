# Loop safety

Reading observations, and writing them into other Java objects, can't cascade into endless observation. Four guards make sure of it.

## 1. Lens's own objects are never observed

Reports, events and the `lens` script API always get Rhino's **plain** wrappers. A script can call any method on a report without creating an event:

```javascript
var r = lens.report();
r.toJson(); r.toText(); r.events(); r.netChanges();   // no events
```

## 2. Nothing is observed while Lens is working

A per-thread guard is set while Lens:
- captures values (including the getters it calls), or
- dispatches events to listeners.

Anything that happens meanwhile isn't observed; it's counted in `stats().suppressed()`. So **an event can never cause another event**, even if a listener runs another script that changes observed objects.

## 3. Reports are snapshots

Values are rendered to text when they're captured, and a report never refers to itself. Writing a report into an observed object therefore creates **exactly one** event: the write.

```javascript
order.setStatus('A');
order.setAuditJson(lens.report().toJson());   // events: status, auditJson. Then nothing more.
```

To record nothing at all for such writes, use a quiet block:

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

In Java:

<!-- snippet: unobserved -->
```java
Lens.unobserved(() -> evaluate("maintenance.js", "order.setStatus('ARCHIVED');", Map.of("order", order)));
```

## 4. A cap per execution

A script that changes objects in a long loop is capped at `maxEventsPerExecution` (10,000 by default):
- events beyond the cap are dropped and counted, and the report is marked `truncated`
- dropped changes skip capture entirely, so they cost almost nothing
- the script carries on unaffected

Measured: 1,000,000 setter calls with a 1,000-event cap complete in under 2 seconds on an Intel Mac laptop (compiled mode), with the 999,000 dropped events counted.

## Java code is never observed

Only operations a **script** performs through Rhino's wrappers are observed. Java code that changes objects (your services, listeners, or code storing a report after the script finishes) is invisible to Lens. Storing a report from Java can therefore never trigger observation.

## Tested

- A script writes its own report into an observed object → exactly one new event.
- Calling methods on report objects → no events.
- A listener that runs another script changing observed objects → no recursion; the nested operations are counted as suppressed.
- 1,000,000 setter calls with a 1,000-event cap → 1,000 events, 999,000 dropped, the script completes.
- `lens.quietly` and `Lens.unobserved` → nothing recorded.
