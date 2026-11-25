# Groovy Config Merger

Merges JSON configuration layers while recording the source of every resulting leaf and a conflict log. The caller chooses whether conflicting values are rejected, preserved, or overwritten.

## Run

Requires JDK 21 and [Groovy 4](https://groovy.apache.org/download). No third-party libraries beyond Groovy's JSON module.

```sh
groovy -cp src bin/merge.groovy override fixtures/base.json fixtures/production.json
groovy -cp src test/MergerTest.groovy
```

The output contains `config`, `provenance`, and `conflicts`. For example, `/service/port` retains its base source, while `/service/timeoutSeconds` becomes 15 and points to the production layer.

## Merge contract

Objects merge recursively. Arrays are atomic values and are replaced as a whole. Explicit JSON `null` is a value, not a deletion instruction. Equal values leave the original provenance intact. Paths use JSON Pointer escaping: `~` becomes `~0` and `/` becomes `~1`. Empty objects and arrays receive their own source entry.

| Policy | Conflicting value |
| --- | --- |
| `reject` | Fails without emitting a merged configuration; CLI exits 2 |
| `keep` | Retains the existing value and records the conflict |
| `override` | Uses the incoming value and removes obsolete child provenance |

Layer order is significant. Inputs are copied before merging. The conflict log includes previous and incoming values, so use redacted sample configuration when sharing output. Environment-variable interpolation and executable configuration are intentionally unsupported.

## Verification

Tests exercise all three policies, nested objects, arrays, nulls, JSON Pointer escapes, type replacement, stale provenance removal, empty-object transitions, duplicate layer names, and input immutability. CI runs the same native Groovy tests and fixture command.

MIT licensed.
