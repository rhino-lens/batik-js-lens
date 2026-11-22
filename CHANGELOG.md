# Changelog

## 0.1.0 (unreleased)

First version.

- **Records what scripts do to Java objects,** with rule name, line, target object, path, and old → new value:
  - property changes through setters, `=` on bean properties, and public fields
  - Java array element changes
  - `Collection` and `Map` changes
  - service calls, with arguments, result or exception, and duration
  - checkpoints (`lens.mark`)
- **Per-execution reports** (`LensReport`): timing, outcome, events, net changes, statistics including Lens's own overhead, and JSON / map / text export. Available in Java (`Lens.execution`, `Lens.observe`, `Lens.lastReport`, listeners) and to scripts (`lens.report()`).
- **Installation:** one call, `Lens.install()`, with no change to calling code. Optional `installAsGlobalFactory` for automatic per-evaluation reports.
- **Marking runs in your own rule method:** `Lens.start(ruleName)` before the evaluation and `Lens.finish()` (or `Lens.finish(error)`) in `finally`, with nothing to hold. Both do nothing when Lens is off. A run whose `finish()` was skipped is closed as `STOPPED` by the next `start` (and flagged), so runs never pile up on pooled threads; nested runs stay open.
- **Failure isolation:** all observation code is failure-safe. Failures are flagged in `Lens.diagnostics()`, logged, added to reports, and sent as `ObservationFailure` events. Scripts are never affected.
- **Loop safety:**
  - Lens's own objects are never observed
  - a re-entrancy guard covers capture and listeners
  - reports are snapshots
  - quiet blocks: `lens.quietly`, `Lens.unobserved`
  - a per-execution event cap
- **Configuration:** old-value modes (`ALWAYS`, `SIMPLE_TYPES`, `NONE`), service policies, id resolver, exclusions, limits, and line numbers in compiled mode.
- **Rhino:** no Rhino classes are changed. It runs on Batik 1.7's Rhino, declared as a `provided` dependency so the application's own copy (`batik-js-lens-base` 1.7 or Apache's identical `batik-js` 1.7) is used and never duplicated. Java 11+.
