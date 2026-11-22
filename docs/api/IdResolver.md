# IdResolver

`public interface IdResolver` (functional): finds an object's identity, e.g. a JPA entity's primary key. It appears as `TargetRef.entityId()` and in labels such as `Order(id=42)`, which lets you match Lens events with what a persistence layer saw.

| Method | Notes |
|---|---|
| `Object idOf(Object object)` | The id, or `null` if none. Exceptions are flagged and treated as "no id". |
| `static getIdMethod()` | The default: calls a public no-argument `getId()` (or `getID()`) when the class has one. Lookups are cached per class. |
| `static none()` | Never reports an id. |

Custom example: `LensConfig.defaults().idResolver(o -> o instanceof BaseEntity ? ((BaseEntity) o).getUuid() : null)`.
