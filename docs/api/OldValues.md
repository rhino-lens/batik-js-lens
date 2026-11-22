# OldValues

`public enum OldValues`: whether Lens reads a property's value (through its getter or field) before and after a change. Set it with `LensConfig.oldValues(...)`.

| Constant | Reads | Notes |
|---|---|---|
| `ALWAYS` (default) | Every property | Most complete. Getters of lazy JPA relationships may load data |
| `SIMPLE_TYPES` | Primitives and their wrappers, `String` / `CharSequence`, enums, `BigDecimal`, `BigInteger`, `Date`, `java.time` types, `UUID` | Other properties: old value not captured, new value = the assigned value. Their getters are never called |
| `NONE` | Nothing | New value = the assigned value |

A value that wasn't read has `ValueSnapshot.isCaptured() == false` and the text `<not captured>`. Java arrays' elements are always read (reading an array element has no side effects).
