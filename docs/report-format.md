# Report format

`LensReport.toJson()` writes the report as JSON, and `toMap()` gives the same structure as plain maps, lists, strings, numbers and booleans. Both need no extra library. The structure is described formally in [report.schema.json](report.schema.json) (JSON Schema 2020-12), and a test checks real reports against it.

## Example

This was produced by running the rule below with `Lens.observe("pricing-rule", {recordId: 42, user: "ann"}, …)`. Ids, times, hashes and durations were replaced by fixed values so the example stays stable; a test regenerates it from a real run.

```javascript
order.setStatus('PRICED');
order.finalTotal = 35.09;
order.getItems().add(order.getItems().get(0));
repo.save(order);
lens.mark('pricing done');
```

<!-- include: docs/examples/report-example.json -->
```json
{
  "executionId": "3f2b9c1e-7d4a-4e8b-9a6f-2c1d5e8f0a47",
  "ruleName": "pricing-rule",
  "attributes": {
    "recordId": 42,
    "user": "ann"
  },
  "startedAt": "2026-11-22T09:30:00Z",
  "endedAt": "2026-11-22T09:30:00.004800Z",
  "durationNanos": 4800000,
  "outcome": "SUCCESS",
  "error": null,
  "truncated": false,
  "stats": {
    "counts": {
      "PROPERTY": 2,
      "COLLECTION": 1,
      "SERVICE_CALL": 1,
      "MARK": 1
    },
    "serviceCallNanos": 1200000,
    "overheadNanos": 61000,
    "suppressed": 0,
    "dropped": 0
  },
  "events": [
    {
      "kind": "PROPERTY",
      "sequence": 0,
      "ruleName": "pricing-rule.js",
      "line": 1,
      "offsetNanos": 100000,
      "thread": "main",
      "time": "2026-11-22T09:30:00.000100Z",
      "target": {
        "className": "io.github.rhinolens.testapp.Order",
        "identityHash": "1b6d3586",
        "entityId": "42",
        "path": "order",
        "label": "Order(id=42)"
      },
      "property": "status",
      "via": "SETTER",
      "oldValue": {
        "type": "String",
        "text": "\"NEW\""
      },
      "newValue": {
        "type": "String",
        "text": "\"PRICED\""
      }
    },
    {
      "kind": "PROPERTY",
      "sequence": 1,
      "ruleName": "pricing-rule.js",
      "line": 2,
      "offsetNanos": 800000,
      "thread": "main",
      "time": "2026-11-22T09:30:00.000900Z",
      "target": {
        "className": "io.github.rhinolens.testapp.Order",
        "identityHash": "1b6d3586",
        "entityId": "42",
        "path": "order",
        "label": "Order(id=42)"
      },
      "property": "finalTotal",
      "via": "ASSIGNMENT",
      "oldValue": {
        "type": "Double",
        "text": "0.0"
      },
      "newValue": {
        "type": "Double",
        "text": "35.09"
      }
    },
    {
      "kind": "COLLECTION",
      "sequence": 2,
      "ruleName": "pricing-rule.js",
      "line": 3,
      "offsetNanos": 1500000,
      "thread": "main",
      "time": "2026-11-22T09:30:00.001400Z",
      "target": {
        "className": "java.util.ArrayList",
        "identityHash": "4554617c",
        "entityId": null,
        "path": "order.getItems()",
        "label": "ArrayList@4554617c"
      },
      "method": "add",
      "args": [
        {
          "type": "LineItem",
          "text": "MUG-RED x2"
        }
      ],
      "sizeBefore": 2,
      "sizeAfter": 3,
      "returned": {
        "type": "Boolean",
        "text": "true"
      },
      "error": null
    },
    {
      "kind": "SERVICE_CALL",
      "sequence": 3,
      "ruleName": "pricing-rule.js",
      "line": 4,
      "offsetNanos": 2200000,
      "thread": "main",
      "time": "2026-11-22T09:30:00.002100Z",
      "target": {
        "className": "io.github.rhinolens.testapp.OrderRepository",
        "identityHash": "74a14482",
        "entityId": null,
        "path": "OrderRepository",
        "label": "OrderRepository@74a14482"
      },
      "service": "orderRepository",
      "method": "save",
      "args": [
        {
          "type": "Order",
          "text": "Order(id=42)"
        }
      ],
      "result": {
        "type": "Order",
        "text": "Order(id=42)"
      },
      "error": null,
      "durationNanos": 1200000
    },
    {
      "kind": "MARK",
      "sequence": 4,
      "ruleName": "pricing-rule.js",
      "line": 5,
      "offsetNanos": 2900000,
      "thread": "main",
      "time": "2026-11-22T09:30:00.003600Z",
      "label": "pricing done"
    }
  ],
  "netChanges": [
    {
      "target": {
        "className": "io.github.rhinolens.testapp.Order",
        "identityHash": "1b6d3586",
        "entityId": "42",
        "path": "order",
        "label": "Order(id=42)"
      },
      "property": "status",
      "oldValue": {
        "type": "String",
        "text": "\"NEW\""
      },
      "newValue": {
        "type": "String",
        "text": "\"PRICED\""
      },
      "changes": 1,
      "lines": [
        1
      ]
    },
    {
      "target": {
        "className": "io.github.rhinolens.testapp.Order",
        "identityHash": "1b6d3586",
        "entityId": "42",
        "path": "order",
        "label": "Order(id=42)"
      },
      "property": "finalTotal",
      "oldValue": {
        "type": "Double",
        "text": "0.0"
      },
      "newValue": {
        "type": "Double",
        "text": "35.09"
      },
      "changes": 1,
      "lines": [
        2
      ]
    }
  ],
  "observationFailures": []
}
```

