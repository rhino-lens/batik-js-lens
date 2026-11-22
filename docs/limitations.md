# Limitations

| Not observed | Why | What to do |
|---|---|---|
| Changes made **by Java code** a script calls (inside a service, a setter's side effects on other objects) | Lens sees what scripts do through Rhino's wrappers; Java code runs past them | The call itself is recorded (`ServiceCall`). Combine it with your persistence layer's change tracking ([EclipseLink](integration/eclipselink.md)) |
| Contexts created **before** `Lens.install()` | Lens instruments Contexts as Rhino creates them | Install at startup, or call `Lens.instrument(cx)` on an existing Context |
| Contexts whose `WrapFactory` the application **replaces** after creation | The application's factory then hands out the wrappers | Don't replace it, or call `Lens.instrument(cx)` afterwards |
| Static fields and methods reached through `Packages` / `java.*` (e.g. `Packages.com.x.Config.LIMIT = 5`) | Those go through Rhino's `NativeJavaClass`, which isn't created by the `WrapFactory` | Not covered in 0.1 |
| Changes to **JavaScript** objects (`var o = {}; o.x = 1`) | Lens observes Java objects | Not covered |
| Code inside `lens.quietly(...)` / `Lens.unobserved(...)` | By design | Counted as `suppressed` |
| Events beyond `maxEventsPerExecution` | By design (bounded memory) | Counted as `dropped`; raise the limit if needed |

## Behaviour to know about

- **Old values call getters.** With `OldValues.ALWAYS` (the default), Lens calls a property's getter before and after each change. On lazy JPA relationships that can load data. Use `SIMPLE_TYPES` to avoid it ([configuration](configuration.md#old-values-oldvalues)).
- **New values are read back.** After a change, the new value is read through the getter, so it shows what was actually stored. A setter that normalizes its input shows the normalized value.
- **Java strings in Rhino 1.6.** Rhino 1.6 hands Java `String` results to scripts as Java objects, so their type shows as `String`, like any other Java value.
- **`String(obj.someMethod)`.** A Lens method wrapper prints as a plain native function. It doesn't list the Java overloads the way Rhino's own method object does.
- **One global factory.** `Lens.installAsGlobalFactory(...)` uses Rhino's single global-factory slot; if another library already took it, Lens falls back to `install()` and flags it.
- **Split package.** `org.mozilla.javascript.LensAccess` lives in Rhino's package. That's fine on the classpath, which is how Rhino 1.6 is used. It wouldn't work if both jars were named Java modules on the module path.
