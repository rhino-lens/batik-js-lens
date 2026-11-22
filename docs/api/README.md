# API reference

Everything is in package `org.mozilla.javascript.lens`. Javadoc is in the `-javadoc.jar` (build it with `mvn javadoc:javadoc`).

## Entry point and setup

| Type | What it is |
|---|---|
| [Lens](Lens.md) | Install, listeners, executions, quiet blocks, services, labels, diagnostics |
| [LensConfig](LensConfig.md) | Settings (immutable) |
| [OldValues](OldValues.md) | Whether property values are read before and after a change |
| [ServicePolicy](ServicePolicy.md) | Which objects are services |
| [IdResolver](IdResolver.md) | How an object's id is found |

## Executions and reports

| Type | What it is |
|---|---|
| [LensExecution](LensExecution.md) | One named execution on the current thread |
| [LensResult](LensResult.md) | Value plus report, from `Lens.observe` |
| [LensReport](LensReport.md) | Everything observed in one execution |
| [NetChange](NetChange.md) | First old → last new value of one property |

## Events

| Type | What it is |
|---|---|
| [ChangeEvent](ChangeEvent.md) | Base type: rule, line, target, execution |
| [PropertyChange](PropertyChange.md) | `obj.setX(v)`, `obj.x = v`, public field |
| [ArrayElementChange](ArrayElementChange.md) | `arr[i] = v` |
| [CollectionChange](CollectionChange.md) | `add`, `remove`, `put`, … on Java collections and maps |
| [ServiceCall](ServiceCall.md) | Any method call on a service |
| [Mark](Mark.md) | `lens.mark(label)` checkpoint |
| [ObservationFailure](ObservationFailure.md) | Lens's own code failed (the script was not affected) |
| [TargetRef](TargetRef.md) | Which object an event is about |
| [ValueSnapshot](ValueSnapshot.md) | A value, frozen at capture time |

## Listeners

| Type | What it is |
|---|---|
| [ChangeListener](ChangeListener.md) | Receive events and finished reports |
| [InMemoryJournal](InMemoryJournal.md) | Collects events per thread until drained |
| [JulChangeLogger](JulChangeLogger.md) | Logs through `java.util.logging` |

## Scripts and diagnostics

| Type | What it is |
|---|---|
| [LensScriptApi](LensScriptApi.md) | What scripts see as `lens` |
| [LensDiagnostics](LensDiagnostics.md) | Failures inside Lens |

## Conventions

- **Never throws into scripts.** All of Lens's own work is failure-safe. Methods documented as "never throws" flag failures in [`Lens.diagnostics()`](LensDiagnostics.md) instead.
- **Per thread.** Executions, `lastReport()` and the journal are per thread: the thread that runs the script.
- **Immutable values.** Events, reports, snapshots and configurations never change after creation.