## Top level

| Field | Type | Meaning |
|---|---|---|
| `executionId` | string | Unique id of the execution; also on each of its events |
| `ruleName` | string or null | The name given to the execution, else the script name from its first event |
| `attributes` | object | The attributes given to the execution. Numbers and booleans stay as they are; everything else becomes text |
| `startedAt`, `endedAt` | ISO-8601 | `endedAt` is null for a `RUNNING` snapshot |
| `durationNanos` | integer | Total time of the execution |
| `outcome` | `RUNNING` · `SUCCESS` · `FAILED` · `STOPPED` | How it ended |
| `error` | string or null | The script's error, when `FAILED` |
| `truncated` | boolean | True if events were dropped at the per-execution limit |
| `stats` | object | `counts` per kind, `serviceCallNanos`, `overheadNanos` (Lens's own time), `suppressed`, `dropped` |
| `events` | array | Every event kept, in order |
| `netChanges` | array | Per object and property: first old value, last new value, number of changes, lines |
| `observationFailures` | array | Failures inside Lens during this execution. The script wasn't affected |

## Events

Every event has `kind`, `sequence`, `ruleName`, `line`, `offsetNanos` (time since the execution started), `thread` and `time`. Most also have a `target`, and `incomplete: true` appears when part of the capture failed.

| `kind` | Extra fields |
|---|---|
| `PROPERTY` | `property`, `via` (`SETTER` · `ASSIGNMENT` · `FIELD`), `oldValue`, `newValue` |
| `ARRAY_ELEMENT` | `index`, `oldValue`, `newValue` |
| `COLLECTION` | `method`, `args`, `sizeBefore`, `sizeAfter`, `returned`, `error` |
| `SERVICE_CALL` | `service`, `method`, `args`, `result`, `error`, `durationNanos` |
| `MARK` | `label` |
| `OBSERVATION_FAILURE` | `step`, `error` (sent to listeners; reports list them under `observationFailures`) |

### `target`

| Field | Meaning |
|---|---|
| `className` | Fully qualified class |
| `identityHash` | Hex identity hash: tells instances apart within a run |
| `entityId` | From the `IdResolver` (default `getId()`), or null |
| `path` | How the script reached the object, e.g. `order.getItems().get(0)` |
| `label` | `Order(id=42)`, or `Order@1b6d3586` without an id |

### Values

| Field | Meaning |
|---|---|
| `type` | Simple type name: `String`, `Double`, `Order`, `ArrayList`, `js:Object`, `undefined`, `null` |
| `text` | The value as it was at capture time. Strings are quoted. Entities show as `Order(id=42)`, collections as `ArrayList(size=3)`, arrays as `String[2]` |
| `captured` | Present, and `false`, only when the value wasn't read (see [old values](configuration.md#old-values-oldvalues)), or when reading it failed (text `<unavailable: …>`) |
